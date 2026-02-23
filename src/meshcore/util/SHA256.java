package meshcore.util;

/**
 * Pure Java SHA-256 for J2ME / CLDC 1.1.
 * No java.security dependency. Used for MeshCore channel secret generation.
 */
public final class SHA256 {

    private SHA256() {}

    /** SHA-256 digest size in bytes */
    private static final int DIGEST_SIZE = 32;

    /** First 16 bytes of SHA-256(name) for channel secret (matches meshcore_py) */
    public static byte[] channelSecret(byte[] name) {
        byte[] hash = digest(name);
        byte[] result = new byte[16];
        System.arraycopy(hash, 0, result, 0, 16);
        return result;
    }

    /** UTF-8 SHA-256 channel secret for a string (e.g. channel name) */
    public static byte[] channelSecret(String name) {
        try {
            return channelSecret(name.getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            return channelSecret(name.getBytes());
        }
    }

    /** Full SHA-256 hash */
    public static byte[] digest(byte[] input) {
        int[] H = new int[8];
        H[0] = 0x6a09e667;
        H[1] = 0xbb67ae85;
        H[2] = 0x3c6ef372;
        H[3] = 0xa54ff53a;
        H[4] = 0x510e527f;
        H[5] = 0x9b05688c;
        H[6] = 0x1f83d9ab;
        H[7] = 0x5be0cd19;

        long bitLen = (long) input.length * 8L;
        int padLen = (55 - (input.length % 64) + 64) % 64;
        int totalLen = input.length + 1 + padLen + 8;
        byte[] block = new byte[totalLen];
        System.arraycopy(input, 0, block, 0, input.length);
        block[input.length] = (byte) 0x80;

        for (int i = 0; i < 8; i++) {
            block[totalLen - 8 + i] = (byte) (bitLen >>> (56 - i * 8));
        }

        int[] W = new int[64];
        for (int off = 0; off < totalLen; off += 64) {
            for (int t = 0; t < 16; t++) {
                int i = off + t * 4;
                W[t] = ((block[i] & 0xFF) << 24) | ((block[i + 1] & 0xFF) << 16)
                        | ((block[i + 2] & 0xFF) << 8) | (block[i + 3] & 0xFF);
            }
            for (int t = 16; t < 64; t++) {
                W[t] = sigma1(W[t - 2]) + W[t - 7] + sigma0(W[t - 15]) + W[t - 16];
            }
            int a = H[0], b = H[1], c = H[2], d = H[3];
            int e = H[4], f = H[5], g = H[6], h = H[7];
            for (int t = 0; t < 64; t++) {
                int T1 = h + bigSigma1(e) + ch(e, f, g) + K[t] + W[t];
                int T2 = bigSigma0(a) + maj(a, b, c);
                h = g; g = f; f = e; e = d + T1; d = c; c = b; b = a; a = T1 + T2;
            }
            H[0] += a; H[1] += b; H[2] += c; H[3] += d;
            H[4] += e; H[5] += f; H[6] += g; H[7] += h;
        }

        byte[] out = new byte[DIGEST_SIZE];
        for (int i = 0; i < 8; i++) {
            out[i * 4] = (byte) (H[i] >>> 24);
            out[i * 4 + 1] = (byte) (H[i] >>> 16);
            out[i * 4 + 2] = (byte) (H[i] >>> 8);
            out[i * 4 + 3] = (byte) H[i];
        }
        return out;
    }

    private static int rotr(int x, int n) {
        return (x >>> n) | (x << (32 - n));
    }

    private static int ch(int x, int y, int z) {
        return (x & y) ^ ((~x) & z);
    }

    private static int maj(int x, int y, int z) {
        return (x & y) ^ (x & z) ^ (y & z);
    }

    private static int bigSigma0(int x) {
        return rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22);
    }

    private static int bigSigma1(int x) {
        return rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25);
    }

    private static int sigma0(int x) {
        return rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3);
    }

    private static int sigma1(int x) {
        return rotr(x, 17) ^ rotr(x, 19) ^ (x >>> 10);
    }

    private static final int[] K = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
        0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
        0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
        0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };
}
