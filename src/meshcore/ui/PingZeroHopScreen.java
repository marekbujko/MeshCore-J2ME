package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Single-screen UI for "Ping (Zero Hop)".
 * Same in-canvas header and waiting pattern as {@link TelemetryResultScreen}.
 */
public final class PingZeroHopScreen extends Canvas implements CommandListener {

    private static final long PING_TIMEOUT_MS = 20000;

    private static final String WAIT_LINE = "Requesting ping";
    private static final String WAIT_SUB = "This may take up to a few minutes on slow links.";

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;
    private volatile boolean resolved = false;
    private volatile boolean waiting = true;

    private volatile int dots = 0;
    private volatile String repeaterName = "";
    private volatile String snrForward = "n/a";
    private volatile String snrBack = "n/a";
    /** Set when result includes RTT; shown in header as Duration: N ms. */
    private volatile long pingDurationMs = -1L;
    private volatile int waitGeneration = 0;

    private final UiScrollController scrollCtrl = new UiScrollController();
    private final UiScrollRepaintThrottler scrollDragRepaint = new UiScrollRepaintThrottler();
    private Font titleFont;
    private Font bodyFont;
    private Font smallFont;
    private Font sectionHeaderFont;

    private int headerBarHeight = 44;

    public PingZeroHopScreen(AppController app, Displayable returnTo) {
        this.app = app;
        this.returnTo = returnTo;
        setFullScreenMode(false);
        setTitle("Ping (Zero Hop)");

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);

        showWaiting();
    }

    private void showWaiting() {
        resolved = false;
        waiting = true;
        dots = 0;
        scrollCtrl.reset();
        waitGeneration++;
        startWaitThreads();
        repaint();
    }

    public void showResult(String repeaterName,
            String snrForward,
            String snrBack,
            int pathHops,
            long durationMs) {
        resolved = true;
        waiting = false;

        this.repeaterName = repeaterName != null ? repeaterName : "";
        this.snrForward = snrForward != null ? snrForward : "n/a";
        this.snrBack = snrBack != null ? snrBack : "n/a";
        this.pingDurationMs = (durationMs >= 0) ? durationMs : -1L;
        scrollCtrl.reset();
        repaint();
    }

    private void startWaitThreads() {
        final javax.microedition.lcdui.Display display = app.getDisplay();
        if (display == null) {
            return;
        }

        final UiWaitController.WaitState state = new UiWaitController.WaitState() {
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
                resolved = true;
                waiting = false;
                repeaterName = "";
                snrForward = "n/a";
                snrBack = "n/a";
                pingDurationMs = -1L;
                repaint();
            }
        };

        UiWaitController.startWaitingDots(display, this, state, 300, 4);
        UiWaitController.startTimeoutWatcher(display, this, state, PING_TIMEOUT_MS, 200);
    }

    private void ensureFonts() {
        if (bodyFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            sectionHeaderFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        }
    }

    private int drawMessageSection(Graphics g, int x, int y, int maxW, String msg) {
        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.BAR_BG);
        g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
        g.setColor(UiTheme.TEXT_DARK);
        y += UiCanvasUtil.drawWrapped(g, "Message", x + 10, y, maxW - 10);
        y += 6;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, msg, x + 10, y, maxW - 10);
        return y + 16;
    }

    /** Single header line (white) when ping succeeded with RTT; empty bar otherwise. */
    private String pingHeaderPrimary() {
        if (waiting || !resolved) {
            return null;
        }
        if (repeaterName == null || repeaterName.length() == 0) {
            return null;
        }
        if (pingDurationMs < 0L) {
            return null;
        }
        return "Duration: " + pingDurationMs + " ms";
    }

    private int measurePingHeaderBar(int w) {
        ensureFonts();
        String p = pingHeaderPrimary();
        if (p != null) {
            return UiScreenHeader.measureHeight(w, p, null, smallFont, smallFont);
        }
        return UiScreenHeader.measureHeight(w, "", null, titleFont, smallFont);
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        ensureFonts();

        String waitDots = dots == 0 ? "" : (dots == 1 ? "." : (dots == 2 ? ".." : "..."));
        String waitHeader = (waiting && !resolved) ? (UiScreenHeader.PLEASE_WAIT + waitDots) : null;

        int headerBarH = waitHeader != null
                ? UiScreenHeader.measureHeight(w, waitHeader, null, titleFont, smallFont)
                : measurePingHeaderBar(w);
        int viewportH = h - headerBarH;
        if (viewportH < 1) {
            viewportH = 1;
        }

        int pad = 8;
        int x = pad;
        int maxW = w - (pad * 2) - 6;

        if (waiting && !resolved) {
            g.setColor(UiTheme.PANEL_BG);
            g.fillRect(0, 0, w, h);
            int avail = viewportH;
            int ty = headerBarH + Math.max(12, (avail - titleFont.getHeight() - bodyFont.getHeight() - 16) / 2);
            g.setFont(titleFont);
            g.setColor(UiTheme.TEXT_DARK);
            UiCanvasUtil.drawWrappedCentered(g, WAIT_LINE + waitDots, 0, ty, w);
            g.setFont(bodyFont);
            g.setColor(UiTheme.TEXT_GRAY);
            UiCanvasUtil.drawWrappedCentered(g, WAIT_SUB, 0, ty + titleFont.getHeight() + 6, w);
            headerBarHeight = UiScreenHeader.paint(g, w, waitHeader, null, titleFont, smallFont);
            return;
        }

        headerBarH = measurePingHeaderBar(w);
        viewportH = h - headerBarH;
        if (viewportH < 1) {
            viewportH = 1;
        }

        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        int scrollY = scrollCtrl.getScrollY();
        g.clipRect(0, headerBarH, w, viewportH);
        int y = headerBarH - scrollY + 10;

        boolean hasReply = repeaterName != null && repeaterName.length() > 0;
        if (hasReply) {
            g.setFont(bodyFont);
            String myNode = UiCanvasUtil.getNodeLabel(app);
            y += UiTimelinePainter.drawNodeBubble(g, x, y, maxW, myNode);
            y += UiTimelinePainter.drawSnrDualArrows(g, x, y, maxW, snrForward, snrBack);
            y += UiTimelinePainter.drawNodeBubble(g, x, y, maxW, repeaterName);
        } else {
            y = drawMessageSection(g, x, y, maxW, "No reply (timeout).");
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
            int syC = scrollCtrl.getScrollY();
            int thumbY = barTop + ((barLen - thumbH) * syC) / maxScroll;
            g.setColor(UiTheme.SCROLL_BAR_THUMB);
            g.fillRect(barX - 1, thumbY, 3, thumbH);
        }

        String hp = pingHeaderPrimary();
        if (hp != null) {
            headerBarHeight = UiScreenHeader.paint(g, w, hp, null, smallFont, smallFont);
        } else {
            headerBarHeight = UiScreenHeader.paint(g, w, "", null, titleFont, smallFont);
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            if (returnTo != null) {
                app.getDisplay().setCurrent(returnTo);
            } else {
                app.showMainMenu();
            }
        }
    }

    protected void keyPressed(int keyCode) {
        if (waiting && !resolved) {
            return;
        }
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int hh = measurePingHeaderBar(w);
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
        if (waiting && !resolved) {
            return;
        }
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int hh = headerBarHeight > 0 ? headerBarHeight : measurePingHeaderBar(w);
        int vh = h - hh;
        if (vh < 1) {
            vh = 1;
        }
        if (scrollCtrl.onDrag(y, vh)) {
            scrollDragRepaint.repaintDrag(this, UiScrollRepaintThrottler.DEFAULT_DRAG_INTERVAL_MS,
                    0, hh, w, h - hh);
        }
    }

    protected void pointerReleased(int x, int y) {
        scrollCtrl.pointerReleased();
        int w = getWidth();
        int h = getHeight();
        int hh = headerBarHeight > 0 ? headerBarHeight : measurePingHeaderBar(w);
        scrollDragRepaint.flushRepaint(this, 0, hh, w, h - hh);
    }
}
