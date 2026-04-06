package meshcore.ui;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Fixed top bar: {@link UiTheme#BAR_BG} with primary (white) and optional subtitle (muted).
 */
public final class UiScreenHeader {

    /** Top bar line while waiting for a network result (screens may append animated dots). */
    public static final String PLEASE_WAIT = "Please wait";

    private static final int PAD_X = 10;
    private static final int PAD_Y = 9;
    private static final int GAP_TITLE_SUB = 5;

    private UiScreenHeader() {}

    public static int measureHeight(int w, String primary, String subtitle, Font titleFont, Font subFont) {
        int maxTW = Math.max(8, w - PAD_X * 2);
        int h = PAD_Y + UiCanvasUtil.measureWrappedHeight(titleFont, primary != null ? primary : "", maxTW);
        if (subtitle != null && subtitle.length() > 0) {
            h += GAP_TITLE_SUB + UiCanvasUtil.measureWrappedHeight(subFont, subtitle, maxTW);
        }
        h += PAD_Y;
        int minH = titleFont.getHeight() + PAD_Y * 2;
        return h < minH ? minH : h;
    }

    /**
     * Draws the bar at the top of the canvas (y=0). Does not clip.
     *
     * @return bar height in pixels
     */
    public static int paint(Graphics g, int w, String primary, String subtitle, Font titleFont, Font subFont) {
        int maxTW = Math.max(8, w - PAD_X * 2);
        int barH = measureHeight(w, primary, subtitle, titleFont, subFont);

        g.setColor(UiTheme.BAR_BG);
        g.fillRect(0, 0, w, barH);

        g.setFont(titleFont);
        g.setColor(UiTheme.BAR_TEXT);
        int y = PAD_Y;
        y += UiCanvasUtil.drawWrapped(g, primary != null ? primary : "", PAD_X, y, maxTW);
        if (subtitle != null && subtitle.length() > 0) {
            y += GAP_TITLE_SUB;
            g.setFont(subFont);
            g.setColor(UiTheme.BAR_MUTED);
            y += UiCanvasUtil.drawWrapped(g, subtitle, PAD_X, y, maxTW);
        }

        g.setColor(0x000000);
        g.drawLine(0, barH - 1, w, barH - 1);
        return barH;
    }

    /**
     * Bar height when the row under the title is split: {@code leftSub} at the left edge,
     * {@code rightSub} at the right (e.g. Path Hops / Duration).
     * If {@code primary} is empty but left/right are set, only one row is measured (uses {@code subFont} height).
     */
    public static int measureHeightPrimarySubLeftRight(int w, String primary, String leftSub, String rightSub,
            Font titleFont, Font subFont) {
        int maxTW = Math.max(8, w - PAD_X * 2);
        boolean hasPrimary = primary != null && primary.length() > 0;
        boolean hasSubRow = (leftSub != null && leftSub.length() > 0)
                || (rightSub != null && rightSub.length() > 0);
        int h;
        if (hasPrimary && hasSubRow) {
            h = PAD_Y + UiCanvasUtil.measureWrappedHeight(titleFont, primary, maxTW)
                    + GAP_TITLE_SUB + subFont.getHeight() + PAD_Y;
        } else if (hasPrimary) {
            h = PAD_Y + UiCanvasUtil.measureWrappedHeight(titleFont, primary, maxTW) + PAD_Y;
        } else if (hasSubRow) {
            h = PAD_Y + subFont.getHeight() + PAD_Y;
        } else {
            h = PAD_Y + PAD_Y;
        }
        int minH = (hasPrimary || !hasSubRow)
                ? titleFont.getHeight() + PAD_Y * 2
                : subFont.getHeight() + PAD_Y * 2;
        return h < minH ? minH : h;
    }

    /**
     * Paints primary title then one muted row: left string at {@link #PAD_X}, right string right-aligned.
     * If {@code primary} is empty but left/right are set, paints one {@link UiTheme#BAR_TEXT} row in {@code subFont}.
     */
    public static int paintPrimarySubLeftRight(Graphics g, int w, String primary, String leftSub, String rightSub,
            Font titleFont, Font subFont) {
        int maxTW = Math.max(8, w - PAD_X * 2);
        int barH = measureHeightPrimarySubLeftRight(w, primary, leftSub, rightSub, titleFont, subFont);

        g.setColor(UiTheme.BAR_BG);
        g.fillRect(0, 0, w, barH);

        boolean hasPrimary = primary != null && primary.length() > 0;
        boolean hasLeft = leftSub != null && leftSub.length() > 0;
        boolean hasRight = rightSub != null && rightSub.length() > 0;
        boolean hasSubRow = hasLeft || hasRight;

        if (hasPrimary) {
            g.setFont(titleFont);
            g.setColor(UiTheme.BAR_TEXT);
            int y = PAD_Y;
            y += UiCanvasUtil.drawWrapped(g, primary, PAD_X, y, maxTW);
            if (hasSubRow) {
                y += GAP_TITLE_SUB;
                g.setFont(subFont);
                g.setColor(UiTheme.BAR_MUTED);
                if (hasLeft) {
                    g.drawString(leftSub, PAD_X, y, Graphics.LEFT | Graphics.TOP);
                }
                if (hasRight) {
                    g.drawString(rightSub, w - PAD_X, y, Graphics.RIGHT | Graphics.TOP);
                }
            }
        } else if (hasSubRow) {
            int y = PAD_Y;
            g.setFont(subFont);
            g.setColor(UiTheme.BAR_TEXT);
            if (hasLeft) {
                g.drawString(leftSub, PAD_X, y, Graphics.LEFT | Graphics.TOP);
            }
            if (hasRight) {
                g.drawString(rightSub, w - PAD_X, y, Graphics.RIGHT | Graphics.TOP);
            }
        }

        g.setColor(0x000000);
        g.drawLine(0, barH - 1, w, barH - 1);
        return barH;
    }
}
