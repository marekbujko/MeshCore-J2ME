package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import java.util.Vector;
import meshcore.protocol.ProtocolConstants;

/**
 * Contacts list screen: tap to open DM, refresh to sync, remove contact.
 * Repeaters are excluded. Search command opens keyboard search to filter the list by name.
 */
public class ContactsScreen extends List implements CommandListener {

    private final AppController app;
    private final Vector contactNames;
    private final Vector contactUnreadCount;
    private final Vector contactTypes;
    /** Maps list position -> real contact index (for filtered view). */
    private final Vector visibleIndices;
    private String filterQuery;
    private final Command cmdSearch;
    private final Command cmdDM;
    private final Command cmdRemove;
    private final Command cmdRefresh;
    private final Command cmdBack;

    /** Remember last selected list index so returning from a DM restores position. */
    private static int lastSelectedListIndex = 0;

    public ContactsScreen(AppController app, Vector contactNames, Vector contactUnreadCount, Vector contactTypes) {
        super("Contacts", List.IMPLICIT);
        this.app = app;
        this.contactNames = contactNames;
        this.contactUnreadCount = contactUnreadCount;
        this.contactTypes = contactTypes;
        this.visibleIndices = new Vector();
        this.filterQuery = "";
        cmdSearch = new Command("Search", Command.ITEM, 0);
        cmdDM = new Command("Message", Command.OK, 1);
        cmdRemove = new Command("Remove", Command.SCREEN, 4);
        cmdRefresh = new Command("Refresh", Command.ITEM, 3);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdSearch);
        addCommand(cmdDM);
        addCommand(cmdRemove);
        addCommand(cmdRefresh);
        addCommand(cmdBack);
        setCommandListener(this);
        refreshList();

        int size = size();
        if (size > 0) {
            if (lastSelectedListIndex < 0 || lastSelectedListIndex >= size) {
                lastSelectedListIndex = 0;
            }
            setSelectedIndex(lastSelectedListIndex, true);
        }
    }

    public void refreshList() {
        deleteAll();
        visibleIndices.removeAllElements();
        String fq = (filterQuery != null) ? filterQuery.trim().toLowerCase() : "";
        int nonRepeaterCount = 0;
        for (int i = 0; i < contactNames.size(); i++) {
            if (i < contactTypes.size() && ((Integer) contactTypes.elementAt(i)).intValue() == ProtocolConstants.ADV_TYPE_REPEATER) {
                continue;
            }
            nonRepeaterCount++;
            String name = (String) contactNames.elementAt(i);
            if (fq.length() == 0 || name.toLowerCase().indexOf(fq) >= 0) {
                visibleIndices.addElement(new Integer(i));
                int unread = (i < contactUnreadCount.size())
                    ? ((Integer) contactUnreadCount.elementAt(i)).intValue() : 0;
                String label = (unread > 0) ? (name + " (" + unread + " new)") : name;
                append(label, null);
            }
        }
        if (visibleIndices.size() == 0) {
            append("(no contacts yet)", null);
        }
        String title = "Contacts (" + visibleIndices.size();
        if (fq.length() > 0) {
            title += "/" + nonRepeaterCount + ")";
        } else {
            title += ")";
        }
        setTitle(title);
    }

    /** Returns the real contact index for the current list selection, or -1. */
    private int getSelectedContactIndex() {
        int sel = getSelectedIndex();
        if (sel < 0 || sel >= visibleIndices.size()) {
            return -1;
        }
        return ((Integer) visibleIndices.elementAt(sel)).intValue();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
            return;
        }
        if (c == cmdSearch) {
            final ContactsScreen self = this;
            final TextBox tb = new TextBox("Search contacts", filterQuery != null ? filterQuery : "", 64, TextField.ANY);
            Command cmdSearchTb = new Command("Search", Command.OK, 0);
            Command cmdCancel = new Command("Cancel", Command.CANCEL, 1);
            tb.addCommand(cmdSearchTb);
            tb.addCommand(cmdCancel);
            tb.setCommandListener(new CommandListener() {
                public void commandAction(Command cmd, Displayable disp) {
                    if (cmd.getCommandType() == Command.OK) {
                        self.filterQuery = tb.getString();
                        self.refreshList();
                    }
                    app.getDisplay().setCurrent(self);
                }
            });
            app.getDisplay().setCurrent(tb);
            return;
        }
        if (c == cmdRefresh) {
            new Thread(new Runnable() {
                public void run() {
                    app.sendGetContacts();
                }
            }).start();
            return;
        }
        if (c == cmdRemove) {
            final int idx = getSelectedContactIndex();
            if (idx >= 0 && idx < contactNames.size()) {
                String name = (String) contactNames.elementAt(idx);
                javax.microedition.lcdui.Alert confirm =
                        new javax.microedition.lcdui.Alert("Remove contact",
                                "Remove " + name + "?", null,
                                javax.microedition.lcdui.AlertType.CONFIRMATION);
                confirm.addCommand(new Command("Yes", Command.OK, 1));
                confirm.addCommand(new Command("No", Command.CANCEL, 2));
                confirm.setCommandListener(new CommandListener() {
                    public void commandAction(Command cmd, Displayable disp) {
                        if (cmd.getCommandType() == Command.OK) {
                            app.removeContact(idx);
                        }
                        app.getDisplay().setCurrent(ContactsScreen.this);
                    }
                });
                app.getDisplay().setCurrent(confirm, this);
            }
            return;
        }
        if (c == cmdDM || c == List.SELECT_COMMAND) {
            int idx = getSelectedContactIndex();
            if (idx >= 0 && idx < contactNames.size()) {
                lastSelectedListIndex = getSelectedIndex();
                app.showDMScreen(idx);
            }
        }
    }
}
