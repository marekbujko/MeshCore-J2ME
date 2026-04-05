package meshcore.ui;

import javax.microedition.lcdui.Canvas;

/**
 * Coalesces {@link Canvas#repaint(int, int, int, int)} during pointer drag so low-end
 * devices are not flooded with full layout passes every move event.
 */
public final class UiScrollRepaintThrottler {

    /** ~30 fps cap during finger drag; keeps layout/paint from saturating slow CLDC devices. */
    public static final long DEFAULT_DRAG_INTERVAL_MS = 32L;

    private long lastMs;

    public void reset() {
        lastMs = 0L;
    }

    /**
     * Repaints at most once per {@code minIntervalMs}; use {@link #flushRepaint} on
     * pointer release so the final scroll offset is always painted.
     */
    public boolean repaintDrag(Canvas c, long minIntervalMs, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        if (now - lastMs >= minIntervalMs) {
            lastMs = now;
            c.repaint(x, y, w, h);
            return true;
        }
        return false;
    }

    public void flushRepaint(Canvas c, int x, int y, int w, int h) {
        lastMs = 0L;
        c.repaint(x, y, w, h);
    }
}
