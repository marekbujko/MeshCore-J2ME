package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import java.util.Vector;

import meshcore.util.TextUtils;

/**
 * Channel list: Channel Info, Share with QR Code, Remove.
 * Add channels via More → Add Channel.
 */
public class ChannelListScreen extends List implements CommandListener {

    public static final String PUBLIC_CHANNEL = "Public";

    private final AppController app;
    private final Vector channelNames;
    private final Vector channelUnreadCount;
    private final Command cmdChannelInfo;
    private final Command cmdShareQr;
    private final Command cmdRemove;
    private final Command cmdBack;

    /** Remember last selected list index so returning from a channel restores position. */
    private static int lastSelectedIndex = 0;

    public ChannelListScreen(AppController app, Vector channelNames, Vector channelUnreadCount) {
        super("Channels", List.IMPLICIT);
        this.app = app;
        this.channelNames = channelNames;
        this.channelUnreadCount = channelUnreadCount;
        refreshList();
        cmdChannelInfo = new Command("Channel Info", Command.SCREEN, 1);
        cmdShareQr = new Command("Share with QR Code", Command.SCREEN, 2);
        cmdRemove = new Command("Remove Channel", Command.SCREEN, 3);
        cmdBack = new Command("Back", Command.BACK, 4);
        addCommand(cmdChannelInfo);
        addCommand(cmdShareQr);
        addCommand(cmdRemove);
        addCommand(cmdBack);
        setCommandListener(this);

        int size = size();
        if (size > 0) {
            if (lastSelectedIndex < 0 || lastSelectedIndex >= size) {
                lastSelectedIndex = 0;
            }
            setSelectedIndex(lastSelectedIndex, true);
        }
    }

    public void refreshList() {
        deleteAll();
        for (int i = 0; i < channelNames.size(); i++) {
            String displayName = app.getChannelDisplayName(i);
            int unread = (i < channelUnreadCount.size())
                ? ((Integer) channelUnreadCount.elementAt(i)).intValue() : 0;
            String label = (unread > 0) ? (displayName + " (" + unread + " new)") : displayName;
            append(label, null);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
            return;
        }
        if (c == cmdChannelInfo) {
            int idx = getSelectedIndex();
            if (idx >= 0 && idx < channelNames.size()) {
                app.getDisplay().setCurrent(new ChannelInfoScreen(app, idx, this));
            }
            return;
        }
        if (c == cmdShareQr) {
            int idx = getSelectedIndex();
            if (idx >= 0 && idx < channelNames.size()) {
                String name = app.getChannelName(idx);
                String secret = app.getChannelSecretHex(idx);
                String url = "meshcore://channel/add?name=" + TextUtils.urlEncode(name)
                        + "&secret=" + (secret != null ? secret : "");
                String title = (name != null && name.length() > 0) ? name : "Channel";
                app.getDisplay().setCurrent(new ShareQrScreen(app, url, title, this));
            }
            return;
        }
        if (c == cmdRemove) {
            final int idx = getSelectedIndex();
            if (idx <= 0) {
                Alerts.info(app.getDisplay(), this, "Channel", "Cannot remove Public channel");
                return;
            }
            if (idx > 0 && idx < channelNames.size()) {
                String name = (String) channelNames.elementAt(idx);
                javax.microedition.lcdui.Alert confirm =
                        Alerts.confirm("Remove Channel", "Remove \"" + name + "\"?");
                confirm.addCommand(new Command("Yes", Command.OK, 1));
                confirm.addCommand(new Command("No", Command.CANCEL, 2));
                confirm.setCommandListener(new CommandListener() {
                    public void commandAction(Command cmd, Displayable disp) {
                        if (cmd.getCommandType() == Command.OK) {
                            app.removeChannel(idx);
                            app.showChannelListScreen();
                        } else {
                            app.getDisplay().setCurrent(ChannelListScreen.this);
                        }
                    }
                });
                app.getDisplay().setCurrent(confirm, this);
            }
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int idx = getSelectedIndex();
            if (idx >= 0) {
                lastSelectedIndex = idx;
                app.showChannelScreen(idx);
            }
        }
    }
}
