package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;

/**
 * Shared scrolling controller for Canvas screens.
 * Keeps duplicated key/pointer scroll logic in one place.
 */
public final class UiScrollController {

    private int scrollY = 0;
    private int contentHeight = 0;
    private int lastPointerY = -1;

    public void reset() {
        scrollY = 0;
        contentHeight = 0;
        lastPointerY = -1;
    }

    public int getScrollY() {
        return scrollY;
    }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
    }

    public void setScrollY(int scrollY) {
        this.scrollY = scrollY;
    }

    public static int computeStep(Font bodyFont) {
        return Math.max(12, bodyFont != null ? bodyFont.getHeight() + 6 : 16);
    }

    public int getMaxScroll(int viewportH) {
        int max = contentHeight - viewportH + 6;
        return max > 0 ? max : 0;
    }

    public void clamp(int viewportH) {
        int max = getMaxScroll(viewportH);
        if (scrollY < 0) scrollY = 0;
        if (scrollY > max) scrollY = max;
    }

    public boolean onKey(int action, int step, int viewportH) {
        int before = scrollY;
        if (action == Canvas.UP) {
            scrollY -= step;
        } else if (action == Canvas.DOWN) {
            scrollY += step;
        }
        clamp(viewportH);
        return scrollY != before;
    }

    public void pointerPressed(int y) {
        lastPointerY = y;
    }

    public boolean onDrag(int y, int viewportH) {
        if (lastPointerY < 0) {
            lastPointerY = y;
            return false;
        }
        int dy = y - lastPointerY;
        lastPointerY = y;
        scrollY -= dy;
        clamp(viewportH);
        return dy != 0;
    }

    public void pointerReleased() {
        lastPointerY = -1;
    }
}

