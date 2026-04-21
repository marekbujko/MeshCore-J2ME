package meshcore.ui;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import meshcore.maps.MapMercator;
import meshcore.maps.MapTileLoader;
import meshcore.maps.MapTileUrl;
import meshcore.protocol.ProtocolConstants;
import meshcore.util.MapViewStore;

/**
 * Lightweight slippy-map viewer: pan (pointer or keys), zoom, tiles from file URL or HTTP(S).
 */
public final class MapViewCanvas extends Canvas implements CommandListener {

    private static final int TILE = 256;
    private static final int MIN_Z = 2;
    private static final int MAX_Z = 18;
    /** Evict off-screen tiles first; try to stay near this count (memory vs refetch). */
    private static final int CACHE_SOFT_MAX = 12;
    /**
     * Must be >= worst-case {@code keepNeeded} (roughly ceil(w/256+4)*ceil(h/256+4)) or we evict
     * visible tiles and re-download every frame (connection storm on large emulator screens).
     */
    private static final int CACHE_HARD_MAX = 42;
    /** Drop pending fetches beyond this so fast pans do not backlog HTTP on low-RAM phones. */
    private static final int PENDING_MAX = 12;
    /**
     * After a failed fetch, do not request that tile again until the user pans this far (px)
     * from the last backoff-clear position (or zoom / new source). Smaller = faster retry.
     */
    private static final int FAIL_BACKOFF_CLEAR_PAN_PX = 96;
    /** Clear per-viewport failed-tile pause after this scroll delta so new edges can retry. */
    private static final int FAIL_RETRY_SCROLL_PX = 48;
    /** D-pad single press: small world-px step (precise nudge). */
    private static final int PAN_KEY_FINE = 6;
    /** D-pad held (key repeat): larger step so scrolling stays quick without chunky single presses. */
    private static final int PAN_KEY_FAST = 22;
    /** Pixels off-screen where we still draw markers (for pan edge). */
    private static final int MARKER_VIEW_MARGIN = 32;
    private static final int COLOR_SELF_FILL = 0x1A9E1A;
    private static final int COLOR_SELF_OUTLINE = 0xFFFFFF;
    private static final int COLOR_REPEATER = 0xE07010;
    private static final int COLOR_CONTACT = 0x2A6BD6;
    private static final int COLOR_ROOM = 0x7B3EB8;
    private static final int COLOR_SENSOR = 0x008888;
    private static final int COLOR_MARKER_TEXT = 0xFFFFFF;
    private static final int COLOR_MARKER_TEXT_BORDER = 0x000000;
    private static final int COLOR_TRACE_TX = 0x1A9E1A;
    private static final int COLOR_TRACE_RX = 0xCC2020;
    private static final int COLOR_MEASURE_LINE = 0x0099CC;
    private static final int COLOR_MEASURE_VERTEX = 0xFFFFFF;
    private static final int MARKER_LABEL_MIN_Z = 12;
    private static final int MARKER_LABEL_COOLDOWN_MS = 380;
    /** Max label bounding boxes tracked per frame (soft cap; overlap culls first). */
    private static final int MARKER_LABEL_MAX_PLACED = 56;
    private static final int MARKER_LABEL_RECT_GAP = 3;

    private static final int MAP_MODE_PAN = 0;
    private static final int MAP_MODE_MEASURE = 1;
    /** Tap radius (px) to open a contact from the map. */
    private static final int MAP_SELECT_HIT_RADIUS = 28;
    private static final int PICK_MAP_SELF = -2;
    /** Ascending nice lengths (m) for the map scale bar. */
    private static final int[] SCALE_BAR_METERS = {
        1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000,
        100000, 200000, 500000, 1000000, 2000000, 5000000
    };
    /** Min gap between adjacent scale labels (px). */
    private static final int SCALE_LABEL_GAP = 4;
    /** Label tick sets: five ticks, then 0/half/max, then ends only. */
    private static final int[][] SCALE_LABEL_PLANS = {
        {0, 1, 2, 3, 4},
        {0, 2, 4},
        {0, 4}
    };

    private final AppController app;
    private final Displayable returnTo;
    private final int focusContactIdx;
    private final String focusContactName;
    /** When non-null/non-empty, draw TRACE path polyline and SNR footer. */
    private final byte[] traceForwardPath;
    private final String traceDestLabel;

    private final Command cmdBack;
    private final Command cmdIn;
    private final Command cmdOut;
    private final Command cmdSource;
    private final Command cmdCenter;
    private final Command cmdMeasureOn;
    private final Command cmdMeasureOff;
    /** Which measure command is currently on the menu (MIDP has no {@code Command.setLabel}). */
    private boolean measureMenuShowsOn;
    private final Command cmdSelect;
    private final Command cmdTracePath;
    private final Command cmdPickAdd;
    private final Command cmdPickRun;
    private final Command cmdPickAutoReturn;
    private final Command cmdPickRemove;
    private final Command cmdPickClear;
    /** Trace picker uses separate zoom/source commands so menu order stays below Run / Reset. */
    private final Command cmdTraceZoomIn;
    private final Command cmdTraceZoomOut;
    private final Command cmdTraceMyLoc;
    private final Command cmdTraceSource;
    private final boolean tracePickMode;
    private final PathEditorModel tracePickPathModel = new PathEditorModel();

    private String template;
    private int z;
    private long scrollX;
    private long scrollY;

    /** One cache for all map screens so reopening map / trace / picker reuses decoded tiles. */
    private static final Hashtable SHARED_TILE_CACHE = new Hashtable();
    private static String sharedTileCacheTemplate = null;
    private final Vector pending = new Vector();
    private final Vector inFlight = new Vector();
    /** Single lock for {@link #pending} + {@link #inFlight} (avoid deadlock vs enqueue / purge). */
    private final Object tileReqLock = new Object();
    /** Tile keys we skip re-fetching until a significant pan or full reset (no time-based auto-retry). */
    private final Hashtable pausedFailedTiles = new Hashtable();
    /** Scroll position when we last cleared failure backoff (see FAIL_BACKOFF_CLEAR_PAN_PX). */
    private long failBackoffOriginX;
    private long failBackoffOriginY;
    private long lastFailRetryScrollX;
    private long lastFailRetryScrollY;
    private boolean failRetryScrollInited;

    private volatile boolean loaderRunning;
    private Thread loaderThread;

    private int dragLastX;
    private int dragLastY;
    private boolean pointerMoved;
    private boolean dragging;
    private long lastInteractionMs;
    private volatile long pendingLabelRepaintAtMs;
    private final long[] tmpMercatorXY = new long[2];
    private final int[] tmpMarkerXY = new int[2];
    private final int[] measureLatLonScratch = new int[2];
    private int mapInteractMode = MAP_MODE_PAN;
    private final Vector measurePoints = new Vector();
    /** Reused by background tile loader (avoid int[] alloc per tile). */
    private final int[] loaderZxyScratch = new int[3];
    /** Reused in {@link #paint} to enqueue missing tiles (avoid new Vector per frame). */
    private final Vector nearEnqueueScratch = new Vector();
    private final Vector farEnqueueScratch = new Vector();
    /** {@link #drawMeshMarkers}: visible contacts sorted by distance to screen center. */
    private final Vector markerCandScratch = new Vector();
    private final int[] markerLabelBoundsScratch = new int[4];
    private final int[] markerOccupyRects = new int[MARKER_LABEL_MAX_PLACED * 4];
    private int markerOccupyCount;

    /** Coalesce many tile completions into one EDT repaint (avoids HTTP re-queue storms while scrolling). */
    private boolean loaderRepaintQueued;
    private final Runnable loaderRepaintRunnable = new Runnable() {
        public void run() {
            synchronized (MapViewCanvas.this) {
                loaderRepaintQueued = false;
            }
            repaint();
        }
    };

    /** One {@link #repaint} per EDT batch while dragging (scroll updates every {@link #pointerDragged}). */
    private boolean dragPaintScheduled;
    private final Runnable dragPaintRunnable = new Runnable() {
        public void run() {
            synchronized (MapViewCanvas.this) {
                dragPaintScheduled = false;
                if (dragging) {
                    repaint();
                }
            }
        }
    };

    public MapViewCanvas(AppController app, Displayable returnTo) {
        this(app, returnTo, -1, null, null, null, false);
    }

    public MapViewCanvas(AppController app, Displayable returnTo, int focusContactIdx, String focusContactName) {
        this(app, returnTo, focusContactIdx, focusContactName, null, null, false);
    }

    public MapViewCanvas(AppController app, Displayable returnTo,
            byte[] traceForwardPath, byte[] tracePathSnrs, int traceFinalSnr4, String traceDestLabel) {
        this(app, returnTo, -1, null, traceForwardPath, traceDestLabel, false);
    }

    public MapViewCanvas(AppController app, Displayable returnTo, boolean tracePickMode) {
        this(app, returnTo, -1, null, null, null, tracePickMode);
    }

    private MapViewCanvas(AppController app, Displayable returnTo,
            int focusContactIdx, String focusContactName,
            byte[] traceForwardPath, String traceDestLabel, boolean tracePickMode) {
        this.app = app;
        this.returnTo = returnTo;
        this.focusContactIdx = focusContactIdx;
        this.focusContactName = (focusContactName != null) ? focusContactName : "";
        this.traceForwardPath = traceForwardPath;
        this.traceDestLabel = (traceDestLabel != null) ? traceDestLabel : "";
        this.tracePickMode = tracePickMode;
        if (tracePickMode) {
            setTitle("Trace Path (Map)");
        } else {
            updateMapTitle();
        }

        template = MapViewStore.loadTemplate();
        z = MapViewStore.loadZoom();
        long[] xy = new long[2];
        MapViewStore.loadScroll(xy);
        scrollX = xy[0];
        scrollY = xy[1];
        if (z < MIN_Z) {
            z = MIN_Z;
        }
        if (z > MAX_Z) {
            z = MAX_Z;
        }
        if (focusContactIdx >= 0) {
            centerOnFocusContactIfAvailable();
        } else if (!hasTraceOverlay()) {
            tryCenterScrollOnNodeAdvert();
        }
        failBackoffOriginX = scrollX;
        failBackoffOriginY = scrollY;

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdPickRun = new Command("Run Trace", Command.SCREEN, 2);
        cmdPickAdd = new Command("Add Repeater", Command.SCREEN, 3);
        cmdPickRemove = new Command("Remove Last Repeater", Command.SCREEN, 4);
        cmdPickAutoReturn = new Command("Auto Return Path", Command.SCREEN, 5);
        cmdSelect = new Command("Select", Command.SCREEN, 2);
        cmdIn = new Command("Zoom +", Command.SCREEN, 3);
        cmdOut = new Command("Zoom -", Command.SCREEN, 4);
        cmdCenter = new Command("My Location", Command.SCREEN, 5);
        cmdMeasureOn = new Command("Measure distance: ON", Command.SCREEN, 6);
        cmdMeasureOff = new Command("Measure distance: OFF", Command.SCREEN, 6);
        cmdPickClear = new Command("Reset Path", Command.SCREEN, 6);
        cmdTracePath = new Command("Trace Path \u2022 Using Map", Command.SCREEN, 7);
        cmdSource = new Command("Map Source", Command.SCREEN, 8);
        cmdTraceZoomIn = new Command("Zoom +", Command.SCREEN, 7);
        cmdTraceZoomOut = new Command("Zoom -", Command.SCREEN, 8);
        cmdTraceMyLoc = new Command("My Location", Command.SCREEN, 9);
        cmdTraceSource = new Command("Map Source", Command.SCREEN, 10);
        addCommand(cmdBack);
        if (tracePickMode) {
            addCommand(cmdPickRun);
            addCommand(cmdPickAdd);
            addCommand(cmdPickRemove);
            addCommand(cmdPickAutoReturn);
            addCommand(cmdPickClear);
            addCommand(cmdTraceZoomIn);
            addCommand(cmdTraceZoomOut);
            addCommand(cmdTraceMyLoc);
            addCommand(cmdTraceSource);
        } else {
            addCommand(cmdSelect);
            addCommand(cmdIn);
            addCommand(cmdOut);
            addCommand(cmdCenter);
            addCommand(cmdMeasureOff);
            measureMenuShowsOn = false;
            addCommand(cmdTracePath);
            addCommand(cmdSource);
        }
        setCommandListener(this);
        syncMeasureMenuCommand();
    }

    private boolean hasTraceOverlay() {
        return traceForwardPath != null && traceForwardPath.length > 0;
    }

    private void centerOnFocusContactIfAvailable() {
        int latE6 = app.getContactAdvLatE6(focusContactIdx);
        int lonE6 = app.getContactAdvLonE6(focusContactIdx);
        if (latE6 == Integer.MIN_VALUE || lonE6 == Integer.MIN_VALUE) {
            return;
        }
        double lat = latE6 / 1000000.0;
        double lon = lonE6 / 1000000.0;
        long[] xy = new long[2];
        MapMercator.latLonToPixel(lat, lon, z, xy);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0) w = 240;
        if (h <= 0) h = 320;
        scrollX = xy[0] - (long) (w / 2);
        scrollY = xy[1] - (long) (h / 2);
    }

    public void setTemplate(String t) {
        if (t == null) {
            t = "";
        } else {
            t = t.trim();
        }
        template = t;
        markInteraction();
        clearSharedTileCacheAndLoaderQueues();
        MapViewStore.save(template, z, scrollX, scrollY);
        repaint();
    }

    /** When tile URL template changes — must drop cached bitmaps. */
    private void clearSharedTileCacheAndLoaderQueues() {
        synchronized (SHARED_TILE_CACHE) {
            SHARED_TILE_CACHE.clear();
        }
        sharedTileCacheTemplate = template != null ? template : "";
        resetLoaderQueuesAndBackoff();
    }

    /** Zoom / pan reset: drop fetch queues only; keep shared tiles for this template and zoom level keys. */
    private void resetLoaderQueuesAndBackoff() {
        synchronized (tileReqLock) {
            pending.removeAllElements();
            inFlight.removeAllElements();
        }
        synchronized (pausedFailedTiles) {
            pausedFailedTiles.clear();
        }
        failBackoffOriginX = scrollX;
        failBackoffOriginY = scrollY;
        failRetryScrollInited = false;
    }

    private void ensureSharedTileCacheTemplate() {
        String t = template != null ? template : "";
        if (sharedTileCacheTemplate == null) {
            sharedTileCacheTemplate = t;
            return;
        }
        if (sharedTileCacheTemplate.equals(t)) {
            return;
        }
        synchronized (SHARED_TILE_CACHE) {
            SHARED_TILE_CACHE.clear();
        }
        sharedTileCacheTemplate = t;
    }

    protected void showNotify() {
        super.showNotify();
        clampScroll();
        loaderRunning = true;
        loaderThread = new Thread(new Runnable() {
            public void run() {
                runLoaderLoop();
            }
        });
        loaderThread.start();
    }

    protected void hideNotify() {
        loaderRunning = false;
        loaderThread = null;
        synchronized (this) {
            loaderRepaintQueued = false;
        }
        MapViewStore.save(template, z, scrollX, scrollY);
        super.hideNotify();
    }

    private void scheduleRepaintFromLoader() {
        synchronized (this) {
            if (loaderRepaintQueued) {
                return;
            }
            loaderRepaintQueued = true;
        }
        try {
            app.getDisplay().callSerially(loaderRepaintRunnable);
        } catch (Throwable t) {
            synchronized (this) {
                loaderRepaintQueued = false;
            }
        }
    }

    private void runLoaderLoop() {
        while (loaderRunning) {
            String key = null;
            synchronized (tileReqLock) {
                if (pending.size() > 0) {
                    key = (String) pending.elementAt(0);
                    pending.removeElementAt(0);
                }
            }
            if (key == null) {
                try {
                    Thread.sleep(55);
                } catch (InterruptedException e) {
                    return;
                }
                continue;
            }
            if (!parseKeyInto(key, loaderZxyScratch) || template == null || template.length() == 0) {
                synchronized (tileReqLock) {
                    inFlight.removeElement(key);
                }
                continue;
            }
            boolean zoomStale;
            synchronized (this) {
                zoomStale = loaderZxyScratch[0] != z;
            }
            if (zoomStale) {
                synchronized (tileReqLock) {
                    inFlight.removeElement(key);
                }
                continue;
            }
            synchronized (SHARED_TILE_CACHE) {
                if (SHARED_TILE_CACHE.containsKey(key)) {
                    synchronized (tileReqLock) {
                        inFlight.removeElement(key);
                    }
                    scheduleRepaintFromLoader();
                    continue;
                }
            }
            String url = MapTileUrl.format(template, loaderZxyScratch[0], loaderZxyScratch[1], loaderZxyScratch[2]);
            Image img = MapTileLoader.loadTile(url);
            if (img != null && loaderRunning) {
                synchronized (pausedFailedTiles) {
                    pausedFailedTiles.remove(key);
                }
                synchronized (this) {
                    if (loaderZxyScratch[0] != z) {
                        synchronized (tileReqLock) {
                            inFlight.removeElement(key);
                        }
                        continue;
                    }
                }
                synchronized (SHARED_TILE_CACHE) {
                    if (!SHARED_TILE_CACHE.containsKey(key)) {
                        SHARED_TILE_CACHE.put(key, img);
                    }
                }
                synchronized (tileReqLock) {
                    inFlight.removeElement(key);
                }
                scheduleRepaintFromLoader();
            } else {
                if (img != null) {
                    synchronized (this) {
                        if (loaderZxyScratch[0] == z) {
                            synchronized (SHARED_TILE_CACHE) {
                                if (!SHARED_TILE_CACHE.containsKey(key)) {
                                    SHARED_TILE_CACHE.put(key, img);
                                }
                            }
                        }
                    }
                } else if (loaderRunning) {
                    synchronized (pausedFailedTiles) {
                        pausedFailedTiles.put(key, key);
                    }
                }
                synchronized (tileReqLock) {
                    inFlight.removeElement(key);
                }
            }
        }
    }

    private static boolean parseKeyInto(String key, int[] out) {
        if (key == null || out == null || out.length < 3) {
            return false;
        }
        int p1 = key.indexOf(':');
        int p2 = key.indexOf(':', p1 + 1);
        if (p1 < 0 || p2 < 0) {
            return false;
        }
        try {
            out[0] = Integer.parseInt(key.substring(0, p1));
            out[1] = Integer.parseInt(key.substring(p1 + 1, p2));
            out[2] = Integer.parseInt(key.substring(p2 + 1));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Drop queued or in-flight fetches for tiles no longer in the viewport so fast pans do not
     * waste CPU, heap, and network on rows the user has already left behind.
     */
    private void purgeStaleTileRequests(Hashtable keepNeeded) {
        synchronized (tileReqLock) {
            for (int i = pending.size() - 1; i >= 0; i--) {
                String k = (String) pending.elementAt(i);
                if (!keepNeeded.containsKey(k)) {
                    pending.removeElementAt(i);
                    inFlight.removeElement(k);
                }
            }
            for (int i = inFlight.size() - 1; i >= 0; i--) {
                String k = (String) inFlight.elementAt(i);
                if (!keepNeeded.containsKey(k)) {
                    inFlight.removeElementAt(i);
                }
            }
        }
    }

    /**
     * Evict only tiles not needed for this paint frame (never drop a key in {@code keepNeeded} until soft max).
     */
    private void trimExcessTiles(Hashtable keepNeeded) {
        synchronized (SHARED_TILE_CACHE) {
            while (SHARED_TILE_CACHE.size() > CACHE_SOFT_MAX) {
                String victim = null;
                Enumeration e = SHARED_TILE_CACHE.keys();
                while (e.hasMoreElements()) {
                    String k = (String) e.nextElement();
                    if (!keepNeeded.containsKey(k)) {
                        victim = k;
                        break;
                    }
                }
                if (victim == null) {
                    break;
                }
                SHARED_TILE_CACHE.remove(victim);
            }
            while (SHARED_TILE_CACHE.size() > CACHE_HARD_MAX) {
                String victim = pickCachedKeyNotIn(keepNeeded);
                if (victim == null) {
                    // All remaining keys are still in the viewport; do not evict them (avoids HTTP thrash).
                    break;
                }
                SHARED_TILE_CACHE.remove(victim);
            }
        }
    }

    private String pickCachedKeyNotIn(Hashtable keepNeeded) {
        synchronized (SHARED_TILE_CACHE) {
            Enumeration e = SHARED_TILE_CACHE.keys();
            while (e.hasMoreElements()) {
                String k = (String) e.nextElement();
                if (!keepNeeded.containsKey(k)) {
                    return k;
                }
            }
        }
        return null;
    }

    private static int wrapTileDistanceX(int a, int b, int n) {
        int d = a - b;
        if (d < 0) {
            d = -d;
        }
        int alt = n - d;
        return alt < d ? alt : d;
    }

    /**
     * After a failed fetch, tiles stay in {@link #pausedFailedTiles} until backoff.
     * On scroll, allow visible tiles to retry so pans do not leave permanent holes until zoom.
     */
    private void maybeRetryPausedForViewport(Hashtable keepNeeded) {
        if (!failRetryScrollInited) {
            lastFailRetryScrollX = scrollX;
            lastFailRetryScrollY = scrollY;
            failRetryScrollInited = true;
            return;
        }
        long ddx = scrollX - lastFailRetryScrollX;
        if (ddx < 0) {
            ddx = -ddx;
        }
        long ddy = scrollY - lastFailRetryScrollY;
        if (ddy < 0) {
            ddy = -ddy;
        }
        if (ddx >= (long) FAIL_RETRY_SCROLL_PX || ddy >= (long) FAIL_RETRY_SCROLL_PX) {
            lastFailRetryScrollX = scrollX;
            lastFailRetryScrollY = scrollY;
            synchronized (pausedFailedTiles) {
                Enumeration e = keepNeeded.keys();
                while (e.hasMoreElements()) {
                    pausedFailedTiles.remove((String) e.nextElement());
                }
            }
        }
    }

    /**
     * @param headOfQueue when true, load before outer tiles (insert at front; may drop a queued tail if full).
     */
    private void enqueueTile(String key, boolean headOfQueue) {
        synchronized (SHARED_TILE_CACHE) {
            if (SHARED_TILE_CACHE.containsKey(key)) {
                return;
            }
        }
        synchronized (pausedFailedTiles) {
            if (pausedFailedTiles.containsKey(key)) {
                return;
            }
        }
        synchronized (tileReqLock) {
            if (inFlight.contains(key)) {
                if (headOfQueue && pending.contains(key)) {
                    pending.removeElement(key);
                    pending.insertElementAt(key, 0);
                }
                return;
            }
            if (pending.contains(key)) {
                if (headOfQueue) {
                    pending.removeElement(key);
                    pending.insertElementAt(key, 0);
                }
                return;
            }
            if (pending.size() >= PENDING_MAX) {
                if (!headOfQueue) {
                    return;
                }
                String tail = (String) pending.elementAt(pending.size() - 1);
                pending.removeElementAt(pending.size() - 1);
                inFlight.removeElement(tail);
            }
            inFlight.addElement(key);
            if (headOfQueue) {
                pending.insertElementAt(key, 0);
            } else {
                pending.addElement(key);
            }
        }
    }

    private Image getCached(String key) {
        synchronized (SHARED_TILE_CACHE) {
            return (Image) SHARED_TILE_CACHE.get(key);
        }
    }

    private void clampScroll() {
        int w = getWidth();
        int h = getHeight();
        int n = 1 << z;
        long world = (long) TILE * (long) n;
        long maxX = world - w;
        long maxY = world - h;
        if (maxX < 0) {
            maxX = 0;
        }
        if (maxY < 0) {
            maxY = 0;
        }
        if (scrollX < 0) {
            scrollX = 0;
        }
        if (scrollY < 0) {
            scrollY = 0;
        }
        if (scrollX > maxX) {
            scrollX = maxX;
        }
        if (scrollY > maxY) {
            scrollY = maxY;
        }
    }

    /** Grid spacing when no tile source; lines follow pan so the canvas still feels map-like. */
    private static final int EMPTY_GRID_STEP = 48;

    private static int scrollModPositive(long scroll, int step) {
        long r = scroll % (long) step;
        if (r < 0) {
            r += (long) step;
        }
        return (int) r;
    }

    private void drawEmptyMapBackground(Graphics g, int w, int h) {
        g.setColor(UiTheme.MAP_EMPTY_BG);
        g.fillRect(0, 0, w, h);
        int step = EMPTY_GRID_STEP;
        int ox = scrollModPositive(scrollX, step);
        int oy = scrollModPositive(scrollY, step);
        g.setColor(UiTheme.MAP_EMPTY_GRID);
        for (int x = -ox; x < w + step; x += step) {
            g.drawLine(x, 0, x, h - 1);
        }
        for (int y = -oy; y < h + step; y += step) {
            g.drawLine(0, y, w - 1, y);
        }
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        if (template == null || template.length() == 0) {
            drawEmptyMapBackground(g, w, h);
            clampScroll();
            Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(f);
            drawMeshMarkers(g, f, w, h);
            drawMeasureOverlay(g, f, w, h);
            drawFocusDistanceOverlay(g, f, w, h);
            drawTracePathOverlay(g, f, w, h);
            drawTracePickPathOverlay(g, f, w, h);
            drawMapCenterCrosshair(g, w, h);
            drawZoomOverlay(g, f, w);
            if (hasTraceOverlay() || tracePickMode) {
                drawTraceLineLegend(g, f, w);
            }
            if (mapInteractMode == MAP_MODE_MEASURE) {
                int ty = 3;
                if (hasTraceOverlay() || tracePickMode) {
                    ty = 3 + traceLineLegendHeight(f) + 2;
                }
                drawMeasureSummaryBox(g, f, w, ty);
            }
            drawScaleBar(g, f, w, h);
            drawTracePickOverlay(g, f, w, h);
            return;
        }

        g.setColor(0xDDE6F0);
        g.fillRect(0, 0, w, h);

        clampScroll();
        ensureSharedTileCacheTemplate();

        int n = 1 << z;
        int ts = TILE;
        int centerTileX = (int) ((scrollX + (long) (w / 2)) / (long) ts);
        int centerTileY = (int) ((scrollY + (long) (h / 2)) / (long) ts);
        int txStart = (int) (scrollX / (long) ts) - 1;
        int txEnd = (int) ((scrollX + (long) w + (long) ts - 1L) / (long) ts) + 1;
        int tyStart = (int) (scrollY / (long) ts) - 1;
        int tyEnd = (int) ((scrollY + (long) h + (long) ts - 1L) / (long) ts) + 1;

        Hashtable keepNeeded = new Hashtable();
        for (int tx = txStart; tx <= txEnd; tx++) {
            for (int ty = tyStart; ty <= tyEnd; ty++) {
                if (ty < 0 || ty >= n) {
                    continue;
                }
                int normX = MapMercator.wrapTileX(tx, z);
                int normY = ty;
                String key = z + ":" + normX + ":" + normY;
                keepNeeded.put(key, key);
            }
        }
        purgeStaleTileRequests(keepNeeded);
        trimExcessTiles(keepNeeded);
        maybeRetryPausedForViewport(keepNeeded);

        int cxw = MapMercator.wrapTileX(centerTileX, z);
        int cyt = centerTileY;
        Vector nearQ = nearEnqueueScratch;
        Vector farQ = farEnqueueScratch;
        nearQ.removeAllElements();
        farQ.removeAllElements();

        for (int tx = txStart; tx <= txEnd; tx++) {
            for (int ty = tyStart; ty <= tyEnd; ty++) {
                if (ty < 0 || ty >= n) {
                    continue;
                }
                int normX = MapMercator.wrapTileX(tx, z);
                int normY = ty;
                String key = z + ":" + normX + ":" + normY;
                Image img = getCached(key);
                int sx = (int) ((long) tx * (long) ts - scrollX);
                int sy = (int) ((long) ty * (long) ts - scrollY);
                if (img != null) {
                    g.drawImage(img, sx, sy, Graphics.TOP | Graphics.LEFT);
                } else {
                    g.setColor(0xC8D4E4);
                    g.fillRect(sx, sy, ts, ts);
                    g.setColor(0x8899AA);
                    g.drawRect(sx, sy, ts - 1, ts - 1);
                    int ddx = wrapTileDistanceX(normX, cxw, n);
                    int ddy = normY - cyt;
                    if (ddy < 0) {
                        ddy = -ddy;
                    }
                    if (ddx <= 1 && ddy <= 1) {
                        nearQ.addElement(key);
                    } else {
                        farQ.addElement(key);
                    }
                }
            }
        }

        for (int i = 0; i < nearQ.size(); i++) {
            enqueueTile((String) nearQ.elementAt(i), true);
        }
        for (int i = 0; i < farQ.size(); i++) {
            enqueueTile((String) farQ.elementAt(i), false);
        }

        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        drawMeshMarkers(g, f, w, h);
        drawMeasureOverlay(g, f, w, h);
        drawFocusDistanceOverlay(g, f, w, h);
        drawTracePathOverlay(g, f, w, h);
        drawTracePickPathOverlay(g, f, w, h);
        drawMapCenterCrosshair(g, w, h);
        drawZoomOverlay(g, f, w);
        if (hasTraceOverlay() || tracePickMode) {
            drawTraceLineLegend(g, f, w);
        }
        if (mapInteractMode == MAP_MODE_MEASURE) {
            int ty = 3;
            if (hasTraceOverlay() || tracePickMode) {
                ty = 3 + traceLineLegendHeight(f) + 2;
            }
            drawMeasureSummaryBox(g, f, w, ty);
        }
        drawScaleBar(g, f, w, h);
        drawTracePickOverlay(g, f, w, h);
    }

    private static String shortMapLabel(String template) {
        if (template == null || template.length() == 0) {
            return "Lightweight";
        }
        String t = template;
        String lower = t.toLowerCase();
        if (lower.indexOf("kade.si") >= 0) {
            return "BGMountains";
        }
        if (lower.indexOf("google.com") >= 0) {
            if (lower.indexOf("lyrs=s") >= 0) {
                return "Google Satellite";
            }
            if (lower.indexOf("lyrs=y") >= 0) {
                return "Google Hybrid";
            }
            if (lower.indexOf("lyrs=p") >= 0) {
                return "Google Terrain";
            }
            if (lower.indexOf("lyrs=m") >= 0) {
                return "Google Roadmap";
            }
            return "Google Roadmap";
        }
        if (lower.startsWith("file:")) {
            return "External SD Card";
        }
        int scheme = t.indexOf("://");
        int start = scheme >= 0 ? scheme + 3 : 0;
        int end = t.length();
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        if (end <= start) {
            return trimToLen(t, 26);
        }
        String host = t.substring(start, end);
        return trimToLen(host, 26);
    }

    private static String trimToLen(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        if (maxLen <= 3) {
            return "...";
        }
        return s.substring(0, maxLen - 2) + "..";
    }

    private static String truncateForWidth(String s, Font f, int maxW) {
        if (s == null || s.length() == 0) {
            return "";
        }
        if (f.stringWidth(s) <= maxW) {
            return s;
        }
        for (int len = s.length() - 1; len >= 2; len--) {
            String t = s.substring(0, len) + "..";
            if (f.stringWidth(t) <= maxW) {
                return t;
            }
        }
        return "..";
    }

    private boolean projectLatLonE6ToScreen(int latE6, int lonE6, int w, int h, int[] out) {
        if (latE6 == Integer.MIN_VALUE || lonE6 == Integer.MIN_VALUE) {
            return false;
        }
        double lat = latE6 / 1000000.0;
        double lon = lonE6 / 1000000.0;
        MapMercator.latLonToPixel(lat, lon, z, tmpMercatorXY);
        int sx = (int) (tmpMercatorXY[0] - scrollX);
        int sy = (int) (tmpMercatorXY[1] - scrollY);
        out[0] = sx;
        out[1] = sy;
        return sx >= -MARKER_VIEW_MARGIN && sy >= -MARKER_VIEW_MARGIN
                && sx <= w + MARKER_VIEW_MARGIN && sy <= h + MARKER_VIEW_MARGIN;
    }

    /** Screen pixel to WGS84 (microdegrees), using current scroll and zoom. */
    private void screenToLatLonE6(int sx, int sy, int[] outLatLonE6) {
        double world = 256.0 * (double) (1 << z);
        double wx = (double) scrollX + (double) sx;
        double wy = (double) scrollY + (double) sy;
        double lon = (wx / world) * 360.0 - 180.0;
        double lat = MapMercator.worldPixelYToLatDeg(wy, z);
        outLatLonE6[0] = (int) (lat * 1000000.0);
        outLatLonE6[1] = (int) (lon * 1000000.0);
    }

    /** Map geo to screen without culling off-viewport (for measure lines while panning). */
    private void projectLatLonE6ToScreenRaw(int latE6, int lonE6, int[] out) {
        double lat = latE6 / 1000000.0;
        double lon = lonE6 / 1000000.0;
        MapMercator.latLonToPixel(lat, lon, z, tmpMercatorXY);
        out[0] = (int) (tmpMercatorXY[0] - scrollX);
        out[1] = (int) (tmpMercatorXY[1] - scrollY);
    }

    private void updateMapTitle() {
        if (tracePickMode) {
            return;
        }
        String base = hasTraceOverlay() ? "Trace map" : "Map";
        if (mapInteractMode == MAP_MODE_MEASURE) {
            setTitle(base + " \u00B7 Measure");
        } else {
            setTitle(base);
        }
    }

    /**
     * Nearest map marker within hit radius: contact index, or {@link #PICK_MAP_SELF}, or {@code -1}.
     */
    private int pickMapMarkerForSelect(int sx, int sy) {
        int w = getWidth();
        int h = getHeight();
        int n = app.getContactCount();
        int bestIdx = -1;
        int bestD2 = Integer.MAX_VALUE;
        int r2 = MAP_SELECT_HIT_RADIUS * MAP_SELECT_HIT_RADIUS;
        for (int i = 0; i < n; i++) {
            int la = app.getContactAdvLatE6(i);
            int lo = app.getContactAdvLonE6(i);
            if (!projectLatLonE6ToScreen(la, lo, w, h, tmpMarkerXY)) {
                continue;
            }
            int dx = tmpMarkerXY[0] - sx;
            int dy = tmpMarkerXY[1] - sy;
            int d2 = dx * dx + dy * dy;
            if (d2 <= r2 && d2 < bestD2) {
                bestD2 = d2;
                bestIdx = i;
            }
        }
        int nLa = app.getNodeAdvLatE6();
        int nLo = app.getNodeAdvLonE6();
        if (nLa != Integer.MIN_VALUE && nLo != Integer.MIN_VALUE
                && projectLatLonE6ToScreen(nLa, nLo, w, h, tmpMarkerXY)) {
            int dx = tmpMarkerXY[0] - sx;
            int dy = tmpMarkerXY[1] - sy;
            int d2 = dx * dx + dy * dy;
            if (d2 <= r2 && d2 < bestD2) {
                return PICK_MAP_SELF;
            }
        }
        return bestIdx;
    }

    private void openContactActions(int contactIdx) {
        String name = app.getContactName(contactIdx);
        int type = app.getContactAdvertType(contactIdx);
        app.getDisplay().setCurrent(new ContactActionsScreen(app, contactIdx, name, type, this));
    }

    /**
     * Open the marker under the viewport crosshair. {@code showEmptyHint} is for the menu command;
     * the FIRE key passes false so empty crosshair stays silent.
     */
    private void selectAtCrosshair(boolean showEmptyHint) {
        int cw = getWidth();
        int ch = getHeight();
        if (cw <= 0) {
            cw = 240;
        }
        if (ch <= 0) {
            ch = 320;
        }
        int pick = pickMapMarkerForSelect(cw / 2, ch / 2);
        if (pick >= 0) {
            openContactActions(pick);
        } else if (pick == PICK_MAP_SELF) {
            Alerts.info(app.getDisplay(), this, "Map", "This is your position.");
        } else if (showEmptyHint) {
            Alerts.info(app.getDisplay(), this, "Map", "Nothing at crosshair.");
        }
    }

    private static int markerColorForType(int advType) {
        if (advType == ProtocolConstants.ADV_TYPE_REPEATER) {
            return COLOR_REPEATER;
        }
        if (advType == ProtocolConstants.ADV_TYPE_ROOM) {
            return COLOR_ROOM;
        }
        if (advType == ProtocolConstants.ADV_TYPE_SENSOR) {
            return COLOR_SENSOR;
        }
        return COLOR_CONTACT;
    }

    private void drawMarkerDot(Graphics g, int sx, int sy, int r, int fillRgb) {
        int d = 2 * r;
        g.setColor(fillRgb);
        g.fillArc(sx - r, sy - r, d, d, 0, 360);
        g.setColor(COLOR_SELF_OUTLINE);
        g.drawArc(sx - r, sy - r, d, d, 0, 360);
    }

    private void drawMarkerLabel(Graphics g, Font f, int sx, int textTopY, String name, int canvasW) {
        if (name == null || name.length() == 0) {
            name = "?";
        }
        int maxW = canvasW - 8;
        if (maxW < 40) {
            maxW = 40;
        }
        String label = truncateForWidth(name, f, maxW);
        int lw = f.stringWidth(label);
        int lx = sx - lw / 2;
        int pad = 3;
        if (lx < pad) {
            lx = pad;
        }
        if (lx + lw > canvasW - pad) {
            lx = canvasW - pad - lw;
        }
        int ly = textTopY;
        g.setColor(COLOR_MARKER_TEXT_BORDER);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                g.drawString(label, lx + dx, ly + dy, Graphics.LEFT | Graphics.TOP);
            }
        }
        g.setColor(COLOR_MARKER_TEXT);
        g.drawString(label, lx, ly, Graphics.LEFT | Graphics.TOP);
    }

    /** Pixels: lx, ly, lw, lh — matches {@link #drawMarkerLabel} placement. */
    private static void computeMarkerLabelBounds(Font f, String name, int sx, int textTopY, int canvasW, int[] out) {
        if (name == null || name.length() == 0) {
            name = "?";
        }
        int maxW = canvasW - 8;
        if (maxW < 40) {
            maxW = 40;
        }
        String label = truncateForWidth(name, f, maxW);
        int lw = f.stringWidth(label);
        int lx = sx - lw / 2;
        int pad = 3;
        if (lx < pad) {
            lx = pad;
        }
        if (lx + lw > canvasW - pad) {
            lx = canvasW - pad - lw;
        }
        out[0] = lx;
        out[1] = textTopY;
        out[2] = lw;
        out[3] = f.getHeight();
    }

    /** Pixels: lx, ly, lw, lh — matches {@link #drawMarkerLabelCenteredOnX}. */
    private static void computeMarkerLabelCenteredBounds(Font f, String name, int sx, int textTopY, int canvasW, int[] out) {
        if (name == null || name.length() == 0) {
            name = "?";
        }
        int maxW = canvasW - 8;
        if (maxW < 40) {
            maxW = 40;
        }
        String label = truncateForWidth(name, f, maxW);
        int lw = f.stringWidth(label);
        int lx = sx - lw / 2;
        out[0] = lx;
        out[1] = textTopY;
        out[2] = lw;
        out[3] = f.getHeight();
    }

    private static boolean labelRectsOverlap(int ax, int ay, int aw, int ah,
            int bx, int by, int bw, int bh, int gap) {
        return !(ax + aw + gap <= bx || bx + bw + gap <= ax
                || ay + ah + gap <= by || by + bh + gap <= ay);
    }

    private void clearMarkerLabelOccupancy() {
        markerOccupyCount = 0;
    }

    private boolean canPlaceMarkerLabel(int lx, int ly, int lw, int lh, int gap) {
        if (markerOccupyCount >= MARKER_LABEL_MAX_PLACED) {
            return false;
        }
        for (int i = 0; i < markerOccupyCount; i++) {
            int o = i * 4;
            int ox = markerOccupyRects[o];
            int oy = markerOccupyRects[o + 1];
            int ow = markerOccupyRects[o + 2];
            int oh = markerOccupyRects[o + 3];
            if (labelRectsOverlap(lx, ly, lw, lh, ox, oy, ow, oh, gap)) {
                return false;
            }
        }
        return true;
    }

    private void placeMarkerLabelRect(int lx, int ly, int lw, int lh) {
        int o = markerOccupyCount * 4;
        markerOccupyRects[o] = lx;
        markerOccupyRects[o + 1] = ly;
        markerOccupyRects[o + 2] = lw;
        markerOccupyRects[o + 3] = lh;
        markerOccupyCount++;
    }

    private boolean drawMarkerLabelIfFree(Graphics g, Font f, int sx, int textTopY, String name, int canvasW) {
        computeMarkerLabelBounds(f, name, sx, textTopY, canvasW, markerLabelBoundsScratch);
        int lx = markerLabelBoundsScratch[0];
        int ly = markerLabelBoundsScratch[1];
        int lw = markerLabelBoundsScratch[2];
        int lh = markerLabelBoundsScratch[3];
        if (!canPlaceMarkerLabel(lx, ly, lw, lh, MARKER_LABEL_RECT_GAP)) {
            return false;
        }
        drawMarkerLabel(g, f, sx, textTopY, name, canvasW);
        placeMarkerLabelRect(lx, ly, lw, lh);
        return true;
    }

    private boolean drawMarkerLabelCenteredIfFree(Graphics g, Font f, int sx, int textTopY, String name, int canvasW) {
        computeMarkerLabelCenteredBounds(f, name, sx, textTopY, canvasW, markerLabelBoundsScratch);
        int lx = markerLabelBoundsScratch[0];
        int ly = markerLabelBoundsScratch[1];
        int lw = markerLabelBoundsScratch[2];
        int lh = markerLabelBoundsScratch[3];
        if (!canPlaceMarkerLabel(lx, ly, lw, lh, MARKER_LABEL_RECT_GAP)) {
            return false;
        }
        drawMarkerLabelCenteredOnX(g, f, sx, textTopY, name, canvasW);
        placeMarkerLabelRect(lx, ly, lw, lh);
        return true;
    }

    /**
     * Outlined label centered on {@code sx} (no horizontal clamp to canvas edges). Trace repeater names stay
     * visually fixed under their badge while panning; generic {@link #drawMarkerLabel} slides text inward at borders.
     */
    private void drawMarkerLabelCenteredOnX(Graphics g, Font f, int sx, int textTopY, String name, int canvasW) {
        if (name == null || name.length() == 0) {
            name = "?";
        }
        int maxW = canvasW - 8;
        if (maxW < 40) {
            maxW = 40;
        }
        String label = truncateForWidth(name, f, maxW);
        int lw = f.stringWidth(label);
        int lx = sx - lw / 2;
        int ly = textTopY;
        g.setColor(COLOR_MARKER_TEXT_BORDER);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                g.drawString(label, lx + dx, ly + dy, Graphics.LEFT | Graphics.TOP);
            }
        }
        g.setColor(COLOR_MARKER_TEXT);
        g.drawString(label, lx, ly, Graphics.LEFT | Graphics.TOP);
    }

    private void markInteraction() {
        lastInteractionMs = System.currentTimeMillis();
        scheduleLabelRepaint();
    }

    private boolean shouldDrawMarkerLabels() {
        if (dragging) {
            return false;
        }
        if (z < MARKER_LABEL_MIN_Z) {
            return false;
        }
        long dt = System.currentTimeMillis() - lastInteractionMs;
        return dt >= (long) MARKER_LABEL_COOLDOWN_MS;
    }

    /**
     * Ensure labels re-appear after cooldown even when tiles/network are idle (no automatic repaint).
     */
    private void scheduleLabelRepaint() {
        final long due = lastInteractionMs + (long) MARKER_LABEL_COOLDOWN_MS + 20L;
        pendingLabelRepaintAtMs = due;
        new Thread(new Runnable() {
            public void run() {
                long wait = due - System.currentTimeMillis();
                if (wait > 0L) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (pendingLabelRepaintAtMs != due) {
                    return;
                }
                try {
                    app.getDisplay().callSerially(new Runnable() {
                        public void run() {
                            if (pendingLabelRepaintAtMs == due) {
                                repaint();
                            }
                        }
                    });
                } catch (Throwable t) {
                    // Ignore display lifecycle races; next input/tile repaint will refresh.
                }
            }
        }).start();
    }

    private static void drawSelfMarkerGlyph(Graphics g, int sx, int sy) {
        int tipY = sy - 9;
        int leftX = sx - 8;
        int rightX = sx + 8;
        int baseY = sy + 7;
        g.setColor(COLOR_SELF_FILL);
        g.fillTriangle(sx, tipY, leftX, baseY, rightX, baseY);
        g.setColor(COLOR_SELF_OUTLINE);
        g.drawLine(sx, tipY, leftX, baseY);
        g.drawLine(leftX, baseY, rightX, baseY);
        g.drawLine(rightX, baseY, sx, tipY);
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

    private static String formatDistance(int meters) {
        if (meters < 1000) {
            return meters + " m";
        }
        int km10 = (meters + 50) / 100;
        int km = km10 / 10;
        int d = km10 % 10;
        return km + "." + d + " km";
    }

    private static void drawDottedLine(Graphics g, int x1, int y1, int x2, int y2, int onPx, int offPx) {
        int dx = x2 - x1;
        if (dx < 0) {
            dx = -dx;
        }
        int dy = y2 - y1;
        if (dy < 0) {
            dy = -dy;
        }
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int period = onPx + offPx;
        if (period <= 0) {
            period = 1;
        }
        int step = 0;
        while (true) {
            if ((step % period) < onPx) {
                g.drawLine(x1, y1, x1, y1);
            }
            if (x1 == x2 && y1 == y2) {
                break;
            }
            int e2 = err << 1;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
            step++;
        }
    }

    private static void drawThickMapLine(Graphics g, int x1, int y1, int x2, int y2) {
        drawThickMapLine(g, x1, y1, x2, y2, COLOR_REPEATER);
    }

    private static void drawThickMapLine(Graphics g, int x1, int y1, int x2, int y2, int outerColor) {
        final int onPx = 5;
        final int offPx = 4;
        g.setColor(outerColor);
        drawDottedLine(g, x1 - 1, y1, x2 - 1, y2, onPx, offPx);
        drawDottedLine(g, x1 + 1, y1, x2 + 1, y2, onPx, offPx);
        drawDottedLine(g, x1, y1 - 1, x2, y2 - 1, onPx, offPx);
        drawDottedLine(g, x1, y1 + 1, x2, y2 + 1, onPx, offPx);
        g.setColor(0x000000);
        drawDottedLine(g, x1, y1, x2, y2, onPx, offPx);
    }

    /** Solid bold polyline for measure mode (~3 px stroke). */
    private static void drawBoldSolidLine(Graphics g, int x1, int y1, int x2, int y2, int rgb) {
        g.setColor(rgb);
        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x1 - 1, y1, x2 - 1, y2);
        g.drawLine(x1 + 1, y1, x2 + 1, y2);
        g.drawLine(x1, y1 - 1, x2, y2 - 1);
        g.drawLine(x1, y1 + 1, x2, y2 + 1);
    }

    /** Order-insensitive key so A->B and B->A share the same overlap bucket. */
    private static String traceSegPairKey(int ax, int ay, int bx, int by) {
        if (ax < bx || (ax == bx && ay <= by)) {
            return ax + "," + ay + "|" + bx + "," + by;
        }
        return bx + "," + by + "|" + ax + "," + ay;
    }

    /**
     * Draw repeated segment as a shallow two-leg curve (midpoint pushed along segment normal)
     * so overlapping back/forth hops remain distinguishable on small screens.
     */
    private static void drawTraceCurvedSegment(Graphics g, int ax, int ay, int bx, int by, int ordinal, int total, int outerColor) {
        // Use canonical A-B direction for the normal so A->B and B->A bend consistently on opposite sides.
        int cax = ax;
        int cay = ay;
        int cbx = bx;
        int cby = by;
        if (cax > cbx || (cax == cbx && cay > cby)) {
            cax = bx;
            cay = by;
            cbx = ax;
            cby = ay;
        }
        int dx = cbx - cax;
        int dy = cby - cay;
        int norm = dx;
        if (norm < 0) {
            norm = -norm;
        }
        int ady = dy;
        if (ady < 0) {
            ady = -ady;
        }
        if (ady > norm) {
            norm = ady;
        }
        if (norm < 1) {
            drawThickMapLine(g, ax, ay, bx, by, outerColor);
            return;
        }
        // Center ordinals around zero: total=2 => {-1,+1}, total=3 => {-1,0,+1}.
        int slot = ordinal * 2 - (total - 1);
        int bendPx = slot * 5;
        int mx = (ax + bx) / 2 + ((-dy) * bendPx) / norm;
        int my = (ay + by) / 2 + (dx * bendPx) / norm;
        drawThickMapLine(g, ax, ay, mx, my, outerColor);
        drawThickMapLine(g, mx, my, bx, by, outerColor);
    }

    /**
     * Append repeater → node segment whenever the trace has at least one hop (including single repeater) so
     * inbound RX is always visible back to home, not only on multi-hop returns.
     */
    private void maybeAppendTraceClosingReturnToSelf(Vector pts, Vector pathIndices, byte[] pathBytes) {
        if (pathBytes == null || pathBytes.length < 1) {
            return;
        }
        if (pts.size() < 2 || pathIndices.size() != pts.size()) {
            return;
        }
        if (((Integer) pathIndices.elementAt(0)).intValue() != 0) {
            return;
        }
        int nLa = app.getNodeAdvLatE6();
        int nLo = app.getNodeAdvLonE6();
        if (nLa == Integer.MIN_VALUE || nLo == Integer.MIN_VALUE) {
            return;
        }
        int[] first = (int[]) pts.elementAt(0);
        pts.addElement(new int[]{first[0], first[1]});
        pathIndices.addElement(new Integer(pathBytes.length + 1));
    }

    /**
     * RX (red) when this hop ends at a repeater already seen earlier in the path (any backtrack), or on the
     * synthetic leg back to the node ({@code pathDestIndex > pathBytes.length}). Otherwise TX (green).
     * Path index 1..N matches {@code pathBytes[0]..pathBytes[N-1]}.
     */
    private static int traceLineColorForHop(byte[] pathBytes, int pathDestIndex) {
        if (pathBytes == null) {
            return COLOR_TRACE_TX;
        }
        if (pathDestIndex > pathBytes.length) {
            return COLOR_TRACE_RX;
        }
        if (pathDestIndex < 1) {
            return COLOR_TRACE_TX;
        }
        byte dest = pathBytes[pathDestIndex - 1];
        for (int j = 0; j < pathDestIndex - 1; j++) {
            if ((pathBytes[j] & 0xFF) == (dest & 0xFF)) {
                return COLOR_TRACE_RX;
            }
        }
        return COLOR_TRACE_TX;
    }

    private void drawTraceLineLegend(Graphics g, Font f, int w) {
        int padX = 4;
        int padY = 2;
        int lineGap = 1;
        int lh = f.getHeight();
        String tx = "TX";
        String rx = "RX";
        int tw = f.stringWidth(tx);
        int rw = f.stringWidth(rx);
        if (rw > tw) {
            tw = rw;
        }
        int sampleW = 16;
        int bw = (sampleW + 5 + tw) + padX * 2;
        int bh = padY * 2 + lh * 2 + lineGap;
        int ox = w - bw - 3;
        if (ox < 3) {
            ox = 3;
        }
        int oy = 3;
        int sx = ox + padX;
        int txX = sx + sampleW + 5;
        int y1 = oy + padY + lh / 2 + 1;
        int y2 = oy + padY + lh + lineGap + lh / 2 + 1;

        g.setColor(0xE8EEF5);
        g.fillRect(ox, oy, bw, bh);
        g.setColor(0x888888);
        g.drawRect(ox, oy, bw - 1, bh - 1);
        drawThickMapLine(g, sx, y1, sx + sampleW, y1, COLOR_TRACE_TX);
        drawThickMapLine(g, sx, y2, sx + sampleW, y2, COLOR_TRACE_RX);
        g.setColor(0x222222);
        g.drawString(tx, txX, oy + padY, Graphics.LEFT | Graphics.TOP);
        g.drawString(rx, txX, oy + padY + lh + lineGap, Graphics.LEFT | Graphics.TOP);
    }

    private static int traceLineLegendHeight(Font f) {
        int padY = 2;
        int lineGap = 1;
        return padY * 2 + f.getHeight() * 2 + lineGap;
    }

    private void syncMeasureMenuCommand() {
        boolean wantOn = mapInteractMode == MAP_MODE_MEASURE;
        if (wantOn == measureMenuShowsOn) {
            return;
        }
        if (wantOn) {
            removeCommand(cmdMeasureOff);
            addCommand(cmdMeasureOn);
            measureMenuShowsOn = true;
        } else {
            removeCommand(cmdMeasureOn);
            addCommand(cmdMeasureOff);
            measureMenuShowsOn = false;
        }
    }

    private void appendMeasurePointLatLon(int latE6, int lonE6) {
        if (latE6 == Integer.MIN_VALUE || lonE6 == Integer.MIN_VALUE) {
            return;
        }
        measurePoints.addElement(new int[]{latE6, lonE6});
    }

    private void addMeasurePointAtCrosshair() {
        int cw = getWidth();
        int ch = getHeight();
        if (cw <= 0) {
            cw = 240;
        }
        if (ch <= 0) {
            ch = 320;
        }
        screenToLatLonE6(cw / 2, ch / 2, measureLatLonScratch);
        appendMeasurePointLatLon(measureLatLonScratch[0], measureLatLonScratch[1]);
    }

    private static int measurePathSumMeters(Vector pts) {
        int n = pts.size();
        if (n < 2) {
            return 0;
        }
        int sum = 0;
        for (int i = 1; i < n; i++) {
            int[] a = (int[]) pts.elementAt(i - 1);
            int[] b = (int[]) pts.elementAt(i);
            sum += approxDistanceMeters(a[0], a[1], b[0], b[1]);
        }
        return sum;
    }

    /** Compact total for measure HUD (e.g. {@code 150m}, {@code 1.2km}). */
    private static String formatMeasureTotalOnly(int meters) {
        if (meters < 0) {
            meters = 0;
        }
        if (meters < 1000) {
            return Integer.toString(meters) + "m";
        }
        int km10 = (meters + 50) / 100;
        int km = km10 / 10;
        int d = km10 % 10;
        return Integer.toString(km) + "." + Integer.toString(d) + "km";
    }

    /** Top-right: TX/RX-style box with path total only. */
    private void drawMeasureSummaryBox(Graphics g, Font f, int canvasW, int topY) {
        if (mapInteractMode != MAP_MODE_MEASURE) {
            return;
        }
        int sumM = measurePathSumMeters(measurePoints);
        String text = formatMeasureTotalOnly(sumM);
        int padX = 4;
        int padY = 2;
        int lh = f.getHeight();
        int sampleW = 16;
        int tw = f.stringWidth(text);
        int bw = (sampleW + 5 + tw) + padX * 2;
        int bh = padY * 2 + lh;
        int ox = canvasW - bw - 3;
        if (ox < 3) {
            ox = 3;
        }
        int oy = topY;
        int sx = ox + padX;
        int txX = sx + sampleW + 5;
        int y = oy + padY;

        g.setColor(0xE8EEF5);
        g.fillRect(ox, oy, bw, bh);
        g.setColor(0x888888);
        g.drawRect(ox, oy, bw - 1, bh - 1);

        int yMid = y + lh / 2 + 1;
        drawBoldSolidLine(g, sx, yMid, sx + sampleW, yMid, COLOR_MEASURE_LINE);
        g.setColor(0x222222);
        g.drawString(text, txX, y, Graphics.LEFT | Graphics.TOP);
    }

    /**
     * Collects screen points for the trace polyline and parallel logical path indices:
     * 0 = you, 1..N = repeater position along {@link #traceForwardPath} (N = path length).
     */
    private void collectTraceScreenPoints(Vector outPts, Vector outPathIndices) {
        outPts.removeAllElements();
        outPathIndices.removeAllElements();
        int nLa = app.getNodeAdvLatE6();
        int nLo = app.getNodeAdvLonE6();
        if (nLa != Integer.MIN_VALUE && nLo != Integer.MIN_VALUE) {
            MapMercator.latLonToPixel(nLa / 1000000.0, nLo / 1000000.0, z, tmpMercatorXY);
            int sx = (int) (tmpMercatorXY[0] - scrollX);
            int sy = (int) (tmpMercatorXY[1] - scrollY);
            outPts.addElement(new int[]{sx, sy});
            outPathIndices.addElement(new Integer(0));
        }
        if (traceForwardPath == null) {
            return;
        }
        for (int i = 0; i < traceForwardPath.length; i++) {
            int cidx = app.getRepeaterContactIndexForPathByte(traceForwardPath[i]);
            if (cidx < 0) {
                continue;
            }
            int la = app.getContactAdvLatE6(cidx);
            int lo = app.getContactAdvLonE6(cidx);
            if (la == Integer.MIN_VALUE || lo == Integer.MIN_VALUE) {
                continue;
            }
            MapMercator.latLonToPixel(la / 1000000.0, lo / 1000000.0, z, tmpMercatorXY);
            int sx = (int) (tmpMercatorXY[0] - scrollX);
            int sy = (int) (tmpMercatorXY[1] - scrollY);
            outPts.addElement(new int[]{sx, sy});
            outPathIndices.addElement(new Integer(i + 1));
        }
    }

    private static int traceScreenGroupKey(int sx, int sy) {
        int qx = sx / 6;
        int qy = sy / 6;
        return qx * 0xBFFF + qy;
    }

    /** Sorts point indices so their {@code pathIndices[pt]} are ascending. */
    private static void sortTracePtIndicesByPath(Vector ptIndices, Vector pathIndices) {
        for (int a = 0; a < ptIndices.size(); a++) {
            for (int b = a + 1; b < ptIndices.size(); b++) {
                int pta = ((Integer) ptIndices.elementAt(a)).intValue();
                int ptb = ((Integer) ptIndices.elementAt(b)).intValue();
                int ipa = ((Integer) pathIndices.elementAt(pta)).intValue();
                int ipb = ((Integer) pathIndices.elementAt(ptb)).intValue();
                if (ipb < ipa) {
                    ptIndices.setElementAt(new Integer(ptb), a);
                    ptIndices.setElementAt(new Integer(pta), b);
                }
            }
        }
    }

    /** @return null to skip drawing (start node) */
    private static String traceHopBadgeLabel(int pathIndex) {
        if (pathIndex == 0) {
            return null;
        }
        return "(" + pathIndex + ")";
    }

    private static String composeTraceBadgeLabel(Vector sortedPtIndices, Vector pathIndices) {
        if (sortedPtIndices.size() == 1) {
            int pt = ((Integer) sortedPtIndices.elementAt(0)).intValue();
            int pidx = ((Integer) pathIndices.elementAt(pt)).intValue();
            return traceHopBadgeLabel(pidx);
        }
        StringBuffer sb = new StringBuffer("(");
        for (int i = 0; i < sortedPtIndices.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            int pt = ((Integer) sortedPtIndices.elementAt(i)).intValue();
            sb.append(((Integer) pathIndices.elementAt(pt)).intValue());
        }
        sb.append(')');
        return sb.toString();
    }

    private static int traceNumberBadgeRadius(Font f, String num) {
        int tw = f.stringWidth(num);
        int r = tw / 2 + 6;
        if (r < 11) {
            r = 11;
        }
        if (r > 26) {
            r = 26;
        }
        return r;
    }

    private void drawTraceNumberBadge(Graphics g, Font f, int sx, int sy, String num, boolean isSelf) {
        int tw = f.stringWidth(num);
        int r = traceNumberBadgeRadius(f, num);
        g.setColor(isSelf ? COLOR_SELF_FILL : COLOR_REPEATER);
        g.fillArc(sx - r, sy - r, 2 * r, 2 * r, 0, 360);
        g.setColor(0x000000);
        g.drawArc(sx - r, sy - r, 2 * r, 2 * r, 0, 360);
        g.setColor(0xFFFFFF);
        g.drawString(num, sx - tw / 2, sy - f.getHeight() / 2, Graphics.LEFT | Graphics.TOP);
    }

    /**
     * Distinct repeater contact names for this screen group ({@code pathIndex > 0}), first-seen path order;
     * same contact on multiple hops appears once.
     */
    private String buildTraceRepeaterNamesLine(Vector sortedPtIndices, Vector pathIndices) {
        if (traceForwardPath == null) {
            return null;
        }
        Hashtable seenCidx = new Hashtable();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < sortedPtIndices.size(); i++) {
            int pt = ((Integer) sortedPtIndices.elementAt(i)).intValue();
            int pidx = ((Integer) pathIndices.elementAt(pt)).intValue();
            if (pidx < 1 || pidx > traceForwardPath.length) {
                continue;
            }
            int cidx = app.getRepeaterContactIndexForPathByte(traceForwardPath[pidx - 1]);
            if (cidx < 0) {
                continue;
            }
            Integer ck = new Integer(cidx);
            if (seenCidx.get(ck) != null) {
                continue;
            }
            seenCidx.put(ck, ck);
            String nm = app.getContactName(cidx);
            if (nm == null || nm.length() == 0) {
                nm = "?";
            }
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(nm);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void drawTracePathOverlay(Graphics g, Font f, int w, int h) {
        if (!hasTraceOverlay()) {
            return;
        }
        Vector pts = new Vector();
        Vector pathIndices = new Vector();
        collectTraceScreenPoints(pts, pathIndices);
        maybeAppendTraceClosingReturnToSelf(pts, pathIndices, traceForwardPath);
        boolean enough = pts.size() >= 2;
        if (enough) {
            Hashtable segTotals = new Hashtable();
            for (int i = 0; i < pts.size() - 1; i++) {
                int[] a = (int[]) pts.elementAt(i);
                int[] b = (int[]) pts.elementAt(i + 1);
                String key = traceSegPairKey(a[0], a[1], b[0], b[1]);
                Integer v = (Integer) segTotals.get(key);
                int c = (v != null) ? v.intValue() : 0;
                segTotals.put(key, new Integer(c + 1));
            }
            Hashtable segSeen = new Hashtable();
            for (int i = 0; i < pts.size() - 1; i++) {
                int[] a = (int[]) pts.elementAt(i);
                int[] b = (int[]) pts.elementAt(i + 1);
                String key = traceSegPairKey(a[0], a[1], b[0], b[1]);
                int total = ((Integer) segTotals.get(key)).intValue();
                Integer seenV = (Integer) segSeen.get(key);
                int seen = (seenV != null) ? seenV.intValue() : 0;
                int pathJb = ((Integer) pathIndices.elementAt(i + 1)).intValue();
                int lineColor = traceLineColorForHop(traceForwardPath, pathJb);
                if (total > 1) {
                    drawTraceCurvedSegment(g, a[0], a[1], b[0], b[1], seen, total, lineColor);
                } else {
                    drawThickMapLine(g, a[0], a[1], b[0], b[1], lineColor);
                }
                segSeen.put(key, new Integer(seen + 1));
            }
            g.setFont(f);
            boolean drawTraceRepLabels = shouldDrawMarkerLabels();
            Hashtable groups = new Hashtable();
            for (int i = 0; i < pts.size(); i++) {
                int pidx = ((Integer) pathIndices.elementAt(i)).intValue();
                if (pidx > traceForwardPath.length) {
                    continue;
                }
                int[] p = (int[]) pts.elementAt(i);
                Integer key = new Integer(traceScreenGroupKey(p[0], p[1]));
                Vector list = (Vector) groups.get(key);
                if (list == null) {
                    list = new Vector();
                    groups.put(key, list);
                }
                list.addElement(new Integer(i));
            }
            for (Enumeration ge = groups.elements(); ge.hasMoreElements();) {
                Vector idxList = (Vector) ge.nextElement();
                sortTracePtIndicesByPath(idxList, pathIndices);
                int leadPt = ((Integer) idxList.elementAt(0)).intValue();
                int[] p = (int[]) pts.elementAt(leadPt);
                int leadPath = ((Integer) pathIndices.elementAt(leadPt)).intValue();
                String label = composeTraceBadgeLabel(idxList, pathIndices);
                if (label == null) {
                    continue;
                }
                int br = traceNumberBadgeRadius(f, label);
                drawTraceNumberBadge(g, f, p[0], p[1], label, leadPath == 0);
                if (drawTraceRepLabels) {
                    String repNames = buildTraceRepeaterNamesLine(idxList, pathIndices);
                    if (repNames != null) {
                        drawMarkerLabelCenteredIfFree(g, f, p[0], p[1] + br + 3, repNames, w);
                    }
                }
            }
        }
    }

    private void drawTracePickPathOverlay(Graphics g, Font f, int w, int h) {
        if (!tracePickMode || tracePickPathModel.size() <= 0) {
            return;
        }
        Vector pts = new Vector();
        Vector pathIndices = new Vector();
        byte[] pickPath = tracePickPathModel.toByteArray();
        int nLa = app.getNodeAdvLatE6();
        int nLo = app.getNodeAdvLonE6();
        if (nLa != Integer.MIN_VALUE && nLo != Integer.MIN_VALUE) {
            MapMercator.latLonToPixel(nLa / 1000000.0, nLo / 1000000.0, z, tmpMercatorXY);
            pts.addElement(new int[]{(int) (tmpMercatorXY[0] - scrollX), (int) (tmpMercatorXY[1] - scrollY)});
            pathIndices.addElement(new Integer(0));
        }
        for (int i = 0; i < tracePickPathModel.size(); i++) {
            int cidx = app.getRepeaterContactIndexForPathByte(tracePickPathModel.get(i));
            if (cidx < 0) {
                continue;
            }
            int la = app.getContactAdvLatE6(cidx);
            int lo = app.getContactAdvLonE6(cidx);
            if (la == Integer.MIN_VALUE || lo == Integer.MIN_VALUE) {
                continue;
            }
            MapMercator.latLonToPixel(la / 1000000.0, lo / 1000000.0, z, tmpMercatorXY);
            pts.addElement(new int[]{(int) (tmpMercatorXY[0] - scrollX), (int) (tmpMercatorXY[1] - scrollY)});
            pathIndices.addElement(new Integer(i + 1));
        }
        maybeAppendTraceClosingReturnToSelf(pts, pathIndices, pickPath);
        if (pts.size() < 2) {
            return;
        }
        Hashtable segTotals = new Hashtable();
        for (int i = 0; i < pts.size() - 1; i++) {
            int[] a = (int[]) pts.elementAt(i);
            int[] b = (int[]) pts.elementAt(i + 1);
            String key = traceSegPairKey(a[0], a[1], b[0], b[1]);
            Integer v = (Integer) segTotals.get(key);
            int c = (v != null) ? v.intValue() : 0;
            segTotals.put(key, new Integer(c + 1));
        }
        Hashtable segSeen = new Hashtable();
        for (int i = 0; i < pts.size() - 1; i++) {
            int[] a = (int[]) pts.elementAt(i);
            int[] b = (int[]) pts.elementAt(i + 1);
            String key = traceSegPairKey(a[0], a[1], b[0], b[1]);
            int total = ((Integer) segTotals.get(key)).intValue();
            Integer seenV = (Integer) segSeen.get(key);
            int seen = (seenV != null) ? seenV.intValue() : 0;
            int pathJb = ((Integer) pathIndices.elementAt(i + 1)).intValue();
            int lineColor = traceLineColorForHop(pickPath, pathJb);
            if (total > 1) {
                drawTraceCurvedSegment(g, a[0], a[1], b[0], b[1], seen, total, lineColor);
            } else {
                drawThickMapLine(g, a[0], a[1], b[0], b[1], lineColor);
            }
            segSeen.put(key, new Integer(seen + 1));
        }
        Hashtable groups = new Hashtable();
        for (int i = 0; i < pts.size(); i++) {
            int pidx = ((Integer) pathIndices.elementAt(i)).intValue();
            if (pidx > pickPath.length) {
                continue;
            }
            int[] p = (int[]) pts.elementAt(i);
            Integer key = new Integer(traceScreenGroupKey(p[0], p[1]));
            Vector list = (Vector) groups.get(key);
            if (list == null) {
                list = new Vector();
                groups.put(key, list);
            }
            list.addElement(new Integer(i));
        }
        for (Enumeration ge = groups.elements(); ge.hasMoreElements();) {
            Vector idxList = (Vector) ge.nextElement();
            sortTracePtIndicesByPath(idxList, pathIndices);
            int leadPt = ((Integer) idxList.elementAt(0)).intValue();
            int[] p = (int[]) pts.elementAt(leadPt);
            int leadPath = ((Integer) pathIndices.elementAt(leadPt)).intValue();
            String label = composeTraceBadgeLabel(idxList, pathIndices);
            if (label == null) {
                continue;
            }
            drawTraceNumberBadge(g, f, p[0], p[1], label, leadPath == 0);
        }
    }

    private void drawFocusDistanceOverlay(Graphics g, Font f, int w, int h) {
        if (focusContactIdx < 0) {
            return;
        }
        int cLat = app.getContactAdvLatE6(focusContactIdx);
        int cLon = app.getContactAdvLonE6(focusContactIdx);
        int nLat = app.getNodeAdvLatE6();
        int nLon = app.getNodeAdvLonE6();
        if (cLat == Integer.MIN_VALUE || cLon == Integer.MIN_VALUE
                || nLat == Integer.MIN_VALUE || nLon == Integer.MIN_VALUE) {
            return;
        }

        int[] node = new int[2];
        int[] dst = new int[2];
        projectLatLonE6ToScreen(nLat, nLon, w, h, node);
        projectLatLonE6ToScreen(cLat, cLon, w, h, dst);
        int x1 = clamp(node[0], 0, w - 1);
        int y1 = clamp(node[1], 0, h - 1);
        int x2 = clamp(dst[0], 0, w - 1);
        int y2 = clamp(dst[1], 0, h - 1);
        if (x1 != x2 || y1 != y2) {
            // Bold line with contrast outline: black border + white center.
            g.setColor(0x000000);
            g.drawLine(x1 - 1, y1, x2 - 1, y2);
            g.drawLine(x1 + 1, y1, x2 + 1, y2);
            g.drawLine(x1, y1 - 1, x2, y2 - 1);
            g.drawLine(x1, y1 + 1, x2, y2 + 1);
            g.setColor(0xFFFFFF);
            g.drawLine(x1, y1, x2, y2);
        }

        String name = (focusContactName != null && focusContactName.length() > 0)
                ? focusContactName
                : app.getContactName(focusContactIdx);
        if (name == null || name.length() == 0) {
            name = "Target";
        }
        name = truncateForWidth(name, f, w / 2);
        String dist = formatDistance(approxDistanceMeters(nLat, nLon, cLat, cLon));
        int padX = 4;
        int padY = 2;
        int lineGap = 1;
        int lineH = f.getHeight();
        int innerW = f.stringWidth(name);
        int dW = f.stringWidth(dist);
        if (dW > innerW) {
            innerW = dW;
        }
        int tw = innerW + padX * 2;
        int th = lineH * 2 + lineGap + padY * 2;
        int ox = w - tw - 3;
        if (ox < 3) {
            ox = 3;
        }
        int oy = 3;
        g.setColor(0xEEF3FA);
        g.fillRect(ox, oy, tw, th);
        g.setColor(0x888888);
        g.drawRect(ox, oy, tw - 1, th - 1);
        g.setColor(0x222222);
        g.drawString(name, ox + padX, oy + padY, Graphics.LEFT | Graphics.TOP);
        g.drawString(dist, ox + padX, oy + padY + lineH + lineGap, Graphics.LEFT | Graphics.TOP);
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    /**
     * Sort key for map labels: repeaters first, then by distance to viewport center (see int[] in
     * {@link #markerCandScratch}: index 1 = distSq).
     */
    private int compareMeshMarkerCandidates(int[] cb, int[] ca) {
        boolean rb = app.getContactAdvertType(cb[0]) == ProtocolConstants.ADV_TYPE_REPEATER;
        boolean ra = app.getContactAdvertType(ca[0]) == ProtocolConstants.ADV_TYPE_REPEATER;
        if (rb && !ra) {
            return -1;
        }
        if (ra && !rb) {
            return 1;
        }
        int db = cb[1];
        int da = ca[1];
        if (db < da) {
            return -1;
        }
        if (db > da) {
            return 1;
        }
        return 0;
    }

    /**
     * Contacts/repeaters with advert coordinates, then local node on top.
     * Labels: repeaters before other types, then nearest-to-center; skip on overlap.
     */
    private void drawMeshMarkers(Graphics g, Font f, int w, int h) {
        clearMarkerLabelOccupancy();
        boolean drawLabels = shouldDrawMarkerLabels() && !hasTraceOverlay();
        int cx = w / 2;
        int cy = h / 2;
        int n = app.getContactCount();
        markerCandScratch.removeAllElements();
        for (int i = 0; i < n; i++) {
            int la = app.getContactAdvLatE6(i);
            int lo = app.getContactAdvLonE6(i);
            if (!projectLatLonE6ToScreen(la, lo, w, h, tmpMarkerXY)) {
                continue;
            }
            int sx = tmpMarkerXY[0];
            int sy = tmpMarkerXY[1];
            int type = app.getContactAdvertType(i);
            int r = (type == ProtocolConstants.ADV_TYPE_REPEATER) ? 5 : 4;
            int dx = sx - cx;
            int dy = sy - cy;
            markerCandScratch.addElement(new int[]{i, dx * dx + dy * dy, sx, sy, r});
        }
        int sz = markerCandScratch.size();
        for (int a = 0; a < sz; a++) {
            for (int b = a + 1; b < sz; b++) {
                int[] pa = (int[]) markerCandScratch.elementAt(a);
                int[] pb = (int[]) markerCandScratch.elementAt(b);
                if (compareMeshMarkerCandidates(pb, pa) < 0) {
                    markerCandScratch.setElementAt(pb, a);
                    markerCandScratch.setElementAt(pa, b);
                }
            }
        }
        for (int k = sz - 1; k >= 0; k--) {
            int[] c = (int[]) markerCandScratch.elementAt(k);
            drawMarkerDot(g, c[2], c[3], c[4], markerColorForType(app.getContactAdvertType(c[0])));
        }
        int nLa = app.getNodeAdvLatE6();
        int nLo = app.getNodeAdvLonE6();
        boolean selfOnScreen = projectLatLonE6ToScreen(nLa, nLo, w, h, tmpMarkerXY);
        if (selfOnScreen) {
            drawSelfMarkerGlyph(g, tmpMarkerXY[0], tmpMarkerXY[1]);
        }
        if (drawLabels) {
            if (selfOnScreen) {
                int sx = tmpMarkerXY[0];
                int sy = tmpMarkerXY[1];
                int baseY = sy + 7;
                String nodeName = app.getNodeName();
                if (nodeName == null || nodeName.length() == 0) {
                    nodeName = "You";
                }
                drawMarkerLabelIfFree(g, f, sx, baseY + 3, nodeName, w);
            }
            for (int k = 0; k < sz; k++) {
                int[] c = (int[]) markerCandScratch.elementAt(k);
                drawMarkerLabelIfFree(g, f, c[2], c[3] + c[4] + 3, app.getContactName(c[0]), w);
            }
        }
    }

    private void drawMeasureOverlay(Graphics g, Font f, int w, int h) {
        if (mapInteractMode != MAP_MODE_MEASURE || measurePoints.size() < 1) {
            return;
        }
        int n = measurePoints.size();
        int pr = 3;
        g.setColor(COLOR_MEASURE_LINE);
        for (int i = 0; i < n; i++) {
            int[] p = (int[]) measurePoints.elementAt(i);
            projectLatLonE6ToScreenRaw(p[0], p[1], tmpMarkerXY);
            int sx = tmpMarkerXY[0];
            int sy = tmpMarkerXY[1];
            if (i > 0) {
                int[] q = (int[]) measurePoints.elementAt(i - 1);
                projectLatLonE6ToScreenRaw(q[0], q[1], tmpMarkerXY);
                int ax = tmpMarkerXY[0];
                int ay = tmpMarkerXY[1];
                drawBoldSolidLine(g, ax, ay, sx, sy, COLOR_MEASURE_LINE);
                int segM = approxDistanceMeters(q[0], q[1], p[0], p[1]);
                String label = formatDistance(segM);
                int mx = (ax + sx) / 2;
                int my = (ay + sy) / 2;
                int lw = f.stringWidth(label);
                int lh = f.getHeight();
                int lx = mx - lw / 2;
                int ly = my - lh / 2;
                g.setColor(0x000000);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        g.drawString(label, lx + dx, ly + dy, Graphics.LEFT | Graphics.TOP);
                    }
                }
                g.setColor(0xFFFFFF);
                g.drawString(label, lx, ly, Graphics.LEFT | Graphics.TOP);
                g.setColor(COLOR_MEASURE_LINE);
            }
            g.setColor(COLOR_MEASURE_VERTEX);
            g.fillArc(sx - pr, sy - pr, pr * 2, pr * 2, 0, 360);
            g.setColor(0x222222);
            g.drawArc(sx - pr, sy - pr, pr * 2, pr * 2, 0, 360);
            g.setColor(COLOR_MEASURE_LINE);
        }
    }

    /** Map source label and zoom (drawn after tiles and markers). */
    private void drawZoomOverlay(Graphics g, Font f, int canvasW) {
        String mapLine = shortMapLabel(template);
        String zoomLine = "Zoom: " + z;
        int padX = 4;
        int padY = 2;
        int lineGap = 1;
        int lineH = f.getHeight();
        int innerW = f.stringWidth(zoomLine);
        int w2 = f.stringWidth(mapLine);
        if (w2 > innerW) {
            innerW = w2;
        }
        int maxLineW = canvasW - 10 - padX * 2;
        if (maxLineW < 40) {
            maxLineW = 40;
        }
        if (innerW > maxLineW) {
            mapLine = truncateForWidth(mapLine, f, maxLineW);
            innerW = f.stringWidth(mapLine);
            int zw = f.stringWidth(zoomLine);
            if (zw > innerW) {
                innerW = zw;
            }
        }
        int tw = innerW + padX * 2;
        int th = padY * 2 + lineH * 2 + lineGap;
        int ox = 3;
        int oy = 3;
        g.setColor(0xE8EEF5);
        g.fillRect(ox, oy, tw, th);
        g.setColor(0x888888);
        g.drawRect(ox, oy, tw - 1, th - 1);
        g.setColor(0x222222);
        g.drawString(mapLine, ox + padX, oy + padY, Graphics.LEFT | Graphics.TOP);
        g.drawString(zoomLine, ox + padX, oy + padY + lineH + lineGap, Graphics.LEFT | Graphics.TOP);
    }

    /** Bottom-center metric scale bar (hidden on trace map / trace pick). Web Mercator at viewport center lat. */
    private void drawScaleBar(Graphics g, Font f, int w, int h) {
        if (hasTraceOverlay() || tracePickMode) {
            return;
        }
        double worldY = (double) scrollY + (double) (h / 2);
        double lat = MapMercator.worldPixelYToLatDeg(worldY, z);
        if (lat > 85.0) {
            lat = 85.0;
        } else if (lat < -85.0) {
            lat = -85.0;
        }
        double mpp = MapMercator.metersPerPixelAtLat(lat, z);
        if (mpp <= 1.0e-9 || mpp != mpp) {
            return;
        }
        int maxBarPx = (w * 4) / 5 - 20;
        if (maxBarPx > 248) {
            maxBarPx = 248;
        }
        if (maxBarPx < 52) {
            maxBarPx = 52;
        }
        int chosenM = SCALE_BAR_METERS[0];
        int barPx = 1;
        for (int i = 0; i < SCALE_BAR_METERS.length; i++) {
            int m = SCALE_BAR_METERS[i];
            int px = (int) (m / mpp + 0.5);
            if (px <= maxBarPx && px >= 24) {
                chosenM = m;
                barPx = px;
            }
        }
        if (barPx < 16) {
            return;
        }
        int padX = 6;
        int padY = 1;
        int lineGap = 1;
        int barH = 5;
        int tickH = 9;
        int fh = f.getHeight();

        int[] labelPlan = SCALE_LABEL_PLANS[2];
        int nLabelPlan = 2;
        boolean labelCompact = true;
        boolean labelEndAnchors = false;
        boolean foundPlan = false;
        for (int pi = 0; pi < SCALE_LABEL_PLANS.length && !foundPlan; pi++) {
            int[] plan = SCALE_LABEL_PLANS[pi];
            int n = plan.length;
            for (int c = 0; c < 2; c++) {
                boolean compact = (c != 0);
                if (scaleLabelPlanFits(barPx, plan, n, compact, f, chosenM)) {
                    labelPlan = plan;
                    nLabelPlan = n;
                    labelCompact = compact;
                    foundPlan = true;
                    break;
                }
            }
        }
        if (!foundPlan) {
            labelEndAnchors = true;
            labelPlan = SCALE_LABEL_PLANS[2];
            nLabelPlan = 2;
            labelCompact = true;
        }

        int leftExt = 0;
        int rightExt = 0;
        if (labelEndAnchors) {
            String s0 = formatScaleSegmentLabelShort(0);
            String s1 = formatScaleSegmentLabelShort(chosenM);
            leftExt = f.stringWidth(s0) + 2;
            rightExt = f.stringWidth(s1) + 2;
        } else {
            for (int k = 0; k < nLabelPlan; k++) {
                int ti = labelPlan[k];
                int segM = (chosenM * ti) / 4;
                String ts = labelCompact ? formatScaleSegmentLabelShort(segM) : formatScaleSegmentLabel(segM);
                int sw = f.stringWidth(ts);
                int half = (sw + 1) / 2;
                int tx = (barPx * ti) / 4;
                int needL = half - tx;
                if (needL > leftExt) {
                    leftExt = needL;
                }
                int needR = half - (barPx - tx);
                if (needR > rightExt) {
                    rightExt = needR;
                }
            }
            if (leftExt < 0) {
                leftExt = 0;
            }
            if (rightExt < 0) {
                rightExt = 0;
            }
        }
        int symPad = leftExt > rightExt ? leftExt : rightExt;
        leftExt = symPad;
        rightExt = symPad;
        int innerW = barPx + leftExt + rightExt;
        int boxW = innerW + padX * 2;
        int vInset = 2;
        int boxH = padY * 2 + vInset + tickH + lineGap + fh + vInset;
        int margin = 3;
        int bottomGap = 1;
        int ox = (w - boxW) / 2;
        if (ox < margin) {
            ox = margin;
        }
        int oy = h - boxH - bottomGap;
        if (oy < margin) {
            oy = margin;
        }
        if (oy + boxH > h - bottomGap) {
            oy = h - bottomGap - boxH;
            if (oy < margin) {
                oy = margin;
            }
        }
        int barLeft = ox + padX + leftExt;
        int barTop = oy + padY + vInset + (tickH - barH) / 2;
        if (barTop < oy + padY + vInset) {
            barTop = oy + padY + vInset;
        }
        int labelY = oy + padY + vInset + tickH + lineGap;
        int bottomStem = tickH - barH;
        if (bottomStem < 1) {
            bottomStem = 1;
        }
        int topStem = bottomStem / 2;
        if (topStem < 1) {
            topStem = 1;
        }
        int tickY0 = barTop - topStem;
        int tickY1 = barTop + tickH;

        g.setColor(0x222222);
        g.fillRect(barLeft, barTop, barPx, barH);
        for (int ti = 0; ti < 5; ti++) {
            int tx = barLeft + (barPx * ti) / 4;
            g.drawLine(tx, tickY0, tx, tickY1);
        }
        if (labelEndAnchors) {
            String s0 = formatScaleSegmentLabelShort(0);
            String s1 = formatScaleSegmentLabelShort(chosenM);
            g.drawString(s0, barLeft - 2, labelY, Graphics.RIGHT | Graphics.TOP);
            g.drawString(s1, barLeft + barPx + 2, labelY, Graphics.LEFT | Graphics.TOP);
        } else {
            for (int k = 0; k < nLabelPlan; k++) {
                int ti = labelPlan[k];
                int segM = (chosenM * ti) / 4;
                String ts = labelCompact ? formatScaleSegmentLabelShort(segM) : formatScaleSegmentLabel(segM);
                int tx = barLeft + (barPx * ti) / 4;
                g.drawString(ts, tx, labelY, Graphics.HCENTER | Graphics.TOP);
            }
        }
    }

    private static boolean scaleLabelPlanFits(int barPx, int[] plan, int nPlan, boolean compact, Font f, int chosenM) {
        for (int k = 0; k < nPlan - 1; k++) {
            int i = plan[k];
            int j = plan[k + 1];
            int dist = (barPx * (j - i)) / 4;
            int segMi = (chosenM * i) / 4;
            int segMj = (chosenM * j) / 4;
            String si = compact ? formatScaleSegmentLabelShort(segMi) : formatScaleSegmentLabel(segMi);
            String sj = compact ? formatScaleSegmentLabelShort(segMj) : formatScaleSegmentLabel(segMj);
            int need = (f.stringWidth(si) + 1) / 2 + (f.stringWidth(sj) + 1) / 2 + SCALE_LABEL_GAP;
            if (dist < need) {
                return false;
            }
        }
        return true;
    }

    /** Shorter scale text when space is tight (no space before m/km). */
    private static String formatScaleSegmentLabelShort(int meters) {
        if (meters <= 0) {
            return "0";
        }
        if (meters < 1000) {
            return Integer.toString(meters) + "m";
        }
        if ((meters % 1000) == 0) {
            return Integer.toString(meters / 1000) + "km";
        }
        if ((meters % 100) == 0) {
            return Integer.toString(meters / 1000) + "." + Integer.toString((meters % 1000) / 100) + "km";
        }
        return Integer.toString(meters) + "m";
    }

    /** Label for one tick on the scale bar (0, quarter, half, …, full). */
    private static String formatScaleSegmentLabel(int meters) {
        if (meters <= 0) {
            return "0";
        }
        if (meters < 1000) {
            return Integer.toString(meters) + " m";
        }
        if ((meters % 1000) == 0) {
            return Integer.toString(meters / 1000) + " km";
        }
        if ((meters % 100) == 0) {
            return Integer.toString(meters / 1000) + "." + Integer.toString((meters % 1000) / 100) + " km";
        }
        return Integer.toString(meters) + " m";
    }

    private void runTraceFromPickedPath() {
        byte[] fwd = tracePickPathModel.toByteArray();
        if (fwd == null || fwd.length == 0) {
            Alerts.warning(app.getDisplay(), this, "Trace Path", "Select at least one repeater.", 2500);
            return;
        }
        app.tracePathManual(fwd, this);
    }

    private void autoReturnPickedPath() {
        int n = tracePickPathModel.size();
        if (n <= 1) {
            Alerts.warning(app.getDisplay(), this, "Trace Path",
                    "Need at least 2 repeaters for auto return.", 2200);
            return;
        }
        for (int i = n - 2; i >= 0; i--) {
            tracePickPathModel.addHop(tracePickPathModel.get(i));
        }
        repaint();
    }

    private void pickNearestRepeaterAt(int sx, int sy) {
        int w = getWidth();
        int h = getHeight();
        int n = app.getContactCount();
        int bestIdx = -1;
        int bestD2 = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (app.getContactAdvertType(i) != ProtocolConstants.ADV_TYPE_REPEATER) {
                continue;
            }
            int la = app.getContactAdvLatE6(i);
            int lo = app.getContactAdvLonE6(i);
            if (!projectLatLonE6ToScreen(la, lo, w, h, tmpMarkerXY)) {
                continue;
            }
            int dx = tmpMarkerXY[0] - sx;
            int dy = tmpMarkerXY[1] - sy;
            int d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                bestIdx = i;
            }
        }
        if (bestIdx < 0 || bestD2 > (28 * 28)) {
            Alerts.info(app.getDisplay(), this, "Trace Path", "No repeater near pointer.");
            return;
        }
        tracePickPathModel.addHop(app.getRepeaterPathByte(bestIdx));
        String nm = app.getContactName(bestIdx);
        if (nm == null || nm.length() == 0) {
            nm = "Repeater";
        }
        Alerts.info(app.getDisplay(), this, "Trace Path", "Added: " + nm);
    }

    private String tracePickPathSummary() {
        byte[] p = tracePickPathModel.toByteArray();
        if (p == null || p.length == 0) {
            return "Path: (none)";
        }
        return "Path: " + PathHexCodec.formatBytesCsv(p);
    }

    private String tracePickLastHopName() {
        int n = tracePickPathModel.size();
        if (n <= 0) {
            return "Tap or Add here to select repeater";
        }
        byte b = tracePickPathModel.get(n - 1);
        String nm = app.getRepeaterNameForPathByte(b);
        if (nm == null || nm.length() == 0) {
            nm = "Repeater " + n;
        }
        return "Last: " + nm + " (#" + n + ")";
    }

    /**
     * Viewport center + (all map modes). Short black ticks, 2px stroke (no halo).
     */
    private static void drawMapCenterCrosshair(Graphics g, int w, int h) {
        int cx = w / 2;
        int cy = h / 2;
        int arm = 7;
        int gap = 2;
        g.setColor(0x000000);
        g.drawLine(cx - arm, cy - 1, cx - gap, cy - 1);
        g.drawLine(cx - arm, cy + 1, cx - gap, cy + 1);
        g.drawLine(cx + gap, cy - 1, cx + arm, cy - 1);
        g.drawLine(cx + gap, cy + 1, cx + arm, cy + 1);
        g.drawLine(cx - 1, cy - arm, cx - 1, cy - gap);
        g.drawLine(cx + 1, cy - arm, cx + 1, cy - gap);
        g.drawLine(cx - 1, cy + gap, cx - 1, cy + arm);
        g.drawLine(cx + 1, cy + gap, cx + 1, cy + arm);
    }

    private void drawTracePickOverlay(Graphics g, Font f, int w, int h) {
        if (!tracePickMode) {
            return;
        }

        int padX = 4;
        int padY = 2;
        int lineGap = 1;
        int fh = f.getHeight();
        String l1 = tracePickPathSummary();
        String l2 = tracePickLastHopName();
        int tw = f.stringWidth(l1);
        int w2 = f.stringWidth(l2);
        if (w2 > tw) {
            tw = w2;
        }
        int maxW = w - 8 - padX * 2;
        if (maxW < 40) {
            maxW = 40;
        }
        if (tw > maxW) {
            l1 = truncateForWidth(l1, f, maxW);
            l2 = truncateForWidth(l2, f, maxW);
            tw = f.stringWidth(l1);
            w2 = f.stringWidth(l2);
            if (w2 > tw) {
                tw = w2;
            }
        }
        int bw = tw + padX * 2;
        int bh = padY * 2 + fh * 2 + lineGap;
        int ox = (w - bw) / 2;
        if (ox < 2) {
            ox = 2;
        }
        int oy = h - bh - 2;
        g.setColor(0xE8EEF5);
        g.fillRect(ox, oy, bw, bh);
        g.setColor(0x888888);
        g.drawRect(ox, oy, bw - 1, bh - 1);
        g.setColor(0x222222);
        g.drawString(l1, ox + padX, oy + padY, Graphics.LEFT | Graphics.TOP);
        g.drawString(l2, ox + padX, oy + padY + fh + lineGap, Graphics.LEFT | Graphics.TOP);
    }

    protected void pointerPressed(int x, int y) {
        dragging = true;
        dragLastX = x;
        dragLastY = y;
        pointerMoved = false;
        markInteraction();
    }

    protected void pointerDragged(int x, int y) {
        if (!dragging) {
            return;
        }
        int dx = x - dragLastX;
        int dy = y - dragLastY;
        if (dx > 2 || dx < -2 || dy > 2 || dy < -2) {
            pointerMoved = true;
        }
        dragLastX = x;
        dragLastY = y;
        scrollX -= dx;
        scrollY -= dy;
        clampScroll();
        markInteraction();
        try {
            synchronized (this) {
                if (!dragPaintScheduled) {
                    dragPaintScheduled = true;
                    app.getDisplay().callSerially(dragPaintRunnable);
                }
            }
        } catch (Throwable t) {
            synchronized (this) {
                dragPaintScheduled = false;
            }
            repaint();
        }
    }

    protected void pointerReleased(int x, int y) {
        synchronized (this) {
            dragPaintScheduled = false;
        }
        dragging = false;
        maybeClearFailBackoffAfterSignificantPan();
        markInteraction();
        if (tracePickMode && !pointerMoved) {
            pickNearestRepeaterAt(x, y);
        } else if (!tracePickMode && !pointerMoved) {
            if (mapInteractMode == MAP_MODE_MEASURE) {
                int pick = pickMapMarkerForSelect(x, y);
                if (pick >= 0) {
                    appendMeasurePointLatLon(app.getContactAdvLatE6(pick), app.getContactAdvLonE6(pick));
                } else if (pick == PICK_MAP_SELF) {
                    appendMeasurePointLatLon(app.getNodeAdvLatE6(), app.getNodeAdvLonE6());
                } else {
                    screenToLatLonE6(x, y, measureLatLonScratch);
                    appendMeasurePointLatLon(measureLatLonScratch[0], measureLatLonScratch[1]);
                }
            } else {
                int pick = pickMapMarkerForSelect(x, y);
                if (pick >= 0) {
                    openContactActions(pick);
                } else if (pick == PICK_MAP_SELF) {
                    Alerts.info(app.getDisplay(), this, "Map", "This is your position.");
                }
            }
        }
        repaint();
    }

    /**
     * Pan from D-pad / arrow keys. {@code stepPx} is {@link #PAN_KEY_FINE} on a single press and
     * {@link #PAN_KEY_FAST} from {@link #keyRepeated} while the key is held.
     */
    private void panMapByGameAction(int action, int stepPx) {
        int dx = 0;
        int dy = 0;
        if (action == UP) {
            dy = stepPx;
        } else if (action == DOWN) {
            dy = -stepPx;
        } else if (action == LEFT) {
            dx = stepPx;
        } else if (action == RIGHT) {
            dx = -stepPx;
        }
        if (dx != 0 || dy != 0) {
            scrollX -= dx;
            scrollY -= dy;
            clampScroll();
            maybeClearFailBackoffAfterSignificantPan();
            markInteraction();
            repaint();
        }
    }

    protected void keyPressed(int keyCode) {
        if (keyCode == KEY_NUM1) {
            zoomIn();
            return;
        }
        if (keyCode == KEY_NUM3) {
            zoomOut();
            return;
        }
        int action = getGameAction(keyCode);
        if (tracePickMode && action == FIRE) {
            pickNearestRepeaterAt(getWidth() / 2, getHeight() / 2);
            repaint();
            return;
        }
        if (!tracePickMode && action == FIRE) {
            int cw = getWidth();
            int ch = getHeight();
            if (mapInteractMode == MAP_MODE_MEASURE) {
                addMeasurePointAtCrosshair();
                repaint();
                return;
            }
            selectAtCrosshair(false);
            repaint();
            return;
        }
        if (action == UP || action == DOWN || action == LEFT || action == RIGHT) {
            panMapByGameAction(action, PAN_KEY_FINE);
            return;
        }
    }

    protected void keyRepeated(int keyCode) {
        int action = getGameAction(keyCode);
        if (action == UP || action == DOWN || action == LEFT || action == RIGHT) {
            panMapByGameAction(action, PAN_KEY_FAST);
        }
    }

    /**
     * Forget per-tile failure backoff only after a real viewport move, so the same broken/missing
     * tiles are not re-fetched on every small drag segment.
     */
    private void maybeClearFailBackoffAfterSignificantPan() {
        long dx = scrollX - failBackoffOriginX;
        if (dx < 0) {
            dx = -dx;
        }
        long dy = scrollY - failBackoffOriginY;
        if (dy < 0) {
            dy = -dy;
        }
        if (dx >= (long) FAIL_BACKOFF_CLEAR_PAN_PX || dy >= (long) FAIL_BACKOFF_CLEAR_PAN_PX) {
            synchronized (pausedFailedTiles) {
                pausedFailedTiles.clear();
            }
            failBackoffOriginX = scrollX;
            failBackoffOriginY = scrollY;
        }
    }

    private void zoomIn() {
        if (z >= MAX_Z) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        long cx = scrollX + (long) (w / 2);
        long cy = scrollY + (long) (h / 2);
        resetLoaderQueuesAndBackoff();
        z++;
        scrollX = cx * 2L - (long) (w / 2);
        scrollY = cy * 2L - (long) (h / 2);
        clampScroll();
        markInteraction();
        repaint();
    }

    private void zoomOut() {
        if (z <= MIN_Z) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        long cx = scrollX + (long) (w / 2);
        long cy = scrollY + (long) (h / 2);
        resetLoaderQueuesAndBackoff();
        z--;
        scrollX = cx / 2L - (long) (w / 2);
        scrollY = cy / 2L - (long) (h / 2);
        clampScroll();
        markInteraction();
        repaint();
    }

    /**
     * Centers scroll on this node's advert coords if set. No UI.
     * @return true if scroll was updated
     */
    private boolean tryCenterScrollOnNodeAdvert() {
        int latE6 = app.getNodeAdvLatE6();
        int lonE6 = app.getNodeAdvLonE6();
        if (latE6 == Integer.MIN_VALUE || lonE6 == Integer.MIN_VALUE) {
            return false;
        }
        double lat = latE6 / 1000000.0;
        double lon = lonE6 / 1000000.0;
        long[] xy = new long[2];
        MapMercator.latLonToPixel(lat, lon, z, xy);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0) {
            w = 240;
        }
        if (h <= 0) {
            h = 320;
        }
        scrollX = xy[0] - (long) (w / 2);
        scrollY = xy[1] - (long) (h / 2);
        clampScroll();
        return true;
    }

    private void centerOnNodeAdvert() {
        if (!tryCenterScrollOnNodeAdvert()) {
            Alerts.info(app.getDisplay(), this, "Map",
                    "Set advert latitude and longitude in Settings first.");
            return;
        }
        markInteraction();
        repaint();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            MapViewStore.save(template, z, scrollX, scrollY);
            loaderRunning = false;
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdIn || c == cmdTraceZoomIn) {
            zoomIn();
            return;
        }
        if (c == cmdOut || c == cmdTraceZoomOut) {
            zoomOut();
            return;
        }
        if (c == cmdSource || c == cmdTraceSource) {
            app.getDisplay().setCurrent(new MapSourceForm(app, this));
            return;
        }
        if (c == cmdCenter || c == cmdTraceMyLoc) {
            centerOnNodeAdvert();
            return;
        }
        if (!tracePickMode && c == cmdSelect) {
            if (mapInteractMode == MAP_MODE_MEASURE) {
                addMeasurePointAtCrosshair();
                repaint();
                return;
            }
            selectAtCrosshair(true);
            return;
        }
        if (!tracePickMode && (c == cmdMeasureOn || c == cmdMeasureOff)) {
            if (mapInteractMode == MAP_MODE_MEASURE) {
                mapInteractMode = MAP_MODE_PAN;
                measurePoints.removeAllElements();
            } else {
                mapInteractMode = MAP_MODE_MEASURE;
                measurePoints.removeAllElements();
            }
            updateMapTitle();
            syncMeasureMenuCommand();
            repaint();
            return;
        }
        if (tracePickMode) {
            if (c == cmdPickAdd) {
                pickNearestRepeaterAt(getWidth() / 2, getHeight() / 2);
                repaint();
                return;
            }
            if (c == cmdPickRun) {
                runTraceFromPickedPath();
                return;
            }
            if (c == cmdPickAutoReturn) {
                autoReturnPickedPath();
                return;
            }
            if (c == cmdPickRemove) {
                tracePickPathModel.removeLast();
                repaint();
                return;
            }
            if (c == cmdPickClear) {
                tracePickPathModel.clear();
                repaint();
                return;
            }
        }
        if (c == cmdTracePath) {
            app.getDisplay().setCurrent(new MapViewCanvas(app, this, true));
        }
    }
}
