package meshcore.util;

import java.util.Vector;

import meshcore.util.AppConstants;

public final class ContactStore {

    private final Vector names = new Vector();
    private final Vector keys = new Vector();
    private final Vector types = new Vector();
    private final Vector pathHops = new Vector();
    private final Vector pathBytes = new Vector();
    private final Vector contactFlags = new Vector();
    private final Vector lastAdvert = new Vector();
    private final Vector dmBuffers = new Vector();
    private final Vector unreadCounts = new Vector();
    /** Advert latitude * 1E6 (int32). */
    private final Vector advLatE6 = new Vector();
    /** Advert longitude * 1E6 (int32). */
    private final Vector advLonE6 = new Vector();

    public Vector getNames() {
        return names;
    }

    public Vector getKeys() {
        return keys;
    }

    public Vector getTypes() {
        return types;
    }

    public Vector getPathHops() {
        return pathHops;
    }

    public Vector getPathBytes() {
        return pathBytes;
    }

    /** Hop count for contact (0 = direct, >0 = N hops), or -1 if unknown. */
    public int getPathHopsCount(int index) {
        if (index < 0 || index >= pathHops.size()) return -1;
        return ((Integer) pathHops.elementAt(index)).intValue();
    }

    /** Path bytes for contact (1 byte per hop = node hash = first byte of repeater pub key). */
    public byte[] getPathBytes(int index) {
        if (index < 0 || index >= pathBytes.size()) return null;
        return (byte[]) pathBytes.elementAt(index);
    }

    public int getFlags(int index) {
        if (index < 0 || index >= contactFlags.size()) return 0;
        return ((Integer) contactFlags.elementAt(index)).intValue();
    }

    public long getLastAdvert(int index) {
        if (index < 0 || index >= lastAdvert.size()) return 0;
        return ((Long) lastAdvert.elementAt(index)).longValue();
    }

    public Vector getContactFlags() {
        return contactFlags;
    }

    public Vector getLastAdvert() {
        return lastAdvert;
    }

    public int size() {
        return names.size();
    }

    public int getType(int index) {
        if (index < 0 || index >= types.size()) return 0;
        return ((Integer) types.elementAt(index)).intValue();
    }

    public String getName(int index) {
        return (String) names.elementAt(index);
    }

    public StringBuffer getDmBuffer(int index) {
        ensureDmSize(index + 1);
        return (StringBuffer) dmBuffers.elementAt(index);
    }

    public void ensureDmSize(int size) {
        while (dmBuffers.size() < size) {
            dmBuffers.addElement(new StringBuffer());
        }
    }

    public void ensureUnreadSize(int size) {
        while (unreadCounts.size() < size) {
            unreadCounts.addElement(new Integer(0));
        }
    }

    public void incUnread(int idx) {
        ensureUnreadSize(idx + 1);
        int n = ((Integer) unreadCounts.elementAt(idx)).intValue();
        unreadCounts.setElementAt(new Integer(n + 1), idx);
    }

    public void setUnread(int idx, int val) {
        ensureUnreadSize(idx + 1);
        unreadCounts.setElementAt(new Integer(val), idx);
    }

    public Vector getUnreadCounts() {
        return unreadCounts;
    }

    public Vector getAdvLatE6() {
        return advLatE6;
    }

    public Vector getAdvLonE6() {
        return advLonE6;
    }

    public int getAdvLatE6(int index) {
        if (index < 0 || index >= advLatE6.size()) return Integer.MIN_VALUE;
        Integer v = (Integer) advLatE6.elementAt(index);
        return (v != null) ? v.intValue() : Integer.MIN_VALUE;
    }

    public int getAdvLonE6(int index) {
        if (index < 0 || index >= advLonE6.size()) return Integer.MIN_VALUE;
        Integer v = (Integer) advLonE6.elementAt(index);
        return (v != null) ? v.intValue() : Integer.MIN_VALUE;
    }

    public void appendDmLine(int contactIdx, String line) {
        if (contactIdx < 0) return;
        ensureDmSize(contactIdx + 1);
        StringBuffer buf = (StringBuffer) dmBuffers.elementAt(contactIdx);
        buf.append(line).append("\n");
        if (buf.length() > AppConstants.MAX_BUFFER_LENGTH) {
            buf.delete(0, buf.length() - AppConstants.MAX_BUFFER_LENGTH);
        }
    }
}

