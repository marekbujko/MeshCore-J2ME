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

    public FrameHandler(FrameHandlerListener listener, Vector contactNames, Vector contactKeys) {
        this.listener = listener;
        this.contactNames = contactNames;
        this.contactKeys = contactKeys;
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
        } else if (code == ProtocolConstants.RESP_ERR) {
            if (f.length > 1) listener.onError(f[1] & 0xFF);
        }
    }

    private void handleDeviceInfo(byte[] f) {
        String ver = "";
        if (f.length >= 2) ver = "fw" + (f[1] & 0xFF);
        if (f.length >= 17) ver += " " + FrameUtils.extractString(f, 5, 12).trim();
        listener.onDeviceInfo(ver);
    }

    private void handleSelfInfo(byte[] f) {
        int txPwr = f.length > 4 ? f[2] & 0xFF : 0;
        long freq = f.length > 52 ? FrameTransport.readUint32LE(f, 40) : 0;
        long bw = f.length > 52 ? FrameTransport.readUint32LE(f, 44) : 0;
        int sf = f.length > 52 ? f[48] & 0xFF : 0;
        int cr = f.length > 52 ? f[49] & 0xFF : 0;
        String name = f.length > 50 ? FrameUtils.extractVarchar(f, 50) : "";
        listener.onSelfInfo(name, txPwr, freq, bw, sf, cr);
    }

    private void handleContact(byte[] f) {
        if (f.length >= 132) {
            byte[] key = new byte[32];
            System.arraycopy(f, 1, key, 0, 32);
            String name = FrameUtils.extractString(f, 100, 32);
            if (name.length() == 0) name = FrameUtils.bytesToHex(key, 0, 3);
            contactKeys.addElement(key);
            contactNames.addElement(name);
        }
    }

    private void handleContactsEnd(byte[] f) {
        listener.appendActivityLog("[*] Contacts synced: " + contactNames.size());
        listener.onContactsEnd();
    }

    private void handleChannelMessage(byte[] f, int code) {
        int chIdx = (code == ProtocolConstants.RESP_CHANNEL_MSG_V3) ? (f[4] & 0xFF) : (f[1] & 0xFF);
        int off = (code == ProtocolConstants.RESP_CHANNEL_MSG_V3) ? 10 : 7;
        listener.appendChannel(chIdx, "[CH] " + FrameUtils.extractVarchar(f, off));
        listener.trySyncMessages();
    }

    private void handleContactMessage(byte[] f, int code) {
        int pkOff = (code == ProtocolConstants.RESP_CONTACT_MSG_V3) ? 4 : 1;
        int textOff = (code == ProtocolConstants.RESP_CONTACT_MSG_V3) ? 13 : 9;
        String sender = findContactByPrefix(f, pkOff);
        String text = FrameUtils.extractVarchar(f, textOff);
        listener.appendDM("[" + sender + "] " + text);
        listener.appendActivityLog("[DM from " + sender + "]");
        listener.trySyncMessages();
    }

    private void handleBattery(byte[] f) {
        if (f.length >= 3) {
            int mv = (f[1] & 0xFF) | ((f[2] & 0xFF) << 8);
            String s = mv + "mV (" + (mv / 1000) + "." + ((mv % 1000) / 100) + "V)";
            if (f.length >= 11) {
                s += "  " + FrameTransport.readUint32LE(f, 3) + "/" + FrameTransport.readUint32LE(f, 7) + " KB";
            }
            listener.onBatteryUpdate(s);
        }
    }

    private void handleDeviceTime(byte[] f) {
        if (f.length >= 5) {
            long epoch = FrameTransport.readUint32LE(f, 1);
            listener.onDeviceTime("Device time: " + epoch + " (epoch)");
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

    private String findContactByPrefix(byte[] frame, int off) {
        for (int i = 0; i < contactKeys.size(); i++) {
            byte[] key = (byte[]) contactKeys.elementAt(i);
            boolean match = true;
            for (int j = 0; j < 6 && off + j < frame.length; j++) {
                if (key[j] != frame[off + j]) {
                    match = false;
                    break;
                }
            }
            if (match) return (String) contactNames.elementAt(i);
        }
        return FrameUtils.bytesToHex(frame, off, 3);
    }
}
