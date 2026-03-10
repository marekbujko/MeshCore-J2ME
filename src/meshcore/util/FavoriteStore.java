package meshcore.util;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;
import java.util.Vector;

import meshcore.util.FrameUtils;

/**
 * Persists favorite contact public keys (hex) in RMS.
 */
public final class FavoriteStore {

    private static final String STORE_NAME = "MeshCoreFav";
    private static Vector favorites = null;

    private FavoriteStore() {}

    /** Load favorites from RMS. Returns Vector of hex strings (public keys). */
    public static Vector load() {
        if (favorites != null) return favorites;
        favorites = new Vector();
        try {
            RecordStore rs = RecordStore.openRecordStore(STORE_NAME, true);
            int n = rs.getNumRecords();
            for (int i = 1; i <= n; i++) {
                try {
                    byte[] data = rs.getRecord(i);
                    if (data != null && data.length > 0) {
                        String hex = new String(data, "UTF-8").trim().toLowerCase();
                        if (hex.length() > 0 && !favorites.contains(hex)) {
                            favorites.addElement(hex);
                        }
                    }
                } catch (Exception ignore) {}
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // empty on first run
        }
        return favorites;
    }

    private static void save() {
        try {
            RecordStore.deleteRecordStore(STORE_NAME);
        } catch (RecordStoreNotFoundException ignore) {
        } catch (RecordStoreException ignore) {}
        try {
            RecordStore rs = RecordStore.openRecordStore(STORE_NAME, true);
            for (int i = 0; i < favorites.size(); i++) {
                String hex = (String) favorites.elementAt(i);
                byte[] data = hex.getBytes("UTF-8");
                rs.addRecord(data, 0, data.length);
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // ignore
        }
    }

    public static boolean isFavorite(String keyHex) {
        Vector v = load();
        if (keyHex == null) return false;
        String h = keyHex.trim().toLowerCase();
        if (h.length() == 0) return false;
        for (int i = 0; i < v.size(); i++) {
            if (((String) v.elementAt(i)).equals(h)) return true;
        }
        return false;
    }

    public static void addFavorite(String keyHex) {
        Vector v = load();
        if (keyHex == null) return;
        String h = keyHex.trim().toLowerCase();
        if (h.length() == 0) return;
        if (!v.contains(h)) {
            v.addElement(h);
            save();
        }
    }

    public static void removeFavorite(String keyHex) {
        Vector v = load();
        if (keyHex == null) return;
        String h = keyHex.trim().toLowerCase();
        for (int i = 0; i < v.size(); i++) {
            if (((String) v.elementAt(i)).equals(h)) {
                v.removeElementAt(i);
                save();
                return;
            }
        }
    }

    /** Get contact index by key hex, or -1. */
    public static int indexByKey(Vector keys, String keyHex) {
        if (keys == null || keyHex == null) return -1;
        String h = keyHex.trim().toLowerCase();
        if (h.length() == 0) return -1;
        for (int i = 0; i < keys.size(); i++) {
            byte[] k = (byte[]) keys.elementAt(i);
            if (k != null) {
                String kh = FrameUtils.bytesToHex(k, 0, k.length);
                if (kh.equals(h)) return i;
            }
        }
        return -1;
    }

    /** Convert byte[] key to hex. */
    public static String keyToHex(byte[] key) {
        if (key == null || key.length == 0) return "";
        return FrameUtils.bytesToHex(key, 0, key.length).toLowerCase();
    }
}
