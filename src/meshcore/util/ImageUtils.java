package meshcore.util;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Image scaling and splash utilities for J2ME.
 */
public final class ImageUtils {

    private ImageUtils() {}

    /** Sample corner pixels and return dominant background color as 0xRRGGBB. */
    public static int getBackgroundColor(Image img) {
        if (img == null) return 0x191F2D;
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) return 0x191F2D;
        try {
            int[] rgb = new int[4];
            img.getRGB(rgb, 0, 1, 0, 0, 1, 1);
            int p = rgb[0];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            return (r << 16) | (g << 8) | b;
        } catch (Throwable t) {
            return 0x191F2D;
        }
    }

    /** Create a full-screen splash Canvas with logo and Loading text. */
    public static Canvas createSplashCanvas(final Image logo) {
        return createStatusSplashCanvas(logo, new String[]{"Loading..."});
    }

    /** Create a full-screen splash Canvas with logo and mutable status text. Update statusHolder[0] and call repaint(). */
    public static Canvas createStatusSplashCanvas(final Image logo, final String[] statusHolder) {
        final int bgColor = logo != null ? getBackgroundColor(logo) : 0x191F2D;
        return new Canvas() {
            private Image scaledLogo;
            private int scaledW = -1;
            private int scaledH = -1;
            {
                setFullScreenMode(true);
            }

            protected void paint(Graphics g) {
                int w = getWidth();
                int h = getHeight();
                g.setColor(bgColor);
                g.fillRect(0, 0, w, h);
                if (logo != null) {
                    int lw = logo.getWidth();
                    int lh = logo.getHeight();
                    int dw = w;
                    int dh = h - 30;
                    if (lw > 0 && lh > 0 && dw > 0 && dh > 0) {
                        if (scaledLogo == null || scaledW != dw || scaledH != dh) {
                            scaledLogo = scaleImageToFit(logo, dw, dh);
                            scaledW = dw;
                            scaledH = dh;
                        }
                        int x = (w - scaledLogo.getWidth()) / 2;
                        int y = (dh - scaledLogo.getHeight()) / 2;
                        g.drawImage(scaledLogo, x, y, Graphics.TOP | Graphics.LEFT);
                    }
                }
                g.setColor(bgColor);
                g.fillRect(0, h - 35, w, 40);
                g.setColor(0xFFFFFF);
                g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_LARGE));
                String status = (statusHolder != null && statusHolder.length > 0 && statusHolder[0] != null)
                    ? statusHolder[0] : "Loading...";
                int tx = w / 2;
                int ty = h - 15;
                g.drawString(status, tx, ty, Graphics.BASELINE | Graphics.HCENTER);
            }
        };
    }

    /** Scale image to fit within maxWidth x maxHeight, keeping aspect ratio. */
    public static Image scaleImageToFit(Image src, int maxWidth, int maxHeight) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0 || maxWidth <= 0 || maxHeight <= 0) return src;
        if (w <= maxWidth && h <= maxHeight) return src;
        int dstW = w;
        int dstH = h;
        if (w > maxWidth || h > maxHeight) {
            if (w * maxHeight > h * maxWidth) {
                dstW = maxWidth;
                dstH = (h * maxWidth) / w;
            } else {
                dstH = maxHeight;
                dstW = (w * maxHeight) / h;
            }
        }
        if (dstW <= 0) dstW = 1;
        if (dstH <= 0) dstH = 1;
        try {
            return scaleImage(src, dstW, dstH);
        } catch (Throwable t) {
            return src;
        }
    }

    /** Scale source image to dstW x dstH using Graphics clip/draw. */
    public static Image scaleImage(Image src, int dstW, int dstH) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        if (srcW <= 0 || srcH <= 0) return src;
        Image tmp = Image.createImage(dstW, srcH);
        Graphics g = tmp.getGraphics();
        long ratioW = ((long) srcW << 16) / dstW;
        for (int x = 0; x < dstW; x++) {
            int sx = (int) ((x * ratioW) >> 16);
            g.setClip(x, 0, 1, srcH);
            g.drawImage(src, x - sx, 0, Graphics.TOP | Graphics.LEFT);
        }
        Image out = Image.createImage(dstW, dstH);
        g = out.getGraphics();
        long ratioH = ((long) srcH << 16) / dstH;
        for (int y = 0; y < dstH; y++) {
            int sy = (int) ((y * ratioH) >> 16);
            g.setClip(0, y, dstW, 1);
            g.drawImage(tmp, 0, y - sy, Graphics.TOP | Graphics.LEFT);
        }
        return out;
    }
}
