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

    /** Load host and port in one RMS open. Returns String[]{host, port}. */
    public static String[] loadHostAndPort() {
        String[] out = new String[] { DEFAULT_HOST, DEFAULT_PORT };
        try {
            RecordStore rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() < 1) {
                rs.closeRecordStore();
                return out;
            }
            byte[] data = rs.getRecord(1);
            rs.closeRecordStore();
            String s = new String(data, "UTF-8");
            int sep = s.indexOf('\n');
            if (sep >= 0) {
                String h = s.substring(0, sep).trim();
                String p = s.substring(sep + 1).trim();
                if (h.length() > 0) out[0] = h;
                if (p.length() > 0) out[1] = p;
            } else if (s.length() > 0) {
                out[0] = s.trim();
            }
        } catch (Exception e) {
            // use defaults
        }
        return out;
    }

    public static String loadHost() {
        return loadHostAndPort()[0];
    }

    public static String loadPort() {
        return loadHostAndPort()[1];
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
