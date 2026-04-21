package meshcore.maps;

import java.io.InputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.lcdui.Image;

/**
 * Loads one 256px map tile from {@code file://} or {@code http(s)://}.
 * File URLs use {@link StreamConnection} only (no {@code FileConnection}) so the JAR
 * does not hard-link JSR-75 file API; avoids Nokia stalls without removable storage.
 */
public final class MapTileLoader {

    /** 256px map tiles are typically well under this; caps RAM while reading unknown-length bodies. */
    private static final int MAX_BYTES = 98304;

    private MapTileLoader() {}

    public static Image loadTile(String url) {
        if (url == null || url.length() == 0) {
            return null;
        }
        try {
            if (startsWithIgnoreCase(url, "file:")) {
                return loadFile(url);
            }
            return loadHttp(url);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean startsWithIgnoreCase(String s, String p) {
        return s.regionMatches(true, 0, p, 0, p.length());
    }

    private static Image loadFile(String url) throws Exception {
        StreamConnection sc = null;
        InputStream in = null;
        try {
            sc = (StreamConnection) Connector.open(url, Connector.READ);
            if (sc == null) {
                return null;
            }
            in = sc.openInputStream();
            byte[] buf = readFullyUnknown(in);
            if (buf == null || buf.length == 0) {
                return null;
            }
            return Image.createImage(buf, 0, buf.length);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (sc != null) {
                try {
                    sc.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Image loadHttp(String url) throws Exception {
        HttpConnection hc = null;
        InputStream in = null;
        try {
            hc = (HttpConnection) Connector.open(url);
            hc.setRequestMethod(HttpConnection.GET);
            hc.setRequestProperty("User-Agent", "MeshCore-J2ME/1.0 (offline map viewer)");
            int code = hc.getResponseCode();
            if (code != HttpConnection.HTTP_OK) {
                return null;
            }
            long len = hc.getLength();
            if (len > MAX_BYTES) {
                return null;
            }
            in = hc.openInputStream();
            byte[] buf;
            if (len > 0) {
                buf = readFully(in, (int) len);
            } else {
                buf = readFullyUnknown(in);
            }
            if (buf == null || buf.length == 0) {
                return null;
            }
            return Image.createImage(buf, 0, buf.length);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (hc != null) {
                try {
                    hc.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static byte[] readFully(InputStream in, int max) throws Exception {
        byte[] buf = new byte[max];
        int off = 0;
        while (off < max) {
            int n = in.read(buf, off, max - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        if (off == max) {
            return buf;
        }
        if (off == 0) {
            return null;
        }
        byte[] out = new byte[off];
        System.arraycopy(buf, 0, out, 0, off);
        return out;
    }

    private static byte[] readFullyUnknown(InputStream in) throws Exception {
        int cap = 4096;
        byte[] buf = new byte[cap];
        int len = 0;
        byte[] chunk = new byte[2048];
        while (len < MAX_BYTES) {
            int room = cap - len;
            if (room == 0) {
                int newCap = cap << 1;
                if (newCap > MAX_BYTES) {
                    newCap = MAX_BYTES;
                }
                if (newCap <= cap) {
                    break;
                }
                byte[] nb = new byte[newCap];
                System.arraycopy(buf, 0, nb, 0, len);
                buf = nb;
                cap = newCap;
                room = cap - len;
            }
            int toRead = chunk.length < room ? chunk.length : room;
            int n = in.read(chunk, 0, toRead);
            if (n < 0) {
                break;
            }
            System.arraycopy(chunk, 0, buf, len, n);
            len += n;
        }
        if (len == 0) {
            return null;
        }
        if (len == buf.length) {
            return buf;
        }
        byte[] out = new byte[len];
        System.arraycopy(buf, 0, out, 0, len);
        return out;
    }
}
