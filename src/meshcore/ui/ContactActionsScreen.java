package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;

import meshcore.protocol.ProtocolConstants;
import meshcore.util.TextUtils;

/**
 * Per-contact / repeater actions screen.
 * Shows options like Details, Favorite / Share, Set Path, Reset Path, Remove Contact.
 * For repeaters only, also shows "Ping (Zero Hop)".
 */
public final class ContactActionsScreen extends List implements CommandListener {

    private final AppController app;
    private final int contactIdx;
    private final Displayable returnTo;
    private final boolean isRepeater;
    private final int contactType;

    private final Command cmdBack;

    // Icons for actions
    private final Image iconWriteMessage;
    private final Image iconDetails;
    private final Image iconFavoriteSelected;
    private final Image iconFavoriteUnselected;
    private final Image iconShare;
    private final Image iconSetPath;
    private final Image iconResetPath;
    private final Image iconRemoveContact;
    private final Image iconPingZeroHop;

    private static final int IDX_FAVORITE_CONTACTS = 2;
    private static final int IDX_FAVORITE_REPEATERS = 1;

    public ContactActionsScreen(AppController app, int contactIdx, String name, int type, Displayable returnTo) {
        super("Contact", List.IMPLICIT);
        this.app = app;
        this.contactIdx = contactIdx;
        this.returnTo = returnTo;
        this.contactType = type;
        this.isRepeater = (type == ProtocolConstants.ADV_TYPE_REPEATER);

        String safeName = TextUtils.sanitizeLabel(name, 32);
        setTitle(safeName.length() > 0 ? safeName : "Contact");

        // Load icons once per screen instance
        iconWriteMessage = loadIcon("/write-message.png");
        iconDetails = loadIcon("/contact-details.png");
        iconFavoriteSelected = loadIcon("/favorite-selected.png");
        iconFavoriteUnselected = loadIcon("/favorite-unselected.png");
        iconShare = loadIcon("/share.png");
        iconSetPath = loadIcon("/set-path.png");
        iconResetPath = loadIcon("/reset-path.png");
        iconRemoveContact = loadIcon("/contact-remove.png");
        iconPingZeroHop = loadIcon("/ping-zero-hop.png");

        Image favIcon = app.isFavorite(contactIdx) ? iconFavoriteSelected : iconFavoriteUnselected;

        if (!isRepeater) {
            // Contacts: Write, Details, Favorite, Share, Set/Reset/Remove
            append("Write Message", iconWriteMessage);     // 0
            append("Details", iconDetails);                // 1
            append(app.isFavorite(contactIdx) ? "Remove from Favorites" : "Add to Favorites", favIcon);  // 2
            append("Share", iconShare);                    // 3
            append("Set Path", iconSetPath);               // 4
            append("Reset Path", iconResetPath);           // 5
            append("Remove Contact", iconRemoveContact);   // 6
        } else {
            // Repeaters: Details, Favorite, Share, Ping, Set/Reset/Remove
            append("Details", iconDetails);                // 0
            append(app.isFavorite(contactIdx) ? "Remove from Favorites" : "Add to Favorites", favIcon);  // 1
            append("Share", iconShare);                    // 2
            append("Ping (Zero Hop)", iconPingZeroHop);    // 3
            append("Set Path", iconSetPath);               // 4
            append("Reset Path", iconResetPath);           // 5
            append("Remove Contact", iconRemoveContact);   // 6
        }

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    private static Image loadIcon(String path) {
        try {
            return Image.createImage(path);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateFavoriteRow() {
        int idx = isRepeater ? IDX_FAVORITE_REPEATERS : IDX_FAVORITE_CONTACTS;
        String label = app.isFavorite(contactIdx) ? "Remove from Favorites" : "Add to Favorites";
        Image icon = app.isFavorite(contactIdx) ? iconFavoriteSelected : iconFavoriteUnselected;
        set(idx, label, icon);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int sel = getSelectedIndex();
            if (!isRepeater) {
                // Contacts layout
                switch (sel) {
                    case 0: openWriteMessage(); break;
                    case 1: showDetails(); break;
                    case 2: showFavoriteInfo(); break;
                    case 3: showShareInfo(); break;
                    case 4: openSetPath(); break;
                    case 5: openResetPathConfirm(); break;
                    case 6: openRemoveConfirm(); break;
                }
            } else {
                // Repeaters layout
                switch (sel) {
                    case 0: showDetails(); break;
                    case 1: showFavoriteInfo(); break;
                    case 2: showShareInfo(); break;
                    case 3: openPingInfo(); break;
                    case 4: openSetPath(); break;
                    case 5: openResetPathConfirm(); break;
                    case 6: openRemoveConfirm(); break;
                }
            }
        }
    }

    private void showDetails() {
        // For now, just show contact index and type; can be expanded later.
        String typeStr = isRepeater ? "Repeater" : "Contact";
        Alerts.info(app.getDisplay(), this, "Details",
                "Type: " + typeStr + "\nIndex: " + contactIdx);
    }

    private void showFavoriteInfo() {
        if (app.isFavorite(contactIdx)) {
            app.removeFavorite(contactIdx);
            if (returnTo instanceof ContactsScreen) {
                ((ContactsScreen) returnTo).refreshList();
            }
            if (returnTo instanceof FavoritesScreen) {
                ((FavoritesScreen) returnTo).refreshList();
            }
            updateFavoriteRow();
            Alerts.info(app.getDisplay(), this, "Favorite", "Removed from favorites.");
        } else {
            app.addFavorite(contactIdx);
            if (returnTo instanceof ContactsScreen) {
                ((ContactsScreen) returnTo).refreshList();
            }
            if (returnTo instanceof FavoritesScreen) {
                ((FavoritesScreen) returnTo).refreshList();
            }
            updateFavoriteRow();
            Alerts.info(app.getDisplay(), this, "Favorite", "Added to favorites.");
        }
    }

    private void showShareInfo() {
        String safeName = getTitle();
        app.getDisplay().setCurrent(new ShareContactScreen(app, contactIdx, safeName, contactType, this));
    }

    private void openWriteMessage() {
        // Return to this actions screen when backing out of the DM
        app.showDMScreen(contactIdx, this);
    }

    private void openSetPath() {
        String name = isRepeater ? app.getRepeaterNameForPathByte(app.getRepeaterPathByte(contactIdx))
                                 : null;
        if (name == null || name.length() == 0) {
            name = "Contact";
        }
        app.getDisplay().setCurrent(new PathListScreen(app, contactIdx, name, this));
    }

    private void openResetPathConfirm() {
        javax.microedition.lcdui.Alert a = Alerts.confirm("Reset Path",
                "Reset routing path for this contact? Messages will use flood until a new path is learned.");
        a.addCommand(new Command("Yes", Command.OK, 1));
        a.addCommand(new Command("No", Command.BACK, 2));
        a.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    app.resetPath(contactIdx);
                }
                app.getDisplay().setCurrent(ContactActionsScreen.this);
            }
        });
        app.getDisplay().setCurrent(a, this);
    }

    private void openRemoveConfirm() {
        javax.microedition.lcdui.Alert a = Alerts.confirm("Remove Contact",
                "Remove this contact from your contacts?");
        a.addCommand(new Command("Yes", Command.OK, 1));
        a.addCommand(new Command("No", Command.BACK, 2));
        a.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    app.removeContact(contactIdx);
                    if (isRepeater) {
                        app.showRepeatersScreen();
                    } else {
                        app.showContactsScreen();
                    }
                } else {
                    app.getDisplay().setCurrent(ContactActionsScreen.this);
                }
            }
        });
        app.getDisplay().setCurrent(a, this);
    }

    private void openPingInfo() {
        Alerts.info(app.getDisplay(), this, "Ping (Zero Hop)",
                "Ping (Zero Hop) is not implemented yet.");
    }
}

