package meshcore.net;

import java.io.IOException;

import meshcore.protocol.ProtocolConstants;
import meshcore.util.AppConstants;

public final class ConnectionManager {

    public interface Listener {
        void onFrame(byte[] frame);
        void onConnectionLost();
        void onKeepAliveError(String message);
    }

    private final Listener listener;
    private FrameTransport transport;
    private volatile boolean running = false;
    private volatile boolean keepAliveRunning = false;

    public ConnectionManager(Listener listener) {
        this.listener = listener;
    }

    public void setTransport(FrameTransport transport) {
        this.transport = transport;
    }

    public void startLoops() {
        running = true;
        keepAliveRunning = true;
        new Thread(new Runnable() {
            public void run() {
                receiveLoop();
            }
        }).start();
        new Thread(new Runnable() {
            public void run() {
                startKeepAlive();
            }
        }).start();
    }

    public void stop() {
        running = false;
        keepAliveRunning = false;
    }

    private void receiveLoop() {
        try {
            while (running) {
                if (transport == null) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                    continue;
                }
                byte[] frame = transport.readFrame();
                if (frame != null && frame.length > 0) {
                    listener.onFrame(frame);
                }
            }
        } catch (IOException e) {
            if (running) {
                listener.onConnectionLost();
            }
        }
    }

    private void startKeepAlive() {
        while (keepAliveRunning) {
            try {
                Thread.sleep(AppConstants.KEEPALIVE_INTERVAL_MS);
            } catch (InterruptedException ignore) {
                return;
            }
            if (!keepAliveRunning || !running || transport == null) continue;
            try {
                byte[] cmd = {(byte) ProtocolConstants.CMD_GET_BATT_STORAGE};
                transport.sendFrame(cmd);
            } catch (IOException e) {
                listener.onKeepAliveError("[!] Keepalive failed");
                listener.onConnectionLost();
                return;
            }
        }
    }
}

