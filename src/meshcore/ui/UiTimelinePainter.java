package meshcore.ui;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Shared drawing routines for timeline-like nodes/arrows.
 */
public final class UiTimelinePainter {

    private UiTimelinePainter() {}

    private static String truncateToMaxWidth(String s, Font f, int maxW) {
        if (s == null) {
            s = "";
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

    /**
     * Full-width repeater/hop chip: forest panel + mint accent + pale green label (see {@link UiTheme} timeline colors).
     */
    public static int drawNodeBubble(Graphics g, int x, int y, int w, String text) {
        return drawNodeBubble(g, x, y, w, text, null);
    }

    /**
     * Same as {@link #drawNodeBubble(Graphics, int, int, int, String)}; optional {@code rightText} is drawn
     * right-aligned inside the chip (e.g. hop index). Left label is truncated if needed to avoid overlap.
     */
    public static int drawNodeBubble(Graphics g, int x, int y, int w, String text, String rightText) {
        Font f = g.getFont();
        int fh = f.getHeight();
        int pillH = fh + 14;
        int r = 8;
        g.setColor(UiTheme.TIMELINE_NODE_FILL);
        g.fillRoundRect(x, y, w, pillH, r, r);
        g.setColor(UiTheme.TIMELINE_NODE_BORDER);
        g.drawRoundRect(x, y, w - 1, pillH - 1, r, r);
        int inset = Math.min(5, (pillH - 6) / 2);
        if (inset > 0 && pillH > inset * 2) {
            g.setColor(UiTheme.TIMELINE_NODE_ACCENT);
            g.fillRect(x + 4, y + inset, 3, pillH - inset * 2);
        }
        g.setColor(UiTheme.TIMELINE_NODE_TEXT);
        String s = text != null ? text : "";
        int ty = y + (pillH - fh) / 2;
        int tx = x + 13;
        if (rightText != null && rightText.length() > 0) {
            int rtw = f.stringWidth(rightText);
            int gap = 6;
            int rightPad = 8;
            int leftMax = w - (tx - x) - rtw - gap - rightPad;
            if (leftMax < 24) {
                leftMax = 24;
            }
            s = truncateToMaxWidth(s, f, leftMax);
            int rtx = x + w - rightPad - rtw;
            g.drawString(s, tx, ty, Graphics.LEFT | Graphics.TOP);
            g.drawString(rightText, rtx, ty, Graphics.LEFT | Graphics.TOP);
        } else {
            g.drawString(s, tx, ty, Graphics.LEFT | Graphics.TOP);
        }
        return pillH + 4;
    }

    /**
     * Draws one SNR label + arrow (down direction).
     * Caller should ensure g font matches desired font for metrics.
     */
    public static int drawSnrArrowDown(Graphics g, int x, int y, int w, String snr) {
        int cx = x + (w / 2);
        String label = "SNR " + (snr != null ? snr : "n/a");
        g.setColor(UiTheme.TIMELINE_SNR_TEXT);
        int tw = g.getFont().stringWidth(label);
        g.drawString(label, cx - (tw / 2), y - 3, Graphics.LEFT | Graphics.TOP);

        int yLine = y + g.getFont().getHeight() - 3;
        g.setColor(UiTheme.TIMELINE_ARROW);
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
     * Like {@link #drawSnrArrowDown}, but when {@code distanceLine} is set the text is one compact line:
     * {@code SNR 11.75 dB \u00B7 706 m} (middle dot; truncated to {@code w} if needed).
     */
    public static int drawSnrArrowDownWithDistance(Graphics g, int x, int y, int w, String snr, String distanceLine) {
        int cx = x + (w / 2);
        Font f = g.getFont();
        String snrPart = snr != null ? snr : "n/a";
        String label;
        if (distanceLine != null && distanceLine.length() > 0) {
            label = "SNR " + snrPart + " \u00B7 " + distanceLine;
        } else {
            label = "SNR " + snrPart;
        }
        label = truncateToMaxWidth(label, f, w - 8);
        g.setColor(UiTheme.TIMELINE_SNR_TEXT);
        int tw = f.stringWidth(label);
        g.drawString(label, cx - (tw / 2), y - 3, Graphics.LEFT | Graphics.TOP);

        int yLine = y + f.getHeight() - 3;
        g.setColor(UiTheme.TIMELINE_ARROW);
        g.drawLine(cx, yLine, cx, yLine + 8);
        g.drawLine(cx + 1, yLine, cx + 1, yLine + 8);
        g.drawLine(cx, yLine + 8, cx - 4, yLine + 4);
        g.drawLine(cx, yLine + 8, cx + 4, yLine + 4);
        g.drawLine(cx + 1, yLine + 8, cx - 3, yLine + 4);
        g.drawLine(cx + 1, yLine + 8, cx + 5, yLine + 4);

        return f.getHeight() + 11;
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

        g.setColor(UiTheme.TIMELINE_SNR_TEXT);
        int twL = g.getFont().stringWidth(leftLabel);
        int twR = g.getFont().stringWidth(rightLabel);
        g.drawString(leftLabel, leftX - (twL / 2), y - 3, Graphics.LEFT | Graphics.TOP);
        g.drawString(rightLabel, rightX - (twR / 2), y - 3, Graphics.LEFT | Graphics.TOP);

        int yLineTop = y + g.getFont().getHeight() - 3;
        int yLineBottom = yLineTop + 8;
        g.setColor(UiTheme.TIMELINE_ARROW);

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

