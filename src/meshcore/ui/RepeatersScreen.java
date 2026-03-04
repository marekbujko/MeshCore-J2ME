package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import java.util.Vector;

import meshcore.protocol.ProtocolConstants;

/**
 * Repeaters list screen: shows contacts with type ADV_TYPE_REPEATER.
 */
public class RepeatersScreen extends List implements CommandListener {

    private final AppController app;
    private final Vector contactNames;
    private final Vector contactTypes;
    private final Vector visibleIndices;
    private final Command cmdRefresh;
    private final Command cmdBack;

    public RepeatersScreen(AppController app, Vector contactNames, Vector contactTypes) {
        super("Repeaters", List.IMPLICIT);
        this.app = app;
        this.contactNames = contactNames;
        this.contactTypes = contactTypes;
        this.visibleIndices = new Vector();
        cmdRefresh = new Command("Refresh", Command.SCREEN, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdRefresh);
        addCommand(cmdBack);
        setCommandListener(this);
        refreshList();
    }

    public void refreshList() {
        deleteAll();
        visibleIndices.removeAllElements();
        for (int i = 0; i < contactNames.size(); i++) {
            int type = (i < contactTypes.size())
                ? ((Integer) contactTypes.elementAt(i)).intValue() : ProtocolConstants.ADV_TYPE_NONE;
            if (type == ProtocolConstants.ADV_TYPE_REPEATER) {
                String name = (String) contactNames.elementAt(i);
                visibleIndices.addElement(new Integer(i));
                append(name, null);
            }
        }
        if (visibleIndices.size() == 0) {
            append("(no repeaters)", null);
        }
        setTitle("Repeaters (" + visibleIndices.size() + ")");
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
    }
}
