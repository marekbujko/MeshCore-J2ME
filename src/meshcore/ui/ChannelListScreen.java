package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import java.util.Vector;

/**
 * Channel list: Public (default) + custom channels. Join Hashtag / Join Private / Remove.
 */
public class ChannelListScreen extends List implements CommandListener {

    public static final String PUBLIC_CHANNEL = "Public";

    private final AppController app;
    private final Vector channelNames;
    private final Vector channelUnreadCount;
    private final Command cmdJoinHashtag;
    private final Command cmdJoinPrivate;
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
        cmdJoinHashtag = new Command("Join Hashtag Channel", Command.SCREEN, 1);
        cmdJoinPrivate = new Command("Join Private Channel", Command.SCREEN, 2);
        cmdRemove = new Command("Remove", Command.SCREEN, 3);
        cmdBack = new Command("Back", Command.BACK, 4);
        addCommand(cmdJoinHashtag);
        addCommand(cmdJoinPrivate);
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

    private static String trimAll(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') sb.append(c);
        }
        return sb.toString();
    }

    public void refreshList() {
        deleteAll();
        for (int i = 0; i < channelNames.size(); i++) {
            String name = (String) channelNames.elementAt(i);
            int unread = (i < channelUnreadCount.size())
                ? ((Integer) channelUnreadCount.elementAt(i)).intValue() : 0;
            String label = (unread > 0) ? (name + " (" + unread + " new)") : name;
            append(label, null);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
            return;
        }
        if (c == cmdJoinHashtag) {
            final TextBox tb = new TextBox("Join Hashtag Channel", "", 32, 0);
            tb.addCommand(new Command("OK", Command.OK, 1));
            tb.addCommand(new Command("Cancel", Command.CANCEL, 2));
            tb.setCommandListener(new CommandListener() {
                public void commandAction(Command cmd, Displayable disp) {
                    if (cmd.getCommandType() == Command.OK) {
                        String name = tb.getString().trim();
                        if (name.length() > 0) {
                            if (!name.startsWith("#")) name = "#" + name;
                            app.addChannel(name);
                        }
                    }
                    app.showChannelListScreen();
                }
            });
            app.getDisplay().setCurrent(tb);
            return;
        }
        if (c == cmdJoinPrivate) {
            final Form form = new Form("Join Private Channel");
            final TextField tfName = new TextField("Channel name", "", 32, TextField.ANY);
            final TextField tfSecret = new TextField("Secret (32 hex)", "", 32, TextField.ANY);
            form.append(tfName);
            form.append(tfSecret);
            form.addCommand(new Command("OK", Command.OK, 1));
            form.addCommand(new Command("Cancel", Command.CANCEL, 2));
            form.setCommandListener(new CommandListener() {
                public void commandAction(Command cmd, Displayable disp) {
                    if (cmd.getCommandType() == Command.OK) {
                        String name = tfName.getString().trim();
                        String secret = trimAll(tfSecret.getString().trim());
                        if (name.length() > 0 && secret.length() == 32) {
                            app.addPrivateChannel(name, secret);
                        } else if (secret.length() != 32) {
                            Alerts.warning(app.getDisplay(), ChannelListScreen.this,
                                    "Secret", "Secret must be 32 hex chars", 2500);
                            return;
                        }
                    }
                    app.showChannelListScreen();
                }
            });
            app.getDisplay().setCurrent(form);
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
                        Alerts.confirm("Remove channel", "Remove \"" + name + "\"?");
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
