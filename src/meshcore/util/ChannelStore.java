package meshcore.util;

import java.util.Vector;

import meshcore.ui.ChannelListScreen;
import meshcore.util.AppConstants;
import meshcore.util.FrameUtils;

public final class ChannelStore {

    private final Vector names = new Vector();
    private final Vector buffers = new Vector();
    private final Vector unreadCounts = new Vector();
    /** Optional 32-hex secret per channel (null for public/hashtag or when not stored). */
    private final Vector secrets = new Vector();

    public ChannelStore() {
        initChannels();
    }

    public void initChannels() {
        names.removeAllElements();
        buffers.removeAllElements();
        unreadCounts.removeAllElements();
        secrets.removeAllElements();
        names.addElement(ChannelListScreen.PUBLIC_CHANNEL);
        buffers.addElement(new StringBuffer());
        unreadCounts.addElement(new Integer(0));
        secrets.addElement(null);
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
        return addChannel(name, null);
    }

    /** Adds a new channel with optional secret (16 bytes). */
    public int addChannel(String name, byte[] secretBytes) {
        names.addElement(name);
        buffers.addElement(new StringBuffer());
        unreadCounts.addElement(new Integer(0));
        String hex = (secretBytes != null && secretBytes.length == 16)
                ? FrameUtils.bytesToHex(secretBytes, 0, 16) : null;
        secrets.addElement(hex);
        return names.size() - 1;
    }

    /** Stored secret (32 hex) for channel, or null if not stored. */
    public String getSecretHex(int index) {
        if (index < 0 || index >= secrets.size()) return null;
        return (String) secrets.elementAt(index);
    }

    /**
     * Clears a channel slot in place (does not shift indices).
     * Matches device protocol: slots are fixed indices; clearing mid-list must not compact,
     * otherwise sync ({@code onChannelInfo}) reintroduces empty slots as blank UI rows.
     */
    public void removeChannel(int index) {
        if (index <= 0 || index >= names.size()) return;
        names.setElementAt("", index);
        ((StringBuffer) buffers.elementAt(index)).setLength(0);
        unreadCounts.setElementAt(new Integer(0), index);
        secrets.setElementAt(null, index);
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
            secrets.addElement(null);
        }
    }
}

