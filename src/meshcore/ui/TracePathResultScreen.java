package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Result view for manual TRACE (forward only).
 */
public final class TracePathResultScreen extends Canvas implements CommandListener {

    private static final long TRACE_TIMEOUT_MS = 20000;

    private final AppController app;
    private final Displayable returnTo;
    private final byte[] forwardPath;
    private final Command cmdBack;
    private final Command cmdRefresh;

    private String forwardHops = "";
    private String forwardDuration = "";

    private byte[] lastPathSnrs;
    private int lastFinalSNR4;

    private boolean forwardDone = false;
    private boolean waiting = false;
    private boolean timedOut = false;
    private int waitingDots = 0;
    private volatile int waitGeneration = 0;
    private final UiScrollController scrollCtrl = new UiScrollController();

    private Font titleFont;
    private Font bodyFont;

    public TracePathResultScreen(AppController app, Displayable returnTo, byte[] forwardPath) {
        this.app = app;
        this.returnTo = returnTo;
        this.forwardPath = forwardPath;
        setFullScreenMode(false);
        setTitle("Trace Path");

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdRefresh = new Command("Refresh", Command.SCREEN, 2);
        addCommand(cmdBack);
        addCommand(cmdRefresh);
        setCommandListener(this);

        renderWaiting();
    }

    public Displayable getReturnTo() {
        return returnTo;
    }

    public void renderWaiting() {
        forwardDone = false;
        waiting = true;
        timedOut = false;
        waitingDots = 0;
        scrollCtrl.reset();
        waitGeneration++;
        startWaitThreads();
        repaint();
    }

    public void setResult(
            String destName,
            byte[] pathSnrs,
            int finalSNR4,
            int pathHops,
            long durationMs
    ) {
        forwardDone = true;
        waiting = false;
        timedOut = false;
        lastPathSnrs = pathSnrs;
        lastFinalSNR4 = finalSNR4;
        forwardHops = "Path hops: " + pathHops;
        forwardDuration = (durationMs >= 0) ? ("Duration: " + durationMs + " ms") : "";
        scrollCtrl.reset();
        render();
    }

    private void render() {
        repaint();
    }

    private String getRepeaterLabel(int i) {
        if (forwardPath == null || i < 0 || i >= forwardPath.length) {
            return "Repeater " + (i + 1);
        }
        String repName = app.getRepeaterNameForPathByte(forwardPath[i]);
        if (repName == null || repName.length() == 0) {
            repName = "Repeater " + (i + 1);
        }
        return repName;
    }

    private String getHopSnr(int i) {
        if (lastPathSnrs == null || i < 0 || i >= lastPathSnrs.length) {
            return "n/a";
        }
        return formatSnr4ToDb((int) lastPathSnrs[i]);
    }

    private String formatSnr4ToDb(int snr4) {
        boolean neg = snr4 < 0;
        int abs = neg ? -snr4 : snr4;
        int whole = abs / 4;
        int fracQ = abs % 4; // 0..3
        int fracDec = fracQ * 25; // 0,25,50,75
        String fracStr = fracDec < 10 ? "0" + fracDec : String.valueOf(fracDec);
        return (neg ? "-" : "") + whole + "." + fracStr + " dB";
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        if (titleFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        // Background
        g.setColor(UiTheme.BG_WHITE);
        g.fillRect(0, 0, w, h);
        int pad = 8;
        int x = pad;
        int maxW = w - (pad * 2) - 6; // leave room for scroll bar
        int scrollY = scrollCtrl.getScrollY();
        int y = 0 - scrollY;

        g.setFont(titleFont);
        g.setColor(UiTheme.TEXT_DARK);

        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);

        if (!forwardDone) {
            if (timedOut) {
                y += UiCanvasUtil.drawWrappedCentered(g, "No reply (timeout).", x, y, maxW);
                y += UiCanvasUtil.drawWrappedCentered(g, "Press Refresh to try again.", x, y + 2, maxW);
                int contentHeight = y + scrollY + pad;
                scrollCtrl.setContentHeight(contentHeight);
                scrollCtrl.clamp(h);
                return;
            }
            String dots = (waitingDots == 0) ? "" : (waitingDots == 1 ? "." : (waitingDots == 2 ? ".." : "..."));
            y += UiCanvasUtil.drawWrappedCentered(g, "Waiting for reply" + dots, x, y, maxW);
            int contentHeight = y + scrollY + pad;
            scrollCtrl.setContentHeight(contentHeight);
            scrollCtrl.clamp(h);
            return;
        }

        y += drawInfoRow(g, x, y, maxW, forwardHops, forwardDuration);
        y += 3;
        g.setColor(UiTheme.LINE_GRAY);
        g.drawLine(x, y, x + maxW, y);
        y += 3;
        y += 4;

        String myNodeLabel = UiCanvasUtil.getNodeLabel(app);
        y += UiTimelinePainter.drawNodeBubble(g, x, y, maxW, myNodeLabel);

        int hopCount = (forwardPath != null) ? forwardPath.length : 0;
        for (int i = 0; i < hopCount; i++) {
            y += UiTimelinePainter.drawSnrArrowDown(g, x, y, maxW, getHopSnr(i));
            y += UiTimelinePainter.drawNodeBubble(g, x, y, maxW, getRepeaterLabel(i));
        }

        String finalSnr = formatSnr4ToDb(lastFinalSNR4);
        y += UiTimelinePainter.drawSnrArrowDown(g, x, y, maxW, finalSnr);
        y += UiTimelinePainter.drawNodeBubble(g, x, y, maxW, myNodeLabel);

        int contentHeight = y + scrollY + pad;
        scrollCtrl.setContentHeight(contentHeight);
        scrollCtrl.clamp(h);

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

    private int drawInfoRow(Graphics g, int x, int y, int w, String leftText, String rightText) {
        if (leftText == null) leftText = "";
        if (rightText == null) rightText = "";
        rightText = rightText.trim();

        // If right side is empty, keep centered style.
        if (rightText.length() == 0) {
            return UiCanvasUtil.drawWrappedCentered(g, leftText, x, y, w);
        }

        int lw = g.getFont().stringWidth(leftText);
        int rw = g.getFont().stringWidth(rightText);
        int gap = 8;
        int lineH = g.getFont().getHeight();

        // If both fit, render on one line: left and right aligned.
        if (lw + gap + rw <= w) {
            g.drawString(leftText, x, y, Graphics.LEFT | Graphics.TOP);
            g.drawString(rightText, x + w, y, Graphics.RIGHT | Graphics.TOP);
            return lineH;
        }

        // Fallback: keep readable on small screens.
        int used = UiCanvasUtil.drawWrappedCentered(g, leftText, x, y, w);
        used += UiCanvasUtil.drawWrappedCentered(g, rightText, x, y + used, w);
        return used;
    }

    private void startWaitThreads() {
        final javax.microedition.lcdui.Display display = app.getDisplay();
        if (display == null) return;

        final UiWaitController.WaitState state = new UiWaitController.WaitState() {
            public int getGeneration() {
                return waitGeneration;
            }

            public boolean isResolved() {
                return forwardDone;
            }

            public boolean isWaiting() {
                return waiting && !timedOut;
            }

            public void onDot(int dotIndex) {
                waitingDots = dotIndex;
                repaint();
            }

            public void onTimeout() {
                waiting = false;
                timedOut = true;
                repaint();
            }
        };

        UiWaitController.startWaitingDots(display, this, state, 300, 4);
        UiWaitController.startTimeoutWatcher(display, this, state, TRACE_TIMEOUT_MS, 200);
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
        if (c == cmdRefresh && forwardPath != null && forwardPath.length > 0) {
            app.tracePathManualRefresh(forwardPath, this);
        }
    }

    protected void keyPressed(int keyCode) {
        if (!forwardDone) return;
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
        if (!forwardDone) return;
        if (scrollCtrl.onDrag(y, getHeight())) {
            repaint();
        }
    }

    protected void pointerReleased(int x, int y) {
        scrollCtrl.pointerReleased();
    }
}

