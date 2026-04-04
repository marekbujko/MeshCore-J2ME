package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

import meshcore.util.RepeaterLoginSessionStore;
import meshcore.util.TextUtils;

/**
 * Shown when a repeater already has a stored successful login session.
 * Offers Manage repeaters (stub) and Log out (returns to password screen).
 */
public final class RepeaterLoggedInScreen extends Canvas implements CommandListener {

    private final AppController app;
    private final int contactIdx;
    private final String contactTitle;
    private final Displayable returnTo;
    private final Command cmdBack;
    private final Command cmdSelect;

    private final RepeaterLoginSessionStore.Session session;
    private final String pubKeyHex;

    private Font titleFont;
    private Font bodyFont;
    private Font smallFont;
    private Font sectionHeaderFont;

    private final UiScrollController scrollCtrl = new UiScrollController();

    private int headerBarHeight = 44;

    private static final int FOCUS_MANAGE = 0;
    private static final int FOCUS_LOGOUT = 1;

    /** D-pad focus: Manage repeaters row, Log out row. */
    private int focusIndex = FOCUS_MANAGE;

    private int hitManageTop;
    private int hitManageBottom;
    private int hitLogoutTop;
    private int hitLogoutBottom;
    private int contentPadLeft;
    private int contentMaxW;

    private int manageContentTop;
    private int manageContentBottom;
    private int logoutContentTop;
    private int logoutContentBottom;

    public RepeaterLoggedInScreen(AppController app, int contactIdx, String contactTitle, Displayable returnTo) {
        this.app = app;
        this.contactIdx = contactIdx;
        this.contactTitle = (contactTitle != null) ? contactTitle : "Repeater";
        this.returnTo = returnTo;
        this.pubKeyHex = app.getContactPublicKeyHex(contactIdx);
        this.session = (pubKeyHex != null) ? RepeaterLoginSessionStore.load(pubKeyHex) : null;

        setFullScreenMode(false);
        String midletTitle = TextUtils.sanitizeLabel(this.contactTitle, 32);
        setTitle(midletTitle.length() > 0 ? midletTitle : "Repeater");

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdSelect = new Command("Select", Command.SCREEN, 1);

        if (session == null) {
            app.getDisplay().setCurrent(new RepeaterLoginScreen(app, contactIdx, this.contactTitle, returnTo));
            return;
        }

        addCommand(cmdSelect);
        addCommand(cmdBack);
        setCommandListener(this);
        scrollCtrl.reset();
    }

    private static String formatSessionAge(long savedAtMs) {
        long ago = System.currentTimeMillis() - savedAtMs;
        if (ago < 0L) {
            ago = 0L;
        }
        if (ago < 60000L) {
            return "Active since: " + (ago / 1000L) + "s ago";
        }
        if (ago < 3600000L) {
            return "Active since: " + (ago / 60000L) + "min ago";
        }
        return "Active since: " + (ago / 3600000L) + "h ago";
    }

    private String buildDetailLines() {
        if (session == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        sb.append(session.isAdmin() ? "Role: admin" : "Role: guest");
        sb.append('\n');
        sb.append("Session tag: ");
        sb.append(session.serverTag);
        sb.append('\n');
        sb.append("Permissions: ");
        if (session.newPermissionsOrMinus1 >= 0) {
            sb.append(session.newPermissionsOrMinus1);
        } else {
            sb.append('-');
        }
        sb.append('\n');
        sb.append(formatSessionAge(session.savedAtMs));
        return sb.toString();
    }

    private void ensureFonts() {
        if (titleFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            sectionHeaderFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        }
    }

    private void ensureFocusVisible() {
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int headerBarH = UiScreenHeader.measureHeight(w, "Logged In", null, titleFont, smallFont);
        int vh = h - headerBarH;
        if (vh < 1) {
            vh = 1;
        }
        int edge = 10;
        int top;
        int bot;
        switch (focusIndex) {
            case FOCUS_MANAGE:
                top = manageContentTop;
                bot = manageContentBottom;
                break;
            case FOCUS_LOGOUT:
                top = logoutContentTop;
                bot = logoutContentBottom;
                break;
            default:
                return;
        }
        int sy = scrollCtrl.getScrollY();
        if (top - sy < edge) {
            scrollCtrl.setScrollY(Math.max(0, top - edge));
        } else if (bot - sy > vh - edge) {
            int max = scrollCtrl.getMaxScroll(vh);
            int want = bot - vh + edge;
            scrollCtrl.setScrollY(want < 0 ? 0 : (want > max ? max : want));
        }
        scrollCtrl.clamp(vh);
    }

    private void moveFocus(int delta) {
        int n = focusIndex + delta;
        if (n < FOCUS_MANAGE) {
            n = FOCUS_MANAGE;
        }
        if (n > FOCUS_LOGOUT) {
            n = FOCUS_LOGOUT;
        }
        focusIndex = n;
        ensureFocusVisible();
        repaint();
    }

    private void activateFocusedControl() {
        switch (focusIndex) {
            case FOCUS_MANAGE:
                app.getDisplay().setCurrent(new NotImplementedScreen(app, "Manage repeaters", this));
                break;
            case FOCUS_LOGOUT:
                doLogout();
                break;
            default:
                break;
        }
    }

    protected void paint(Graphics g) {
        if (session == null) {
            return;
        }

        ensureFonts();

        int w = getWidth();
        int h = getHeight();
        int pad = 8;
        int scrollY = scrollCtrl.getScrollY();
        int x = pad;
        int maxW = w - (pad * 2) - 6;
        contentPadLeft = x;
        contentMaxW = maxW;

        int headerBarH = UiScreenHeader.measureHeight(w, "Logged In", null, titleFont, smallFont);
        int viewportH = h - headerBarH;
        if (viewportH < 1) {
            viewportH = 1;
        }

        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        g.clipRect(0, headerBarH, w, viewportH);
        int y = headerBarH - scrollY + 8;

        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.BAR_BG);
        g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
        g.setColor(UiTheme.TEXT_DARK);
        y += UiCanvasUtil.drawWrapped(g, "Status", x + 10, y, maxW - 10);
        y += 6;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawNewlineSeparatedWrapped(g, buildDetailLines(), x + 10, y, maxW - 10);
        y += 14;
        g.setColor(UiTheme.CARD_BORDER);
        g.drawLine(x + 4, y, x + maxW - 4, y);
        y += 12;

        int btnH = bodyFont.getHeight() + 14;

        hitManageTop = y;
        manageContentTop = hitManageTop - headerBarH + scrollY;
        g.setColor(UiTheme.BAR_BG);
        g.fillRoundRect(x + 2, y, maxW - 4, btnH, 8, 8);
        g.setColor(UiTheme.BG_WHITE);
        String m = "Manage repeaters";
        int tw = bodyFont.stringWidth(m);
        int tx = x + 2 + (maxW - 4 - tw) / 2;
        if (tx < x + 2) {
            tx = x + 2;
        }
        g.drawString(m, tx, y + (btnH - bodyFont.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
        y += btnH;
        hitManageBottom = y;
        manageContentBottom = hitManageBottom - headerBarH + scrollY;
        y += 10;

        hitLogoutTop = y;
        logoutContentTop = hitLogoutTop - headerBarH + scrollY;
        g.setColor(UiTheme.CARD_FILL);
        g.fillRoundRect(x + 2, y, maxW - 4, btnH, 8, 8);
        g.setColor(UiTheme.CARD_BORDER);
        g.drawRoundRect(x + 2, y, maxW - 4, btnH, 8, 8);
        g.setColor(UiTheme.TEXT_DARK);
        String lo = "Log out";
        int lw = bodyFont.stringWidth(lo);
        int lx = x + 2 + (maxW - 4 - lw) / 2;
        if (lx < x + 2) {
            lx = x + 2;
        }
        g.drawString(lo, lx, y + (btnH - bodyFont.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
        y += btnH;
        hitLogoutBottom = y;
        logoutContentBottom = hitLogoutBottom - headerBarH + scrollY;

        if (focusIndex == FOCUS_MANAGE) {
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x, hitManageTop - 2, maxW, btnH + 4, 10, 10);
            g.drawRoundRect(x - 1, hitManageTop - 3, maxW + 2, btnH + 6, 11, 11);
        }
        if (focusIndex == FOCUS_LOGOUT) {
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x, hitLogoutTop - 2, maxW, btnH + 4, 10, 10);
            g.drawRoundRect(x - 1, hitLogoutTop - 3, maxW + 2, btnH + 6, 11, 11);
        }

        y += 8;
        int contentHeight = y - headerBarH + scrollY + pad;
        if (contentHeight < viewportH) {
            contentHeight = viewportH;
        }
        scrollCtrl.setContentHeight(contentHeight);
        scrollCtrl.clamp(viewportH);

        g.setClip(0, 0, w, h);

        int maxScroll = scrollCtrl.getMaxScroll(viewportH);
        if (maxScroll > 0) {
            int barX = w - 4;
            int barTop = headerBarH + 2;
            int barLen = h - barTop - 4;
            g.setColor(UiTheme.SCROLL_BAR_BG);
            g.drawLine(barX, barTop, barX, barTop + barLen);
            int thumbH = Math.max(10, (barLen * viewportH) / Math.max(contentHeight, 1));
            int thumbY = barTop + ((barLen - thumbH) * scrollCtrl.getScrollY()) / maxScroll;
            g.setColor(UiTheme.SCROLL_BAR_THUMB);
            g.fillRect(barX - 1, thumbY, 3, thumbH);
        }

        headerBarHeight = UiScreenHeader.paint(g, w, "Logged In", null, titleFont, smallFont);
    }

    private boolean inContentX(int x) {
        return x >= contentPadLeft && x < contentPadLeft + contentMaxW;
    }

    protected void pointerPressed(int x, int y) {
        if (session == null) {
            return;
        }
        if (y < headerBarHeight) {
            return;
        }
        scrollCtrl.pointerPressed(y);
        if (y >= hitManageTop && y < hitManageBottom && inContentX(x)) {
            focusIndex = FOCUS_MANAGE;
            app.getDisplay().setCurrent(new NotImplementedScreen(app, "Manage repeaters", this));
            return;
        }
        if (y >= hitLogoutTop && y < hitLogoutBottom && inContentX(x)) {
            focusIndex = FOCUS_LOGOUT;
            doLogout();
        }
    }

    protected void pointerDragged(int x, int y) {
        ensureFonts();
        int w = getWidth();
        int hh = UiScreenHeader.measureHeight(w, "Logged In", null, titleFont, smallFont);
        int vh = getHeight() - hh;
        if (vh < 1) {
            vh = 1;
        }
        if (scrollCtrl.onDrag(y, vh)) {
            repaint();
        }
    }

    protected void pointerReleased(int x, int y) {
        scrollCtrl.pointerReleased();
    }

    protected void keyPressed(int keyCode) {
        ensureFonts();
        int w = getWidth();
        int hh = UiScreenHeader.measureHeight(w, "Logged In", null, titleFont, smallFont);
        int vh = getHeight() - hh;
        if (vh < 1) {
            vh = 1;
        }
        int action = getGameAction(keyCode);
        int step = UiScrollController.computeStep(bodyFont);
        if (action == Canvas.UP) {
            if (focusIndex > FOCUS_MANAGE) {
                moveFocus(-1);
            } else if (scrollCtrl.onKey(Canvas.UP, step, vh)) {
                repaint();
            }
            return;
        }
        if (action == Canvas.DOWN) {
            if (focusIndex < FOCUS_LOGOUT) {
                moveFocus(1);
            } else if (scrollCtrl.onKey(Canvas.DOWN, step, vh)) {
                repaint();
            }
            return;
        }
        if (action == Canvas.FIRE) {
            activateFocusedControl();
            return;
        }
        if (scrollCtrl.onKey(action, step, vh)) {
            repaint();
        }
    }

    private void doLogout() {
        app.clearRepeaterLoginSession(contactIdx);
        app.getDisplay().setCurrent(new RepeaterLoginScreen(app, contactIdx, contactTitle, returnTo));
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            if (returnTo != null) {
                app.getDisplay().setCurrent(returnTo);
            } else {
                app.showMainMenu();
            }
            return;
        }
        if (c == cmdSelect) {
            activateFocusedControl();
        }
    }
}
