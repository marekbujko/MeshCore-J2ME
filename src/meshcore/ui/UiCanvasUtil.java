package meshcore.ui;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Shared helpers for custom Canvas text/layout in MIDP.
 */
public final class UiCanvasUtil {

    private UiCanvasUtil() {}

    public static int drawWrapped(Graphics g, String text, int x, int y, int maxWidth) {
        if (text == null) text = "";
        int lineH = g.getFont().getHeight();
        int startY = y;
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && text.charAt(i) == ' ') i++;
            if (i >= n) break;
            int end = i;
            int lastFitSpace = -1;
            while (end < n) {
                if (text.charAt(end) == ' ') lastFitSpace = end;
                String s = text.substring(i, end + 1);
                if (g.getFont().stringWidth(s) > maxWidth) break;
                end++;
            }
            int cut;
            if (end >= n) cut = n;
            else if (lastFitSpace >= i) cut = lastFitSpace;
            else cut = end > i ? end : (i + 1);
            String line = text.substring(i, cut);
            g.drawString(line, x, y, Graphics.LEFT | Graphics.TOP);
            y += lineH;
            i = (cut < n && text.charAt(cut) == ' ') ? cut + 1 : cut;
        }
        return y - startY;
    }

    /** Total height if {@code text} were drawn with {@link #drawWrapped} using {@code font}. */
    public static int measureWrappedHeight(Font font, String text, int maxWidth) {
        if (text == null) {
            text = "";
        }
        if (text.length() == 0) {
            return font.getHeight();
        }
        int lineH = font.getHeight();
        int lines = 0;
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && text.charAt(i) == ' ') {
                i++;
            }
            if (i >= n) {
                break;
            }
            int end = i;
            int lastFitSpace = -1;
            while (end < n) {
                if (text.charAt(end) == ' ') {
                    lastFitSpace = end;
                }
                String s = text.substring(i, end + 1);
                if (font.stringWidth(s) > maxWidth) {
                    break;
                }
                end++;
            }
            int cut;
            if (end >= n) {
                cut = n;
            } else if (lastFitSpace >= i) {
                cut = lastFitSpace;
            } else {
                cut = end > i ? end : (i + 1);
            }
            lines++;
            i = (cut < n && text.charAt(cut) == ' ') ? cut + 1 : cut;
        }
        return lines * lineH;
    }

    public static int drawWrappedCentered(Graphics g, String text, int x, int y, int maxWidth) {
        if (text == null) text = "";
        int lineH = g.getFont().getHeight();
        int startY = y;
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && text.charAt(i) == ' ') i++;
            if (i >= n) break;
            int end = i;
            int lastFitSpace = -1;
            while (end < n) {
                if (text.charAt(end) == ' ') lastFitSpace = end;
                String s = text.substring(i, end + 1);
                if (g.getFont().stringWidth(s) > maxWidth) break;
                end++;
            }
            int cut;
            if (end >= n) cut = n;
            else if (lastFitSpace >= i) cut = lastFitSpace;
            else cut = end > i ? end : (i + 1);
            String line = text.substring(i, cut);
            int tw = g.getFont().stringWidth(line);
            int drawX = x + ((maxWidth - tw) / 2);
            if (drawX < x) drawX = x;
            g.drawString(line, drawX, y, Graphics.LEFT | Graphics.TOP);
            y += lineH;
            i = (cut < n && text.charAt(cut) == ' ') ? cut + 1 : cut;
        }
        return y - startY;
    }

    /**
     * Draw {@code text} split on {@code '\n'} only; each line uses {@link #drawWrapped}.
     * Empty line segments add no vertical space (no extra gaps between rows).
     */
    public static int drawNewlineSeparatedWrapped(Graphics g, String text, int x, int y, int maxW) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        int y0 = y;
        int beg = 0;
        int n = text.length();
        for (int i = 0; i <= n; i++) {
            if (i == n || text.charAt(i) == '\n') {
                String line = text.substring(beg, i);
                if (line.length() > 0) {
                    y += drawWrapped(g, line, x, y, maxW);
                }
                beg = i + 1;
            }
        }
        return y - y0;
    }

    public static String getNodeLabel(AppController app) {
        if (app == null) return "My Node";
        String n = app.getNodeName();
        if (n == null) return "My Node";
        n = n.trim();
        return n.length() > 0 ? n : "My Node";
    }
}
