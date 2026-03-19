package meshcore.ui;

import java.util.Vector;

/**
 * Shared path-hex formatting and parsing helpers.
 */
public final class PathHexCodec {

    private PathHexCodec() {}

    /** Comma-separated hex summary, e.g. "64, 6F". */
    public static String formatBytesCsv(byte[] b) {
        if (b == null || b.length == 0) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(", ");
            int v = b[i] & 0xFF;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase());
        }
        return sb.toString();
    }

    /** Comma-separated hex summary from Vector<Byte>. */
    public static String formatVectorCsv(Vector byteVector) {
        if (byteVector == null || byteVector.size() == 0) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < byteVector.size(); i++) {
            int v = ((Byte) byteVector.elementAt(i)).byteValue() & 0xFF;
            if (i > 0) sb.append(", ");
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase());
        }
        return sb.toString();
    }

    /** Parse user-typed hex list like "64, 6F" into bytes; returns null on error. */
    public static byte[] parseHexList(String text) {
        Vector out = new Vector();
        StringBuffer tok = new StringBuffer();
        int len = text != null ? text.length() : 0;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == ',' || c == ' ' || c == ';') {
                if (!processHexToken(tok, out)) return null;
                tok.setLength(0);
            } else {
                tok.append(c);
            }
        }
        if (!processHexToken(tok, out)) return null;
        int n = out.size();
        if (n > 64) n = 64;
        byte[] result = new byte[n];
        for (int i = 0; i < n; i++) {
            result[i] = ((Byte) out.elementAt(i)).byteValue();
        }
        return result;
    }

    private static boolean processHexToken(StringBuffer tokBuf, Vector out) {
        if (tokBuf == null) return true;
        String tok = tokBuf.toString().trim();
        if (tok.length() == 0) return true;
        if (tok.startsWith("0x") || tok.startsWith("0X")) tok = tok.substring(2);
        if (tok.length() == 0 || tok.length() > 2) return false;
        for (int i = 0; i < tok.length(); i++) {
            char ch = tok.charAt(i);
            boolean hex = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
            if (!hex) return false;
        }
        int v;
        try {
            v = Integer.parseInt(tok, 16);
        } catch (NumberFormatException e) {
            return false;
        }
        out.addElement(new Byte((byte) v));
        return true;
    }
}

