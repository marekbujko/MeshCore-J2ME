package meshcore.util;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;

/**
 * Persists last connected host and port to RMS.
 */
public final class ConnectStorage {

    private static final String STORE_NAME = "MeshCoreConnect";
    private static final String DEFAULT_HOST = "192.168.4.1";
    private static final String DEFAULT_PORT = "5000";

    private ConnectStorage() {}

    public static String loadHost() {
        try {
            RecordStore rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() < 1) {
                rs.closeRecordStore();
                return DEFAULT_HOST;
            }
            byte[] data = rs.getRecord(1);
            rs.closeRecordStore();
            String s = new String(data, "UTF-8");
            int sep = s.indexOf('\n');
            if (sep < 0) return s.length() > 0 ? s : DEFAULT_HOST;
            return s.substring(0, sep).trim();
        } catch (Exception e) {
            return DEFAULT_HOST;
        }
    }

    public static String loadPort() {
        try {
            RecordStore rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() < 1) {
                rs.closeRecordStore();
                return DEFAULT_PORT;
            }
            byte[] data = rs.getRecord(1);
            rs.closeRecordStore();
            String s = new String(data, "UTF-8");
            int sep = s.indexOf('\n');
            if (sep < 0) return DEFAULT_PORT;
            String port = s.substring(sep + 1).trim();
            return port.length() > 0 ? port : DEFAULT_PORT;
        } catch (Exception e) {
            return DEFAULT_PORT;
        }
    }

    public static void save(String host, String port) {
        try {
            String val = (host != null ? host.trim() : "") + "\n" + (port != null ? port.trim() : "");
            byte[] data = val.getBytes("UTF-8");
            RecordStore rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() >= 1) {
                rs.setRecord(1, data, 0, data.length);
            } else {
                rs.addRecord(data, 0, data.length);
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // ignore save errors
        }
    }
}
