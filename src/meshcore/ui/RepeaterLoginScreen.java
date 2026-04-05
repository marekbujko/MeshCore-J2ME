package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import meshcore.util.RepeaterPasswordStore;
import meshcore.util.TextUtils;

/**
 * Canvas UI for repeater login (CMD_SEND_LOGIN): title, password, remember, path-styled login button.
 */
public final class RepeaterLoginScreen extends Canvas implements CommandListener {

    private static final long LOGIN_REPLY_TIMEOUT_MS = 60000L;

    private static final String TXT_TITLE = "Authentication Required";
    private static final String TXT_SUB =
            "Please log in to the repeater first";
    private static final String TXT_REMEMBER = "Remember password";
    private static final String TXT_FOOTER =
            "Some repeater owners may allow guests to view metrics without a password";
    private static final String TXT_PWD_PLACEHOLDER = "Select to enter password";

    private final AppController app;
    private final int contactIdx;
    /** Shown in the top bar (same source as MIDlet title). */
    private final String nodeTitle;
    private final Displayable returnTo;
    private final Command cmdBack;
    private final Command cmdSelect;

    private final int pathHops;
    private final String pubKeyHex;

    private String password = "";
    private boolean remember = false;

    private Font titleFont;
    private Font bodyFont;
    private Font smallFont;

    private final UiScrollController scrollCtrl = new UiScrollController();
    private final UiScrollRepaintThrottler scrollDragRepaint = new UiScrollRepaintThrottler();

    /** Top bar height; updated each paint for pointer handling. */
    private int headerBarHeight = 44;

    /** Screen Y bounds for hit testing (inclusive top, exclusive bottom), updated each paint. */
    private int hitPwdTop;
    private int hitPwdBottom;
    private int hitCbTop;
    private int hitCbBottom;
    private int hitBtnTop;
    private int hitBtnBottom;
    private int contentPadLeft;
    private int contentMaxW;

    private static final int FOCUS_PASSWORD = 0;
    private static final int FOCUS_REMEMBER = 1;
    private static final int FOCUS_BUTTON = 2;

    /** Keyboard / D-pad focus (password field, remember row, log in button). */
    private int focusIndex = FOCUS_PASSWORD;
    /** Content-space Y (add scrollY for screen coords); updated each paint. */
    private int pwdContentTop;
    private int pwdContentBottom;
    private int cbContentTop;
    private int cbContentBottom;
    private int btnContentTop;
    private int btnContentBottom;

    /** Waiting for PUSH login success/fail after send. */
    private volatile boolean loginWaiting = false;
    private volatile boolean loginResolved = true;
    private volatile int loginWaitGeneration = 0;
    private volatile int loginDots = 0;

    public RepeaterLoginScreen(AppController app, int contactIdx, String nodeTitle, Displayable returnTo) {
        this.app = app;
        this.contactIdx = contactIdx;
        this.nodeTitle = (nodeTitle != null) ? nodeTitle : "";
        this.returnTo = returnTo;
        this.pathHops = app.getContactPathHops(contactIdx);
        this.pubKeyHex = app.getContactPublicKeyHex(contactIdx);

        setFullScreenMode(false);
        setTitle(TextUtils.sanitizeLabel(nodeTitle, 32).length() > 0
                ? TextUtils.sanitizeLabel(nodeTitle, 32) : "Repeater");

        if (pubKeyHex != null && pubKeyHex.length() > 0) {
            String saved = RepeaterPasswordStore.load(pubKeyHex);
            if (saved != null) {
                password = saved;
                remember = true;
            }
        }

        cmdBack = new Command("Back", Command.BACK, 2);
        cmdSelect = new Command("Select", Command.SCREEN, 1);
        addCommand(cmdSelect);
        addCommand(cmdBack);
        setCommandListener(this);

        scrollCtrl.reset();
    }

    protected void showNotify() {
        scrollCtrl.reset();
        focusIndex = FOCUS_PASSWORD;
        repaint();
    }

    protected void sizeChanged(int w, int h) {
        ensureFonts();
        int hh = UiScreenHeader.measureHeight(w, "Log in", null, titleFont, smallFont);
        int vh = h - hh;
        if (vh < 1) {
            vh = 1;
        }
        scrollCtrl.clamp(vh);
        ensureFocusVisible();
        repaint();
    }

    private void ensureFocusVisible() {
        if (loginWaiting) {
            return;
        }
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int headerBarH = UiScreenHeader.measureHeight(w, "Log in", null, titleFont, smallFont);
        int vh = h - headerBarH;
        if (vh < 1) {
            vh = 1;
        }
        int edge = 10;
        int top;
        int bot;
        switch (focusIndex) {
            case FOCUS_PASSWORD:
                top = pwdContentTop;
                bot = pwdContentBottom;
                break;
            case FOCUS_REMEMBER:
                top = cbContentTop;
                bot = cbContentBottom;
                break;
            case FOCUS_BUTTON:
                top = btnContentTop;
                bot = btnContentBottom;
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
        if (loginWaiting) {
            return;
        }
        int n = focusIndex + delta;
        if (n < FOCUS_PASSWORD) {
            n = FOCUS_PASSWORD;
        }
        if (n > FOCUS_BUTTON) {
            n = FOCUS_BUTTON;
        }
        focusIndex = n;
        ensureFocusVisible();
        repaint();
    }

    /** Activate control under current focus (password / checkbox / button). */
    private void activateFocusedControl() {
        if (loginWaiting) {
            return;
        }
        switch (focusIndex) {
            case FOCUS_PASSWORD:
                openPasswordEditor();
                break;
            case FOCUS_REMEMBER:
                remember = !remember;
                repaint();
                break;
            case FOCUS_BUTTON:
                doLogin();
                break;
            default:
                break;
        }
    }

    /** Shows or hides the full-screen "sending login" wait overlay and its timers. */
    public void setAwaitingLoginReply(boolean waiting) {
        if (waiting) {
            loginWaitGeneration++;
            loginResolved = false;
            loginWaiting = true;
            loginDots = 0;
            startLoginWaitThreads();
        } else {
            loginResolved = true;
            loginWaiting = false;
        }
        repaint();
    }

    private void startLoginWaitThreads() {
        final javax.microedition.lcdui.Display disp = app.getDisplay();
        if (disp == null) {
            return;
        }

        final UiWaitController.WaitState waitState = new UiWaitController.WaitState() {
            public int getGeneration() {
                return loginWaitGeneration;
            }

            public boolean isResolved() {
                return loginResolved;
            }

            public boolean isWaiting() {
                return loginWaiting;
            }

            public void onDot(int dotIndex) {
                loginDots = dotIndex;
                repaint();
            }

            public void onTimeout() {
                if (loginResolved) {
                    return;
                }
                loginResolved = true;
                loginWaiting = false;
                app.clearPendingTelemetryAfterLogin();
                app.clearLoginPending();
                Alerts.ok(disp, RepeaterLoginScreen.this, "Login",
                        "No login response (timeout). The node may be offline or not support login.");
                repaint();
            }
        };

        UiWaitController.startWaitingDots(disp, this, waitState, 300, 4);
        UiWaitController.startTimeoutWatcher(disp, this, waitState, LOGIN_REPLY_TIMEOUT_MS, 250);
    }

    private String loginButtonLabel() {
        if (pathHops > 0) {
            if (pathHops == 1) {
                return "Log In \u2022 1 hop";
            }
            return "Log In \u2022 " + pathHops + " hops";
        }
        return "Log In \u2022 Direct";
    }

    private Font sectionHeaderFont;

    private void ensureFonts() {
        if (titleFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            sectionHeaderFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        }
    }

    /**
     * Draws a thick checkmark inside the checkbox (multiple stroked offsets; J2ME has no line width).
     */
    private static void drawBoldCheckmark(Graphics g, int bx, int by, int box) {
        g.setColor(UiTheme.TEXT_DARK);
        int ax = bx + 3;
        int ay = by + 8;
        int mx = bx + 6;
        int my = by + 11;
        int ex = bx + box - 2;
        int ey = by + 4;
        for (int d = -1; d <= 1; d++) {
            g.drawLine(ax + d, ay, mx + d, my);
            g.drawLine(ax, ay + d, mx, my + d);
        }
        for (int d = -1; d <= 1; d++) {
            g.drawLine(mx + d, my, ex + d, ey);
            g.drawLine(mx, my + d, ex, ey + d);
        }
    }

    protected void paint(Graphics g) {
        ensureFonts();

        int w = getWidth();
        int h = getHeight();
        int pad = 8;
        int scrollY = scrollCtrl.getScrollY();
        int x = pad;
        int maxW = w - (pad * 2) - 6;
        contentPadLeft = x;
        contentMaxW = maxW;

        int headerBarH = UiScreenHeader.measureHeight(w, "Log in", null, titleFont, smallFont);
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
        y += UiCanvasUtil.drawWrapped(g, TXT_TITLE, x + 10, y, maxW - 10);
        y += 6;
        g.setFont(smallFont);
        g.setColor(0x666666);
        y += UiCanvasUtil.drawWrapped(g, TXT_SUB, x + 10, y, maxW - 10);
        y += 12;
        g.setColor(UiTheme.CARD_BORDER);
        g.drawLine(x + 4, y, x + maxW - 4, y);
        y += 12;

        g.setFont(bodyFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Password", x + 2, y, maxW - 2);
        y += 4;

        int pwdFieldH = bodyFont.getHeight() + 14;
        hitPwdTop = y;
        pwdContentTop = hitPwdTop - headerBarH + scrollY;
        g.setColor(UiTheme.CARD_FILL);
        g.fillRoundRect(x + 2, y, maxW - 4, pwdFieldH, 8, 8);
        g.setColor(UiTheme.CARD_BORDER);
        g.drawRoundRect(x + 2, y, maxW - 4, pwdFieldH, 8, 8);
        int textY = y + (pwdFieldH - bodyFont.getHeight()) / 2;
        if (password.length() == 0) {
            g.setColor(0x888888);
            g.drawString(TXT_PWD_PLACEHOLDER, x + 10, textY, Graphics.LEFT | Graphics.TOP);
        } else {
            g.setColor(UiTheme.TEXT_DARK);
            StringBuffer stars = new StringBuffer();
            for (int i = 0; i < password.length(); i++) {
                stars.append('*');
            }
            g.drawString(stars.toString(), x + 10, textY, Graphics.LEFT | Graphics.TOP);
        }
        y += pwdFieldH;
        hitPwdBottom = y;
        pwdContentBottom = hitPwdBottom - headerBarH + scrollY;
        y += 10;

        int box = 14;
        hitCbTop = y;
        cbContentTop = hitCbTop - headerBarH + scrollY;
        g.setColor(UiTheme.CARD_FILL);
        g.fillRect(x + 2, y, box, box);
        g.setColor(UiTheme.CARD_BORDER);
        g.drawRect(x + 2, y, box, box);
        if (remember) {
            drawBoldCheckmark(g, x + 2, y, box);
        }
        g.setColor(UiTheme.TEXT_GRAY);
        g.setFont(bodyFont);
        g.drawString(TXT_REMEMBER, x + box + 14, y + 1, Graphics.LEFT | Graphics.TOP);
        int cbRowH = Math.max(box, bodyFont.getHeight());
        y += cbRowH;
        hitCbBottom = y;
        cbContentBottom = hitCbBottom - headerBarH + scrollY;
        y += 10;

        String btnLabel = loginButtonLabel();
        g.setFont(bodyFont);
        int btnH = bodyFont.getHeight() + 14;
        hitBtnTop = y;
        btnContentTop = hitBtnTop - headerBarH + scrollY;
        g.setColor(UiTheme.BAR_BG);
        g.fillRoundRect(x + 2, y, maxW - 4, btnH, 8, 8);
        g.setColor(UiTheme.BG_WHITE);
        int tw = bodyFont.stringWidth(btnLabel);
        int tx = x + 2 + (maxW - 4 - tw) / 2;
        if (tx < x + 2) {
            tx = x + 2;
        }
        g.drawString(btnLabel, tx, y + (btnH - bodyFont.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
        y += btnH;
        hitBtnBottom = y;
        btnContentBottom = hitBtnBottom - headerBarH + scrollY;
        y += 10;

        if (!loginWaiting && focusIndex == FOCUS_PASSWORD) {
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x, hitPwdTop - 2, maxW, pwdFieldH + 4, 10, 10);
            g.drawRoundRect(x - 1, hitPwdTop - 3, maxW + 2, pwdFieldH + 6, 11, 11);
        }
        if (!loginWaiting && focusIndex == FOCUS_REMEMBER) {
            g.setColor(UiTheme.BAR_BG);
            g.drawRect(x, hitCbTop - 2, maxW, cbRowH + 4);
            g.drawRect(x - 1, hitCbTop - 3, maxW + 2, cbRowH + 6);
        }
        if (!loginWaiting && focusIndex == FOCUS_BUTTON) {
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x, hitBtnTop - 2, maxW, btnH + 4, 10, 10);
            g.drawRoundRect(x - 1, hitBtnTop - 3, maxW + 2, btnH + 6, 11, 11);
        }

        g.setColor(UiTheme.CARD_BORDER);
        g.drawLine(x + 4, y, x + maxW - 4, y);
        y += 10;
        g.setFont(smallFont);
        g.setColor(0x666666);
        y += UiCanvasUtil.drawWrappedCentered(g, TXT_FOOTER, x, y, maxW);

        y += 12;
        int contentHeight = y - headerBarH + scrollY + pad;
        if (contentHeight < viewportH) {
            contentHeight = viewportH;
        }
        scrollCtrl.setContentHeight(contentHeight);
        scrollCtrl.clamp(viewportH);

        g.setClip(0, 0, w, h);

        int maxScroll = scrollCtrl.getMaxScroll(viewportH);
        if (!loginWaiting && maxScroll > 0) {
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

        if (!loginResolved && loginWaiting) {
            g.setColor(0xE8E8E8);
            g.fillRect(0, headerBarH, w, h - headerBarH);
            g.setFont(titleFont);
            g.setColor(UiTheme.TEXT_DARK);
            String base = "Connecting to repeater";
            String dotsStr = loginDots == 0 ? "" : (loginDots == 1 ? "." : (loginDots == 2 ? ".." : "..."));
            int avail = h - headerBarH;
            int ty = headerBarH + Math.max(16, (avail - titleFont.getHeight() - bodyFont.getHeight() - 12) / 2);
            UiCanvasUtil.drawWrappedCentered(g, base + dotsStr, 0, ty, w);
            g.setFont(bodyFont);
            g.setColor(UiTheme.TEXT_GRAY);
            UiCanvasUtil.drawWrappedCentered(g, "This may take up to a few minutes on slow links.", 0, ty + titleFont.getHeight() + 6, w);
        }

        headerBarHeight = UiScreenHeader.paint(g, w, "Log in", null, titleFont, smallFont);
    }

    private boolean inX(int x) {
        return x >= contentPadLeft && x < contentPadLeft + contentMaxW;
    }

    protected void pointerPressed(int x, int y) {
        if (!loginResolved && loginWaiting) {
            return;
        }
        if (y < headerBarHeight) {
            return;
        }
        scrollCtrl.pointerPressed(y);
        if (inX(x) && y >= hitPwdTop && y < hitPwdBottom) {
            focusIndex = FOCUS_PASSWORD;
            openPasswordEditor();
            return;
        }
        if (y >= hitCbTop && y < hitCbBottom && x >= contentPadLeft && x < contentPadLeft + contentMaxW) {
            focusIndex = FOCUS_REMEMBER;
            remember = !remember;
            repaint();
            return;
        }
        if (y >= hitBtnTop && y < hitBtnBottom && inX(x)) {
            focusIndex = FOCUS_BUTTON;
            doLogin();
        }
    }

    protected void pointerDragged(int x, int y) {
        if (!loginResolved && loginWaiting) {
            return;
        }
        ensureFonts();
        int w = getWidth();
        int hh = headerBarHeight > 0 ? headerBarHeight
                : UiScreenHeader.measureHeight(w, "Log in", null, titleFont, smallFont);
        int vh = getHeight() - hh;
        if (vh < 1) {
            vh = 1;
        }
        if (scrollCtrl.onDrag(y, vh)) {
            scrollDragRepaint.repaintDrag(this, UiScrollRepaintThrottler.DEFAULT_DRAG_INTERVAL_MS,
                    0, hh, w, getHeight() - hh);
        }
    }

    protected void pointerReleased(int x, int y) {
        scrollCtrl.pointerReleased();
        int w = getWidth();
        int hh = headerBarHeight > 0 ? headerBarHeight
                : UiScreenHeader.measureHeight(w, "Log in", null, titleFont, smallFont);
        scrollDragRepaint.flushRepaint(this, 0, hh, w, getHeight() - hh);
    }

    protected void keyPressed(int keyCode) {
        if (!loginResolved && loginWaiting) {
            return;
        }
        int action = 0;
        try {
            action = getGameAction(keyCode);
        } catch (IllegalArgumentException e) {
            action = 0;
        }
        boolean up = (action == Canvas.UP || keyCode == Canvas.KEY_NUM2);
        boolean down = (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8);
        boolean select = (action == Canvas.FIRE || keyCode == Canvas.KEY_NUM5);
        int step = UiScrollController.computeStep(bodyFont);
        int hh = UiScreenHeader.measureHeight(getWidth(), "Log in", null, titleFont, smallFont);
        int vh = getHeight() - hh;
        if (vh < 1) {
            vh = 1;
        }
        if (up) {
            if (focusIndex > FOCUS_PASSWORD) {
                moveFocus(-1);
            } else if (scrollCtrl.onKey(Canvas.UP, step, vh)) {
                repaint();
            }
            return;
        }
        if (down) {
            if (focusIndex < FOCUS_BUTTON) {
                moveFocus(1);
            } else if (scrollCtrl.onKey(Canvas.DOWN, step, vh)) {
                repaint();
            }
            return;
        }
        if (select) {
            activateFocusedControl();
            return;
        }
        if (scrollCtrl.onKey(action, step, vh)) {
            repaint();
        }
    }

    private void openPasswordEditor() {
        final TextBox tb = new TextBox("Password", password, 15, TextField.PASSWORD);
        tb.addCommand(new Command("OK", Command.OK, 1));
        tb.addCommand(new Command("Cancel", Command.BACK, 2));
        tb.setCommandListener(new javax.microedition.lcdui.CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    String s = tb.getString();
                    password = (s != null) ? s : "";
                }
                app.getDisplay().setCurrent(RepeaterLoginScreen.this);
                repaint();
            }
        });
        app.getDisplay().setCurrent(tb);
    }

    private void doLogin() {
        String effectivePassword = (password != null) ? password : "";
        if (effectivePassword.trim().length() == 0) {
            // Empty password means guest login.
            effectivePassword = "";
        }
        if (pubKeyHex != null && pubKeyHex.length() > 0) {
            if (remember && effectivePassword.length() > 0) {
                RepeaterPasswordStore.save(pubKeyHex, effectivePassword);
            } else {
                RepeaterPasswordStore.remove(pubKeyHex);
            }
        }
        app.requestRepeaterLogin(contactIdx, effectivePassword, returnTo);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.clearPendingTelemetryAfterLogin();
            if (!loginResolved && loginWaiting) {
                app.clearLoginPending();
                setAwaitingLoginReply(false);
            }
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdSelect) {
            activateFocusedControl();
            return;
        }
    }
}
