package meshcore.util;

import javax.microedition.rms.RecordStore;

/**
 * Local preferences for settings not yet applied over the wire (e.g. share position).
 */
public final class SettingsExtrasStore {

    private static final String STORE = "MeshSetEx";
    private static final int RID = 1;

    private SettingsExtrasStore() {}

    public static boolean loadSharePositionInAdvert() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE, true);
            if (rs.getNumRecords() < 1) {
                return false;
            }
            byte[] b = rs.getRecord(RID);
            return b != null && b.length > 0 && b[0] != 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static void saveSharePositionInAdvert(boolean on) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE, true);
            byte[] b = new byte[] { (byte) (on ? 1 : 0) };
            if (rs.getNumRecords() >= 1) {
                rs.setRecord(RID, b, 0, b.length);
            } else {
                rs.addRecord(b, 0, b.length);
            }
        } catch (Throwable ignored) {
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
