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

    /** Parse hex string to bytes. Returns null if invalid. Expects even length (2 chars = 1 byte). */
    public static byte[] hexDecode(String hex) {
        if (hex == null) return null;
        hex = hex.trim().toLowerCase();
        int len = hex.length();
        if (len % 2 != 0 || len == 0) return null;
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = hexChar(hex.charAt(i * 2));
            int lo = hexChar(hex.charAt(i * 2 + 1));
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static int hexChar(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }
}
