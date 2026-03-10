package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;

import meshcore.protocol.ProtocolConstants;

/**
 * Main menu list: Channels, Contacts.
 * Options menu: Advert • Zero Hop, Advert • Flood Routed, Activity Log, Disconnect, Settings.
 */
public class MainMenuScreen extends List implements CommandListener {

    private final AppController app;
    private final Command cmdAdvertZeroHop;
    private final Command cmdAdvertFloodRouted;
    private final Command cmdActivityLog;
    private final Command cmdDisconnect;
    private final Command cmdConnectTo;
    private final Command cmdSettings;

    public static final int IDX_NOTIFICATIONS = 0;
    public static final int IDX_FAVORITES = 1;
    public static final int IDX_CHANNEL = 2;
    public static final int IDX_CONTACTS = 3;
    public static final int IDX_REPEATERS = 4;

    private final Image iconChannels;
    private final Image iconContacts;
    private final Image iconRepeaters;
    private final Image iconNotif;
    private final Image iconNotifNew;
    private final Image iconFavorites;

    public MainMenuScreen(AppController app) {
        super("MeshCore", List.IMPLICIT);
        this.app = app;
        iconChannels = loadIcon("/channels.png");
        iconContacts = loadIcon("/contacts.png");
        iconRepeaters = loadIcon("/repeaters.png");
        iconNotif = loadIcon("/notification-bell.png");
        iconNotifNew = loadIcon("/notification-bell-new.png");
        iconFavorites = loadIcon("/favorite.png");
        append("Notifications", iconNotif != null ? iconNotif : null);
        append("Favorites", iconFavorites != null ? iconFavorites : null);
        append("Channels", iconChannels);
        append("Contacts / DM", iconContacts);
        append("Repeaters", iconRepeaters);
        cmdAdvertZeroHop = new Command("Advert • Zero Hop", Command.SCREEN, 0);
        cmdAdvertFloodRouted = new Command("Advert • Flood Routed", Command.SCREEN, 1);
        cmdActivityLog = new Command("Activity Log", Command.SCREEN, 2);
        cmdDisconnect = new Command("Disconnect", Command.SCREEN, 3);
        cmdConnectTo = new Command("Connect To", Command.SCREEN, 3);
        cmdSettings = new Command("Settings", Command.BACK, 4);
        addCommand(cmdAdvertZeroHop);
        addCommand(cmdAdvertFloodRouted);
        addCommand(cmdActivityLog);
        addCommand(cmdDisconnect);
        addCommand(cmdSettings);
        setCommandListener(this);
    }

    public void setNotificationHasNew(boolean hasNew) {
        Image icon = hasNew ? iconNotifNew : iconNotif;
        String label = "Notifications";
        set(IDX_NOTIFICATIONS, label, icon);
    }

    /** When connected show Disconnect, when not-connected show Connect To. */
    public void setConnectCommandMode(boolean showConnectTo) {
        if (showConnectTo) {
            removeCommand(cmdDisconnect);
            addCommand(cmdConnectTo);
        } else {
            removeCommand(cmdConnectTo);
            addCommand(cmdDisconnect);
        }
    }

    private static Image loadIcon(String path) {
        try {
            return Image.createImage(path);
        } catch (Exception e) {
            return null;
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdAdvertZeroHop) {
            showAdvertConfirm("Zero Hop", ProtocolConstants.ADVERT_ZERO_HOP);
            return;
        }
        if (c == cmdAdvertFloodRouted) {
            showAdvertConfirm("Flood Routed", ProtocolConstants.ADVERT_FLOOD);
            return;
        }
        if (c == cmdActivityLog) {
            app.showActivityLogScreen();
            return;
        }
        if (c == cmdDisconnect || c == cmdConnectTo) {
            app.disconnect();
            app.showConnectScreen();
            return;
        }
        if (c == cmdSettings) {
            app.showSettingsScreen();
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int idx = getSelectedIndex();
            if (idx == IDX_CHANNEL) {
                app.showChannelListScreen();
            } else if (idx == IDX_CONTACTS) {
                app.showContactsScreen();
            } else if (idx == IDX_REPEATERS) {
                app.showRepeatersScreen();
            } else if (idx == IDX_NOTIFICATIONS) {
                app.showNotificationsScreen();
            } else if (idx == IDX_FAVORITES) {
                app.showFavoritesScreen();
            }
        }
    }

    private void showAdvertConfirm(String advertName, final int advertType) {
        javax.microedition.lcdui.Alert alert = Alerts.confirm("Advert", "Send " + advertName + " Advert?");
        Command cmdYes = new Command("Yes", Command.OK, 1);
        Command cmdNo = new Command("No", Command.CANCEL, 2);
        alert.addCommand(cmdYes);
        alert.addCommand(cmdNo);
        final MainMenuScreen self = this;
        alert.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable disp) {
                if (cmd.getCommandType() == Command.OK) {
                    app.setAdvertType(advertType);
                    new Thread(new Runnable() {
                        public void run() {
                            app.sendAdvert();
                            app.getDisplay().callSerially(new Runnable() {
                                public void run() {
                                    Alerts.info(app.getDisplay(), self, "Success", "Advert sent");
                                }
                            });
                        }
                    }).start();
                }
                app.getDisplay().setCurrent(self);
            }
        });
        app.getDisplay().setCurrent(alert, this);
    }
}
