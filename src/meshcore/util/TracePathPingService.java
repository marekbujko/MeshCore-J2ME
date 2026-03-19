package meshcore.util;

import javax.microedition.lcdui.Displayable;

import java.io.IOException;

import meshcore.net.FrameTransport;
import meshcore.protocol.MeshProtocolClient;

/**
 * Sends TRACE packet (companion protocol) for the forward path only.
 *
 * Correlates response via (tag, auth_code).
 */
public final class TracePathPingService {

    public interface Listener {
        void onStageResult(
                byte[] pathBytes,
                byte[] pathSnrs,
                int finalSNR4,
                long durationMs,
                Displayable returnTo
        );
    }

    private final Listener listener;

    private FrameTransport transport;
    private Displayable returnTo;

    private byte[] forwardPath;

    private volatile long pendingTag = -1;
    private volatile long pendingAuth = -1;
    private volatile long pendingStartMs = -1;

    public TracePathPingService(Listener listener) {
        this.listener = listener;
    }

    public void start(FrameTransport transport,
                       byte[] forwardPath,
                       Displayable returnTo) throws IOException {
        if (transport == null) return;
        if (forwardPath == null || forwardPath.length == 0) return;

        this.transport = transport;
        this.returnTo = returnTo;
        this.forwardPath = forwardPath;
        sendForwardPing();
    }

    private void sendForwardPing() throws IOException {
        if (transport == null) return;

        long nowSec = System.currentTimeMillis() / 1000L;
        int tag = (int) (nowSec & 0xFFFFFFFFL);
        // Keep auth deterministic for this send (stage-independent).
        int auth = (int) (nowSec & 0xFFFFFFFFL);

        pendingTag = tag & 0xFFFFFFFFL;
        pendingAuth = auth & 0xFFFFFFFFL;
        pendingStartMs = System.currentTimeMillis();

        int flags = 0;
        MeshProtocolClient.sendTracePath(transport, tag, auth, flags, forwardPath);
    }

    public void handleTraceData(
            int flags,
            long tag,
            long authCode,
            byte[] pathHashes,
            byte[] pathSnrs,
            int finalSNR4) {
        long pTag = pendingTag;
        long pAuth = pendingAuth;
        if (pTag < 0 || pAuth < 0) return;
        if (tag != pTag) return;
        if (authCode != pAuth) return;

        final long startMs = pendingStartMs;
        pendingTag = -1;
        pendingAuth = -1;
        pendingStartMs = -1;

        final long durationMs = (startMs > 0) ? (System.currentTimeMillis() - startMs) : -1;

        if (listener != null) {
            listener.onStageResult(
                    forwardPath,
                    pathSnrs,
                    finalSNR4,
                    durationMs,
                    returnTo
            );
        }
    }
}

