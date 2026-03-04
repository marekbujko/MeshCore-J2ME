package meshcore.util;

import meshcore.protocol.ProtocolConstants;

public final class TextUtils {

    private TextUtils() {}

    public static String formatBatteryStatus(String info) {
        if (info == null) return "";
        int p1 = info.indexOf('(');
        int p2 = (p1 >= 0) ? info.indexOf(')', p1) : -1;
        if (p1 >= 0 && p2 > p1) {
            return info.substring(p1, p2 + 1).trim(); // e.g. "(4.18V)"
        }
        int m = info.indexOf("mV");
        if (m > 0) {
            return info.substring(0, m + 2).trim(); // e.g. "4100mV"
        }
        return "";
    }

    public static String sanitizeAlertMessage(String s, int maxLen) {
        if (s == null || s.length() == 0) return "New message";
        StringBuffer sb = new StringBuffer();
        int len = Math.min(s.length(), maxLen);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127) sb.append(c);
        }
        return sb.length() > 0 ? sb.toString() : "New message";
    }

    public static String extractDMSender(String line) {
        int s = line.indexOf('[');
        int e = (s >= 0) ? line.indexOf(']', s) : -1;
        return (s >= 0 && e > s) ? line.substring(s + 1, e) : "";
    }

    public static String getErrorCodeMessage(int code) {
        switch (code) {
            case ProtocolConstants.ERR_UNSUPPORTED_CMD: return "unsupported command";
            case ProtocolConstants.ERR_NOT_FOUND:       return "not found";
            case ProtocolConstants.ERR_TABLE_FULL:      return "table full";
            case ProtocolConstants.ERR_BAD_STATE:       return "bad state";
            case ProtocolConstants.ERR_FILE_IO:         return "file I/O error";
            case ProtocolConstants.ERR_ILLEGAL_ARG:     return "illegal argument";
            default: return null;
        }
    }

    public static String computeMainMenuTitle(boolean connected,
                                              boolean reconnectScheduled,
                                              String lastBatteryStatus,
                                              int titleRotationIndex) {
        String voltage = (lastBatteryStatus != null && lastBatteryStatus.length() > 0)
                ? (" " + lastBatteryStatus) : "";
        if (connected) {
            int idx = titleRotationIndex % 3;
            if (idx == 0 || idx == 2) {
                return "Connected" + voltage;
            } else {
                return "MeshCore" + voltage;
            }
        } else {
            int idx = titleRotationIndex % 2;
            return (idx == 0)
                    ? (reconnectScheduled ? "Reconnecting..." : "Disconnected")
                    : "MeshCore";
        }
    }
}

