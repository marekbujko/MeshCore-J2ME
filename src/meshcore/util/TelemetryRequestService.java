package meshcore.util;

import java.io.IOException;

import javax.microedition.lcdui.Displayable;

import meshcore.net.FrameTransport;
import meshcore.protocol.MeshProtocolClient;
import meshcore.protocol.ProtocolConstants;

/**
 * Sends CMD_SEND_TELEMETRY_REQ and correlates PUSH_CODE_TELEMETRY_RESPONSE
 * using the 6-byte public key prefix from the protocol.
 */
public final class TelemetryRequestService {

    public interface Listener {
        /**
         * @param ch1Decoded LPP decode for {@link ProtocolConstants#TELEM_CHANNEL_SELF} only (may be several lines).
         * @param rawHex       full payload hex (all channels).
         */
        void onTelemetryResult(
                String contactName,
                String ch1Decoded,
                String rawHex,
                long durationMs,
                Displayable returnTo
        );
    }

    private static final int MAX_HEX_CHARS = 4096;

    private final Listener listener;

    private volatile int pendingContactIdx = -1;
    private volatile long pendingStartMs = -1;
    private volatile String pendingContactName = "";
    private volatile Displayable pendingReturnTo = null;
    private final byte[] pendingPrefix = new byte[6];

    public TelemetryRequestService(Listener listener) {
        this.listener = listener;
    }

    public void sendRequest(
            FrameTransport transport,
            int contactIdx,
            String contactName,
            byte[] publicKey32,
            Displayable returnTo) throws IOException {
        if (transport == null) return;
        if (publicKey32 == null || publicKey32.length != 32) return;

        pendingContactIdx = contactIdx;
        pendingStartMs = System.currentTimeMillis();
        pendingContactName = (contactName != null) ? contactName : "";
        pendingReturnTo = returnTo;
        System.arraycopy(publicKey32, 0, pendingPrefix, 0, 6);

        try {
            MeshProtocolClient.sendTelemetryRequest(transport, publicKey32);
        } catch (IOException e) {
            clearPending();
            throw e;
        }
    }

    public void handleTelemetryResponse(byte[] pubKeyPrefix6, byte[] lppPayload) {
        final int idx = pendingContactIdx;
        if (idx < 0) return;
        if (pubKeyPrefix6 == null || pubKeyPrefix6.length < 6) return;
        for (int i = 0; i < 6; i++) {
            if (pubKeyPrefix6[i] != pendingPrefix[i]) {
                return;
            }
        }

        pendingContactIdx = -1;

        final long durationMs = (pendingStartMs > 0)
                ? (System.currentTimeMillis() - pendingStartMs) : -1;
        pendingStartMs = -1;

        final String name = pendingContactName;
        final Displayable rt = pendingReturnTo;
        pendingReturnTo = null;

        String ch1 = "";
        String hex = "";
        if (lppPayload == null || lppPayload.length == 0) {
            ch1 = "(no payload)";
            hex = "(no payload)";
        } else {
            String dec = CayenneLppDecoder.decodeToText(lppPayload, ProtocolConstants.TELEM_CHANNEL_SELF);
            ch1 = (dec != null && dec.length() > 0) ? dec : "(no decode)";
            hex = formatLppHex(lppPayload);
        }

        if (listener != null) {
            listener.onTelemetryResult(name, ch1, hex, durationMs, rt);
        }
    }

    private static String formatLppHex(byte[] lpp) {
        if (lpp == null || lpp.length == 0) {
            return "(no payload)";
        }
        int n = lpp.length;
        int maxBytes = MAX_HEX_CHARS / 2;
        boolean truncated = n > maxBytes;
        if (truncated) {
            n = maxBytes;
        }
        String h = FrameUtils.bytesToHex(lpp, 0, n);
        if (truncated) {
            return h + "\n...(truncated)";
        }
        return h;
    }

    /** Clear pending state (e.g. user left screen). */
    public void clearPending() {
        pendingContactIdx = -1;
        pendingStartMs = -1;
        pendingReturnTo = null;
    }
}
