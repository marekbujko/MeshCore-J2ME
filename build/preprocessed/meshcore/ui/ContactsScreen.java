package meshcore.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import java.util.Vector;

/**
 * Contacts list screen: tap to open DM, refresh to sync, remove contact.
 */
public class ContactsScreen extends List implements CommandListener {

    private final AppController app;
    private final Vector contactNames;
    private final Command cmdDM;
    private final Command cmdRemove;
    private final Command cmdRefresh;
    private final Command cmdBack;

    public ContactsScreen(AppController app, Vector contactNames) {
        super("Contacts (" + contactNames.size() + ")", List.IMPLICIT);
        this.app = app;
        this.contactNames = contactNames;
        refreshList();
        cmdDM = new Command("Message", Command.OK, 1);
        cmdRemove = new Command("Remove", Command.SCREEN, 4);
        cmdRefresh = new Command("Refresh", Command.ITEM, 3);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdDM);
        addCommand(cmdRemove);
        addCommand(cmdRefresh);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void refreshList() {
        deleteAll();
        for (int i = 0; i < contactNames.size(); i++) {
            append((String) contactNames.elementAt(i), null);
        }
        if (contactNames.size() == 0) {
            append("(no contacts yet)", null);
        }
        setTitle("Contacts (" + contactNames.size() + ")");
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
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
            final int idx = getSelectedIndex();
            if (idx >= 0 && idx < contactNames.size()) {
                String name = (String) contactNames.elementAt(idx);
                Alert confirm = new Alert("Remove contact", "Remove " + name + "?", null, AlertType.CONFIRMATION);
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
            int idx = getSelectedIndex();
            if (idx >= 0 && idx < contactNames.size()) {
                app.showDMScreen(idx);
            }
        }
    }
}
