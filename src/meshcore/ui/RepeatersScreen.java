package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
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

    private final Image iconFavorite;
    private final Image iconRepeater;

    public RepeatersScreen(AppController app, Vector contactNames, Vector contactTypes) {
        super("Repeaters", List.IMPLICIT);
        this.app = app;
        this.contactNames = contactNames;
        this.contactTypes = contactTypes;
        this.visibleIndices = new Vector();
        this.iconFavorite = loadIcon("/favorite-selected.png");
        this.iconRepeater = loadIcon("/repeaters.png");
        cmdRefresh = new Command("Refresh", Command.SCREEN, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdRefresh);
        addCommand(cmdBack);
        setCommandListener(this);
        refreshList();
    }

    private static Image loadIcon(String path) {
        try {
            return Image.createImage(path);
        } catch (Exception e) {
            return null;
        }
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
                Image icon = app.isFavorite(i) ? iconFavorite : iconRepeater;
                append(name, icon);
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
        if (c == List.SELECT_COMMAND) {
            int sel = getSelectedIndex();
            if (sel < 0 || sel >= visibleIndices.size()) return;
            int idx = ((Integer) visibleIndices.elementAt(sel)).intValue();
            if (idx >= 0 && idx < contactNames.size()) {
                String name = (String) contactNames.elementAt(idx);
                int type = (idx < contactTypes.size())
                        ? ((Integer) contactTypes.elementAt(idx)).intValue()
                        : ProtocolConstants.ADV_TYPE_NONE;
                app.getDisplay().setCurrent(
                        new ContactActionsScreen(app, idx, name, type, this));
            }
        }
    }
}
