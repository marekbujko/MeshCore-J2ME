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
     * Update the sending status to "Sending 2/5", "Sending 3/5 (Flood)" etc.
     * @param contactIdx contact index
     * @param attempt current attempt number (1-based)
     * @param maxAttempts total attempts including any extra flood retries
     * @param flood true when this attempt is after a path reset (flood-mode)
     */
    void updateSendingProgress(int contactIdx, int attempt, int maxAttempts, boolean flood);

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

    /**
     * Reset the routing path for this contact so subsequent retries use flood-mode
     * and can discover a new path.
     */
    void resetPathFor(int contactIdx);
}
