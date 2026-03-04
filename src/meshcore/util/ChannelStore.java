package meshcore.util;

import java.util.Vector;

import meshcore.ui.ChannelListScreen;
import meshcore.util.AppConstants;

public final class ChannelStore {

    private final Vector names = new Vector();
    private final Vector buffers = new Vector();
    private final Vector unreadCounts = new Vector();

    public ChannelStore() {
        initChannels();
    }

    public void initChannels() {
        names.removeAllElements();
        buffers.removeAllElements();
        unreadCounts.removeAllElements();
        names.addElement(ChannelListScreen.PUBLIC_CHANNEL);
        buffers.addElement(new StringBuffer());
        unreadCounts.addElement(new Integer(0));
    }

    public Vector getNames() {
        return names;
    }

    public Vector getUnreadCounts() {
        return unreadCounts;
    }

    public int size() {
        return names.size();
    }

    public String getName(int index) {
        return (String) names.elementAt(index);
    }

    public StringBuffer getBuffer(int index) {
        return (StringBuffer) buffers.elementAt(index);
    }

    public boolean containsNameIgnoreCase(String name) {
        for (int i = 0; i < names.size(); i++) {
            if (name.equalsIgnoreCase((String) names.elementAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Adds a new channel slot at the end and returns its index. */
    public int addChannel(String name) {
        names.addElement(name);
        buffers.addElement(new StringBuffer());
        unreadCounts.addElement(new Integer(0));
        return names.size() - 1;
    }

    public void removeChannel(int index) {
        if (index < 0 || index >= names.size()) return;
        names.removeElementAt(index);
        buffers.removeElementAt(index);
        unreadCounts.removeElementAt(index);
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

    public void appendLine(int channelIndex, String line) {
        int idx = channelIndex;
        if (idx < 0 || idx >= buffers.size()) idx = 0;
        StringBuffer buf = (StringBuffer) buffers.elementAt(idx);
        buf.append(line).append("\n");
        if (buf.length() > AppConstants.MAX_BUFFER_LENGTH) {
            buf.delete(0, buf.length() - AppConstants.MAX_BUFFER_LENGTH);
        }
    }

    /** Ensure slot exists up to chIdx, used when channel info arrives from device. */
    public void ensureSlot(int chIdx) {
        while (names.size() <= chIdx) {
            names.addElement(chIdx == 0 ? ChannelListScreen.PUBLIC_CHANNEL : "");
            buffers.addElement(new StringBuffer());
            unreadCounts.addElement(new Integer(0));
        }
    }
}

