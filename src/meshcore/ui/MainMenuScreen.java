package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import meshcore.protocol.ProtocolConstants;

/**
 * Main menu as a Canvas: 3x2 grid (cols 0-1 = dashboard; col 2 = Notifications top, Favorites bottom),
 * bottom bar (Contacts, Channels, Repeaters). Options via soft keys.
 */
public class MainMenuScreen extends Canvas implements CommandListener {

    private static final int ITEMS = 5;
    private static final int PAD = 4;
    private static final int GRID_COLS = 3;
    /** Dashboard spans 2 columns; col 2 has Notifications and Favorites. */
    private int cellW;
    private int leftPanelW;
    private int leftPanelH;
    private int rightColX;
    private int rightCellW;
    private int rightCellH;
    private static final int BOTTOM_BAR_H = 52;
    private static final int BAR_ICON_ZONE = 32;

    private final AppController app;
    private final Command cmdSelect;
    private final Command cmdAdvertZeroHop;
    private final Command cmdAdvertFloodRouted;
    private final Command cmdActivityLog;
    private final Command cmdDisconnect;
    private final Command cmdConnectTo;
    private final Command cmdMore;

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

    private final String[] labels = new String[]{
        "Notifications", "Favorites", "Channels", "Contacts / DM", "Repeaters"
    };
    /** Bar order left to right: Contacts, Channels, Repeaters. */
    private static final int[] BAR_ORDER = new int[]{ IDX_CONTACTS, IDX_CHANNEL, IDX_REPEATERS };
    private static final String[] barLabels = new String[]{"Contacts", "Channels", "Repeaters"};

    private int selectedIndex = IDX_CHANNEL;
    private boolean notificationHasNew;
    private int gridY;
    private int barY;
    private int barItemW;

    public MainMenuScreen(AppController app) {
        super();
        this.app = app;
        setTitle("MeshCore");
        iconChannels = loadIcon("/channels.png");
        iconContacts = loadIcon("/contacts.png");
        iconRepeaters = loadIcon("/repeaters.png");
        iconNotif = loadIcon("/notification-bell.png");
        iconNotifNew = loadIcon("/notification-bell-new.png");
        iconFavorites = loadIcon("/favorite.png");
        cmdSelect = new Command("Select", Command.OK, 0);
        cmdAdvertZeroHop = new Command("Advert \u2022 Zero Hop", Command.SCREEN, 1);
        cmdAdvertFloodRouted = new Command("Advert \u2022 Flood", Command.SCREEN, 2);
        cmdActivityLog = new Command("Activity Log", Command.SCREEN, 3);
        cmdDisconnect = new Command("Disconnect", Command.SCREEN, 4);
        cmdConnectTo = new Command("Connect To", Command.SCREEN, 4);
        cmdMore = new Command("More", Command.BACK, 5);
        addCommand(cmdSelect);
        addCommand(cmdAdvertZeroHop);
        addCommand(cmdAdvertFloodRouted);
        addCommand(cmdActivityLog);
        addCommand(cmdDisconnect);
        addCommand(cmdMore);
        setCommandListener(this);
    }

    public void setNotificationHasNew(boolean hasNew) {
        if (notificationHasNew != hasNew) {
            notificationHasNew = hasNew;
            repaint();
        }
    }

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

    private void layout(int w, int h) {
        barY = h - BOTTOM_BAR_H;
        barItemW = w / 3;
        gridY = PAD;
        cellW = (w - PAD * (GRID_COLS + 1)) / GRID_COLS;
        leftPanelW = cellW * 2 + PAD;
        leftPanelH = barY - gridY - PAD;
        rightColX = PAD + leftPanelW + PAD;
        rightCellW = cellW;
        rightCellH = (leftPanelH - PAD) / 2;
        if (rightCellH < 32) rightCellH = 32;
    }

    private int indexAt(int x, int y) {
        int w = getWidth();
        int h = getHeight();
        layout(w, h);
        if (y >= barY) {
            int slot = x / barItemW;
            if (slot < 0) slot = 0;
            if (slot > 2) slot = 2;
            return BAR_ORDER[slot];
        }
        if (y < gridY || y >= gridY + leftPanelH) return -1;
        if (x < rightColX) return -1;
        int row = (y - gridY) < (rightCellH + PAD / 2) ? 0 : 1;
        return row == 0 ? IDX_NOTIFICATIONS : IDX_FAVORITES;
    }

    private void activate(int idx) {
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

    private void cellBounds(int index, int[] out) {
        if (index == IDX_NOTIFICATIONS) {
            out[0] = rightColX;
            out[1] = gridY;
            out[2] = rightCellW;
            out[3] = rightCellH;
        } else if (index == IDX_FAVORITES) {
            out[0] = rightColX;
            out[1] = gridY + rightCellH + PAD;
            out[2] = rightCellW;
            out[3] = rightCellH;
        } else {
            int slot = (index == IDX_CONTACTS) ? 0 : (index == IDX_CHANNEL) ? 1 : 2;
            out[0] = slot * barItemW;
            out[1] = barY;
            out[2] = barItemW;
            out[3] = BOTTOM_BAR_H;
        }
    }

    protected void paint(Graphics g) {
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        int w = getWidth();
        int h = getHeight();
        layout(w, h);
        g.setColor(0xE8E8E8);
        g.fillRect(0, 0, w, h);
        int fh = g.getFont().getHeight();

        int leftX = PAD;
        int dashW = leftPanelW - 8;

        g.setColor(0xD8D8D8);
        g.drawRect(leftX, gridY, leftPanelW, leftPanelH);
        g.drawRect(rightColX, gridY, rightCellW, rightCellH);
        g.drawRect(rightColX, gridY + rightCellH + PAD, rightCellW, rightCellH);

        int ins = 4;
        int py = gridY + ins;
        g.setColor(0x606060);
        g.drawString("My Node", leftX + ins, py, Graphics.LEFT | Graphics.TOP);
        String nodeName = app.getNodeName();
        if (nodeName == null || nodeName.length() == 0) nodeName = "-";
        g.setColor(0x000000);
        py += fh;
        g.drawString(trimForWidth(g, nodeName, dashW), leftX + ins, py, Graphics.LEFT | Graphics.TOP);
        py += fh + 4;
        g.setColor(0x606060);
        g.drawString("Noise Floor", leftX + ins, py, Graphics.LEFT | Graphics.TOP);
        Integer noise = app.getNoiseFloor();
        String noiseText;
        if (noise != null) {
            noiseText = noise.intValue() + " dBm";
        } else {
            noiseText = "Loading...";
        }
        g.setColor(0x000000);
        py += fh;
        g.drawString(trimForWidth(g, noiseText, dashW), leftX + ins, py, Graphics.LEFT | Graphics.TOP);
        py += fh + 4;
        g.setColor(0x606060);
        g.drawString("Latest Log", leftX + ins, py, Graphics.LEFT | Graphics.TOP);
        String latestLog = app.getLatestActivityLogLine();
        if (latestLog == null || latestLog.length() == 0) latestLog = "-";
        g.setColor(0x000000);
        py += fh;
        g.drawString(trimForWidth(g, latestLog, dashW), leftX + ins, py, Graphics.LEFT | Graphics.TOP);

        int[] b = new int[4];
        for (int i = 0; i < ITEMS; i++) {
            cellBounds(i, b);
            int cx = b[0], cy = b[1], cw = b[2], ch = b[3];
            boolean selected = (i == selectedIndex);
            if (selected) {
                g.setColor(0xC0C0C0);
                g.fillRect(cx, cy, cw, ch);
            }
            g.setColor(0x000000);
            Image icon = getIcon(i);
            if (icon != null) {
                int ix = cx + cw / 2;
                int iy;
                int anchor;
                if (i < IDX_CHANNEL) {
                    iy = cy + ch / 2;
                    anchor = Graphics.HCENTER | Graphics.VCENTER;
                } else {
                    iy = cy + 2;
                    anchor = Graphics.HCENTER | Graphics.TOP;
                }
                g.drawImage(icon, ix, iy, anchor);
            }
            if (i >= IDX_CHANNEL) {
                String lab = barLabels[(i == IDX_CONTACTS) ? 0 : (i == IDX_CHANNEL) ? 1 : 2];
                g.drawString(lab, cx + cw / 2, cy + BAR_ICON_ZONE, Graphics.HCENTER | Graphics.TOP);
            }
        }
        g.setColor(0xC0C0C0);
        g.drawLine(0, barY, w, barY);
    }

    private String trimForWidth(Graphics g, String s, int maxW) {
        if (s == null || s.length() == 0) return "";
        if (g.getFont().stringWidth(s) <= maxW) return s;
        for (int len = s.length() - 1; len > 0; len--) {
            String t = s.substring(0, len) + "..";
            if (g.getFont().stringWidth(t) <= maxW) return t;
        }
        return "..";
    }

    private Image getIcon(int index) {
        switch (index) {
            case IDX_NOTIFICATIONS: return notificationHasNew ? iconNotifNew : iconNotif;
            case IDX_FAVORITES: return iconFavorites;
            case IDX_CHANNEL: return iconChannels;
            case IDX_CONTACTS: return iconContacts;
            case IDX_REPEATERS: return iconRepeaters;
            default: return null;
        }
    }

    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        layout(getWidth(), getHeight());
        if (action == FIRE || action == KEY_NUM5) {
            activate(selectedIndex);
            return;
        }
        if (action == UP) {
            if (selectedIndex == IDX_FAVORITES) {
                selectedIndex = IDX_NOTIFICATIONS;
            } else if (selectedIndex == IDX_NOTIFICATIONS) {
                selectedIndex = IDX_CONTACTS;
            } else if (selectedIndex >= IDX_CHANNEL) {
                selectedIndex = (selectedIndex == IDX_CHANNEL) ? IDX_NOTIFICATIONS : IDX_FAVORITES;
            }
        } else if (action == DOWN) {
            if (selectedIndex == IDX_NOTIFICATIONS) {
                selectedIndex = IDX_FAVORITES;
            } else if (selectedIndex == IDX_FAVORITES) {
                selectedIndex = IDX_REPEATERS;
            } else if (selectedIndex >= IDX_CHANNEL) {
                selectedIndex = (selectedIndex == IDX_CHANNEL) ? IDX_NOTIFICATIONS : IDX_FAVORITES;
            }
        } else if (action == LEFT) {
            if (selectedIndex <= IDX_FAVORITES) {
                selectedIndex = (selectedIndex == IDX_FAVORITES) ? IDX_NOTIFICATIONS : selectedIndex;
            } else {
                int slot = (selectedIndex == IDX_CONTACTS) ? 0 : (selectedIndex == IDX_CHANNEL) ? 1 : 2;
                selectedIndex = BAR_ORDER[(slot + 2) % 3];
            }
        } else if (action == RIGHT) {
            if (selectedIndex <= IDX_FAVORITES) {
                selectedIndex = (selectedIndex == IDX_NOTIFICATIONS) ? IDX_FAVORITES : selectedIndex;
            } else {
                int slot = (selectedIndex == IDX_CONTACTS) ? 0 : (selectedIndex == IDX_CHANNEL) ? 1 : 2;
                selectedIndex = BAR_ORDER[(slot + 1) % 3];
            }
        }
        repaint();
    }

    protected void pointerPressed(int x, int y) {
        int idx = indexAt(x, y);
        if (idx >= 0) {
            selectedIndex = idx;
            repaint();
        }
    }

    protected void pointerReleased(int x, int y) {
        int idx = indexAt(x, y);
        if (idx >= 0 && idx == selectedIndex) {
            activate(idx);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdSelect) {
            activate(selectedIndex);
            return;
        }
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
        if (c == cmdMore) {
            app.getDisplay().setCurrent(new MoreMenuScreen(app, this));
            return;
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
