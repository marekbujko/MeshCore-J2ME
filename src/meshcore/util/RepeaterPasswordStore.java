package meshcore.util;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;

/**
 * Persists repeater login passwords when the user enables "Remember password".
 * Keyed by full contact public key hex (64 chars).
 */
public final class RepeaterPasswordStore {

    private static final String STORE_NAME = "MeshCoreRepPwd";
    private static final int RECORD_ID = 1;
    private static final char SEP = '\t';

    private RepeaterPasswordStore() {}

    private static String normalizeKey(String contactPubKeyHex) {
        if (contactPubKeyHex == null) {
            return "";
        }
        return contactPubKeyHex.trim().toLowerCase();
    }

    public static String load(String contactPubKeyHex) {
        contactPubKeyHex = normalizeKey(contactPubKeyHex);
        if (contactPubKeyHex.length() == 0) {
            return null;
        }
        String data = loadAll();
        if (data == null || data.length() == 0) {
            return null;
        }
        String prefix = contactPubKeyHex + SEP;
        int n = data.length();
        int i = 0;
        while (i < n) {
            int lineEnd = data.indexOf('\n', i);
            if (lineEnd < 0) {
                lineEnd = n;
            }
            String line = data.substring(i, lineEnd);
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
            i = lineEnd + 1;
        }
        return null;
    }

    public static void save(String contactPubKeyHex, String password) {
        contactPubKeyHex = normalizeKey(contactPubKeyHex);
        if (contactPubKeyHex.length() == 0) {
            return;
        }
        if (password == null) {
            password = "";
        }
        remove(contactPubKeyHex);
        String data = loadAll();
        String line = contactPubKeyHex + SEP + password;
        if (data != null && data.length() > 0) {
            data = data + "\n" + line;
        } else {
            data = line;
        }
        saveAll(data);
    }

    public static void remove(String contactPubKeyHex) {
        contactPubKeyHex = normalizeKey(contactPubKeyHex);
        if (contactPubKeyHex.length() == 0) {
            return;
        }
        String data = loadAll();
        if (data == null || data.length() == 0) {
            return;
        }
        String prefix = contactPubKeyHex + SEP;
        StringBuffer sb = new StringBuffer();
        int n = data.length();
        int i = 0;
        while (i < n) {
            int lineEnd = data.indexOf('\n', i);
            if (lineEnd < 0) {
                lineEnd = n;
            }
            String line = data.substring(i, lineEnd);
            if (!line.startsWith(prefix)) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            i = lineEnd + 1;
        }
        saveAll(sb.toString());
    }

    private static String loadAll() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, false);
            if (rs.getNumRecords() < 1) {
                return "";
            }
            byte[] rec = rs.getRecord(RECORD_ID);
            return new String(rec, "UTF-8");
        } catch (RecordStoreNotFoundException e) {
            return "";
        } catch (Exception e) {
            return "";
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (RecordStoreException ignore) {}
            }
        }
    }

    private static void saveAll(String data) {
        if (data == null) {
            data = "";
        }
        RecordStore rs = null;
        try {
            byte[] bytes = data.getBytes("UTF-8");
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() >= 1) {
                rs.setRecord(RECORD_ID, bytes, 0, bytes.length);
            } else {
                rs.addRecord(bytes, 0, bytes.length);
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (RecordStoreException ignore) {}
            }
        }
    }
}
