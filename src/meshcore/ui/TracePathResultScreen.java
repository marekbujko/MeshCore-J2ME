package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Result view for manual TRACE (forward only).
 * In-canvas header and sections match {@link TelemetryResultScreen}.
 */
public final class TracePathResultScreen extends Canvas implements CommandListener {

    private static final long TRACE_TIMEOUT_MS = 20000;

    private static final String WAIT_LINE = "Requesting trace path";
    private static final String WAIT_SUB = "This may take up to a few minutes on slow links.";

    private final AppController app;
    private final Displayable returnTo;
    private final byte[] forwardPath;
    private final Command cmdBack;
    private final Command cmdRefresh;

    private int resultPathHops = -1;
    private long resultDurationMs = -1;

    private byte[] lastPathSnrs;
    private int lastFinalSNR4;

    private boolean forwardDone = false;
    private boolean waiting = false;
    private boolean timedOut = false;
    private int waitingDots = 0;
    private volatile int waitGeneration = 0;

    private final UiScrollController scrollCtrl = new UiScrollController();
    private final UiScrollRepaintThrottler scrollDragRepaint = new UiScrollRepaintThrottler();

    private Font titleFont;
    private Font bodyFont;
    private Font smallFont;
    private Font sectionHeaderFont;

    private int headerBarHeight = 44;

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
        resultPathHops = pathHops;
        resultDurationMs = durationMs;
        scrollCtrl.reset();
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
        int fracQ = abs % 4;
        int fracDec = fracQ * 25;
        String fracStr = fracDec < 10 ? "0" + fracDec : String.valueOf(fracDec);
        return (neg ? "-" : "") + whole + "." + fracStr + " dB";
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

    private String traceHeaderLeft() {
        if (resultPathHops < 0) {
            return "";
        }
        return "Path Hops: " + resultPathHops;
    }

    private String traceHeaderRight() {
        if (resultDurationMs < 0) {
            return "";
        }
        return "Duration: " + resultDurationMs + " ms";
    }

    private int measureTraceHeaderBar(int w) {
        ensureFonts();
        if (forwardDone) {
            return UiScreenHeader.measureHeightPrimarySubLeftRight(w, "",
                    traceHeaderLeft(), traceHeaderRight(), titleFont, smallFont);
        }
        if (waiting && !timedOut) {
            String d = waitingDots == 0 ? "" : (waitingDots == 1 ? "." : (waitingDots == 2 ? ".." : "..."));
            return UiScreenHeader.measureHeight(w, UiScreenHeader.PLEASE_WAIT + d, null, titleFont, smallFont);
        }
        return UiScreenHeader.measureHeight(w, "", null, titleFont, smallFont);
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        ensureFonts();

        int headerBarH = measureTraceHeaderBar(w);
        int viewportH = h - headerBarH;
        if (viewportH < 1) {
            viewportH = 1;
        }

        int pad = 8;
        int x = pad;
        int maxW = w - (pad * 2) - 6;

        if (!forwardDone && waiting && !timedOut) {
            g.setColor(UiTheme.PANEL_BG);
            g.fillRect(0, 0, w, h);
            String dots = (waitingDots == 0) ? "" : (waitingDots == 1 ? "." : (waitingDots == 2 ? ".." : "..."));
            String waitHeader = UiScreenHeader.PLEASE_WAIT + dots;
            int avail = viewportH;
            int ty = headerBarH + Math.max(12, (avail - titleFont.getHeight() - bodyFont.getHeight() - 16) / 2);
            g.setFont(titleFont);
            g.setColor(UiTheme.TEXT_DARK);
            UiCanvasUtil.drawWrappedCentered(g, WAIT_LINE + dots, 0, ty, w);
            g.setFont(bodyFont);
            g.setColor(UiTheme.TEXT_GRAY);
            UiCanvasUtil.drawWrappedCentered(g, WAIT_SUB, 0, ty + titleFont.getHeight() + 6, w);
            headerBarHeight = UiScreenHeader.paint(g, w, waitHeader, null, titleFont, smallFont);
            return;
        }

        headerBarH = measureTraceHeaderBar(w);
        viewportH = h - headerBarH;
        if (viewportH < 1) {
            viewportH = 1;
        }

        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        int scrollY = scrollCtrl.getScrollY();
        g.clipRect(0, headerBarH, w, viewportH);
        int y = headerBarH - scrollY + 10;

        if (!forwardDone && timedOut) {
            y = drawMessageSection(g, x, y, maxW, "No reply (timeout). Press Refresh to try again.");
        } else if (forwardDone) {
            g.setFont(bodyFont);
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

        if (forwardDone) {
            headerBarHeight = UiScreenHeader.paintPrimarySubLeftRight(g, w, "",
                    traceHeaderLeft(), traceHeaderRight(), titleFont, smallFont);
        } else {
            headerBarHeight = UiScreenHeader.paint(g, w, "", null, titleFont, smallFont);
        }
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
        if (!forwardDone) {
            return;
        }
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int hh = measureTraceHeaderBar(w);
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
        if (!forwardDone) {
            return;
        }
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int hh = headerBarHeight > 0 ? headerBarHeight : measureTraceHeaderBar(w);
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
        int hh = headerBarHeight > 0 ? headerBarHeight : measureTraceHeaderBar(w);
        scrollDragRepaint.flushRepaint(this, 0, hh, w, h - hh);
    }
}
