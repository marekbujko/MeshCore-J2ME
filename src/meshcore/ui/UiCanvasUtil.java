package meshcore.ui;

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

    public static String getNodeLabel(AppController app) {
        if (app == null) return "My Node";
        String n = app.getNodeName();
        if (n == null) return "My Node";
        n = n.trim();
        return n.length() > 0 ? n : "My Node";
    }
}
