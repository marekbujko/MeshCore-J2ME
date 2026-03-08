package meshcore.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Vector;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/**
 * RMS-backed history store for channel and DM logs.
 * Uses block format: many messages per record, binary headers, epoch + UTF text.
 */
public final class HistoryStore {

    private static final String PREFIX_DM = "DM_";
    private static final String PREFIX_CH = "CH_";

    /** Version byte for block format. */
    private static final byte BLOCK_VERSION = 1;

    /** Max bytes per block; RMS record limit is typically 64KB. */
    private static final int MAX_BLOCK_BYTES = 4096;

    /** Reused buffers to reduce GC pressure on weak devices. */
    private static ByteArrayOutputStream reuseBout;
    private static DataOutputStream reuseDout;

    private HistoryStore() {}

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public static void appendDmLine(int contactIdx, String stampedLine) {
        if (contactIdx < 0 || stampedLine == null) return;
        appendLine(PREFIX_DM + contactIdx, stampedLine, AppConstants.HISTORY_MAX_DM_MESSAGES);
    }

    public static void appendChannelLine(int channelIdx, String stampedLine) {
        if (channelIdx < 0 || stampedLine == null) return;
        appendLine(PREFIX_CH + channelIdx, stampedLine, AppConstants.HISTORY_MAX_CHANNEL_MESSAGES);
    }

    public static void loadDmTailIntoBuffer(int contactIdx, StringBuffer buf) {
        if (contactIdx < 0 || buf == null) return;
        loadTailIntoBuffer(PREFIX_DM + contactIdx, buf);
    }

    public static void loadChannelTailIntoBuffer(int channelIdx, StringBuffer buf) {
        if (channelIdx < 0 || buf == null) return;
        loadTailIntoBuffer(PREFIX_CH + channelIdx, buf);
    }

    public static boolean loadOlderDmIntoBuffer(int contactIdx, StringBuffer buf) {
        if (contactIdx < 0 || buf == null) return false;
        return loadOlderIntoBuffer(PREFIX_DM + contactIdx, buf);
    }

    public static boolean loadOlderChannelIntoBuffer(int channelIdx, StringBuffer buf) {
        if (channelIdx < 0 || buf == null) return false;
        return loadOlderIntoBuffer(PREFIX_CH + channelIdx, buf);
    }

    public static void clearDmHistory(int contactIdx) {
        if (contactIdx < 0) return;
        clearStore(PREFIX_DM + contactIdx);
    }

    public static void clearAllDmHistory() {
        clearAll(PREFIX_DM);
    }

    public static void updateLastDmStatus(int contactIdx, String marker, String newStatus) {
        if (contactIdx < 0 || marker == null || newStatus == null) return;
        updateLastStatus(PREFIX_DM + contactIdx, marker, newStatus);
    }

    public static void clearChannelHistory(int channelIdx) {
        if (channelIdx < 0) return;
        clearStore(PREFIX_CH + channelIdx);
    }

    public static void clearAllChannelHistory() {
        clearAll(PREFIX_CH);
    }

    /**
     * Total bytes used by all DM and channel history stores (for storage monitor).
     */
    public static int getHistoryStorageUsedBytes() {
        String[] names = null;
        try {
            names = RecordStore.listRecordStores();
        } catch (Throwable e) {
            return 0;
        }
        if (names == null) return 0;
        int total = 0;
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            if (n == null) continue;
            if (!n.startsWith(PREFIX_DM) && !n.startsWith(PREFIX_CH)) continue;
            RecordStore rs = null;
            RecordEnumeration en = null;
            try {
                rs = RecordStore.openRecordStore(n, false);
                if (rs == null) continue;
                en = rs.enumerateRecords(null, null, false);
                while (en.hasNextElement()) {
                    int id = en.nextRecordId();
                    total += rs.getRecordSize(id);
                }
            } catch (Throwable ignore) {
            } finally {
                if (en != null) {
                    try { en.destroy(); } catch (Throwable ignore) {}
                }
                if (rs != null) {
                    try { rs.closeRecordStore(); } catch (RecordStoreException ignore) {}
                }
            }
        }
        return total;
    }

    /** Storage used in KB (for display). */
    public static int getHistoryStorageUsedKB() {
        int bytes = getHistoryStorageUsedBytes();
        return (bytes + 512) / 1024;
    }

    /** True if history storage is at or over the warn threshold. */
    public static boolean isHistoryStorageOverLimit() {
        return getHistoryStorageUsedKB() >= AppConstants.HISTORY_STORAGE_WARN_KB;
    }

    // ---------------------------------------------------------------------
    // Block format: [1B version][2B numMsg] [4B epoch][UTF text] ...
    // ---------------------------------------------------------------------

    private static int getEpoch() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    /** Parse a block record into ordered list of stamped lines. */
    private static void parseRecord(byte[] data, Vector out) {
        if (data == null || data.length < 1 || data[0] != BLOCK_VERSION) return;
        try {
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(data));
            din.readByte();
            int n = din.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                din.readInt();
                out.addElement(din.readUTF());
            }
        } catch (Throwable ignore) {}
    }

    private static void ensureReuseStreams() {
        if (reuseBout == null) {
            reuseBout = new ByteArrayOutputStream(MAX_BLOCK_BYTES + 512);
            reuseDout = new DataOutputStream(reuseBout);
        }
        reuseBout.reset();
    }

    /** Write a block of lines. Returns byte array. */
    private static byte[] writeBlock(String[] lines, int count) {
        try {
            ensureReuseStreams();
            reuseDout.writeByte(BLOCK_VERSION);
            reuseDout.writeShort(count);
            int epoch = getEpoch();
            for (int i = 0; i < count; i++) {
                reuseDout.writeInt(epoch);
                reuseDout.writeUTF(lines[i] != null ? lines[i] : "");
            }
            reuseDout.flush();
            return reuseBout.toByteArray();
        } catch (Throwable e) {
            return null;
        }
    }

    private static void appendLine(String storeName, String stampedLine, int maxRecords) {
        RecordStore rs = null;
        RecordEnumeration en = null;
        try {
            rs = RecordStore.openRecordStore(storeName, true);
            int num = rs.getNumRecords();
            int lastId = -1;
            byte[] lastData = null;
            int totalMsgs = 0;
            int[] ids = null;
            byte[][] datas = null;
            int count = 0;

            if (num > 0) {
                en = rs.enumerateRecords(null, null, false);
                ids = new int[num];
                datas = new byte[num][];
                Vector msgs = new Vector();
                while (en.hasNextElement() && count < num) {
                    int id = en.nextRecordId();
                    byte[] data = rs.getRecord(id);
                    ids[count] = id;
                    datas[count] = data;
                    msgs.setSize(0);
                    parseRecord(data, msgs);
                    totalMsgs += msgs.size();
                    count++;
                }
                sortByIdAndData(ids, datas, count);
                lastId = ids[count - 1];
                lastData = datas[count - 1];
            }

            Vector msgs = new Vector();
            if (lastData != null) {
                parseRecord(lastData, msgs);
            }
            msgs.addElement(stampedLine);

            int newTotalMsgs = totalMsgs + 1;
            int toDelete = (maxRecords > 0 && newTotalMsgs > maxRecords) ? newTotalMsgs - maxRecords : 0;
            boolean truncationHandled = false;

            int size = estimateBlockSize(msgs);
            if (size <= MAX_BLOCK_BYTES && (lastData == null || lastData[0] == BLOCK_VERSION)) {
                if (toDelete > 0 && count == 1) {
                    for (int k = 0; k < toDelete && msgs.size() > 0; k++) {
                        msgs.removeElementAt(0);
                    }
                    truncationHandled = true;
                }
                String[] arr = vecToArray(msgs);
                byte[] block = writeBlock(arr, arr.length);
                if (block != null) {
                    if (lastId >= 0) {
                        rs.setRecord(lastId, block, 0, block.length);
                    } else {
                        rs.addRecord(block, 0, block.length);
                    }
                }
            } else {
                if (lastData != null && lastData[0] == BLOCK_VERSION && msgs.size() > 1) {
                    msgs.removeElementAt(msgs.size() - 1);
                    if (toDelete > 0 && count == 1) {
                        for (int k = 0; k < toDelete && msgs.size() > 0; k++) {
                            msgs.removeElementAt(0);
                        }
                        truncationHandled = true;
                    }
                    String[] arr = vecToArray(msgs);
                    byte[] block = writeBlock(arr, arr.length);
                    if (block != null) {
                        rs.setRecord(lastId, block, 0, block.length);
                    }
                }
                String[] single = new String[] { stampedLine };
                byte[] block = writeBlock(single, 1);
                if (block != null) {
                    rs.addRecord(block, 0, block.length);
                }
            }

            if (toDelete > 0 && !truncationHandled && ids != null && datas != null) {
                int deleted = 0;
                for (int i = 0; i < count && deleted < toDelete; i++) {
                    msgs.setSize(0);
                    parseRecord(datas[i], msgs);
                    int n = msgs.size();
                    int remainingToDelete = toDelete - deleted;
                    if (n <= remainingToDelete) {
                        try {
                            rs.deleteRecord(ids[i]);
                            deleted += n;
                        } catch (RecordStoreException ignore) {}
                    } else {
                        for (int k = 0; k < remainingToDelete; k++) {
                            msgs.removeElementAt(0);
                        }
                        String[] arr = vecToArray(msgs);
                        byte[] block = writeBlock(arr, arr.length);
                        if (block != null) {
                            try {
                                rs.setRecord(ids[i], block, 0, block.length);
                            } catch (RecordStoreException ignore) {}
                        }
                        deleted += remainingToDelete;
                        break;
                    }
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (en != null) {
                try { en.destroy(); } catch (Throwable ignore) {}
            }
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignore) {}
            }
        }
    }

    private static int estimateBlockSize(Vector msgs) {
        try {
            ensureReuseStreams();
            reuseDout.writeByte(BLOCK_VERSION);
            reuseDout.writeShort(msgs.size());
            for (int i = 0; i < msgs.size(); i++) {
                reuseDout.writeInt(0);
                reuseDout.writeUTF((String) msgs.elementAt(i));
            }
            reuseDout.flush();
            return reuseBout.size();
        } catch (Throwable e) {
            return MAX_BLOCK_BYTES + 1;
        }
    }

    private static String[] vecToArray(Vector v) {
        int n = v.size();
        String[] a = new String[n];
        for (int i = 0; i < n; i++) {
            a[i] = (String) v.elementAt(i);
        }
        return a;
    }

    private static void loadTailIntoBuffer(String storeName, StringBuffer buf) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(storeName, false);
            if (rs == null) {
                buf.setLength(0);
                return;
            }
            Vector slice = loadLastNWithIndex(rs, AppConstants.HISTORY_MAX_LOADED_LINES);
            buf.setLength(0);
            for (int i = 0; i < slice.size(); i++) {
                String s = (String) slice.elementAt(i);
                if (s != null) buf.append(s).append('\n');
            }
        } catch (RecordStoreException e) {
            buf.setLength(0);
        } catch (Throwable ignore) {
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignore) {}
            }
        }
    }

    private static boolean loadOlderIntoBuffer(String storeName, StringBuffer buf) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(storeName, false);
            if (rs == null) return false;
            int current = countLines(buf);
            Vector slice = loadOlderBatchWithIndex(rs, current);
            if (slice == null || slice.size() == 0) return false;
            String existing = buf.toString();
            buf.setLength(0);
            for (int i = 0; i < slice.size(); i++) {
                String s = (String) slice.elementAt(i);
                if (s != null) buf.append(s).append('\n');
            }
            buf.append(existing);
            return true;
        } catch (Throwable ignore) {
            return false;
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignore) {}
            }
        }
    }

    /**
     * Load last N messages using index: one RMS pass, parse only records containing the slice.
     */
    private static Vector loadLastNWithIndex(RecordStore rs, int n) {
        return collectSliceWithIndex(rs, n, -1, -1);
    }

    /**
     * Load next batch of older messages. currentShown = lines already in buf. One RMS pass.
     */
    private static Vector loadOlderBatchWithIndex(RecordStore rs, int currentShown) {
        return collectSliceWithIndex(rs, -1, currentShown, AppConstants.HISTORY_MAX_LOADED_LINES);
    }

    /**
     * Build index and collect slice in one RMS pass. Parse only records containing the slice.
     * Mode A (tail): n>=0 -> return last n messages
     * Mode B (older): n<0, currentShown>=0, batch>0 -> return next batch of older
     */
    private static Vector collectSliceWithIndex(RecordStore rs, int n, int currentShown, int batch) {
        Vector result = new Vector();
        RecordEnumeration en = null;
        try {
            int num = rs.getNumRecords();
            if (num <= 0) return result;
            en = rs.enumerateRecords(null, null, false);
            int[] ids = new int[num];
            byte[][] datas = new byte[num][];
            int[] msgCounts = new int[num];
            int count = 0;
            int totalMsgs = 0;
            while (en.hasNextElement() && count < num) {
                int id = en.nextRecordId();
                byte[] data = rs.getRecord(id);
                ids[count] = id;
                datas[count] = data;
                int mc = (data != null && data.length >= 3 && data[0] == BLOCK_VERSION)
                    ? (((data[1] & 0xFF) << 8) | (data[2] & 0xFF)) : 0;
                msgCounts[count] = mc;
                totalMsgs += mc;
                count++;
            }
            sortByIdAndData(ids, datas, count);

            int startIdx;
            int endIdx;
            if (n >= 0) {
                startIdx = totalMsgs - n;
                if (startIdx < 0) startIdx = 0;
                endIdx = totalMsgs;
            } else {
                int remaining = totalMsgs - currentShown;
                if (remaining <= 0 || batch <= 0) return result;
                if (batch > remaining) batch = remaining;
                startIdx = remaining - batch;
                endIdx = remaining;
            }
            if (startIdx >= totalMsgs || startIdx >= endIdx) return result;
            if (endIdx > totalMsgs) endIdx = totalMsgs;

            int cumCount = 0;
            int firstRecord = -1;
            int lastRecord = -1;
            int localStart = 0;
            int localEnd = 0;
            for (int i = 0; i < count; i++) {
                int nextCum = cumCount + msgCounts[i];
                if (firstRecord < 0 && nextCum > startIdx) {
                    firstRecord = i;
                    localStart = startIdx - cumCount;
                }
                if (lastRecord < 0 && nextCum >= endIdx) {
                    lastRecord = i;
                    localEnd = endIdx - cumCount;
                    break;
                }
                cumCount = nextCum;
            }
            if (firstRecord < 0 || lastRecord < 0) return result;

            Vector block = new Vector();
            for (int r = firstRecord; r <= lastRecord; r++) {
                block.setSize(0);
                parseRecord(datas[r], block);
                int a = (r == firstRecord) ? localStart : 0;
                int b = (r == lastRecord) ? localEnd : block.size();
                for (int j = a; j < b; j++) {
                    result.addElement(block.elementAt(j));
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (en != null) {
                try { en.destroy(); } catch (Throwable ignore) {}
            }
        }
        return result;
    }

    private static Vector collectAllInOrder(RecordStore rs) {
        Vector all = new Vector();
        RecordEnumeration en = null;
        try {
            int num = rs.getNumRecords();
            if (num <= 0) return all;
            en = rs.enumerateRecords(null, null, false);
            int[] ids = new int[num];
            byte[][] datas = new byte[num][];
            int count = 0;
            while (en.hasNextElement() && count < num) {
                int id = en.nextRecordId();
                ids[count] = id;
                datas[count] = rs.getRecord(id);
                count++;
            }
            sortByIdAndData(ids, datas, count);
            Vector block = new Vector();
            for (int i = 0; i < count; i++) {
                block.setSize(0);
                parseRecord(datas[i], block);
                for (int j = 0; j < block.size(); j++) {
                    all.addElement(block.elementAt(j));
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (en != null) {
                try { en.destroy(); } catch (Throwable ignore) {}
            }
        }
        return all;
    }

    private static void updateLastStatus(String storeName, String marker, String newStatus) {
        RecordStore rs = null;
        RecordEnumeration en = null;
        try {
            rs = RecordStore.openRecordStore(storeName, false);
            if (rs == null) return;
            int num = rs.getNumRecords();
            if (num <= 0) return;
            en = rs.enumerateRecords(null, null, false);
            int[] ids = new int[num];
            byte[][] datas = new byte[num][];
            int count = 0;
            while (en.hasNextElement() && count < num) {
                int id = en.nextRecordId();
                ids[count] = id;
                datas[count] = rs.getRecord(id);
                count++;
            }
            if (count == 0) return;
            sortByIdAndData(ids, datas, count);
            int lastIdx = count - 1;
            int lastId = ids[lastIdx];
            byte[] lastData = datas[lastIdx];
            Vector msgs = new Vector();
            parseRecord(lastData, msgs);
            if (msgs.size() == 0) return;
            int lastMsgIdx = msgs.size() - 1;
            String lastLine = (String) msgs.elementAt(lastMsgIdx);
            String updated = replaceStatusToken(lastLine, marker, newStatus);
            if (updated == null) return;
            msgs.setElementAt(updated, lastMsgIdx);
            String[] arr = vecToArray(msgs);
            byte[] block = writeBlock(arr, arr.length);
            if (block != null) {
                rs.setRecord(lastId, block, 0, block.length);
            }
        } catch (Throwable ignore) {
        } finally {
            if (en != null) {
                try { en.destroy(); } catch (Throwable ignore) {}
            }
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (RecordStoreException ignore) {}
            }
        }
    }

    private static String replaceStatusToken(String line, String marker, String newStatus) {
        int slen = marker.length();
        int pos = -1;
        for (int i = line.length() - slen; i >= 0; i--) {
            boolean match = true;
            for (int j = 0; j < slen; j++) {
                if (line.charAt(i + j) != marker.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                pos = i;
                break;
            }
        }
        if (pos < 0) return null;
        int tokenEnd = pos + slen;
        int clen = line.length();
        if (tokenEnd < clen && line.charAt(tokenEnd) == ' ') {
            tokenEnd++;
            while (tokenEnd < clen && line.charAt(tokenEnd) >= '0' && line.charAt(tokenEnd) <= '9') tokenEnd++;
            if (tokenEnd < clen && line.charAt(tokenEnd) == '/') {
                tokenEnd++;
                while (tokenEnd < clen && line.charAt(tokenEnd) >= '0' && line.charAt(tokenEnd) <= '9') tokenEnd++;
            }
        }
        return line.substring(0, pos) + newStatus + line.substring(tokenEnd);
    }

    private static void sortByIdAndData(int[] ids, byte[][] datas, int count) {
        for (int i = 0; i < count - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < count; j++) {
                if (ids[j] < ids[minIdx]) minIdx = j;
            }
            if (minIdx != i) {
                int ti = ids[i];
                ids[i] = ids[minIdx];
                ids[minIdx] = ti;
                byte[] td = datas[i];
                datas[i] = datas[minIdx];
                datas[minIdx] = td;
            }
        }
    }

    private static int countLines(StringBuffer buf) {
        if (buf == null) return 0;
        int n = 0;
        int len = buf.length();
        for (int i = 0; i < len; i++) {
            if (buf.charAt(i) == '\n') n++;
        }
        return n;
    }

    private static void clearStore(String storeName) {
        try {
            RecordStore.deleteRecordStore(storeName);
        } catch (RecordStoreException ignore) {
        } catch (Throwable ignore) {}
    }

    private static void clearAll(String prefix) {
        String[] names = null;
        try {
            names = RecordStore.listRecordStores();
        } catch (Throwable ignore) {}
        if (names == null) return;
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            if (n != null && n.startsWith(prefix)) clearStore(n);
        }
    }
}
