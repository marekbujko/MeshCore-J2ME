package meshcore.util;

import java.util.Vector;

import meshcore.util.AppConstants;

public final class ContactStore {

    private final Vector names = new Vector();
    private final Vector keys = new Vector();
    private final Vector types = new Vector();
    private final Vector pathHops = new Vector();
    private final Vector dmBuffers = new Vector();
    private final Vector unreadCounts = new Vector();

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

    /** Hop count for contact (0 = direct, >0 = N hops), or -1 if unknown. */
    public int getPathHopsCount(int index) {
        if (index < 0 || index >= pathHops.size()) return -1;
        return ((Integer) pathHops.elementAt(index)).intValue();
    }

    public int size() {
        return names.size();
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

