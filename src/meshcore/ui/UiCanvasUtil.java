package meshcore.ui;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Shared helpers for custom Canvas text/layout in MIDP.
 */
public final class UiCanvasUtil {

    private UiCanvasUtil() {}

    /**
     * Exclusive end index for one wrapped line starting at {@code i} (after leading spaces
     * are skipped by caller). Uses {@link Font#charWidth(char)} so we avoid {@code substring}
     * allocations in the hot loop (important on real devices).
     */
    private static int lineBreakCut(Font font, String text, int n, int i, int maxWidth) {
        int end = i;
        int lastFitSpace = -1;
        int lineWidth = 0;
        while (end < n) {
            char c = text.charAt(end);
            if (c == ' ') {
                lastFitSpace = end;
            }
            int cw = font.charWidth(c);
            if (lineWidth + cw > maxWidth) {
                if (end > i) {
                    break;
                }
            }
            lineWidth += cw;
            end++;
        }
        if (end >= n) {
            return n;
        }
        if (lastFitSpace >= i) {
            return lastFitSpace;
        }
        return end > i ? end : (i + 1);
    }

    private static int substringWidthChars(Font font, String text, int from, int to) {
        int w = 0;
        for (int j = from; j < to; j++) {
            w += font.charWidth(text.charAt(j));
        }
        return w;
    }

    public static int drawWrapped(Graphics g, String text, int x, int y, int maxWidth) {
        if (text == null) {
            text = "";
        }
        Font font = g.getFont();
        int lineH = font.getHeight();
        int startY = y;
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && text.charAt(i) == ' ') {
                i++;
            }
            if (i >= n) {
                break;
            }
            int cut = lineBreakCut(font, text, n, i, maxWidth);
            g.drawSubstring(text, i, cut - i, x, y, Graphics.LEFT | Graphics.TOP);
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
            int cut = lineBreakCut(font, text, n, i, maxWidth);
            lines++;
            i = (cut < n && text.charAt(cut) == ' ') ? cut + 1 : cut;
        }
        return lines * lineH;
    }

    public static int drawWrappedCentered(Graphics g, String text, int x, int y, int maxWidth) {
        if (text == null) {
            text = "";
        }
        Font font = g.getFont();
        int lineH = font.getHeight();
        int startY = y;
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && text.charAt(i) == ' ') {
                i++;
            }
            if (i >= n) {
                break;
            }
            int cut = lineBreakCut(font, text, n, i, maxWidth);
            int tw = substringWidthChars(font, text, i, cut);
            int drawX = x + ((maxWidth - tw) / 2);
            if (drawX < x) {
                drawX = x;
            }
            g.drawSubstring(text, i, cut - i, drawX, y, Graphics.LEFT | Graphics.TOP);
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
