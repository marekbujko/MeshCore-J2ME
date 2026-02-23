package meshcore.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import meshcore.protocol.ProtocolConstants;

/**
 * TCP frame transport for MeshCore Companion Radio protocol.
 * Frame format: '>' + len_lo + len_hi + frame_bytes (outbound: radio->app)
 *              '<' + len_lo + len_hi + frame_bytes (inbound: app->radio)
 */
public class FrameTransport {

    private final InputStream in;
    private final OutputStream out;

    public FrameTransport(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    public synchronized void sendFrame(byte[] frame) throws IOException {
        int len = frame.length;
        out.write(ProtocolConstants.FRAME_INBOUND_MARKER);
        out.write(len & 0xFF);
        out.write((len >> 8) & 0xFF);
        out.write(frame);
        out.flush();
    }

    public byte[] readFrame() throws IOException {
        int b;
        do {
            b = in.read();
            if (b < 0) throw new IOException("EOF");
        } while (b != ProtocolConstants.FRAME_OUTBOUND_MARKER);
        int lo = in.read(), hi = in.read();
        if (lo < 0 || hi < 0) throw new IOException("EOF");
        int len = lo | (hi << 8);
        if (len <= 0 || len > ProtocolConstants.FRAME_MAX_LEN) return new byte[0];
        byte[] frame = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(frame, read, len - read);
            if (n < 0) throw new IOException("EOF");
            read += n;
        }
        return frame;
    }

    public static void writeUint32LE(byte[] b, int o, long v) {
        b[o]     = (byte) (v & 0xFF);
        b[o + 1] = (byte) ((v >> 8) & 0xFF);
        b[o + 2] = (byte) ((v >> 16) & 0xFF);
        b[o + 3] = (byte) ((v >> 24) & 0xFF);
    }

    public static long readUint32LE(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o + 1] & 0xFFL) << 8)
            | ((b[o + 2] & 0xFFL) << 16) | ((b[o + 3] & 0xFFL) << 24);
    }
}
