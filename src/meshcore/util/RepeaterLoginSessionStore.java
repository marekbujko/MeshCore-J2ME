package meshcore.util;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;

/**
 * Persists successful repeater login session (per full public key hex) for UI gating.
 */
public final class RepeaterLoginSessionStore {

    private static final String STORE_NAME = "MeshCoreLoginSess";
    private static final int RECORD_ID = 1;
    private static final char SEP = '\t';

    /** Parsed session row from RMS. */
    public static final class Session {
        public final int permissionsByte;
        public final long serverTag;
        /** -1 if not sent by firmware. */
        public final int newPermissionsOrMinus1;
        public final long savedAtMs;

        public Session(int permissionsByte, long serverTag, int newPermissionsOrMinus1, long savedAtMs) {
            this.permissionsByte = permissionsByte;
            this.serverTag = serverTag;
            this.newPermissionsOrMinus1 = newPermissionsOrMinus1;
            this.savedAtMs = savedAtMs;
        }

        public boolean isAdmin() {
            return (permissionsByte & 1) != 0;
        }
    }

    private RepeaterLoginSessionStore() {}

    private static String normalizeKey(String pubKeyHex) {
        if (pubKeyHex == null) {
            return "";
        }
        return pubKeyHex.trim().toLowerCase();
    }

    /**
     * Line format: pubkeyHex TAB permissionsByte TAB serverTag TAB newPerm TAB savedAtMs
     */
    public static void save(String pubKeyHex, int permissionsByte, long serverTag, int newPermOrMinus1) {
        pubKeyHex = normalizeKey(pubKeyHex);
        if (pubKeyHex.length() == 0) {
            return;
        }
        remove(pubKeyHex);
        long now = System.currentTimeMillis();
        String line = pubKeyHex + SEP + permissionsByte + SEP + serverTag + SEP + newPermOrMinus1 + SEP + now;
        String data = loadAll();
        if (data != null && data.length() > 0) {
            data = data + "\n" + line;
        } else {
            data = line;
        }
        saveAll(data);
    }

    public static Session load(String pubKeyHex) {
        pubKeyHex = normalizeKey(pubKeyHex);
        if (pubKeyHex.length() == 0) {
            return null;
        }
        String data = loadAll();
        if (data == null || data.length() == 0) {
            return null;
        }
        String prefix = pubKeyHex + SEP;
        int n = data.length();
        int i = 0;
        while (i < n) {
            int lineEnd = data.indexOf('\n', i);
            if (lineEnd < 0) {
                lineEnd = n;
            }
            String line = data.substring(i, lineEnd);
            if (line.startsWith(prefix)) {
                return parseLine(line, prefix.length());
            }
            i = lineEnd + 1;
        }
        return null;
    }

    private static Session parseLine(String line, int valueStart) {
        try {
            String rest = line.substring(valueStart);
            int p1 = rest.indexOf(SEP);
            if (p1 < 0) return null;
            int p2 = rest.indexOf(SEP, p1 + 1);
            if (p2 < 0) return null;
            int p3 = rest.indexOf(SEP, p2 + 1);
            if (p3 < 0) return null;
            int perm = Integer.parseInt(rest.substring(0, p1).trim());
            long tag = Long.parseLong(rest.substring(p1 + 1, p2).trim());
            int newP = Integer.parseInt(rest.substring(p2 + 1, p3).trim());
            long saved = Long.parseLong(rest.substring(p3 + 1).trim());
            return new Session(perm, tag, newP, saved);
        } catch (Exception e) {
            return null;
        }
    }

    public static void remove(String pubKeyHex) {
        pubKeyHex = normalizeKey(pubKeyHex);
        if (pubKeyHex.length() == 0) {
            return;
        }
        String data = loadAll();
        if (data == null || data.length() == 0) {
            return;
        }
        String prefix = pubKeyHex + SEP;
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
