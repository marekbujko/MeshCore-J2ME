package meshcore.protocol;

import java.util.Vector;

import meshcore.net.FrameTransport;
import meshcore.util.FrameUtils;
import meshcore.util.TextUtils;

/**
 * Parses incoming protocol frames and dispatches to listener.
 */
public final class FrameHandler {

    private final FrameHandlerListener listener;
    private final Vector contactNames;
    private final Vector contactKeys;
    private final Vector contactTypes;
    private final Vector contactPathHops;
    private final Vector contactPathBytes;
    private final Vector contactFlags;
    private final Vector contactLastAdvert;

    public FrameHandler(FrameHandlerListener listener, Vector contactNames, Vector contactKeys, Vector contactTypes,
                        Vector contactPathHops, Vector contactPathBytes, Vector contactFlags, Vector contactLastAdvert) {
        this.listener = listener;
        this.contactNames = contactNames;
        this.contactKeys = contactKeys;
        this.contactTypes = contactTypes;
        this.contactPathHops = contactPathHops;
        this.contactPathBytes = contactPathBytes;
        this.contactFlags = contactFlags;
        this.contactLastAdvert = contactLastAdvert;
    }

    public void handleFrame(byte[] f) {
        int code = f[0] & 0xFF;

        if (code == ProtocolConstants.RESP_DEVICE_INFO) {
            handleDeviceInfo(f);
        } else if (code == ProtocolConstants.RESP_SELF_INFO) {
            handleSelfInfo(f);
        } else if (code == ProtocolConstants.RESP_CONTACTS_START) {
            contactNames.removeAllElements();
            contactKeys.removeAllElements();
            contactTypes.removeAllElements();
            contactPathHops.removeAllElements();
            contactPathBytes.removeAllElements();
            contactFlags.removeAllElements();
            contactLastAdvert.removeAllElements();
            listener.onContactsStart();
        } else if (code == ProtocolConstants.RESP_CONTACT) {
            handleContact(f);
        } else if (code == ProtocolConstants.RESP_END_CONTACTS) {
            handleContactsEnd(f);
        } else if (code == ProtocolConstants.RESP_CHANNEL_MSG || code == ProtocolConstants.RESP_CHANNEL_MSG_V3) {
            handleChannelMessage(f, code);
        } else if (code == ProtocolConstants.RESP_CONTACT_MSG || code == ProtocolConstants.RESP_CONTACT_MSG_V3) {
            handleContactMessage(f, code);
        } else if (code == ProtocolConstants.RESP_BATT_STORAGE) {
            handleBattery(f);
        } else if (code == ProtocolConstants.PUSH_MSG_WAITING) {
            listener.trySyncMessages();
        } else if (code == ProtocolConstants.PUSH_SEND_CONFIRMED) {
            listener.appendActivityLog("[*] Delivered!");
            listener.onMessageDelivered();
        } else if (code == ProtocolConstants.PUSH_ADVERT || code == ProtocolConstants.PUSH_PATH_UPDATED) {
            new Thread(new Runnable() {
                public void run() {
                    listener.sendGetContacts();
                }
            }).start();
        } else if (code == ProtocolConstants.RESP_SENT) {
            listener.appendActivityLog("[*] Sent OK");
        } else if (code == ProtocolConstants.RESP_CURR_TIME) {
            handleDeviceTime(f);
        } else if (code == ProtocolConstants.RESP_STATS) {
            handleStats(f);
        } else if (code == ProtocolConstants.RESP_CHANNEL_INFO) {
            handleChannelInfo(f);
        } else if (code == ProtocolConstants.RESP_ERR) {
            if (f.length > 1) listener.onError(f[1] & 0xFF);
        } else if (code == ProtocolConstants.PUSH_TRACE_DATA) {
            handleTraceData(f);
        }
    }

    private void handleDeviceInfo(byte[] f) {
        String ver = "";
        if (f.length >= 2) {
            int fwVer = f[1] & 0xFF;
            if (f.length >= 80) {
                String verStr = FrameUtils.extractString(f, 60, 20).trim();
                if (verStr.length() > 0) {
                    ver = verStr;
                } else {
                    ver = "fw" + fwVer;
                }
            } else if (f.length >= 20) {
                String fwBuild = FrameUtils.extractString(f, 8, 12).trim();
                ver = "fw" + fwVer + (fwBuild.length() > 0 ? " " + fwBuild : "");
            } else {
                ver = "fw" + fwVer;
            }
        }
        listener.onDeviceInfo(ver);
    }

    private void handleSelfInfo(byte[] f) {
        int txPwr = f.length > 3 ? f[2] & 0xFF : 0;
        byte[] nodePublicKey = null;
        if (f.length >= 36) {
            nodePublicKey = new byte[32];
            System.arraycopy(f, 4, nodePublicKey, 0, 32);
        }
        long freqRaw = f.length > 55 ? FrameTransport.readUint32LE(f, 48) : 0;
        long bwRaw = f.length > 59 ? FrameTransport.readUint32LE(f, 52) : 0;
        int sf = f.length > 56 ? f[56] & 0xFF : 0;
        int cr = f.length > 57 ? f[57] & 0xFF : 0;
        String name = f.length > 58 ? FrameUtils.extractVarchar(f, 58) : "";
        listener.onSelfInfo(name, txPwr, freqRaw, bwRaw, sf, cr, nodePublicKey);
    }

    private void handleContact(byte[] f) {
        if (f.length >= 132) {
            byte[] key = new byte[32];
            System.arraycopy(f, 1, key, 0, 32);
            int type = f[33] & 0xFF;
            int outPathLen = f.length >= 36 ? (byte) f[35] : 0;
            int hops;
            if (outPathLen == (byte) 0xFF) {
                // Firmware uses -1 (0xFF) to mean "no fixed path" (flood/unknown).
                hops = -1;
            } else if (outPathLen > 0 && outPathLen <= 64) {
                hops = outPathLen;
            } else {
                // 0 from firmware means direct mode (no repeaters).
                hops = 0;
            }
            String name = FrameUtils.extractString(f, 100, 32);
            if (name.length() == 0) {
                name = FrameUtils.bytesToHex(key, 0, 3);
            }
            // Strip unsupported characters (e.g. emojis) so UI can render cleanly.
            name = TextUtils.sanitizeLabel(name, 32);
            contactKeys.addElement(key);
            contactNames.addElement(name);
            contactTypes.addElement(new Integer(type));
            contactPathHops.addElement(new Integer(hops));
            byte[] path = null;
            if (hops > 0 && f.length >= 36 + hops) {
                path = new byte[hops];
                System.arraycopy(f, 36, path, 0, hops);
            }
            contactPathBytes.addElement(path);
            int flags = f.length >= 35 ? (f[34] & 0xFF) : 0;
            long lastAdv = f.length >= 136 ? FrameTransport.readUint32LE(f, 132) : 0;
            contactFlags.addElement(new Integer(flags));
            contactLastAdvert.addElement(new Long(lastAdv));
        }
    }

    private void handleContactsEnd(byte[] f) {
        listener.appendActivityLog("[*] Contacts synced: " + contactNames.size());
        listener.onContactsEnd();
    }

    private void handleChannelMessage(byte[] f, int code) {
        int chIdx = (code == ProtocolConstants.RESP_CHANNEL_MSG_V3) ? (f[4] & 0xFF) : (f[1] & 0xFF);
        int off = (code == ProtocolConstants.RESP_CHANNEL_MSG_V3) ? 11 : 8;
        String text = FrameUtils.extractVarchar(f, off);
        text = TextUtils.sanitizeMessage(text, 0);
        // Preserve any leading "[sender]" prefix from the node so the UI can
        // treat it as a sender label above the bubble, not inside it.
        listener.appendChannel(chIdx, TextUtils.escapeNewlines(text));
        listener.trySyncMessages();
    }

    private void handleContactMessage(byte[] f, int code) {
        int pkOff = (code == ProtocolConstants.RESP_CONTACT_MSG_V3) ? 4 : 1;
        int txtTypeOff = (code == ProtocolConstants.RESP_CONTACT_MSG_V3) ? 11 : 8;
        int textOff = (code == ProtocolConstants.RESP_CONTACT_MSG_V3) ? 16 : 13;
        if (f.length > txtTypeOff && (f[txtTypeOff] & 0xFF) == 2) {
            textOff += 4;
        }
        int idx = findContactIndexByPrefix(f, pkOff);
        String sender = (idx >= 0) ? (String) contactNames.elementAt(idx) : FrameUtils.bytesToHex(f, pkOff, 3);
        String text = FrameUtils.extractVarchar(f, textOff);
        text = TextUtils.sanitizeMessage(text, 0);
        listener.appendDM(idx, "[" + sender + "] " + TextUtils.escapeNewlines(text));
        listener.appendActivityLog("[DM from " + sender + "]");
        listener.trySyncMessages();
    }

    private void handleBattery(byte[] f) {
        if (f.length >= 3) {
            int mv = (f[1] & 0xFF) | ((f[2] & 0xFF) << 8);
            int vInt = mv / 1000;
            int frac = (mv % 1000) / 10; // two decimals
            String fracStr = (frac < 10) ? ("0" + frac) : String.valueOf(frac);
            String s = mv + "mV (" + vInt + "." + fracStr + "V)";
            if (f.length >= 11) {
                s += "  " + FrameTransport.readUint32LE(f, 3) + "/" + FrameTransport.readUint32LE(f, 7) + " KB";
            }
            listener.onBatteryUpdate(s);
        }
    }

    private void handleTraceData(byte[] f) {
        // PUSH_TRACE_DATA (0x89) layout:
        // [0]   code
        // [1]   reserved (0)
        // [2]   path_len
        // [3]   flags
        // [4..7]   tag (uint32 LE)
        // [8..11]  auth_code (uint32 LE)
        // [12..]   path_hashes[path_len], path_snrs[path_len], final_snr(int8)
        if (f.length < 12 + 1) return;
        if (f.length < 14) return;

        int pathLen = f[2] & 0xFF;
        int flags = f[3] & 0xFF;
        long tag = (f.length >= 8) ? FrameTransport.readUint32LE(f, 4) : 0;
        long auth = (f.length >= 12) ? FrameTransport.readUint32LE(f, 8) : 0;

        int off = 12;
        int need = off + pathLen + pathLen + 1;
        if (f.length < need) return;

        byte[] hashes = new byte[pathLen];
        System.arraycopy(f, off, hashes, 0, pathLen);
        off += pathLen;

        byte[] snrs = new byte[pathLen];
        System.arraycopy(f, off, snrs, 0, pathLen);
        off += pathLen;

        int finalSnr4 = f[off]; // signed int8
        listener.onTraceData(flags, tag, auth, hashes, snrs, finalSnr4);
    }

    private void handleDeviceTime(byte[] f) {
        if (f.length >= 5) {
            long epoch = FrameTransport.readUint32LE(f, 1);
            listener.onDeviceTime("Node time: " + epoch + " (epoch)");
        }
    }

    private void handleStats(byte[] f) {
        if (f.length < 2) return;
        int subType = f[1] & 0xFF;
        if (subType == 0 && f.length >= 11) {
            int batt = (f[2] & 0xFF) | ((f[3] & 0xFF) << 8);
            if (batt > 32767) batt -= 65536;
            long uptime = FrameTransport.readUint32LE(f, 4);
            int queueLen = f[10] & 0xFF;
            String content = "Batt: " + batt + "mV\nUptime: " + uptime + "s\nQueue: " + queueLen;
            listener.onStats("Stats", content);
        } else if (subType == 1 && f.length >= 14) {
            int noise = (f[2] & 0xFF) | ((f[3] & 0xFF) << 8);
            if (noise >= 32768) noise -= 65536;
            listener.onNoiseFloor(noise);
        }
    }

    private void handleChannelInfo(byte[] f) {
        if (f.length < 34) return;
        int chIdx = f[1] & 0xFF;
        String name = FrameUtils.extractString(f, 2, 32);
        if (name != null && name.length() > 0) {
            listener.onChannelInfo(chIdx, name);
        }
    }

    private String findContactByPrefix(byte[] frame, int off) {
        int idx = findContactIndexByPrefix(frame, off);
        return (idx >= 0) ? (String) contactNames.elementAt(idx) : FrameUtils.bytesToHex(frame, off, 3);
    }

    private int findContactIndexByPrefix(byte[] frame, int off) {
        for (int i = 0; i < contactKeys.size(); i++) {
            byte[] key = (byte[]) contactKeys.elementAt(i);
            boolean match = true;
            for (int j = 0; j < 6 && off + j < frame.length; j++) {
                if (key[j] != frame[off + j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }
}
