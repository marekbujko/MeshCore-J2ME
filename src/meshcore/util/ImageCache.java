package meshcore.util;

import javax.microedition.lcdui.Image;

import java.util.Vector;

/**
 * Very small CLDC-safe image cache.
 * J2ME / MIDP resources can be re-loaded each time a screen is opened;
 * caching avoids repeated PNG decode / file reads.
 *
 * Uses a simple Vector-backed list (no HashMap in some CLDC profiles).
 */
public final class ImageCache {

    private static final int MAX_ENTRIES = 32;

    private static final Vector keys = new Vector();
    private static final Vector values = new Vector();

    private ImageCache() {}

    public static Image get(String path) {
        if (path == null) return null;
        synchronized (ImageCache.class) {
            for (int i = 0; i < keys.size(); i++) {
                if (path.equals(keys.elementAt(i))) {
                    return (Image) values.elementAt(i);
                }
            }

            Image img = null;
            try {
                img = Image.createImage(path);
            } catch (Throwable t) {
                img = null;
            }

            if (img != null) {
                if (keys.size() >= MAX_ENTRIES) {
                    // Drop oldest entry to keep memory bounded.
                    keys.removeElementAt(0);
                    values.removeElementAt(0);
                }
                keys.addElement(path);
                values.addElement(img);
            }
            return img;
        }
    }

    public static void clear() {
        synchronized (ImageCache.class) {
            keys.removeAllElements();
            values.removeAllElements();
        }
    }
}

