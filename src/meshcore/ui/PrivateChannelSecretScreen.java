package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;

/**
 * Shows the generated secret for a new private channel so the user can share it.
 */
public final class PrivateChannelSecretScreen extends Form implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;

    public PrivateChannelSecretScreen(AppController app, Displayable returnTo, String channelName, String secretHex) {
        super("Channel created");
        this.app = app;
        this.returnTo = returnTo;

        append("Channel \"" + channelName + "\" created.\n\n");
        append("Share this secret with others so they can join:\n\n");
        append(secretHex);

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
        }
    }
}
