import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Vector;

import meshcore.protocol.FrameHandler;
import meshcore.protocol.FrameHandlerListener;
import meshcore.protocol.ProtocolConstants;
import meshcore.protocol.MeshProtocolClient;
import meshcore.net.FrameTransport;
import meshcore.net.ConnectionManager;
import meshcore.util.ActivityLogHelper;
import meshcore.util.AppConstants;
import meshcore.util.ChannelStore;
import meshcore.util.ContactStore;
import meshcore.util.ConnectStorage;
import meshcore.util.FrameUtils;
import meshcore.util.ImageUtils;
import meshcore.util.ParseUtils;
import meshcore.util.SHA256;
import meshcore.util.TextUtils;
import meshcore.ui.AppController;
import meshcore.ui.ActivityLogScreen;
import meshcore.ui.ChannelListScreen;
import meshcore.ui.ConnectScreen;
import meshcore.ui.MainMenuScreen;
import meshcore.ui.ChannelScreen;
import meshcore.ui.ContactsScreen;
import meshcore.ui.RepeatersScreen;
import meshcore.ui.NotificationsScreen;
import meshcore.ui.DMScreen;
import meshcore.ui.SettingsScreen;
import meshcore.ui.Alerts;
import meshcore.ui.NotificationPresenter;
import meshcore.ui.SettingsPresenter;

/**
 * MeshCore WiFi TCP Client for Nokia Asha 210 (J2ME MIDP 2.0 / CLDC 1.1)
 *
 * Screens:
 *   1. ConnectScreen    - IP/port entry
 *   2. MainMenu         - Channels / Contacts / Repeaters / Activity Log / Settings / Disconnect
 *   3. ChannelListScreen- Channel list selector
 *   4. ChannelScreen    - Public broadcast chat
 *   5. ContactsScreen   - Contact list (non-repeaters), tap to open DM
 *   6. RepeatersScreen  - Repeaters discovered via adverts
 *   7. DMScreen         - Direct message with a contact
 *   8. SettingsScreen   - Radio params, node name, battery, stats
 *   9. ActivityLogScreen- App + protocol event log
 *
 * Protocol: MeshCore Companion Radio binary frame protocol over TCP
 * Reference: https://github.com/meshcore-dev/MeshCore/wiki/Companion-Radio-Protocol
 */
public class MeshCore extends MIDlet implements AppController, FrameHandlerListener {

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private Display display;

    private StreamConnection conn;
    private FrameTransport transport;
    private volatile boolean connected = false;
    private volatile boolean running = false;

    private String myName = "";
    private String nodeName = "";
    private String firmwareVer = "";
    private String nodePublicKeyHex = "";

    private ChannelStore channelStore = new ChannelStore();
    private ContactStore contactStore = new ContactStore();
    private StringBuffer activityLogBuf = new StringBuffer();

    private long nodeFreq = 915000L;
    private long nodeBw = 250L;
    private int nodeSf = 10;
    private int nodeCr = 5;
    private int nodeTxPwr = 20;
    private int advertType = 0; // 0 = Flood, 1 = Zero hop

    // Screen references (for UI updates from background threads)
    private ConnectScreen connectScreen;
    private MainMenuScreen mainMenuScreen;
    private ChannelListScreen channelListScreen;
    private ChannelScreen channelScreen;
    private ContactsScreen contactsScreen;
    private NotificationsScreen notificationsScreen;
    private DMScreen dmScreen;
    private SettingsScreen settingsScreen;
    private RepeatersScreen repeatersScreen;
    private ActivityLogScreen activityLogScreen;

    private FrameHandler frameHandler;
    private String lastBatteryStatus = "";
    private volatile boolean keepAliveRunning = false;
    private volatile boolean userRequestedDisconnect = false;
    private volatile boolean reconnectScheduled = false;
    private volatile int titleRotationIndex = 0;
    private volatile boolean notificationBlinkRunning = false;

    private ConnectionManager connectionManager;

    // -----------------------------------------------------------------------
    // MIDlet lifecycle
    // -----------------------------------------------------------------------
    public void startApp() {
        display = Display.getDisplay(this);
        frameHandler = new FrameHandler(this, contactStore.getNames(), contactStore.getKeys(), contactStore.getTypes());
        connectionManager = new ConnectionManager(new ConnectionManager.Listener() {
            public void onFrame(byte[] frame) {
                frameHandler.handleFrame(frame);
            }
            public void onConnectionLost() {
                if (running) appendActivityLog("[!] Connection lost");
                MeshCore.this.onConnectionLost();
            }
            public void onKeepAliveError(String message) {
                appendActivityLog(message);
            }
        });
        showSplashScreen();
    }

    private void showSplashScreen() {
        connectScreen = new ConnectScreen(this);
        Image logo = null;
        try {
            logo = Image.createImage("/logo.png");
        } catch (IOException ignore) {
            try {
                logo = Image.createImage("/logo.jpg");
            } catch (IOException ignore2) {}
        }
        display.setCurrent(ImageUtils.createSplashCanvas(logo));
        new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(AppConstants.SPLASH_DELAY_MS); } catch (InterruptedException ignore) {}
                display.callSerially(new Runnable() {
                    public void run() {
                        display.setCurrent(connectScreen);
                    }
                });
            }
        }).start();
    }

    private void initChannels() {
        channelStore.initChannels();
    }

    public void pauseApp() {}

    public void destroyApp(boolean u) {
        disconnect();
    }

    // -----------------------------------------------------------------------
    // AppController: navigation
    // -----------------------------------------------------------------------
    public Display getDisplay() {
        return display;
    }

    public void showMainMenu() {
        if (mainMenuScreen == null) {
            mainMenuScreen = new MainMenuScreen(this);
        }
        updateMainMenuTitle();
        display.setCurrent(mainMenuScreen);
    }

    /** Set main menu title: when connected rotate with voltage; when disconnected cycle Disconnected/MeshCore. */
    private void updateMainMenuTitle() {
        final MainMenuScreen menu = mainMenuScreen;
        if (menu == null) return;
        String title = TextUtils.computeMainMenuTitle(
                connected,
                reconnectScheduled,
                lastBatteryStatus,
                titleRotationIndex
        );
        menu.setTitle(title);
        // When not connected (including reconnecting / after give-up), show "Connect To" instead of "Disconnect".
        menu.setConnectCommandMode(!connected);
    }

    private void startTitleRotation() {
        new Thread(new Runnable() {
            public void run() {
                while (connected && running) {
                    try { Thread.sleep(AppConstants.TITLE_ROTATION_MS); } catch (InterruptedException e) { return; }
                    if (!connected || !running) break;
                    titleRotationIndex++;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (mainMenuScreen != null && display.getCurrent() == mainMenuScreen && connected) {
                                updateMainMenuTitle();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    /** Cycle Disconnected / MeshCore when disconnected (stops when reconnected or user disconnects). */
    private void startDisconnectedTitleRotation() {
        new Thread(new Runnable() {
            public void run() {
                while (!connected && !userRequestedDisconnect) {
                    try { Thread.sleep(AppConstants.TITLE_ROTATION_MS); } catch (InterruptedException e) { return; }
                    if (connected || userRequestedDisconnect) break;
                    titleRotationIndex++;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (mainMenuScreen != null && display.getCurrent() == mainMenuScreen && !connected) {
                                updateMainMenuTitle();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    public void showConnectScreen() {
        connectScreen = new ConnectScreen(this);
        display.setCurrent(connectScreen);
    }

    public void showChannelListScreen() {
        channelListScreen = new ChannelListScreen(this, channelStore.getNames(), channelStore.getUnreadCounts());
        display.setCurrent(channelListScreen);
    }

    public void showChannelScreen(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channelStore.size()) return;
        setChannelUnread(channelIndex, 0);
        String name = channelStore.getName(channelIndex);
        StringBuffer buf = channelStore.getBuffer(channelIndex);
        channelScreen = new ChannelScreen(this, channelIndex, name, buf);
        display.setCurrent(channelScreen);
        trySyncMessages();
    }

    public void addChannel(String name) {
        addChannel(name, null);
    }

    public void addChannel(String name, byte[] secretBytes) {
        if (channelStore.containsNameIgnoreCase(name)) {
            return;
        }
        int idx = channelStore.addChannel(name);
        sendSetChannel(idx, name, secretBytes);
    }

    public void addPrivateChannel(String name, String secretHex) {
        byte[] secret = FrameUtils.hexDecode(secretHex);
        if (secret == null || secret.length != 16) {
            appendActivityLog("[!] Secret must be 32 hex chars (16 bytes)");
            return;
        }
        addChannel(name, secret);
    }

    public void removeChannel(int index) {
        if (index <= 0 || index >= channelStore.size()) return;
        sendClearChannelSlot(index);
        channelStore.removeChannel(index);
    }

    /** Tell the node to clear this channel slot (empty name + zero secret). */
    private void sendClearChannelSlot(int channelIndex) {
        if (!connected || transport == null) return;
        try {
            byte[] f = new byte[2 + 32 + 16];
            f[0] = (byte) ProtocolConstants.CMD_SET_CHANNEL;
            f[1] = (byte) (channelIndex & 0xFF);
            for (int i = 2; i < 2 + 32 + 16; i++) f[i] = 0;
            transport.sendFrame(f);
        } catch (Exception e) {
            appendActivityLog("[!] Clear channel failed");
        }
    }

    public void showContactsScreen() {
        ensureContactUnreadSize(contactStore.size());
        contactsScreen = new ContactsScreen(this, contactStore.getNames(), contactStore.getUnreadCounts(), contactStore.getTypes());
        display.setCurrent(contactsScreen);
    }

    public void showRepeatersScreen() {
        repeatersScreen = new RepeatersScreen(this, contactStore.getNames(), contactStore.getTypes());
        display.setCurrent(repeatersScreen);
    }

    public void showDMScreen(int idx) {
        ensureDmBuffersSize(contactStore.size());
        ensureContactUnreadSize(contactStore.size());
        setContactUnread(idx, 0);
        String name = contactStore.getName(idx);
        StringBuffer buf = contactStore.getDmBuffer(idx);
        dmScreen = new DMScreen(this, idx, name, buf);
        display.setCurrent(dmScreen);
        trySyncMessages();
    }

    public void showSettingsScreen() {
        settingsScreen = new SettingsScreen(this, nodeName, firmwareVer,
                nodePublicKeyHex, nodeFreq, nodeBw, nodeSf, nodeCr, nodeTxPwr);
        display.setCurrent(settingsScreen);
        new Thread(new Runnable() {
            public void run() {
                sendGetBattery();
            }
        }).start();
    }

    public void showActivityLogScreen() {
        activityLogScreen = new ActivityLogScreen(this, activityLogBuf);
        display.setCurrent(activityLogScreen);
    }

    public void showNotificationsScreen() {
        notificationsScreen = new NotificationsScreen(
                this,
                channelStore.getNames(), channelStore.getUnreadCounts(),
                contactStore.getNames(), contactStore.getUnreadCounts()
        );
        display.setCurrent(notificationsScreen);
    }

    // -----------------------------------------------------------------------
    // AppController: actions
    // -----------------------------------------------------------------------
    public void connect(String host, int port) {
        userRequestedDisconnect = false;
        setConnectTitle("Connecting...");
        try {
            conn = (StreamConnection) Connector.open("socket://" + host + ":" + port);
            transport = new FrameTransport(conn.openInputStream(), conn.openOutputStream());
            setupConnectedState(host, port, true);
            display.callSerially(new Runnable() {
                public void run() {
                    showMainMenu();
                    startTitleRotation();
                }
            });
        } catch (Exception e) {
            setConnectTitle("Failed: " + e.getMessage());
            appendActivityLog("[!] Connect failed: " + e.getMessage());
            connected = false;
        }
    }

    public void connectWithSplash(final String host, final int port) {
        Image logo = null;
        try {
            logo = Image.createImage("/logo.png");
        } catch (IOException ignore) {
            try {
                logo = Image.createImage("/logo.jpg");
            } catch (IOException ignore2) {}
        }
        final String[] status = new String[]{"Connecting..."};
        final Canvas splash = ImageUtils.createStatusSplashCanvas(logo, status);
        display.setCurrent(splash);
        new Thread(new Runnable() {
            public void run() {
                userRequestedDisconnect = false;
                try {
                    conn = (StreamConnection) Connector.open("socket://" + host + ":" + port);
                    transport = new FrameTransport(conn.openInputStream(), conn.openOutputStream());
                    setupConnectedState(host, port, false);
                    status[0] = "Connected";
                    display.callSerially(new Runnable() {
                        public void run() {
                            splash.repaint();
                        }
                    });
                    try {
                        Thread.sleep(AppConstants.POST_CONNECT_DELAY_MS);
                    } catch (InterruptedException ignore) {}
                    display.callSerially(new Runnable() {
                        public void run() {
                            showMainMenu();
                            startTitleRotation();
                        }
                    });
                } catch (Exception e) {
                    final String err = e.getMessage();
                    appendActivityLog("[!] Connect failed: " + err);
                    connected = false;
                    connectScreen = new ConnectScreen(MeshCore.this);
                    connectScreen.setTitle("Failed: " + err);
                    display.callSerially(new Runnable() {
                        public void run() {
                            display.setCurrent(connectScreen);
                        }
                    });
                }
            }
        }).start();
    }

    /** Common initialization sequence after TCP socket + transport are created. */
    private void setupConnectedState(String host, int port, boolean updateConnectTitle) throws IOException {
        connected = true;
        ConnectStorage.save(host, String.valueOf(port));
        if (updateConnectTitle) {
            setConnectTitle("Connected");
        }
        appendActivityLog("[*] Connected");
        initChannels();

        transport.sendFrame(new byte[]{
            (byte) ProtocolConstants.CMD_DEVICE_QUERY,
            (byte) ProtocolConstants.APP_VER
        });

        byte[] nb = myName.getBytes("UTF-8");
        byte[] as = new byte[2 + 6 + nb.length];
        as[0] = (byte) ProtocolConstants.CMD_APP_START;
        as[1] = (byte) ProtocolConstants.APP_VER;
        System.arraycopy(nb, 0, as, 8, nb.length);
        transport.sendFrame(as);

        byte[] st = new byte[5];
        st[0] = (byte) ProtocolConstants.CMD_SET_DEVICE_TIME;
        FrameTransport.writeUint32LE(st, 1, getEpoch());
        transport.sendFrame(st);

        sendGetContacts();
        sendGetChannels();
        scheduleChannelsSyncedLog();

        // Kick off initial battery query so main menu shows voltage quickly
        sendGetBattery();

        running = true;
        if (connectionManager != null) {
            connectionManager.setTransport(transport);
            connectionManager.startLoops();
        }
    }

    private void closeConnection() {
        transport = null;
        try {
            if (conn != null) conn.close();
        } catch (IOException ignore) {}
        conn = null;
    }

    /** Shared cleanup for both user-initiated disconnect and unexpected loss. */
    private void resetConnectionFlags(boolean userInitiated) {
        userRequestedDisconnect = userInitiated;
        running = false;
        connected = false;
        keepAliveRunning = false;
        if (connectionManager != null) {
            connectionManager.stop();
        }
        closeConnection();
    }

    public void disconnect() {
        resetConnectionFlags(true);
    }

    private void onConnectionLost() {
        resetConnectionFlags(false);
        display.callSerially(new Runnable() {
            public void run() {
                if (mainMenuScreen != null) {
                    updateMainMenuTitle();
                    startDisconnectedTitleRotation();
                }
            }
        });
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (userRequestedDisconnect || reconnectScheduled) return;
        reconnectScheduled = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String[] hp = ConnectStorage.loadHostAndPort();
                    int port = ParseUtils.parseInt(hp[1], 5000);
                    for (int attempt = 0; attempt < AppConstants.RECONNECT_ATTEMPTS; attempt++) {
                        try {
                            Thread.sleep(AppConstants.RECONNECT_DELAY_MS);
                        } catch (InterruptedException e) { return; }
                        if (userRequestedDisconnect) return;
                        connect(hp[0], port);
                        if (connected) return;
                        appendActivityLog("[!] Reconnect " + (attempt + 1) + "/" + AppConstants.RECONNECT_ATTEMPTS + " failed");
                    }
                    appendActivityLog("[!] Reconnect gave up");
                } finally {
                    reconnectScheduled = false;
                }
            }
        }).start();
    }

    public void sendChannelMessage(int channelIndex, String msg) {
        if (channelIndex < 0 || channelIndex >= channelStore.size()) return;
        try {
            MeshProtocolClient.sendChannelMessage(transport, channelIndex, msg, getEpoch());
            appendChannel(channelIndex, "[me] " + msg);
        } catch (IOException e) {
            appendActivityLog("[!] Send error");
        }
    }

    public void sendDirectMessage(int idx, String msg) {
        try {
            byte[] key = (byte[]) contactStore.getKeys().elementAt(idx);
            MeshProtocolClient.sendDirectMessage(transport, key, msg, getEpoch());
            appendDM(idx, "[me] " + msg);
        } catch (IOException e) {
            appendDM(idx, "[!] Send error");
        }
    }

    public void sendGetContacts() {
        if (transport == null) return;
        try {
            MeshProtocolClient.sendGetContacts(transport);
        } catch (IOException e) {
            appendActivityLog("[!] Sync failed");
        }
    }

    public void sendGetChannels() {
        try {
            MeshProtocolClient.sendGetChannels(transport);
        } catch (IOException e) {
            appendActivityLog("[!] Get channels failed");
        }
    }

    public void sendSetChannel(int channelIndex, String name) {
        sendSetChannel(channelIndex, name, null);
    }

    public void sendSetChannel(int channelIndex, String name, byte[] secretBytes) {
        try {
            MeshProtocolClient.sendSetChannel(transport, channelIndex, name, secretBytes);
        } catch (Exception e) {
            appendActivityLog("[!] Set channel failed");
        }
    }

    public void removeContact(int idx) {
        if (idx < 0 || idx >= contactStore.getKeys().size()) return;
        try {
            byte[] key = (byte[]) contactStore.getKeys().elementAt(idx);
            MeshProtocolClient.removeContact(transport, key);
            sendGetContacts();
        } catch (IOException e) {
            appendActivityLog("[!] Remove failed");
        }
    }

    public void sendRefreshSettings() {
        try {
            MeshProtocolClient.sendRefreshSettings(transport, myName);
        } catch (IOException ignore) {}
    }

    public void sendGetBattery() {
        try {
            MeshProtocolClient.sendGetBattery(transport);
        } catch (IOException ignore) {}
    }

    public void sendGetStats() {
        try {
            MeshProtocolClient.sendGetStats(transport);
        } catch (IOException e) {
            appendActivityLog("[!] Stats failed");
        }
    }

    public void sendGetDeviceTime() {
        try {
            MeshProtocolClient.sendGetDeviceTime(transport);
        } catch (IOException e) {
            appendActivityLog("[!] Time failed");
        }
    }

    public void sendAdvert() {
        try {
            int t = MeshProtocolClient.sendAdvert(transport, advertType);
            String kind = (t == ProtocolConstants.ADVERT_ZERO_HOP) ? "Zero Hop" : "Flood Routed";
            appendActivityLog("[*] Advert sent (" + kind + ")");
        } catch (IOException ignore) {}
    }

    public void setAdvertType(int type) {
        advertType = (type == ProtocolConstants.ADVERT_ZERO_HOP)
            ? ProtocolConstants.ADVERT_ZERO_HOP : ProtocolConstants.ADVERT_FLOOD;
    }

    public int getAdvertType() {
        return advertType;
    }

    public void trySyncMessages() {
        if (!connected) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    MeshProtocolClient.sendSyncNextMessage(transport);
                } catch (IOException ignore) {}
            }
        }).start();
    }

    public void saveSettings() {
        try {
            String newName = settingsScreen.getNodeName();
            if (newName.length() > 0 && !newName.equals(nodeName)) {
                byte[] nb = newName.getBytes("UTF-8");
                byte[] f = new byte[1 + nb.length];
                f[0] = (byte) ProtocolConstants.CMD_SET_ADVERT_NAME;
                System.arraycopy(nb, 0, f, 1, nb.length);
                transport.sendFrame(f);
                nodeName = newName;
            }
            long[] vals = MeshProtocolClient.applySettingsAndBuildRadioFrames(
                    new MeshProtocolClient.SettingsScreenLike() {
                        public String getFreq() { return settingsScreen.getFreq(); }
                        public String getBw()   { return settingsScreen.getBw(); }
                        public String getSf()   { return settingsScreen.getSf(); }
                        public String getCr()   { return settingsScreen.getCr(); }
                        public String getTxPwr(){ return settingsScreen.getTxPwr(); }
                    },
                    nodeFreq, nodeBw, nodeSf, nodeCr, nodeTxPwr,
                    transport
            );
            nodeFreq = vals[0];
            nodeBw   = vals[1];
            nodeSf   = (int) vals[2];
            nodeCr   = (int) vals[3];
            nodeTxPwr= (int) vals[4];
            appendActivityLog("[*] Settings saved");
            final SettingsScreen set = settingsScreen;
            display.callSerially(new Runnable() {
                public void run() {
                    if (set != null) {
                        set.setNodeInfo(nodeName, firmwareVer);
                        set.showInfo("Settings saved", "Settings have been updated.");
                    }
                }
            });
        } catch (IOException e) {
            appendActivityLog("[!] Save error: " + e.getMessage());
        }
    }

    public void appendChannel(final int channelIndex, final String line) {
        channelStore.appendLine(channelIndex, line);
        final ChannelScreen ch = channelScreen;
        final int chIdx = (channelIndex < 0 || channelIndex >= channelStore.size()) ? 0 : channelIndex;
        final String chName = chIdx < channelStore.size() ? channelStore.getName(chIdx) : ("#" + chIdx);
        display.callSerially(new Runnable() {
            public void run() {
                if (ch != null && display.getCurrent() == ch && ch.getChannelIndex() == chIdx) {
                    ch.refreshLog();
                } else {
                    incChannelUnread(chIdx);
                    refreshChannelListIfShown();
                    showIncomingNotification("New message in " + chName);
                    updateNotificationBell();
                }
            }
        });
    }

    public void appendActivityLog(final String line) {
        ActivityLogHelper.append(activityLogBuf, line, activityLogScreen, display);
    }

    public void appendDM(final int contactIdx, final String line) {
        contactStore.appendDmLine(contactIdx, line);
        final DMScreen dm = dmScreen;
        final int cIdx = contactIdx;
        display.callSerially(new Runnable() {
            public void run() {
                if (dm != null && display.getCurrent() == dm && dm.getContactIdx() == cIdx) {
                    dm.refreshLog();
                } else {
                    incContactUnread(cIdx);
                    refreshContactsListIfShown();
                    String from = TextUtils.extractDMSender(line);
                    showIncomingNotification("New DM" + (from.length() > 0 ? " from " + from : ""));
                    updateNotificationBell();
                }
            }
        });
    }

    private void ensureDmBuffersSize(int size) {
        contactStore.ensureDmSize(size);
    }

    private void ensureContactUnreadSize(int size) {
        contactStore.ensureUnreadSize(size);
    }

    private void incChannelUnread(int idx) {
        channelStore.incUnread(idx);
        updateNotificationBell();
    }

    private void setChannelUnread(int idx, int val) {
        channelStore.setUnread(idx, val);
        updateNotificationBell();
    }

    private void incContactUnread(int idx) {
        contactStore.incUnread(idx);
        updateNotificationBell();
    }

    private void setContactUnread(int idx, int val) {
        contactStore.setUnread(idx, val);
        updateNotificationBell();
    }

    private void refreshChannelListIfShown() {
        final ChannelListScreen list = channelListScreen;
        if (list != null && display.getCurrent() == list) {
            list.refreshList();
        }
    }

    private void refreshContactsListIfShown() {
        final ContactsScreen list = contactsScreen;
        if (list != null && display.getCurrent() == list) {
            list.refreshList();
        }
    }

    private void showIncomingNotification(String message) {
        NotificationPresenter.showIncoming(display, display.getCurrent(), message);
    }

    private boolean hasAnyUnread() {
        Vector chUnread = channelStore.getUnreadCounts();
        for (int i = 0; i < chUnread.size(); i++) {
            if (((Integer) chUnread.elementAt(i)).intValue() > 0) return true;
        }
        Vector cUnread = contactStore.getUnreadCounts();
        for (int i = 0; i < cUnread.size(); i++) {
            if (((Integer) cUnread.elementAt(i)).intValue() > 0) return true;
        }
        return false;
    }

    private void updateNotificationBell() {
        if (mainMenuScreen == null) return;
        boolean hasNew = hasAnyUnread();
        if (!hasNew) {
            notificationBlinkRunning = false;
            mainMenuScreen.setNotificationHasNew(false);
        } else if (!notificationBlinkRunning) {
            notificationBlinkRunning = true;
            startNotificationBlinker();
        }
        if (notificationsScreen != null && display.getCurrent() == notificationsScreen) {
            notificationsScreen.refreshList();
        }
    }

    private void startNotificationBlinker() {
        new Thread(new Runnable() {
            public void run() {
                boolean showNew = true;
                while (notificationBlinkRunning && hasAnyUnread()) {
                    final boolean currentShowNew = showNew;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (mainMenuScreen != null) {
                                mainMenuScreen.setNotificationHasNew(currentShowNew);
                            }
                        }
                    });
                    try {
                        Thread.sleep(AppConstants.NOTIFICATION_BLINK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                    showNew = !showNew;
                }
                notificationBlinkRunning = false;
                display.callSerially(new Runnable() {
                    public void run() {
                        if (mainMenuScreen != null) {
                            mainMenuScreen.setNotificationHasNew(false);
                        }
                    }
                });
            }
        }).start();
    }

    private void setConnectTitle(final String t) {
        display.callSerially(new Runnable() {
            public void run() {
                if (connectScreen != null) connectScreen.setTitle(t);
            }
        });
    }

    // -----------------------------------------------------------------------
    // FrameHandlerListener implementation
    // -----------------------------------------------------------------------
    public void onDeviceInfo(String ver) {
        firmwareVer = ver;
        appendActivityLog("[*] " + firmwareVer);
        refreshSettingsNodeIfShown();
    }

    public void onSelfInfo(String name, int txPwr, long freq, long bw, int sf, int cr, byte[] nodePublicKey) {
        String newKeyHex = (nodePublicKey != null && nodePublicKey.length >= 32)
            ? FrameUtils.bytesToHex(nodePublicKey, 0, 32) : null;
        if (newKeyHex != null) {
            nodePublicKeyHex = newKeyHex;
        }
        nodeName = name;
        nodeTxPwr = txPwr;
        nodeFreq = freq;
        nodeBw = bw;
        nodeSf = sf;
        nodeCr = cr;
        appendActivityLog("[*] Node: " + nodeName);
        refreshSettingsNodeIfShown();
        trySyncMessages();
    }

    private void refreshSettingsNodeIfShown() {
        final SettingsScreen set = settingsScreen;
        if (set == null) return;
        final String keyHex = nodePublicKeyHex;
        display.callSerially(new Runnable() {
            public void run() {
                if (display.getCurrent() == set) {
                    set.setNodeInfo(nodeName, firmwareVer);
                    set.setPublicKey(keyHex);
                }
            }
        });
    }

    public void onContactsStart() {
    }

    public void onContactsEnd() {
        display.callSerially(new Runnable() {
            public void run() {
                if (display.getCurrent() == contactsScreen && contactsScreen != null) {
                    contactsScreen.refreshList();
                } else if (display.getCurrent() == repeatersScreen && repeatersScreen != null) {
                    repeatersScreen.refreshList();
                }
            }
        });
    }

    public void onChannelInfo(final int chIdx, final String name) {
        display.callSerially(new Runnable() {
            public void run() {
                channelStore.ensureSlot(chIdx);
                channelStore.getNames().setElementAt(name, chIdx);
            }
        });
    }

    /** Log total channel count once after sync (like contacts), with short delay. */
    private void scheduleChannelsSyncedLog() {
        new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(AppConstants.CHANNELS_SYNCED_LOG_DELAY_MS); } catch (InterruptedException e) { return; }
                final int n = channelStore.size();
                display.callSerially(new Runnable() {
                    public void run() {
                        appendActivityLog("[*] Channels synced: " + n);
                    }
                });
            }
        }).start();
    }

    public void onBatteryUpdate(final String info) {
        final SettingsScreen set = settingsScreen;
        lastBatteryStatus = SettingsPresenter.handleBatteryUpdate(set, info);
        display.callSerially(new Runnable() {
            public void run() {
                // Battery UI already updated in presenter; nothing else to do here.
            }
        });
    }

    public void onStats(final String title, final String content) {
        final SettingsScreen set = settingsScreen;
        display.callSerially(new Runnable() {
            public void run() {
                SettingsPresenter.showStats(set, title, content);
            }
        });
    }

    public void onDeviceTime(final String content) {
        final SettingsScreen set = settingsScreen;
        display.callSerially(new Runnable() {
            public void run() {
                SettingsPresenter.showDeviceTime(set, content);
            }
        });
    }

    public void onError(int code) {
        String msg = TextUtils.getErrorCodeMessage(code);
        appendActivityLog("[!] Error " + code + (msg != null ? " (" + msg + ")" : ""));
    }

    public boolean isContactsScreenCurrent() {
        return display.getCurrent() == contactsScreen;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private int getEpoch() {
        return (int) (System.currentTimeMillis() / 1000L);
    }
}
