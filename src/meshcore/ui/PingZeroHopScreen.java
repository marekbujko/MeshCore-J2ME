package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Displayable;

/**
 * Single-screen UI for "Ping (Zero Hop)".
 * Shows waiting text first, then replaces it with the result when it arrives.
 */
public final class PingZeroHopScreen extends Canvas implements CommandListener {

    private static final long PING_TIMEOUT_MS = 20000; // match service expectations

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;
    private volatile boolean resolved = false;
    private volatile boolean waiting = true;

    private volatile int dots = 0;
    private volatile String repeaterName = "";
    private volatile String snrForward = "n/a";
    private volatile String snrBack = "n/a";
    private volatile String hopsLabel = "";
    private volatile String durationLabel = "";
    private volatile int waitGeneration = 0;
    private final UiScrollController scrollCtrl = new UiScrollController();

    private Font titleFont;
    private Font bodyFont;

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
        String hopsLabel = (pathHops <= 1)
                ? "0 hops (Direct)"
                : (String.valueOf(pathHops) + " hops");
        this.hopsLabel = hopsLabel;
        this.durationLabel = (durationMs >= 0) ? (durationMs + " ms") : "";
        scrollCtrl.reset();
        repaint();
    }

    private void startWaitThreads() {
        final javax.microedition.lcdui.Display display = app.getDisplay();
        if (display == null) return;

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
                hopsLabel = "—";
                durationLabel = "";
                repaint();
            }
        };

        UiWaitController.startWaitingDots(display, this, state, 300, 4);
        UiWaitController.startTimeoutWatcher(display, this, state, PING_TIMEOUT_MS, 200);
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        // Background (same spirit as trace path canvas).
        g.setColor(UiTheme.BG_WHITE);
        g.fillRect(0, 0, w, h);

        if (bodyFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        int pad = 8;
        int x = pad;
        int maxW = w - (pad * 2);
        int scrollY = scrollCtrl.getScrollY();
        int y = 0 - scrollY;

        g.setFont(titleFont);
        g.setColor(UiTheme.TEXT_DARK);

        g.setFont(bodyFont);
        int lineH = bodyFont.getHeight();
        g.setColor(UiTheme.TEXT_GRAY);

        int contentHeight;
        if (waiting && !resolved) {
            String base = "Waiting for reply";
            String dotsStr = "";
            int di = dots;
            if (di == 0) dotsStr = "";
            else if (di == 1) dotsStr = ".";
            else if (di == 2) dotsStr = "..";
            else dotsStr = "...";
            y += UiCanvasUtil.drawWrappedCentered(g, base + dotsStr, x, y, maxW);
            contentHeight = y + scrollY + pad;
            scrollCtrl.setContentHeight(contentHeight);
            scrollCtrl.clamp(h);
        } else {
            if (repeaterName != null && repeaterName.length() > 0) {
                if (durationLabel != null && durationLabel.length() > 0) {
                    y += UiCanvasUtil.drawWrappedCentered(g, "Duration: " + durationLabel, x, y, maxW);
                }
                y += 3;
                g.setColor(UiTheme.LINE_GRAY);
                g.drawLine(x, y, x + maxW, y);
                y += 7;

                String myNode = UiCanvasUtil.getNodeLabel(app);
                y += UiTimelinePainter.drawNodeBubble(g, x, y, maxW, myNode);
                y += UiTimelinePainter.drawSnrDualArrows(g, x, y, maxW, snrForward, snrBack);
                y += UiTimelinePainter.drawNodeBubble(g, x, y, maxW, repeaterName);
            } else {
                y += UiCanvasUtil.drawWrappedCentered(g, "No reply (timeout).", x, y, maxW);
            }
            contentHeight = y + scrollY + pad;
            scrollCtrl.setContentHeight(contentHeight);
            scrollCtrl.clamp(h);
        }

        int maxScroll = scrollCtrl.getMaxScroll(h);
        if (maxScroll > 0) {
            int barX = w - 4;
            int barTop = 4;
            int barH = h - 8;
            g.setColor(0xBBBBBB);
            g.drawLine(barX, barTop, barX, barTop + barH);
            int thumbH = Math.max(10, (barH * h) / contentHeight);
            int scrollYClamped = scrollCtrl.getScrollY();
            int thumbY = barTop + ((barH - thumbH) * scrollYClamped) / maxScroll;
            g.setColor(UiTheme.SCROLL_BAR_THUMB);
            g.fillRect(barX - 1, thumbY, 3, thumbH);
        }
    }

    // Bubble/arrow drawing moved to UiTimelinePainter.

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
        if (waiting && !resolved) return;
        int action = getGameAction(keyCode);
        int step = UiScrollController.computeStep(bodyFont);
        if (scrollCtrl.onKey(action, step, getHeight())) {
            repaint();
        }
    }

    protected void pointerPressed(int x, int y) {
        scrollCtrl.pointerPressed(y);
    }

    protected void pointerDragged(int x, int y) {
        if (waiting && !resolved) return;
        if (scrollCtrl.onDrag(y, getHeight())) {
            repaint();
        }
    }

    protected void pointerReleased(int x, int y) {
        scrollCtrl.pointerReleased();
    }
}

