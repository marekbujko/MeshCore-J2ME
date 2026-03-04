package meshcore.ui;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;

import meshcore.util.AppConstants;
import meshcore.util.TextUtils;

public final class NotificationPresenter {

    private NotificationPresenter() {}

    public static void showIncoming(Display display, Displayable current, String message) {
        if (display == null || current == null) return;
        try {
            String safe = TextUtils.sanitizeAlertMessage(message, 60);
            Alerts.show(display, current, "Message", safe, AlertType.INFO, AppConstants.NOTIFICATION_DURATION_MS);
        } catch (Exception e) {
            // Ignore; some devices may throw on certain alerts.
        }
    }
}

