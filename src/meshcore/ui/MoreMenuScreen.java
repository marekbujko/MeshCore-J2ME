package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;

/**
 * More menu: Add Contact, Add Channel, Share My Contact, Tools, Settings, About.
 */
public final class MoreMenuScreen extends List implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;

    private static final int IDX_ADD_CONTACT = 0;
    private static final int IDX_ADD_CHANNEL = 1;
    private static final int IDX_SHARE_MY_CONTACT = 2;
    private static final int IDX_TOOLS = 3;
    private static final int IDX_SETTINGS = 4;
    private static final int IDX_ABOUT = 5;

    private final Command cmdBack;

    private static Image loadIcon(String path) {
        try {
            return Image.createImage(path);
        } catch (Exception e) {
            return null;
        }
    }

    public MoreMenuScreen(AppController app, Displayable returnTo) {
        super("More", List.IMPLICIT);
        this.app = app;
        this.returnTo = returnTo;

        append("Add Contact", loadIcon("/add-contact.png"));
        append("Add Channel", loadIcon("/add-channel.png"));
        append("Share My Contact", loadIcon("/qr-code.png"));
        append("Tools", loadIcon("/tools.png"));
        append("Settings", loadIcon("/settings.png"));
        append("About", loadIcon("/about.png"));

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int sel = getSelectedIndex();
            if (sel == IDX_ADD_CONTACT) {
                app.getDisplay().setCurrent(new AddContactOptionsScreen(app, returnTo));
            } else if (sel == IDX_ADD_CHANNEL) {
                app.showChannelListScreen();
            } else if (sel == IDX_SHARE_MY_CONTACT) {
                app.showMyContactCode();
            } else if (sel == IDX_TOOLS) {
                app.getDisplay().setCurrent(new ToolsScreen(app, this));
            } else if (sel == IDX_SETTINGS) {
                app.showSettingsScreen();
            } else if (sel == IDX_ABOUT) {
                app.getDisplay().setCurrent(new AboutScreen(app, this));
            }
        }
    }
}
