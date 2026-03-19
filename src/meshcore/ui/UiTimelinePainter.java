package meshcore.ui;

import javax.microedition.lcdui.Graphics;

/**
 * Shared drawing routines for timeline-like nodes/arrows.
 */
public final class UiTimelinePainter {

    private UiTimelinePainter() {}

    public static int drawNodeBubble(Graphics g, int x, int y, int w, String text) {
        int h = g.getFont().getHeight() + 8;
        g.setColor(UiTheme.BUBBLE_FILL);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(UiTheme.BUBBLE_BORDER);
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setColor(UiTheme.BUBBLE_TEXT);
        g.drawString(text != null ? text : "", x + 6, y + 4, Graphics.LEFT | Graphics.TOP);
        return h + 4;
    }

    /**
     * Draws one SNR label + arrow (down direction).
     * Caller should ensure g font matches desired font for metrics.
     */
    public static int drawSnrArrowDown(Graphics g, int x, int y, int w, String snr) {
        int cx = x + (w / 2);
        String label = "SNR " + (snr != null ? snr : "n/a");
        g.setColor(UiTheme.SNR_TEXT);
        int tw = g.getFont().stringWidth(label);
        g.drawString(label, cx - (tw / 2), y - 3, Graphics.LEFT | Graphics.TOP);

        int yLine = y + g.getFont().getHeight() - 3;
        g.setColor(UiTheme.ARROW);
        // Slightly bolder shaft + larger head (double-stroke look).
        g.drawLine(cx, yLine, cx, yLine + 8);
        g.drawLine(cx + 1, yLine, cx + 1, yLine + 8);
        g.drawLine(cx, yLine + 8, cx - 4, yLine + 4);
        g.drawLine(cx, yLine + 8, cx + 4, yLine + 4);
        g.drawLine(cx + 1, yLine + 8, cx - 3, yLine + 4);
        g.drawLine(cx + 1, yLine + 8, cx + 5, yLine + 4);

        return g.getFont().getHeight() + 11;
    }

    /**
     * Draws both SNR values + two arrows:
     * - left: SNR There with down arrow
     * - right: SNR Back with up arrow
     */
    public static int drawSnrDualArrows(Graphics g,
                                         int x,
                                         int y,
                                         int w,
                                         String snrThere,
                                         String snrBack) {
        int leftX = x + (w / 4);
        int rightX = x + ((w * 3) / 4);

        String leftLabel = "SNR " + (snrThere != null ? snrThere : "n/a");
        String rightLabel = "SNR " + (snrBack != null ? snrBack : "n/a");

        g.setColor(UiTheme.SNR_TEXT);
        int twL = g.getFont().stringWidth(leftLabel);
        int twR = g.getFont().stringWidth(rightLabel);
        g.drawString(leftLabel, leftX - (twL / 2), y - 3, Graphics.LEFT | Graphics.TOP);
        g.drawString(rightLabel, rightX - (twR / 2), y - 3, Graphics.LEFT | Graphics.TOP);

        int yLineTop = y + g.getFont().getHeight() - 3;
        int yLineBottom = yLineTop + 8;
        g.setColor(UiTheme.ARROW);

        // Left: down arrow (there).
        g.drawLine(leftX, yLineTop, leftX, yLineBottom);
        g.drawLine(leftX + 1, yLineTop, leftX + 1, yLineBottom);
        g.drawLine(leftX, yLineBottom, leftX - 4, yLineBottom - 4);
        g.drawLine(leftX, yLineBottom, leftX + 4, yLineBottom - 4);
        g.drawLine(leftX + 1, yLineBottom, leftX - 3, yLineBottom - 4);
        g.drawLine(leftX + 1, yLineBottom, leftX + 5, yLineBottom - 4);

        // Right: up arrow (back).
        g.drawLine(rightX, yLineBottom, rightX, yLineTop);
        g.drawLine(rightX + 1, yLineBottom, rightX + 1, yLineTop);
        g.drawLine(rightX, yLineTop, rightX - 4, yLineTop + 4);
        g.drawLine(rightX, yLineTop, rightX + 4, yLineTop + 4);
        g.drawLine(rightX + 1, yLineTop, rightX - 3, yLineTop + 4);
        g.drawLine(rightX + 1, yLineTop, rightX + 5, yLineTop + 4);

        return g.getFont().getHeight() + 11;
    }
}

