package meshcore.util;

import java.io.IOException;

import javax.microedition.lcdui.Displayable;

import meshcore.net.FrameTransport;
import meshcore.protocol.MeshProtocolClient;

/**
 * Sends CMD_SEND_LOGIN and correlates PUSH_LOGIN_SUCCESS / PUSH_LOGIN_FAIL
 * by 6-byte public key prefix.
 * <p>
 * Empty-password (guest) logins: some repeaters still report the previous admin session
 * in PUSH_LOGIN_SUCCESS. When the app sent an empty password and login succeeds, the
 * admin bit (LSB) of {@code permissions} is cleared before notifying the listener so
 * local session and UI match guest intent.
 */
public final class LoginRequestService {

    public interface Listener {
        void onLoginResult(
                boolean success,
                int contactIdx,
                String contactName,
                boolean isAdmin,
                int permissionsByte,
                long serverTag,
                int newPermissionsOrMinus1,
                long durationMs,
                Displayable returnTo
        );
    }

    private final Listener listener;

    private volatile int pendingContactIdx = -1;
    private volatile long pendingStartMs = -1;
    private volatile String pendingContactName = "";
    private volatile Displayable pendingReturnTo = null;
    private volatile boolean pendingGuestPasswordAttempt = false;
    private final byte[] pendingPrefix = new byte[6];

    public LoginRequestService(Listener listener) {
        this.listener = listener;
    }

    public void sendLogin(
            FrameTransport transport,
            int contactIdx,
            String contactName,
            byte[] publicKey32,
            String password,
            Displayable returnTo) throws IOException {
        if (transport == null) return;
        if (publicKey32 == null || publicKey32.length != 32) return;

        pendingContactIdx = contactIdx;
        pendingStartMs = System.currentTimeMillis();
        pendingContactName = (contactName != null) ? contactName : "";
        pendingReturnTo = returnTo;
        pendingGuestPasswordAttempt = (password == null || password.length() == 0);
        System.arraycopy(publicKey32, 0, pendingPrefix, 0, 6);

        try {
            MeshProtocolClient.sendLogin(transport, publicKey32, password);
        } catch (IOException e) {
            clearPending();
            throw e;
        }
    }

    public void handleLoginPush(
            boolean success,
            byte[] pubKeyPrefix6,
            int permissionsByte,
            long serverTag,
            int newPermissionsOrMinus1) {
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

        final boolean guestTry = pendingGuestPasswordAttempt;
        pendingGuestPasswordAttempt = false;

        int permByte = permissionsByte & 0xFF;
        if (success && guestTry) {
            permByte &= ~1;
        }
        final boolean admin = success && ((permByte & 1) != 0);

        if (listener != null) {
            listener.onLoginResult(
                    success,
                    idx,
                    name,
                    admin,
                    permByte,
                    serverTag,
                    newPermissionsOrMinus1,
                    durationMs,
                    rt
            );
        }
    }

    public void clearPending() {
        pendingContactIdx = -1;
        pendingStartMs = -1;
        pendingReturnTo = null;
        pendingGuestPasswordAttempt = false;
    }
}
