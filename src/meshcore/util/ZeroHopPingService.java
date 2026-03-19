package meshcore.util;

import javax.microedition.lcdui.Displayable;

import java.io.IOException;

import meshcore.net.FrameTransport;
import meshcore.protocol.MeshProtocolClient;
import meshcore.protocol.ProtocolConstants;

/**
 * Handles "Ping (Zero Hop)" by sending CMD_SEND_TRACE_PATH and correlating
 * the response PUSH_TRACE_DATA frames.
 *
 * This is intentionally UI-agnostic: it reports a result via a listener.
 */
public final class ZeroHopPingService {

    public interface Listener {
        void onZeroHopPingResult(
                int contactIdx,
                String contactName,
                int pathHops,
                String snrForward,
                String snrBack,
                long durationMs,
                Displayable returnTo
        );
    }

    private final Listener listener;

    private volatile long pendingTag = -1;
    private volatile long pendingAuth = -1;
    private volatile long pendingStartMs = -1;
    private volatile int pendingContactIdx = -1;
    private volatile String pendingContactName = "";
    private volatile Displayable pendingReturnTo = null;

    public ZeroHopPingService(Listener listener) {
        this.listener = listener;
    }

    public void sendPing(FrameTransport transport,
                          int contactIdx,
                          String contactName,
                          byte destPrefix,
                          Displayable returnTo) throws IOException {
        if (transport == null) return;

        long nowSec = System.currentTimeMillis() / 1000L;
        int tag = (int) (nowSec & 0xFFFFFFFFL);
        int auth = (int) ((nowSec ^ (contactIdx * 2654435761L)) & 0xFFFFFFFFL);
        int flags = 0; // simple reachability / SNR ping

        pendingTag = tag & 0xFFFFFFFFL;
        pendingAuth = auth & 0xFFFFFFFFL;
        pendingStartMs = System.currentTimeMillis();
        pendingContactIdx = contactIdx;
        pendingContactName = (contactName != null) ? contactName : "";
        pendingReturnTo = returnTo;

        MeshProtocolClient.sendTracePath(transport, tag, auth, flags, new byte[]{destPrefix});
    }

    public void handleTraceData(
            int flags,
            long tag,
            long authCode,
            byte[] pathHashes,
            byte[] pathSnrs,
            int finalSNR4) {
        final long pTag = pendingTag;
        final long pAuth = pendingAuth;
        final int contactIdx = pendingContactIdx;

        if (contactIdx < 0) return;
        if (pTag < 0 || pAuth < 0) return;
        if (tag != pTag) return;
        if (authCode != pAuth) return;

        // Clear pending so we don't double-fire.
        pendingContactIdx = -1;
        pendingTag = -1;
        pendingAuth = -1;

        final long durationMs = (pendingStartMs > 0)
                ? (System.currentTimeMillis() - pendingStartMs) : -1;
        pendingStartMs = -1;

        final int pathLen = (pathHashes != null) ? pathHashes.length : 0;
        final String snrForward = formatSnrForward(pathSnrs);
        final String snrBack = formatSnr(finalSNR4);

        if (listener != null) {
            listener.onZeroHopPingResult(
                    contactIdx,
                    pendingContactName,
                    pathLen,
                    snrForward,
                    snrBack,
                    durationMs,
                    pendingReturnTo
            );
        }
    }

    private static String formatSnrForward(byte[] pathSnrs) {
        if (pathSnrs == null || pathSnrs.length == 0) return "n/a";
        // Signed int8 stored in byte array; convert to int sign-extended.
        int snr4 = (int) pathSnrs[0];
        return formatSnr(snr4);
    }

    /**
     * Convert SNR*4 (0.25dB steps) into "<whole>.<frac> dB".
     * Example: -12 => -3.00? Actually -12/4 = -3.0 and remainder controls 0.25 increments.
     */
    private static String formatSnr(int snr4) {
        boolean neg = snr4 < 0;
        int abs = neg ? -snr4 : snr4;
        int whole = abs / 4;
        int fracQ = abs % 4; // 0..3
        int fracDec = fracQ * 25; // 0,25,50,75
        String fracStr = fracDec < 10 ? "0" + fracDec : String.valueOf(fracDec);
        return (neg ? "-" : "") + whole + "." + fracStr + " dB";
    }
}

