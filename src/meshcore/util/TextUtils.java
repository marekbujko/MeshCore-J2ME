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

    /** Compact public key display: "<first8...last8>" or "" if hex is empty. */
    public static String formatPublicKeyShort(String hex) {
        if (hex == null) return "";
        hex = hex.trim().toLowerCase();
        if (hex.length() == 0) return "";
        if (hex.length() <= 24) return "<" + hex + ">";
        return "<" + hex.substring(0, 8) + "..." + hex.substring(hex.length() - 8) + ">";
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

    /**
     * Sanitize a generic UI label (contact/repeater name) to characters the device
     * font can reliably show, trimming to maxLen.
     *
     * Rules:
     * - Allow all printable characters (code >= 32), including non-Latin (eg. Cyrillic)
     * - Drop control chars and surrogate halves (used by emoji etc.)
     */
    public static String sanitizeLabel(String s, int maxLen) {
        if (s == null || s.length() == 0) return "";
        StringBuffer sb = new StringBuffer();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c < 32) continue;                    // control chars
            if (c >= 0xD800 && c <= 0xDFFF) continue; // surrogate halves (emoji etc.)
            sb.append(c);
            if (maxLen > 0 && sb.length() >= maxLen) break;
        }
        return sb.toString();
    }

    /**
     * Sanitize message text before storing/rendering.
     * Keeps printable characters (including non-Latin) plus basic whitespace (\n, \r, \t)
     * and drops control chars and surrogate halves (common for emojis).
     * If maxLen > 0, trims to that many characters.
     */
    public static String sanitizeMessage(String s, int maxLen) {
        if (s == null || s.length() == 0) return "";
        StringBuffer sb = new StringBuffer();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            // allow basic whitespace
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);
            } else if (c >= 32 && !(c >= 0xD800 && c <= 0xDFFF)) {
                // printable and not a surrogate (skip emoji etc.)
                sb.append(c);
            } else {
                continue;
            }
            if (maxLen > 0 && sb.length() >= maxLen) break;
        }
        // If everything was stripped (e.g. message only contained unsupported emoji),
        // return a small placeholder so the bubble is not completely empty.
        if (sb.length() == 0 && len > 0) {
            return "[emoji]";
        }
        return sb.toString();
    }

    /**
     * Truncate a string so that its UTF-8 encoding fits within maxBytes.
     * Preserves whole characters (does not cut inside a multi-byte sequence).
     */
    public static String truncateUtf8ToBytes(String s, int maxBytes) {
        if (s == null || s.length() == 0 || maxBytes <= 0) return "";
        StringBuffer out = new StringBuffer();
        int used = 0;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            String one = String.valueOf(c);
            byte[] enc;
            try {
                enc = one.getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                enc = one.getBytes();
            }
            if (used + enc.length > maxBytes) {
                break;
            }
            out.append(c);
            used += enc.length;
        }
        return out.toString();
    }

    /** URL-encode for query strings, using UTF-8-like semantics: space -> '+', others -> %HH as needed. */
    public static String urlEncode(String s) {
        if (s == null) return "";
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else if (c == ' ') {
                out.append('+');
            } else {
                int b = c;
                if (b < 0) b += 256;
                int hi = (b >> 4) & 0xF;
                int lo = b & 0xF;
                out.append('%');
                out.append(intToHex(hi));
                out.append(intToHex(lo));
            }
        }
        return out.toString();
    }

    private static char intToHex(int v) {
        return (char) (v < 10 ? ('0' + v) : ('A' + (v - 10)));
    }

    /** Basic URL-decoder for query values (handles + and %HH). */
    public static String urlDecode(String s) {
        if (s == null) return "";
        StringBuffer out = new StringBuffer();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '+') {
                out.append(' ');
            } else if (c == '%' && i + 2 < len) {
                char c1 = s.charAt(i + 1);
                char c2 = s.charAt(i + 2);
                int hi = hexToInt(c1);
                int lo = hexToInt(c2);
                if (hi >= 0 && lo >= 0) {
                    out.append((char) ((hi << 4) | lo));
                    i += 2;
                } else {
                    out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int hexToInt(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
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

    public static String escapeNewlines(String s) {
        if (s == null || s.length() == 0) return s;
        StringBuffer out = new StringBuffer();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String unescapeNewlines(String s) {
        if (s == null || s.length() == 0) return s;
        StringBuffer out = new StringBuffer();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char n = s.charAt(i + 1);
                if (n == 'n') {
                    out.append('\n');
                    i++;
                    continue;
                } else if (n == 'r') {
                    out.append('\r');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    public static String formatNowDateTime() {
        long nowMs = System.currentTimeMillis();
        long totalSeconds = nowMs / 1000L;

        int sec = (int) (totalSeconds % 60L);
        long totalMinutes = totalSeconds / 60L;
        int min = (int) (totalMinutes % 60L);
        long totalHours = totalMinutes / 60L;
        int hour = (int) (totalHours % 24L);
        long totalDays = totalHours / 24L;

        int year = 1970;
        while (true) {
            int daysInYear = isLeapYear(year) ? 366 : 365;
            if (totalDays >= daysInYear) {
                totalDays -= daysInYear;
                year++;
            } else {
                break;
            }
        }

        int[] monthDays = new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (isLeapYear(year)) {
            monthDays[1] = 29;
        }

        int month = 0;
        while (month < 12 && totalDays >= monthDays[month]) {
            totalDays -= monthDays[month];
            month++;
        }
        int day = (int) totalDays + 1;
        int monthOneBased = month + 1;

        StringBuffer sb = new StringBuffer();
        if (day < 10) sb.append('0');
        sb.append(day).append('/');
        String[] monthNames = new String[]{
                "Jan","Feb","Mar","Apr","May","Jun",
                "Jul","Aug","Sep","Oct","Nov","Dec"
        };
        if (monthOneBased >= 1 && monthOneBased <= 12) {
            sb.append(monthNames[monthOneBased - 1]);
        } else {
            sb.append("???");
        }
        sb.append(' ');
        if (hour < 10) sb.append('0');
        sb.append(hour).append(':');
        if (min < 10) sb.append('0');
        sb.append(min);
        return sb.toString();
    }

    private static boolean isLeapYear(int year) {
        if ((year % 4) != 0) return false;
        if ((year % 100) != 0) return true;
        return (year % 400) == 0;
    }
}

