package meshcore.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;

/**
 * Small helper for showing alerts.
 * Keeps Alert usage in one place and applies simple Nokia S40-safe defaults.
 */
public final class Alerts {

    private Alerts() {}

    public static void info(Display display, Displayable next, String title, String text) {
        show(display, next, title, text, AlertType.INFO, Alert.FOREVER);
    }

    public static void warning(Display display, Displayable next, String title, String text, int timeoutMs) {
        show(display, next, title, text, AlertType.WARNING, timeoutMs);
    }

    /** Show a simple alert and return immediately. */
    public static void show(Display display, Displayable next, String title, String text,
                            AlertType type, int timeoutMs) {
        if (display == null || next == null) return;
        String safeTitle = (title != null && title.length() > 0) ? title : "Message";
        String safeText = (text != null && text.length() > 0) ? text : "";
        Alert a = new Alert(safeTitle, safeText, null, type);
        a.setTimeout(timeoutMs);
        display.setCurrent(a, next);
    }

    /**
     * Create a confirmation alert (type CONFIRMATION) without showing it.
     * Caller can add commands and a listener, then call display.setCurrent(alert, next).
     */
    public static Alert confirm(String title, String text) {
        String safeTitle = (title != null && title.length() > 0) ? title : "Confirm";
        String safeText = (text != null && text.length() > 0) ? text : "";
        Alert a = new Alert(safeTitle, safeText, null, AlertType.CONFIRMATION);
        a.setTimeout(Alert.FOREVER);
        return a;
    }
}

