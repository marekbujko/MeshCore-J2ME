package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * Simple menu for adding a new contact or repeater:
 * - Add manually (name + public key)
 * - Scan QR code
 */
public final class AddContactOptionsScreen extends List implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;

    private final Command cmdBack;

    public AddContactOptionsScreen(AppController app, Displayable returnTo) {
        super("Add Contact", List.IMPLICIT);
        this.app = app;
        this.returnTo = returnTo;

        append("Add manually (name + key)", null); // 0
        append("Scan QR code", null);              // 1

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
            if (sel == 0) {
                app.getDisplay().setCurrent(new ManualAddContactScreen(app, returnTo));
            } else if (sel == 1) {
                new AddFromQrScanScreen(app, returnTo);
            }
        }
    }
}

