package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import meshcore.ui.ChannelListScreen;

/**
 * Shows channel info: name, type (Public / Hashtag / Private), and secret with Copy option.
 */
public final class ChannelInfoScreen extends Form implements CommandListener {

    private final AppController app;
    private final int channelIndex;
    private final Displayable returnTo;
    private final String channelName;
    private final String typeLabel;
    private final String secretHex;

    private final Command cmdCopy;  // null for Public channel
    private final Command cmdBack;

    public ChannelInfoScreen(AppController app, int channelIndex, Displayable returnTo) {
        super("Channel Info");
        this.app = app;
        this.channelIndex = channelIndex;
        this.returnTo = returnTo;
        this.channelName = app.getChannelName(channelIndex);
        this.typeLabel = getChannelTypeLabel(channelName);
        this.secretHex = app.getChannelSecretHex(channelIndex);

        boolean isPublic = (channelIndex == 0)
                || (channelName != null && channelName.equalsIgnoreCase(ChannelListScreen.PUBLIC_CHANNEL));
        if (isPublic) {
            append("Public channel");
            cmdCopy = null;
        } else {
            append("Name: " + channelName + "\n");
            append("Type: " + typeLabel + "\n\n");
            append("Secret Key (32 hex):\n");
            append(secretHex);
            cmdCopy = new Command("Copy", Command.ITEM, 1);
            addCommand(cmdCopy);
        }
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    private static String getChannelTypeLabel(String name) {
        if (name == null) return "Unknown";
        if (ChannelListScreen.PUBLIC_CHANNEL.equals(name)) return "Public channel";
        if (name.startsWith("#")) return "Hashtag channel";
        return "Private channel";
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdCopy && cmdCopy != null) {
            TextBox tb = new TextBox("Copy secret", secretHex, 64, TextField.ANY);
            tb.addCommand(new Command("Back", Command.BACK, 1));
            tb.setCommandListener(new CommandListener() {
                public void commandAction(Command cmd, Displayable disp) {
                    app.getDisplay().setCurrent(ChannelInfoScreen.this);
                }
            });
            app.getDisplay().setCurrent(tb);
        }
    }
}
