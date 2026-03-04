package meshcore.util;

import javax.microedition.lcdui.Display;

import meshcore.ui.ActivityLogScreen;
import meshcore.util.AppConstants;

public final class ActivityLogHelper {

    private ActivityLogHelper() {}

    public static void append(final StringBuffer buf,
                              final String line,
                              final ActivityLogScreen screen,
                              final Display display) {
        buf.append(line).append("\n");
        if (buf.length() > AppConstants.MAX_BUFFER_LENGTH) {
            buf.delete(0, buf.length() - AppConstants.MAX_BUFFER_LENGTH);
        }
        if (display == null) return;
        final ActivityLogScreen current = screen;
        display.callSerially(new Runnable() {
            public void run() {
                if (current != null) current.refreshLog();
            }
        });
    }
}

