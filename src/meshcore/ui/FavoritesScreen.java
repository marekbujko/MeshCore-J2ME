package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;
import java.util.Vector;

import meshcore.protocol.ProtocolConstants;
import meshcore.util.FavoriteStore;

/**
 * Favorites list: shows favorite contacts. Tap to open DM or remove from favorites.
 */
public class FavoritesScreen extends List implements CommandListener {

    private final AppController app;
    private final Vector contactNames;
    private final Vector contactTypes;
    private final Vector contactKeys;
    /** For each list row: hex key (String). */
    private final Vector favoriteKeys = new Vector();
    private final Image iconFavorite;

    private final Command cmdRemove;
    private final Command cmdBack;
    private final Command cmdDM;

    public FavoritesScreen(AppController app,
                           Vector contactNames,
                           Vector contactTypes,
                           Vector contactKeys) {
        super("Favorites", List.IMPLICIT);
        this.app = app;
        this.contactNames = contactNames;
        this.contactTypes = contactTypes;
        this.contactKeys = contactKeys;
        iconFavorite = loadIcon("/favorite-selected.png");
        cmdDM = new Command("Write Message", Command.ITEM, 0);
        cmdRemove = new Command("Remove from Favorites", Command.ITEM, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdDM);
        addCommand(cmdRemove);
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
        favoriteKeys.removeAllElements();
        Vector favs = FavoriteStore.load();
        for (int i = 0; i < favs.size(); i++) {
            String keyHex = (String) favs.elementAt(i);
            int contactIdx = FavoriteStore.indexByKey(contactKeys, keyHex);
            if (contactIdx < 0) continue; // contact removed, skip
            String name = (contactIdx < contactNames.size())
                    ? (String) contactNames.elementAt(contactIdx) : keyHex;
            append(name, iconFavorite != null ? iconFavorite : null);
            favoriteKeys.addElement(keyHex);
        }
        if (favoriteKeys.size() == 0) {
            append("(no favorites)", null);
        }
        setTitle("Favorites (" + favoriteKeys.size() + ")");
    }

    private int getSelectedContactIndex() {
        int sel = getSelectedIndex();
        if (sel < 0 || sel >= favoriteKeys.size()) return -1;
        String keyHex = (String) favoriteKeys.elementAt(sel);
        return FavoriteStore.indexByKey(contactKeys, keyHex);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
            return;
        }
        if (c == cmdRemove) {
            int idx = getSelectedContactIndex();
            if (idx >= 0) {
                app.removeFavorite(idx);
                refreshList();
            }
            return;
        }
        if (c == cmdDM) {
            int idx = getSelectedContactIndex();
            if (idx >= 0) {
                app.showDMScreen(idx, this);
            }
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int idx = getSelectedContactIndex();
            if (idx >= 0) {
                String name = (idx < contactNames.size())
                        ? (String) contactNames.elementAt(idx) : "";
                int type = (idx < contactTypes.size())
                        ? ((Integer) contactTypes.elementAt(idx)).intValue()
                        : ProtocolConstants.ADV_TYPE_NONE;
                app.getDisplay().setCurrent(
                        new ContactActionsScreen(app, idx, name, type, this));
            }
        }
    }
}
