package meshcore.protocol;

import java.util.Vector;

import meshcore.net.FrameTransport;
import meshcore.util.FrameUtils;

/**
 * Parses incoming protocol frames and dispatches to listener.
 */
public final class FrameHandler {

    private final FrameHandlerListener listener;
    private final Vector contactNames;
    private final Vector contactKeys;
    private final Vector contactTypes;

    public FrameHandler(FrameHandlerListener listener, Vector contactNames, Vector contactKeys, Vector contactTypes) {
        this.listener = listener;
        this.contactNames = contactNames;
        this.contactKeys = contactKeys;
        this.contactTypes = contactTypes;
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
            String name = FrameUtils.extractString(f, 100, 32);
            if (name.length() == 0) name = FrameUtils.bytesToHex(key, 0, 3);
            contactKeys.addElement(key);
            contactNames.addElement(name);
            contactTypes.addElement(new Integer(type));
        }
    }

    private void handleContactsEnd(byte[] f) {
        listener.appendActivityLog("[*] Contacts synced: " + contactNames.size());
        listener.onContactsEnd();
    }

    private void handleChannelMessage(byte[] f, int code) {
        int chIdx = (code == ProtocolConstants.RESP_CHANNEL_MSG_V3) ? (f[4] & 0xFF) : (f[1] & 0xFF);
        int off = (code == ProtocolConstants.RESP_CHANNEL_MSG_V3) ? 11 : 8;
        listener.appendChannel(chIdx, "[CH] " + FrameUtils.extractVarchar(f, off));
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
        listener.appendDM(idx, "[" + sender + "] " + text);
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

    private void handleDeviceTime(byte[] f) {
        if (f.length >= 5) {
            long epoch = FrameTransport.readUint32LE(f, 1);
            listener.onDeviceTime("Node time: " + epoch + " (epoch)");
        }
    }

    private void handleStats(byte[] f) {
        if (f.length >= 11 && (f[1] & 0xFF) == 0) {
            int batt = (f[2] & 0xFF) | ((f[3] & 0xFF) << 8);
            if (batt > 32767) batt -= 65536;
            long uptime = FrameTransport.readUint32LE(f, 4);
            int queueLen = f[10] & 0xFF;
            listener.onStats("Stats", "Batt: " + batt + "mV\nUptime: " + uptime + "s\nQueue: " + queueLen);
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
