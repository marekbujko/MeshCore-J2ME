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
    private final Command cmdViewMap;

    private int resultPathHops = -1;
    private long resultDurationMs = -1;

    private byte[] lastPathSnrs;
    private int lastFinalSNR4;
    private String resultDestName = "";

    private boolean forwardDone = false;
    private boolean waiting = false;
    private boolean timedOut = false;
    private int waitingDots = 0;
    private volatile int waitGeneration = 0;

    private final UiScrollController scrollCtrl = new UiScrollController();
    private final UiScrollRepaintThrottler scrollDragRepaint = new UiScrollRepaintThrottler();

    /** Lazily rebuilt when width or path changes; avoids redoing string work every paint. */
    private String[] cachedHopLabels;
    private String[] cachedHopSnrs;
    private String[] cachedSegDist;
    private String cachedFinalSnr;
    private String cachedTotalDistLine;
    /** Compact total for header next to hop count (e.g. {@code 1.2 km}, {@code ~850 m}, {@code n/a}). */
    private String cachedHeaderTotalDist = "";
    private int cachedContentWidth = -1;
    private int cachedLayoutHopCount = -2;
    private int cachedContentHeight;
    private int cachedMaxW;
    private int cachedBubbleH;
    /** SNR row height (single line: SNR + distance; see {@link UiTimelinePainter#drawSnrArrowDownWithDistance}). */
    private int cachedSnrH;
    private int cachedTotalDistBandH;
    private final int[] distScratchA = new int[2];
    private final int[] distScratchB = new int[2];
    /** Extra vertical slack so partially visible rows still draw when scrolling. */
    private static final int VIEWPORT_CULL_MARGIN = 56;

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
        cmdViewMap = new Command("View on map", Command.SCREEN, 3);
        addCommand(cmdBack);
        addCommand(cmdRefresh);
        addCommand(cmdViewMap);
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
        clearTraceLayoutCache();
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
        resultDestName = (destName != null) ? destName : "";
        lastPathSnrs = pathSnrs;
        lastFinalSNR4 = finalSNR4;
        resultPathHops = pathHops;
        resultDurationMs = durationMs;
        scrollCtrl.reset();
        clearTraceLayoutCache();
        repaint();
    }

    private void clearTraceLayoutCache() {
        cachedHopLabels = null;
        cachedHopSnrs = null;
        cachedSegDist = null;
        cachedFinalSnr = null;
        cachedTotalDistLine = null;
        cachedContentWidth = -1;
        cachedLayoutHopCount = -2;
        cachedContentHeight = 0;
        cachedMaxW = 0;
        cachedBubbleH = 0;
        cachedSnrH = 0;
        cachedTotalDistBandH = 0;
    }

    private static boolean bandIntersectsViewport(int bandTop, int bandBottom, int viewTop, int viewBottom) {
        int m = VIEWPORT_CULL_MARGIN;
        return bandBottom >= viewTop - m && bandTop <= viewBottom + m;
    }

    /**
     * Precomputes hop labels, SNR strings, and content height (matches {@link UiTimelinePainter} metrics).
     */
    private void ensureTraceLayout(int w) {
        if (!forwardDone) {
            return;
        }
        int hopCount = (forwardPath != null) ? forwardPath.length : 0;
        int pad = 8;
        int maxW = w - (pad * 2) - 6;
        if (cachedContentWidth == w && cachedLayoutHopCount == hopCount
                && cachedHopLabels != null && cachedHopLabels.length == hopCount
                && cachedSegDist != null && cachedSegDist.length == hopCount + 1) {
            return;
        }
        ensureFonts();
        int fh = bodyFont.getHeight();
        int sfh = smallFont.getHeight();
        int bubbleH = fh + 14 + 4;
        int snrBandH = fh + 11;

        cachedContentWidth = w;
        cachedLayoutHopCount = hopCount;
        cachedMaxW = maxW;
        cachedBubbleH = bubbleH;
        cachedSnrH = snrBandH;

        cachedHopLabels = new String[hopCount];
        cachedHopSnrs = new String[hopCount];
        for (int i = 0; i < hopCount; i++) {
            cachedHopLabels[i] = getRepeaterLabel(i);
            cachedHopSnrs[i] = getHopSnr(i);
        }
        cachedFinalSnr = formatSnr4ToDb(lastFinalSNR4);

        buildSegmentDistanceStrings(hopCount);
        cachedTotalDistBandH = sfh + 10;

        cachedContentHeight = 10 + (hopCount + 2) * bubbleH + (hopCount + 1) * snrBandH
                + cachedTotalDistBandH + 16 + pad;
    }

    private static int approxDistanceMeters(int lat1E6, int lon1E6, int lat2E6, int lon2E6) {
        double lat1 = lat1E6 / 1000000.0;
        double lon1 = lon1E6 / 1000000.0;
        double lat2 = lat2E6 / 1000000.0;
        double lon2 = lon2E6 / 1000000.0;
        double meanLat = (lat1 + lat2) * 0.5 * 3.141592653589793 / 180.0;
        double dx = (lon2 - lon1) * 111320.0 * Math.cos(meanLat);
        double dy = (lat2 - lat1) * 110540.0;
        return (int) Math.sqrt(dx * dx + dy * dy);
    }

    private static String formatDistanceShort(int meters) {
        if (meters < 0) {
            meters = 0;
        }
        if (meters < 1000) {
            return Integer.toString(meters) + " m";
        }
        int km10 = (meters + 50) / 100;
        int km = km10 / 10;
        int d = km10 % 10;
        return Integer.toString(km) + "." + Integer.toString(d) + " km";
    }

    private boolean latLonForPathHop(int hopIndex, int[] outLatLonE6) {
        if (forwardPath == null || hopIndex < 0 || hopIndex >= forwardPath.length) {
            return false;
        }
        int cidx = app.getRepeaterContactIndexForPathByte(forwardPath[hopIndex]);
        if (cidx < 0) {
            return false;
        }
        int la = app.getContactAdvLatE6(cidx);
        int lo = app.getContactAdvLonE6(cidx);
        if (la == Integer.MIN_VALUE || lo == Integer.MIN_VALUE) {
            return false;
        }
        outLatLonE6[0] = la;
        outLatLonE6[1] = lo;
        return true;
    }

    private void buildSegmentDistanceStrings(int hopCount) {
        cachedSegDist = new String[hopCount + 1];
        if (hopCount <= 0) {
            int nLa = app.getNodeAdvLatE6();
            int nLo = app.getNodeAdvLonE6();
            if (nLa != Integer.MIN_VALUE && nLo != Integer.MIN_VALUE) {
                cachedSegDist[0] = "0 m";
                cachedTotalDistLine = "Total Path Distance: 0 m";
                cachedHeaderTotalDist = "0 m";
            } else {
                cachedSegDist[0] = "n/a";
                cachedTotalDistLine = "Total Path Distance: unknown";
                cachedHeaderTotalDist = "n/a";
            }
            return;
        }

        int nLa = app.getNodeAdvLatE6();
        int nLo = app.getNodeAdvLonE6();
        boolean nodeOk = nLa != Integer.MIN_VALUE && nLo != Integer.MIN_VALUE;

        int totalM = 0;
        boolean anyOk = false;
        boolean anyMissing = false;

        for (int leg = 0; leg <= hopCount; leg++) {
            boolean okA;
            boolean okB;
            int aLa;
            int aLo;
            int bLa;
            int bLo;

            if (leg == 0) {
                okA = nodeOk;
                aLa = nLa;
                aLo = nLo;
                okB = latLonForPathHop(0, distScratchB);
                bLa = distScratchB[0];
                bLo = distScratchB[1];
            } else if (leg < hopCount) {
                okA = latLonForPathHop(leg - 1, distScratchA);
                aLa = distScratchA[0];
                aLo = distScratchA[1];
                okB = latLonForPathHop(leg, distScratchB);
                bLa = distScratchB[0];
                bLo = distScratchB[1];
            } else {
                okA = latLonForPathHop(hopCount - 1, distScratchA);
                aLa = distScratchA[0];
                aLo = distScratchA[1];
                okB = nodeOk;
                bLa = nLa;
                bLo = nLo;
            }

            if (!okA || !okB) {
                cachedSegDist[leg] = "n/a";
                anyMissing = true;
            } else {
                int m = approxDistanceMeters(aLa, aLo, bLa, bLo);
                cachedSegDist[leg] = formatDistanceShort(m);
                totalM += m;
                anyOk = true;
            }
        }

        if (!anyOk) {
            cachedTotalDistLine = "Total Path Distance: unknown";
            cachedHeaderTotalDist = "n/a";
        } else if (anyMissing) {
            String part = formatDistanceShort(totalM);
            cachedTotalDistLine = "Total Path Distance: ~" + part + " (partial)";
            cachedHeaderTotalDist = "~" + part;
        } else {
            String tot = formatDistanceShort(totalM);
            cachedTotalDistLine = "Total Path Distance: " + tot;
            cachedHeaderTotalDist = tot;
        }
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
        String hops = resultPathHops == 1 ? "1 hop" : (resultPathHops + " hops");
        if (cachedHeaderTotalDist == null || cachedHeaderTotalDist.length() == 0) {
            return hops;
        }
        return cachedHeaderTotalDist + " \u00B7 " + hops;
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
            ensureTraceLayout(w);
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

        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        int scrollY = scrollCtrl.getScrollY();
        g.clipRect(0, headerBarH, w, viewportH);
        int y = headerBarH - scrollY + 10;

        int contentHeight = viewportH;

        if (!forwardDone && timedOut) {
            y = drawMessageSection(g, x, y, maxW, "No reply (timeout). Press Refresh to try again.");
            y += 16;
            contentHeight = y - headerBarH + scrollY + pad;
            if (contentHeight < viewportH) {
                contentHeight = viewportH;
            }
        } else if (forwardDone) {
            ensureTraceLayout(w);
            int viewTop = headerBarH;
            int viewBottom = headerBarH + viewportH;
            int mw = cachedMaxW > 0 ? cachedMaxW : maxW;
            int bubbleH = cachedBubbleH;
            int snrH = cachedSnrH;
            int hopCount = (forwardPath != null) ? forwardPath.length : 0;

            g.setFont(bodyFont);
            String myNodeLabel = UiCanvasUtil.getNodeLabel(app);

            int bandTop = y;
            int bandBottom = y + bubbleH;
            if (bandIntersectsViewport(bandTop, bandBottom, viewTop, viewBottom)) {
                UiTimelinePainter.drawNodeBubble(g, x, y, mw, myNodeLabel);
            }
            y = bandBottom;

            for (int i = 0; i < hopCount; i++) {
                bandTop = y;
                bandBottom = y + snrH;
                if (bandIntersectsViewport(bandTop, bandBottom, viewTop, viewBottom)) {
                    UiTimelinePainter.drawSnrArrowDownWithDistance(g, x, y, mw,
                            cachedHopSnrs[i], cachedSegDist[i]);
                }
                y = bandBottom;
                bandTop = y;
                bandBottom = y + bubbleH;
                if (bandIntersectsViewport(bandTop, bandBottom, viewTop, viewBottom)) {
                    UiTimelinePainter.drawNodeBubble(g, x, y, mw, cachedHopLabels[i], String.valueOf(i + 1));
                }
                y = bandBottom;
            }

            bandTop = y;
            bandBottom = y + snrH;
            if (bandIntersectsViewport(bandTop, bandBottom, viewTop, viewBottom)) {
                UiTimelinePainter.drawSnrArrowDownWithDistance(g, x, y, mw,
                        cachedFinalSnr, cachedSegDist[hopCount]);
            }
            y = bandBottom;
            bandTop = y;
            bandBottom = y + bubbleH;
            if (bandIntersectsViewport(bandTop, bandBottom, viewTop, viewBottom)) {
                UiTimelinePainter.drawNodeBubble(g, x, y, mw, myNodeLabel);
            }
            y = bandBottom;

            bandTop = y;
            bandBottom = y + cachedTotalDistBandH;
            if (cachedTotalDistLine != null
                    && bandIntersectsViewport(bandTop, bandBottom, viewTop, viewBottom)) {
                g.setFont(smallFont);
                g.setColor(UiTheme.TEXT_DARK);
                int tw = smallFont.stringWidth(cachedTotalDistLine);
                g.drawString(cachedTotalDistLine, x + (mw - tw) / 2, y + 2, Graphics.LEFT | Graphics.TOP);
            }
            y += cachedTotalDistBandH;

            contentHeight = cachedContentHeight;
            if (contentHeight < viewportH) {
                contentHeight = viewportH;
            }
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
            return;
        }
        if (c == cmdViewMap) {
            if (!forwardDone || forwardPath == null || forwardPath.length == 0) {
                Alerts.warning(app.getDisplay(), this, "Map", "Run trace first.", 2000);
                return;
            }
            app.showMapViewTracePath(forwardPath, lastPathSnrs, lastFinalSNR4, resultDestName, this);
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

    protected void keyRepeated(int keyCode) {
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
        if (action != UP && action != DOWN) {
            return;
        }
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
