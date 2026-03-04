package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import java.util.Vector;

/**
 * Notifications list: shows only channels/DMs with unread messages.
 */
public class NotificationsScreen extends List implements CommandListener {

    private static final int TYPE_CHANNEL = 0;
    private static final int TYPE_DM = 1;

    private final AppController app;
    private final Vector channelNames;
    private final Vector channelUnread;
    private final Vector contactNames;
    private final Vector contactUnread;

    /** For each list row: type (Integer TYPE_CHANNEL/TYPE_DM). */
    private final Vector itemTypes = new Vector();
    /** For each list row: index into channelNames/contactNames (Integer). */
    private final Vector itemIndices = new Vector();

    private final Command cmdBack;
    private final Command cmdOpen;

    public NotificationsScreen(AppController app,
                               Vector channelNames,
                               Vector channelUnread,
                               Vector contactNames,
                               Vector contactUnread) {
        super("Notifications", List.IMPLICIT);
        this.app = app;
        this.channelNames = channelNames;
        this.channelUnread = channelUnread;
        this.contactNames = contactNames;
        this.contactUnread = contactUnread;

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdOpen = new Command("Open", Command.OK, 0);
        addCommand(cmdBack);
        addCommand(cmdOpen);
        setCommandListener(this);

        refreshList();
    }

    public void refreshList() {
        deleteAll();
        itemTypes.removeAllElements();
        itemIndices.removeAllElements();

        int total = 0;

        // Channels with unread
        for (int i = 0; i < channelNames.size(); i++) {
            int unread = (i < channelUnread.size())
                    ? ((Integer) channelUnread.elementAt(i)).intValue() : 0;
            if (unread > 0) {
                String name = (String) channelNames.elementAt(i);
                String label = "(" + unread + " new) " + name;
                append(label, null);
                itemTypes.addElement(new Integer(TYPE_CHANNEL));
                itemIndices.addElement(new Integer(i));
                total++;
            }
        }

        // Contacts/DMs with unread
        for (int i = 0; i < contactNames.size(); i++) {
            int unread = (i < contactUnread.size())
                    ? ((Integer) contactUnread.elementAt(i)).intValue() : 0;
            if (unread > 0) {
                String name = (String) contactNames.elementAt(i);
                String label = "(" + unread + " new) " + name;
                append(label, null);
                itemTypes.addElement(new Integer(TYPE_DM));
                itemIndices.addElement(new Integer(i));
                total++;
            }
        }

        if (total == 0) {
            append("(no new messages)", null);
        }

        setTitle("Notifications (" + total + ")");
    }

    private void openSelected() {
        int sel = getSelectedIndex();
        if (sel < 0 || sel >= itemTypes.size()) {
            return;
        }
        int type = ((Integer) itemTypes.elementAt(sel)).intValue();
        int idx = ((Integer) itemIndices.elementAt(sel)).intValue();
        if (type == TYPE_CHANNEL) {
            app.showChannelScreen(idx);
        } else if (type == TYPE_DM) {
            app.showDMScreen(idx);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
            return;
        }
        if (c == cmdOpen || c == List.SELECT_COMMAND) {
            openSelected();
        }
    }
}

