package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import meshcore.util.ImageCache;

/**
 * More menu on a Canvas: icon + label rows (same green selection as main grid).
 */
public final class MoreMenuCanvas extends Canvas implements CommandListener {

    private static final int MENU_COUNT = 8;

    private static final String[] MENU_LABELS = new String[]{
        "Add Contact",
        "Add Channel",
        "Share My Contact",
        "Map",
        "Tools",
        "Settings",
        "About",
        "Exit"
    };

    private static final String[] MENU_ICON_PATHS = new String[]{
        "/add-contact.png",
        "/add-channel.png",
        "/share-black.png",
        "/map.png",
        "/tools.png",
        "/settings.png",
        "/about.png",
        "/exit.png"
    };

    private static final int LIST_PAD = 4;
    /** Scroll track width; gap between list and track = {@link #LIST_PAD} (same as left inset). */
    private static final int SCROLL_TRACK_W = 4;
    private static final int ROW_R = 8;
    private static final int ICON_SLOT = 40;
    private static final int TEXT_PAD = 8;
    /** Vertical padding inside row around menu icon (top and bottom). */
    private static final int ICON_V_INSET = 5;

    private final AppController app;
    private final Displayable returnTo;
    private final Image[] menuIcons = new Image[MENU_COUNT];

    private final Command cmdBack;
    private final Command cmdSelect;

    private int listSel;
    private int pointerDownIdx = -1;

    /** Bottom edge of list viewport (exclusive), {@code h - LIST_PAD}. */
    private int listBottom;
    /** Tallest menu row icon (px); used so rows never shrink below icon + insets on small screens. */
    private int maxMenuIconHeight = 32;
    private int rowH = 42;
    private int listTop;
    private int listScrollY;
    private int maxListScroll;
    private int listViewportH;

    private Font rowFont;

    public MoreMenuCanvas(AppController app, Displayable returnTo) {
        this.app = app;
        this.returnTo = returnTo;
        setTitle("More");
        for (int i = 0; i < MENU_COUNT; i++) {
            menuIcons[i] = ImageCache.get(MENU_ICON_PATHS[i]);
            if (menuIcons[i] != null) {
                int ih = menuIcons[i].getHeight();
                if (ih > maxMenuIconHeight) {
                    maxMenuIconHeight = ih;
                }
            }
        }
        cmdBack = new Command("Back", Command.BACK, 2);
        cmdSelect = new Command("Select", Command.OK, 1);
        addCommand(cmdSelect);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    private void ensureFonts() {
        if (rowFont == null) {
            rowFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
    }

    private void layout(int w, int h) {
        listTop = LIST_PAD;
        listBottom = h - LIST_PAD;
        listViewportH = listBottom - listTop;
        if (listViewportH < 1) {
            listViewportH = 1;
        }
        ensureFonts();
        int textBand = rowFont.getHeight() + 10;
        int iconBand = maxMenuIconHeight + 2 * ICON_V_INSET + 8;
        int rowMin = textBand > iconBand ? textBand : iconBand;
        if (rowMin < 40) {
            rowMin = 40;
        }
        rowH = rowMin;
        int totalList = MENU_COUNT * rowH;
        maxListScroll = totalList - listViewportH;
        if (maxListScroll < 0) {
            maxListScroll = 0;
        }
        if (listScrollY > maxListScroll) {
            listScrollY = maxListScroll;
        }
    }

    private void clampScroll() {
        if (listScrollY < 0) {
            listScrollY = 0;
        }
        if (listScrollY > maxListScroll) {
            listScrollY = maxListScroll;
        }
    }

    private void ensureSelectionVisible() {
        int rowTop = listSel * rowH;
        int rowBot = rowTop + rowH;
        if (rowTop < listScrollY) {
            listScrollY = rowTop;
        } else if (rowBot > listScrollY + listViewportH) {
            listScrollY = rowBot - listViewportH;
        }
        clampScroll();
    }

    /** Pixels reserved on the right for gap + scrollbar (when scrollable). */
    private int listRightReserve(int w, boolean scrollVisible) {
        if (!scrollVisible) {
            return LIST_PAD;
        }
        return LIST_PAD + SCROLL_TRACK_W + LIST_PAD;
    }

    private int indexAt(int x, int y) {
        int w = getWidth();
        int h = getHeight();
        layout(w, h);
        if (y < listTop || y >= listBottom) {
            return -1;
        }
        boolean sv = maxListScroll > 0;
        int reserve = listRightReserve(w, sv);
        int contentW = w - LIST_PAD - reserve;
        if (contentW < 1 || x < LIST_PAD || x >= LIST_PAD + contentW) {
            return -1;
        }
        int relY = y - listTop + listScrollY;
        int row = relY / rowH;
        if (row < 0) {
            row = 0;
        }
        if (row >= MENU_COUNT) {
            row = MENU_COUNT - 1;
        }
        return row;
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        layout(w, h);
        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        boolean scrollVisible = maxListScroll > 0;
        int rightReserve = listRightReserve(w, scrollVisible);
        int rw = w - LIST_PAD - rightReserve;
        if (rw < 40) {
            rw = 40;
        }

        g.setFont(rowFont);
        int textLeft = LIST_PAD + ICON_SLOT + TEXT_PAD;
        for (int i = 0; i < MENU_COUNT; i++) {
            int ry = listTop + i * rowH - listScrollY;
            if (ry + rowH <= listTop || ry >= listBottom) {
                continue;
            }
            int rx = LIST_PAD;
            boolean sel = i == listSel;
            if (sel) {
                g.setColor(0xD4EDE0);
                g.fillRoundRect(rx, ry, rw, rowH - 2, ROW_R, ROW_R);
                g.setColor(UiTheme.CARD_BORDER);
                g.drawRoundRect(rx, ry, rw - 1, rowH - 3, ROW_R, ROW_R);
                g.setColor(UiTheme.TIMELINE_NODE_ACCENT);
                g.drawRoundRect(rx + 1, ry + 1, rw - 3, rowH - 5, ROW_R - 1, ROW_R - 1);
            } else {
                g.setColor(UiTheme.CARD_FILL);
                g.fillRoundRect(rx, ry, rw, rowH - 2, ROW_R, ROW_R);
                g.setColor(UiTheme.CARD_BORDER);
                g.drawRoundRect(rx, ry, rw - 1, rowH - 3, ROW_R, ROW_R);
            }
            Image ic = menuIcons[i];
            if (ic != null) {
                int icx = rx + LIST_PAD + ICON_SLOT / 2;
                int cellTop = ry + 1;
                int cellH = rowH - 3;
                int bandH = cellH - 2 * ICON_V_INSET;
                int icy;
                if (bandH < 8) {
                    icy = cellTop + cellH / 2;
                } else {
                    icy = cellTop + ICON_V_INSET + bandH / 2;
                }
                g.drawImage(ic, icx, icy, Graphics.HCENTER | Graphics.VCENTER);
            }
            g.setColor(UiTheme.TEXT_DARK);
            int ty = ry + (rowH - 2 - rowFont.getHeight()) / 2;
            g.drawString(MENU_LABELS[i], textLeft, ty, Graphics.LEFT | Graphics.TOP);
        }

        if (scrollVisible) {
            int trackH = listBottom - listTop;
            if (trackH > 12) {
                int tx = w - LIST_PAD - SCROLL_TRACK_W;
                int tw = SCROLL_TRACK_W;
                int ty = listTop;
                g.setColor(UiTheme.SCROLL_BAR_BG);
                g.fillRect(tx, ty, tw, trackH);
                int thumbH = Math.max(12, trackH * listViewportH / (MENU_COUNT * rowH));
                int tr = maxListScroll == 0 ? 0 : (trackH - thumbH) * listScrollY / maxListScroll;
                g.setColor(UiTheme.SCROLL_BAR_THUMB);
                g.fillRect(tx, ty + tr, tw, thumbH);
            }
        }
    }

    private void activateMenu(int idx) {
        switch (idx) {
            case 0:
                app.getDisplay().setCurrent(new AddContactOptionsScreen(app, this));
                return;
            case 1:
                app.getDisplay().setCurrent(new AddChannelOptionsScreen(app, this));
                return;
            case 2:
                app.showMyContactCode(this);
                return;
            case 3:
                app.showMapView(this);
                return;
            case 4:
                app.showToolsScreen(this);
                return;
            case 5:
                app.showSettingsScreen(this);
                return;
            case 6:
                app.getDisplay().setCurrent(new AboutScreen(app, this));
                return;
            case 7:
                app.exitApp();
                return;
            default:
        }
    }

    protected void keyPressed(int keyCode) {
        layout(getWidth(), getHeight());
        int action = getGameAction(keyCode);
        if (action == FIRE || keyCode == KEY_NUM5) {
            activateMenu(listSel);
            return;
        }
        if (action == UP) {
            listSel = (listSel + MENU_COUNT - 1) % MENU_COUNT;
            ensureSelectionVisible();
        } else if (action == DOWN) {
            listSel = (listSel + 1) % MENU_COUNT;
            ensureSelectionVisible();
        }
        repaint();
    }

    protected void pointerPressed(int x, int y) {
        int idx = indexAt(x, y);
        pointerDownIdx = idx;
        if (idx >= 0) {
            listSel = idx;
            ensureSelectionVisible();
        }
        repaint();
    }

    protected void pointerReleased(int x, int y) {
        int idx = indexAt(x, y);
        if (pointerDownIdx < 0 || idx != pointerDownIdx) {
            return;
        }
        activateMenu(idx);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdSelect) {
            activateMenu(listSel);
        }
    }
}
