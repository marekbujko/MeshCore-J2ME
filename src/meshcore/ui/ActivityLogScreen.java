package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;

/**
 * Activity log screen: status messages, errors, notifications.
 */
public class ActivityLogScreen extends Form implements CommandListener {

    private final AppController app;
    private final StringBuffer logBuf;
    private final StringItem logItem;
    private final Command cmdBack;

    public ActivityLogScreen(AppController app, StringBuffer logBuf) {
        super("Activity Log");
        this.app = app;
        this.logBuf = logBuf;
        logItem = new StringItem("", logBuf.toString());
        append(logItem);
        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void refreshLog() {
        logItem.setText(logBuf.toString());
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
        }
    }
}
