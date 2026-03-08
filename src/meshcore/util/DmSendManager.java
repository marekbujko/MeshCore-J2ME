package meshcore.util;

/**
 * Handles DM send with retries and delivery state. Keeps retry logic out of the main MIDlet.
 * Configurable for future options (retry count, timeouts; later path/flood settings can live elsewhere).
 */
public final class DmSendManager {

    private final DmSendHandler handler;
    private int maxAttempts;
    private int timeoutMs;

    private volatile int pendingContactIdx = -1;
    private volatile int attemptCount = 0;
    private volatile boolean deliveryConfirmed = false;

    public DmSendManager(DmSendHandler handler, int maxAttempts, int timeoutMs) {
        this.handler = handler;
        this.maxAttempts = maxAttempts <= 0 ? 1 : maxAttempts;
        this.timeoutMs = timeoutMs <= 0 ? AppConstants.DM_SEND_TIMEOUT_MS : timeoutMs;
    }

    /** Default from AppConstants (DM_SEND_MAX_ATTEMPTS, DM_SEND_TIMEOUT_MS). */
    public DmSendManager(DmSendHandler handler) {
        this(handler, AppConstants.DM_SEND_MAX_ATTEMPTS, AppConstants.DM_SEND_TIMEOUT_MS);
    }

    /** Set max send attempts (e.g. from user settings). Next send uses the new value. */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts <= 0 ? 1 : maxAttempts;
    }

    /** Set timeout between retries in ms (e.g. from user settings). */
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs <= 0 ? AppConstants.DM_SEND_TIMEOUT_MS : timeoutMs;
    }

    /**
     * Start sending a DM: append "Sending..." line, send once, then retry in background until
     * delivery is confirmed or max attempts reached.
     * @return true if the first send succeeded and retries are running, false if first send failed
     */
    public boolean send(int contactIdx, String message) {
        String escaped = TextUtils.escapeNewlines(message);
        pendingContactIdx = contactIdx;
        attemptCount = 1;
        deliveryConfirmed = false;

        handler.appendSending(contactIdx, escaped);
        if (!handler.doSend(contactIdx, message)) {
            clearPending();
            return false;
        }
        startRetryThread(contactIdx, message);
        return true;
    }

    /** Call when PUSH_SEND_CONFIRMED is received so retries stop and UI can show Delivered. */
    public void setDeliveryConfirmed(boolean confirmed) {
        this.deliveryConfirmed = confirmed;
    }

    public int getPendingContactIdx() {
        return pendingContactIdx;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void clearPending() {
        pendingContactIdx = -1;
    }

    private void startRetryThread(final int contactIdx, final String message) {
        new Thread(new Runnable() {
            public void run() {
                while (!deliveryConfirmed && attemptCount < maxAttempts) {
                    try {
                        Thread.sleep(timeoutMs);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (deliveryConfirmed) return;
                    if (pendingContactIdx != contactIdx) return;
                    attemptCount++;
                    handler.updateSendingProgress(contactIdx, attemptCount, maxAttempts);
                    handler.doSend(contactIdx, message);
                }
                if (!deliveryConfirmed) {
                    try {
                        Thread.sleep(timeoutMs);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (!deliveryConfirmed) {
                        handler.onSendFailed(contactIdx);
                        clearPending();
                    }
                }
            }
        }).start();
    }
}
