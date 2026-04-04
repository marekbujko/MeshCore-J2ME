package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

import meshcore.util.TextUtils;

/**
 * Shows telemetry progress; on success: round-trip time, then channel 1 (self) lines,
 * then full-payload raw hex.
 */
public final class TelemetryResultScreen extends Canvas implements CommandListener {

    private static final long TELEMETRY_TIMEOUT_MS = 120000L;

    private static final int ST_WAITING = 0;
    private static final int ST_OK = 1;
    private static final int ST_TIMEOUT = 2;
    private static final int ST_SEND_ERR = 3;

    private final AppController app;
    private final int contactIdx;
    /** Name from contact list (shown centered after load); stable across refresh. */
    private final String peerDisplayName;
    private final Displayable returnTo;
    private final Command cmdBack;
    private final Command cmdRefresh;

    private volatile int state = ST_WAITING;
    private volatile boolean waiting = true;
    private volatile boolean resolved = false;

    private volatile int dots = 0;
    private volatile int waitGeneration = 0;
    private volatile String contactName = "";
    private volatile String bodyText = "";
    /** Channel 1 (TELEM_CHANNEL_SELF) decoded lines, shown under Round-trip Duration. */
    private volatile String ch1Lines = "";
    /** Full LPP payload hex. */
    private volatile String rawHexBlock = "";
    private volatile String durationLabel = "";

    private final UiScrollController scrollCtrl = new UiScrollController();
    private Font titleFont;
    private Font bodyFont;
    private Font smallFont;
    private Font sectionHeaderFont;

    /** Top bar height for pointer handling. */
    private int headerBarHeight = 44;

    public TelemetryResultScreen(AppController app, int contactIdx, String peerDisplayName, Displayable returnTo) {
        this.app = app;
        this.contactIdx = contactIdx;
        this.peerDisplayName = (peerDisplayName != null) ? peerDisplayName : "";
        this.returnTo = returnTo;
        setFullScreenMode(false);
        String title = TextUtils.sanitizeLabel(this.peerDisplayName, 32);
        setTitle(title.length() > 0 ? title : "Telemetry");

        cmdBack = new Command("Back", Command.BACK, 2);
        cmdRefresh = new Command("Refresh", Command.SCREEN, 1);
        addCommand(cmdRefresh);
        addCommand(cmdBack);
        setCommandListener(this);

        showWaiting();
    }

    /** Where Back returns (same as passed into constructor). */
    public Displayable getTelemetryReturnTo() {
        return returnTo;
    }

    private void showWaiting() {
        state = ST_WAITING;
        resolved = false;
        waiting = true;
        dots = 0;
        bodyText = "";
        ch1Lines = "";
        rawHexBlock = "";
        durationLabel = "";
        scrollCtrl.reset();
        waitGeneration++;
        startWaitThreads();
        repaint();
    }

    /**
     * @param ch1Decoded channel 1 / TELEM_SELF LPP text (newline-separated lines).
     * @param rawHex       full payload hex.
     */
    public void showResult(String name, String ch1Decoded, String rawHex, long durationMs) {
        state = ST_OK;
        resolved = true;
        waiting = false;
        contactName = (name != null) ? name : "";
        bodyText = "";
        ch1Lines = (ch1Decoded != null) ? ch1Decoded : "";
        rawHexBlock = (rawHex != null) ? rawHex : "";
        durationLabel = (durationMs >= 0) ? (durationMs + " ms") : "";
        scrollCtrl.reset();
        repaint();
    }

    public void showSendFailed(String message) {
        state = ST_SEND_ERR;
        resolved = true;
        waiting = false;
        bodyText = (message != null) ? message : "Send failed.";
        ch1Lines = "";
        rawHexBlock = "";
        durationLabel = "";
        scrollCtrl.reset();
        repaint();
    }

    private void startWaitThreads() {
        final javax.microedition.lcdui.Display display = app.getDisplay();
        if (display == null) return;

        final UiWaitController.WaitState waitState = new UiWaitController.WaitState() {
            public int getGeneration() {
                return waitGeneration;
            }

            public boolean isResolved() {
                return resolved;
            }

            public boolean isWaiting() {
                return waiting;
            }

            public void onDot(int dotIndex) {
                dots = dotIndex;
                repaint();
            }

            public void onTimeout() {
                if (state != ST_WAITING) return;
                app.clearTelemetryPending();
                state = ST_TIMEOUT;
                resolved = true;
                waiting = false;
                bodyText = "No telemetry received (timeout). The node may be offline or not support telemetry.";
                durationLabel = "";
                scrollCtrl.reset();
                repaint();
            }
        };

        UiWaitController.startWaitingDots(display, this, waitState, 300, 4);
        UiWaitController.startTimeoutWatcher(display, this, waitState, TELEMETRY_TIMEOUT_MS, 250);
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        if (bodyFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            sectionHeaderFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        }

        int headerBarH = UiScreenHeader.measureHeight(w, "Telemetry", null, titleFont, smallFont);
        int viewportH = h - headerBarH;
        if (viewportH < 1) {
            viewportH = 1;
        }

        if (state == ST_WAITING && !resolved) {
            g.setColor(UiTheme.PANEL_BG);
            g.fillRect(0, 0, w, h);
            String base = "Requesting telemetry";
            String dotsStr = dots == 0 ? "" : (dots == 1 ? "." : (dots == 2 ? ".." : "..."));
            int avail = viewportH;
            int ty = headerBarH + Math.max(12, (avail - titleFont.getHeight() - bodyFont.getHeight() - 16) / 2);
            g.setFont(titleFont);
            g.setColor(UiTheme.TEXT_DARK);
            UiCanvasUtil.drawWrappedCentered(g, base + dotsStr, 0, ty, w);
            g.setFont(bodyFont);
            g.setColor(UiTheme.TEXT_GRAY);
            UiCanvasUtil.drawWrappedCentered(g, "This may take up to a few minutes on slow links.", 0, ty + titleFont.getHeight() + 6, w);
            headerBarHeight = UiScreenHeader.paint(g, w, "Telemetry", null, titleFont, smallFont);
            return;
        }

        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        int pad = 8;
        int x = pad;
        int maxW = w - (pad * 2) - 6;
        int scrollY = scrollCtrl.getScrollY();

        g.clipRect(0, headerBarH, w, viewportH);
        int y = headerBarH - scrollY + 10;

        if (durationLabel != null && durationLabel.length() > 0 && state == ST_OK) {
            g.setFont(sectionHeaderFont);
            g.setColor(UiTheme.BAR_BG);
            g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
            g.setColor(UiTheme.TEXT_DARK);
            y += UiCanvasUtil.drawWrapped(g, "Round trip", x + 10, y, maxW - 10);
            y += 6;
            g.setFont(bodyFont);
            g.setColor(UiTheme.TEXT_GRAY);
            y += UiCanvasUtil.drawWrapped(g, durationLabel, x + 10, y, maxW - 10);
            y += 14;
            g.setColor(UiTheme.CARD_BORDER);
            g.drawLine(x + 4, y, x + maxW - 4, y);
            y += 12;
        }
        if (state == ST_OK) {
            String c1 = ch1Lines;
            if (c1 != null && c1.length() > 0) {
                g.setFont(sectionHeaderFont);
                g.setColor(UiTheme.BAR_BG);
                g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
                g.setColor(UiTheme.TEXT_DARK);
                y += UiCanvasUtil.drawWrapped(g, "Readings", x + 10, y, maxW - 10);
                y += 6;
                g.setFont(bodyFont);
                g.setColor(UiTheme.TEXT_GRAY);
                y += UiCanvasUtil.drawNewlineSeparatedWrapped(g, c1, x + 10, y, maxW - 10);
                y += 14;
                g.setColor(UiTheme.CARD_BORDER);
                g.drawLine(x + 4, y, x + maxW - 4, y);
                y += 12;
            }
            g.setFont(sectionHeaderFont);
            g.setColor(UiTheme.BAR_BG);
            g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
            g.setColor(UiTheme.TEXT_DARK);
            y += UiCanvasUtil.drawWrapped(g, "Raw payload", x + 10, y, maxW - 10);
            y += 6;
            g.setFont(bodyFont);
            g.setColor(UiTheme.TEXT_GRAY);
            String hx = rawHexBlock;
            if (hx != null && hx.length() > 0) {
                y += UiCanvasUtil.drawNewlineSeparatedWrapped(g, hx, x + 10, y, maxW - 10);
            } else {
                y += UiCanvasUtil.drawWrapped(g, "(empty)", x + 10, y, maxW - 10);
            }
        }
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        if (bodyText != null && bodyText.length() > 0) {
            y += 12;
            g.setColor(UiTheme.CARD_BORDER);
            g.drawLine(x + 4, y, x + maxW - 4, y);
            y += 12;
            g.setFont(sectionHeaderFont);
            g.setColor(UiTheme.BAR_BG);
            g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
            g.setColor(UiTheme.TEXT_DARK);
            y += UiCanvasUtil.drawWrapped(g, "Message", x + 10, y, maxW - 10);
            y += 6;
            g.setFont(bodyFont);
            g.setColor(UiTheme.TEXT_GRAY);
            y += UiCanvasUtil.drawWrapped(g, bodyText, x + 10, y, maxW - 10);
        }

        y += 16;
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
            int safeCh = Math.max(contentHeight, 1);
            int thumbH = Math.max(10, (barLen * viewportH) / safeCh);
            int scrollYClamped = scrollCtrl.getScrollY();
            int thumbY = barTop + ((barLen - thumbH) * scrollYClamped) / maxScroll;
            g.setColor(UiTheme.SCROLL_BAR_THUMB);
            g.fillRect(barX - 1, thumbY, 3, thumbH);
        }

        headerBarHeight = UiScreenHeader.paint(g, w, "Telemetry", null, titleFont, smallFont);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdRefresh) {
            if (state == ST_WAITING && !resolved) {
                return;
            }
            app.clearTelemetryPending();
            showWaiting();
            app.refreshContactTelemetry(contactIdx, this);
            return;
        }
        if (c == cmdBack) {
            app.clearTelemetryPending();
            if (returnTo != null) {
                app.getDisplay().setCurrent(returnTo);
            } else {
                app.showMainMenu();
            }
        }
    }

    protected void keyPressed(int keyCode) {
        if (state == ST_WAITING && !resolved) {
            return;
        }
        if (bodyFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        int w = getWidth();
        int h = getHeight();
        int hh = UiScreenHeader.measureHeight(w, "Telemetry", null, titleFont, smallFont);
        int vh = h - hh;
        if (vh < 1) {
            vh = 1;
        }
        int action = getGameAction(keyCode);
        int step = UiScrollController.computeStep(bodyFont);
        if (scrollCtrl.onKey(action, step, vh)) {
            repaint();
        }
    }

    protected void pointerPressed(int x, int y) {
        if (y < headerBarHeight) {
            return;
        }
        scrollCtrl.pointerPressed(y);
    }

    protected void pointerDragged(int x, int y) {
        if (state == ST_WAITING && !resolved) {
            return;
        }
        if (bodyFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        int w = getWidth();
        int h = getHeight();
        int hh = UiScreenHeader.measureHeight(w, "Telemetry", null, titleFont, smallFont);
        int vh = h - hh;
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
}
