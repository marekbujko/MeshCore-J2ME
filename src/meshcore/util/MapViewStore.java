package meshcore.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.rms.RecordStore;

/**
 * RMS-backed map viewer state: tile URL template, zoom, scroll position.
 */
public final class MapViewStore {

    private static final String STORE = "MeshMapVw";
    private static final int REC = 1;

    private MapViewStore() {}

    public static String loadTemplate() {
        byte[] raw = loadRecord();
        if (raw == null || raw.length == 0) {
            return "";
        }
        try {
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(raw));
            return din.readUTF();
        } catch (IOException e) {
            return "";
        }
    }

    public static int loadZoom() {
        byte[] raw = loadRecord();
        if (raw == null || raw.length == 0) {
            return 3;
        }
        try {
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(raw));
            din.readUTF();
            return din.readInt();
        } catch (IOException e) {
            return 3;
        }
    }

    public static void loadScroll(long[] outXY) {
        outXY[0] = 0;
        outXY[1] = 0;
        byte[] raw = loadRecord();
        if (raw == null || raw.length == 0) {
            return;
        }
        try {
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(raw));
            din.readUTF();
            din.readInt();
            outXY[0] = din.readLong();
            outXY[1] = din.readLong();
        } catch (IOException e) {
            outXY[0] = 0;
            outXY[1] = 0;
        }
    }

    public static void save(String template, int zoom, long scrollX, long scrollY) {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bos);
            if (template == null) {
                template = "";
            }
            dout.writeUTF(template);
            dout.writeInt(zoom);
            dout.writeLong(scrollX);
            dout.writeLong(scrollY);
            dout.flush();
            byte[] b = bos.toByteArray();

            rs = RecordStore.openRecordStore(STORE, true);
            if (rs.getNumRecords() >= 1) {
                rs.setRecord(REC, b, 0, b.length);
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

    private static byte[] loadRecord() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE, false);
            if (rs == null || rs.getNumRecords() < 1) {
                return null;
            }
            return rs.getRecord(REC);
        } catch (Throwable t) {
            return null;
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
