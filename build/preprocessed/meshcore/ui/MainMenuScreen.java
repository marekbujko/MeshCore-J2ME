package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * Main menu: Channel Chat, Contacts, Settings, Disconnect.
 */
public class MainMenuScreen extends List implements CommandListener {

    private final AppController app;
    private final Command cmdBack;

    public static final int IDX_CHANNEL = 0;
    public static final int IDX_CONTACTS = 1;
    public static final int IDX_SETTINGS = 2;
    public static final int IDX_ACTIVITY_LOG = 3;
    public static final int IDX_DISCONNECT = 4;

    public MainMenuScreen(AppController app) {
        super("MeshCore", List.IMPLICIT);
        this.app = app;
        append("Channels", null);
        append("Contacts / DM", null);
        append("Settings", null);
        append("Activity Log", null);
        append("Disconnect", null);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            return; // Back on main menu does nothing (original behavior)
        }
        if (c == List.SELECT_COMMAND) {
            int idx = getSelectedIndex();
            if (idx == IDX_CHANNEL) {
                app.showChannelListScreen();
            } else if (idx == IDX_CONTACTS) {
                new Thread(new Runnable() {
                    public void run() {
                        app.sendGetContacts();
                    }
                }).start();
                app.showContactsScreen();
            } else if (idx == IDX_SETTINGS) {
                app.showSettingsScreen();
            } else if (idx == IDX_ACTIVITY_LOG) {
                app.showActivityLogScreen();
            } else if (idx == IDX_DISCONNECT) {
                app.disconnect();
                app.showConnectScreen();
            }
        }
    }
}
