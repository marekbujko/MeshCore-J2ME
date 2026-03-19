package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import java.util.Vector;

import meshcore.util.TextUtils;

/**
 * Channel list: Channel Info, Share with QR Code, Remove.
 * Add channels via More → Add Channel.
 */
public class ChannelListScreen extends List implements CommandListener {

    public static final String PUBLIC_CHANNEL = "Public";

    private final AppController app;
    private final Vector channelNames;
    private final Vector channelUnreadCount;
    private final Command cmdChannelInfo;
    private final Command cmdShareQr;
    private final Command cmdRemove;
    private final Command cmdBack;

    /**
     * List row index -> channel store index (device slot).
     * Empty slots (cleared channels) are omitted from the list but keep their index in storage.
     */
    private int[] rowToStoreIndex = new int[0];

    /** Last selected channel slot (store index), for selection after refresh / reopen. */
    private static int lastSelectedStoreIndex = 0;

    public ChannelListScreen(AppController app, Vector channelNames, Vector channelUnreadCount) {
        super("Channels", List.IMPLICIT);
        this.app = app;
        this.channelNames = channelNames;
        this.channelUnreadCount = channelUnreadCount;
        refreshList();
        cmdChannelInfo = new Command("Channel Info", Command.SCREEN, 1);
        cmdShareQr = new Command("Share with QR Code", Command.SCREEN, 2);
        cmdRemove = new Command("Remove Channel", Command.SCREEN, 3);
        cmdBack = new Command("Back", Command.BACK, 4);
        addCommand(cmdChannelInfo);
        addCommand(cmdShareQr);
        addCommand(cmdRemove);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    private int storeIndexForListRow(int listRow) {
        if (listRow < 0 || listRow >= rowToStoreIndex.length) return -1;
        return rowToStoreIndex[listRow];
    }

    private void syncListSelectionToLastStoreIndex() {
        int n = rowToStoreIndex.length;
        if (n <= 0) return;
        int rowToSelect = 0;
        for (int r = 0; r < n; r++) {
            if (rowToStoreIndex[r] == lastSelectedStoreIndex) {
                rowToSelect = r;
                break;
            }
        }
        setSelectedIndex(rowToSelect, true);
    }

    public void refreshList() {
        deleteAll();
        int cap = channelNames.size();
        if (cap <= 0) {
            rowToStoreIndex = new int[0];
            return;
        }
        int[] map = new int[cap];
        int rows = 0;
        for (int i = 0; i < cap; i++) {
            String raw = (String) channelNames.elementAt(i);
            if (i > 0 && (raw == null || raw.length() == 0)) {
                continue;
            }
            int unread = (i < channelUnreadCount.size())
                    ? ((Integer) channelUnreadCount.elementAt(i)).intValue() : 0;
            String displayName = app.getChannelDisplayName(i);
            String label = (unread > 0) ? (displayName + " (" + unread + " new)") : displayName;
            append(label, null);
            map[rows++] = i;
        }
        rowToStoreIndex = new int[rows];
        System.arraycopy(map, 0, rowToStoreIndex, 0, rows);
        syncListSelectionToLastStoreIndex();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
            return;
        }
        if (c == cmdChannelInfo) {
            int row = getSelectedIndex();
            int storeIdx = storeIndexForListRow(row);
            if (storeIdx >= 0 && storeIdx < channelNames.size()) {
                app.getDisplay().setCurrent(new ChannelInfoScreen(app, storeIdx, this));
            }
            return;
        }
        if (c == cmdShareQr) {
            int row = getSelectedIndex();
            int storeIdx = storeIndexForListRow(row);
            if (storeIdx >= 0 && storeIdx < channelNames.size()) {
                String name = app.getChannelName(storeIdx);
                String secret = app.getChannelSecretHex(storeIdx);
                String url = "meshcore://channel/add?name=" + TextUtils.urlEncode(name)
                        + "&secret=" + (secret != null ? secret : "");
                String title = (name != null && name.length() > 0) ? name : "Channel";
                app.getDisplay().setCurrent(new ShareQrScreen(app, url, title, this));
            }
            return;
        }
        if (c == cmdRemove) {
            final int row = getSelectedIndex();
            final int storeIdx = storeIndexForListRow(row);
            if (storeIdx <= 0) {
                Alerts.info(app.getDisplay(), this, "Channel", "Cannot remove Public channel");
                return;
            }
            if (storeIdx > 0 && storeIdx < channelNames.size()) {
                String name = app.getChannelName(storeIdx);
                javax.microedition.lcdui.Alert confirm =
                        Alerts.confirm("Remove Channel", "Remove \"" + name + "\"?");
                confirm.addCommand(new Command("Yes", Command.OK, 1));
                confirm.addCommand(new Command("No", Command.CANCEL, 2));
                confirm.setCommandListener(new CommandListener() {
                    public void commandAction(Command cmd, Displayable disp) {
                        if (cmd.getCommandType() == Command.OK) {
                            app.removeChannel(storeIdx);
                            if (lastSelectedStoreIndex == storeIdx) {
                                lastSelectedStoreIndex = 0;
                            }
                            app.showChannelListScreen();
                        } else {
                            app.getDisplay().setCurrent(ChannelListScreen.this);
                        }
                    }
                });
                app.getDisplay().setCurrent(confirm, this);
            }
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int row = getSelectedIndex();
            int storeIdx = storeIndexForListRow(row);
            if (storeIdx >= 0) {
                lastSelectedStoreIndex = storeIdx;
                app.showChannelScreen(storeIdx);
            }
        }
    }
}
