package meshcore.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import java.util.Vector;

/**
 * Channel list: Public (default) + custom channels. Add/Remove.
 */
public class ChannelListScreen extends List implements CommandListener {

    public static final String PUBLIC_CHANNEL = "Public";

    private final AppController app;
    private final Vector channelNames;
    private final Command cmdAdd;
    private final Command cmdRemove;
    private final Command cmdBack;

    public ChannelListScreen(AppController app, Vector channelNames) {
        super("Channels", List.IMPLICIT);
        this.app = app;
        this.channelNames = channelNames;
        refreshList();
        cmdAdd = new Command("Add", Command.SCREEN, 1);
        cmdRemove = new Command("Remove", Command.SCREEN, 2);
        cmdBack = new Command("Back", Command.BACK, 3);
        addCommand(cmdAdd);
        addCommand(cmdRemove);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void refreshList() {
        deleteAll();
        for (int i = 0; i < channelNames.size(); i++) {
            append((String) channelNames.elementAt(i), null);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
            return;
        }
        if (c == cmdAdd) {
            final TextBox tb = new TextBox("New channel", "", 32, 0);
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
        if (c == cmdRemove) {
            final int idx = getSelectedIndex();
            if (idx <= 0) {
                Alert a = new Alert("", "Cannot remove Public channel", null, AlertType.INFO);
                a.setTimeout(2000);
                app.getDisplay().setCurrent(a, this);
                return;
            }
            if (idx > 0 && idx < channelNames.size()) {
                String name = (String) channelNames.elementAt(idx);
                Alert confirm = new Alert("Remove channel", "Remove " + name + "?", null, AlertType.CONFIRMATION);
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
                app.showChannelScreen(idx);
            }
        }
    }
}
