package meshcore.ui;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Fixed top bar: {@link UiTheme#BAR_BG} with primary (white) and optional subtitle (muted).
 */
public final class UiScreenHeader {

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
}
