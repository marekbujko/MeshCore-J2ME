package meshcore.util;

import java.io.UnsupportedEncodingException;

/**
 * Utilities for parsing MeshCore protocol frame payloads.
 */
public final class FrameUtils {

    private FrameUtils() {}

    public static String extractString(byte[] b, int off, int max) {
        StringBuffer sb = new StringBuffer();
        for (int i = off; i < off + max && i < b.length; i++) {
            if (b[i] == 0) break;
            sb.append((char) (b[i] & 0xFF));
        }
        return sb.toString();
    }

    public static String extractVarchar(byte[] b, int off) {
        if (off >= b.length) return "";
        try {
            return new String(b, off, b.length - off, "UTF-8").trim();
        } catch (UnsupportedEncodingException e) {
            return new String(b, off, b.length - off).trim();
        }
    }

    public static String bytesToHex(byte[] b, int off, int len) {
        final char[] H = "0123456789abcdef".toCharArray();
        StringBuffer sb = new StringBuffer();
        for (int i = off; i < off + len && i < b.length; i++) {
            sb.append(H[(b[i] & 0xFF) >> 4]).append(H[b[i] & 0xF]);
        }
        return sb.toString();
    }
}
