package meshcore.util;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;
import java.util.Vector;

/**
 * Persists custom channel names to RMS.
 * Public channel (index 0) is not stored.
 */
public final class ChannelStorage {

    private static final String STORE_NAME = "MeshCoreChannels";

    private ChannelStorage() {}

    public static Vector load() {
        Vector channels = new Vector();
        try {
            RecordStore rs = RecordStore.openRecordStore(STORE_NAME, true);
            int n = rs.getNumRecords();
            for (int i = 1; i <= n; i++) {
                try {
                    byte[] data = rs.getRecord(i);
                    String name = new String(data, "UTF-8");
                    if (name.length() > 0) {
                        channels.addElement(name);
                    }
                } catch (Exception e) {
                    // skip bad record
                }
            }
            rs.closeRecordStore();
        } catch (RecordStoreException e) {
            // no store yet
        }
        return channels;
    }

    public static void save(Vector channels) {
        try {
            try {
                RecordStore rs = RecordStore.openRecordStore(STORE_NAME, false);
                rs.closeRecordStore();
            } catch (RecordStoreNotFoundException e) {
                // no store yet
            }
            try {
                RecordStore.deleteRecordStore(STORE_NAME);
            } catch (RecordStoreNotFoundException e) {
                // ignore
            }
            if (channels.size() == 0) return;
            RecordStore rs = RecordStore.openRecordStore(STORE_NAME, true);
            for (int i = 0; i < channels.size(); i++) {
                String name = (String) channels.elementAt(i);
                byte[] data = name.getBytes("UTF-8");
                rs.addRecord(data, 0, data.length);
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // ignore save errors
        }
    }
}
