package meshcore.util;

/**
 * Callbacks used by DmSendManager to append "Sending..." lines and perform the actual send.
 * Implemented by the app (e.g. MeshCore) so retry logic stays separate from UI/transport.
 */
public interface DmSendHandler {

    /**
     * Append a DM line for the current user with "Sending" after the timestamp.
     * @param contactIdx contact index
     * @param escapedMessage message text already escaped for storage (e.g. newlines as \\n)
     */
    void appendSending(int contactIdx, String escapedMessage);

    /**
     * Update the sending status to "Sending 2/3", "Sending 3/3" etc. (attempt 1 shows "Sending").
     */
    void updateSendingProgress(int contactIdx, int attempt, int maxAttempts);

    /**
     * Perform one direct-message send over the transport.
     * @param contactIdx contact index
     * @param message raw message text
     * @return true if the send succeeded (no IO error), false otherwise
     */
    boolean doSend(int contactIdx, String message);

    /**
     * Called when all retry attempts are done and no delivery confirmation was received.
     * Replace "Sending..." with "Failed" in the contact's DM buffer and refresh UI.
     */
    void onSendFailed(int contactIdx);
}
