package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * Message settings: allows clearing all channel / DM histories.
 * Shows history storage used (global monitor).
 */
public final class MessageSettingsScreen extends List implements CommandListener {

    private final AppController app;
    private final Command cmdBack;

    public MessageSettingsScreen(AppController app) {
        super("Message Settings", List.IMPLICIT);
        this.app = app;
        append("Storage used: -- KB", null);
        append("Clear channel history", null);
        append("Clear DM history", null);
        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
        refreshStorageDisplay();
    }

    /** Updates the storage line (index 0) with current history size. */
    public void refreshStorageDisplay() {
        int kb = meshcore.util.HistoryStore.getHistoryStorageUsedKB();
        boolean over = meshcore.util.HistoryStore.isHistoryStorageOverLimit();
        set(0, (over ? "Storage used: " + kb + " KB (!)" : "Storage used: " + kb + " KB"), null);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.returnToSettingsScreen();
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int idx = getSelectedIndex();
            if (idx == 0) {
                refreshStorageDisplay();
                return;
            }
            if (idx == 1) {
                // Clear all channel histories
                javax.microedition.lcdui.Alert a =
                        Alerts.confirm("Clear channels", "Delete message history for all channels?");
                a.addCommand(new Command("Yes", Command.OK, 1));
                a.addCommand(new Command("No", Command.BACK, 2));
                a.setCommandListener(new CommandListener() {
                    public void commandAction(Command cmd, Displayable disp) {
                        if (cmd.getCommandType() == Command.OK) {
                            meshcore.util.HistoryStore.clearAllChannelHistory();
                            refreshStorageDisplay();
                            Alerts.info(app.getDisplay(), MessageSettingsScreen.this,
                                    "Channels", "Channel history cleared.");
                        } else {
                            app.getDisplay().setCurrent(MessageSettingsScreen.this);
                        }
                    }
                });
                app.getDisplay().setCurrent(a, this);
            } else if (idx == 2) {
                // Clear all DM histories
                javax.microedition.lcdui.Alert a =
                        Alerts.confirm("Clear DMs", "Delete message history for all contacts?");
                a.addCommand(new Command("Yes", Command.OK, 1));
                a.addCommand(new Command("No", Command.BACK, 2));
                a.setCommandListener(new CommandListener() {
                    public void commandAction(Command cmd, Displayable disp) {
                        if (cmd.getCommandType() == Command.OK) {
                            meshcore.util.HistoryStore.clearAllDmHistory();
                            refreshStorageDisplay();
                            Alerts.info(app.getDisplay(), MessageSettingsScreen.this,
                                    "Contacts", "DM history cleared.");
                        } else {
                            app.getDisplay().setCurrent(MessageSettingsScreen.this);
                        }
                    }
                });
                app.getDisplay().setCurrent(a, this);
            }
        }
    }
}

