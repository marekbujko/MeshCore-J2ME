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
import meshcore.util.DmSendHandler;
import meshcore.util.DmSendManager;
import meshcore.util.FavoriteStore;
import meshcore.util.FrameUtils;
import meshcore.util.ImageUtils;
import meshcore.util.ParseUtils;
import meshcore.util.SHA256;
import meshcore.util.TextUtils;
import meshcore.ui.ToolsScreen;
import meshcore.ui.AppController;
import meshcore.ui.ActivityLogScreen;
import meshcore.ui.ChannelListScreen;
import meshcore.ui.ConnectScreen;
import meshcore.ui.MainMenuScreen;
import meshcore.ui.NoiseFloorScreen;
import meshcore.ui.ChannelScreen;
import meshcore.ui.ContactsScreen;
import meshcore.ui.RepeatersScreen;
import meshcore.ui.NotificationsScreen;
import meshcore.ui.FavoritesScreen;
import meshcore.ui.DMScreen;
import meshcore.ui.ShareContactScreen;
import meshcore.ui.ShareQrScreen;
import meshcore.ui.SettingsScreen;
import meshcore.ui.MessageSettingsScreen;
import meshcore.ui.Alerts;
import meshcore.ui.NotificationPresenter;
import meshcore.ui.SettingsPresenter;
import meshcore.ui.PingZeroHopScreen;
import meshcore.ui.TracePathResultScreen;
import meshcore.ui.TracePathSelectScreen;
import meshcore.util.ZeroHopPingService;
import meshcore.util.TracePathPingService;

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
public class MeshCore extends MIDlet implements AppController, FrameHandlerListener, DmSendHandler {

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
    private int nodeAdvLatE6 = Integer.MIN_VALUE;
    private int nodeAdvLonE6 = Integer.MIN_VALUE;
    private int advertType = 0; // 0 = Zero hop, 1 = Flood (per protocol)

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
    private ToolsScreen toolsScreen;
    private NoiseFloorScreen noiseFloorScreen;

    private FrameHandler frameHandler;
    private Integer lastNoiseFloor = null;
    private String lastBatteryStatus = "";
    private volatile boolean keepAliveRunning = false;
    private volatile boolean userRequestedDisconnect = false;
    private volatile boolean reconnectScheduled = false;
    private volatile int titleRotationIndex = 0;
    private volatile boolean notificationBlinkRunning = false;
    private volatile boolean mainMenuNoisePollingRunning = false;
    /**
     * During initial connect/splash we suppress modal alerts to avoid freezing the UI
     * while the device syncs old messages (many alerts one-by-one).
     * After main menu is shown, we flush one alert per channel/DM.
     */
    private volatile boolean notificationSuppressed = false;
    private final Vector pendingNotificationKeys = new Vector();
    private final Vector pendingNotificationMessages = new Vector();
    /** De-dup: don't show more than one alert per channel/DM while it has unread>0. */
    private final Vector alertedNotificationKeys = new Vector();

    private ConnectionManager connectionManager;
    private DmSendManager dmSendManager;
    private ZeroHopPingService zeroHopPingService;
    private TracePathPingService tracePathPingService;

    // -----------------------------------------------------------------------
    // MIDlet lifecycle
    // -----------------------------------------------------------------------
    public void startApp() {
        display = Display.getDisplay(this);
        dmSendManager = new DmSendManager(this, AppConstants.DM_SEND_MAX_ATTEMPTS, AppConstants.DM_SEND_TIMEOUT_MS);
        zeroHopPingService = new ZeroHopPingService(new ZeroHopPingService.Listener() {
            public void onZeroHopPingResult(
                    final int contactIdx,
                    final String contactName,
                    final int pathHops,
                    final String snrForward,
                    final String snrBack,
                    final long durationMs,
                    final Displayable returnTo
            ) {
                display.callSerially(new Runnable() {
                    public void run() {
                        Displayable cur = display.getCurrent();
                        if (cur instanceof PingZeroHopScreen) {
                            ((PingZeroHopScreen) cur).showResult(
                                    contactName,
                                    snrForward,
                                    snrBack,
                                    pathHops,
                                    durationMs
                            );
                        }
                    }
                });
            }
        });
        frameHandler = new FrameHandler(
                this,
                contactStore.getNames(),
                contactStore.getKeys(),
                contactStore.getTypes(),
                contactStore.getPathHops(),
                contactStore.getPathBytes(),
                contactStore.getContactFlags(),
                contactStore.getLastAdvert(),
                contactStore.getAdvLatE6(),
                contactStore.getAdvLonE6()
        );
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
        startMainMenuNoisePolling();
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
        if (channelIndex > 0) {
            String nm = channelStore.getName(channelIndex);
            if (nm == null || nm.length() == 0) return;
        }
        setChannelUnread(channelIndex, 0);
        clearAlertKey("ch:" + channelIndex);
        String displayName = getChannelDisplayName(channelIndex);
        StringBuffer buf = channelStore.getBuffer(channelIndex);
        try {
            meshcore.util.HistoryStore.loadChannelTailIntoBuffer(channelIndex, buf);
        } catch (Throwable ignore) {}
        channelScreen = new ChannelScreen(this, channelIndex, displayName, buf);
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
        int idx = channelStore.addChannel(name, secretBytes);
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
        try {
            meshcore.util.HistoryStore.clearChannelHistory(index);
        } catch (Throwable ignore) {
        }
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
        showDMScreen(idx, null);
    }

    public void showDMScreen(int idx, Displayable returnTo) {
        ensureDmBuffersSize(contactStore.size());
        ensureContactUnreadSize(contactStore.size());
        setContactUnread(idx, 0);
        clearAlertKey("dm:" + idx);
        String name = contactStore.getName(idx);
        StringBuffer buf = contactStore.getDmBuffer(idx);
        try {
            meshcore.util.HistoryStore.loadDmTailIntoBuffer(idx, buf);
        } catch (Throwable ignore) {}
        dmScreen = new DMScreen(this, idx, name, buf, returnTo);
        display.setCurrent(dmScreen);
        trySyncMessages();
    }

    public String getContactPublicKeyHex(int contactIdx) {
        // contactIdx >= 0: normal contact/repeater from contactStore
        if (contactIdx >= 0) {
            if (contactIdx >= contactStore.getKeys().size()) return "";
            byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
            if (key == null || key.length == 0) return "";
            return FrameUtils.bytesToHex(key, 0, key.length);
        }
        // contactIdx < 0: treat as "self" contact (node public key)
        return nodePublicKeyHex != null ? nodePublicKeyHex : "";
    }

    public String getChannelName(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channelStore.size()) return "";
        return channelStore.getName(channelIndex);
    }

    /** Display name with prefix for private channels (e.g. "[P] MyChannel"). */
    public String getChannelDisplayName(int channelIndex) {
        String name = getChannelName(channelIndex);
        if (name == null || name.length() == 0) return "";
        if (channelIndex == 0) return name;
        if (name.equalsIgnoreCase(ChannelListScreen.PUBLIC_CHANNEL)) return name;
        if (name.startsWith("#")) return name;
        return "(prv) " + name;
    }

    /** Channel secret as 32 hex (stored for private, or derived from name for public/hashtag). */
    public String getChannelSecretHex(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channelStore.size()) return "";
        String stored = channelStore.getSecretHex(channelIndex);
        if (stored != null && stored.length() == 32) return stored;
        String name = channelStore.getName(channelIndex);
        byte[] derived = SHA256.channelSecret(name);
        return FrameUtils.bytesToHex(derived, 0, 16);
    }

    public String getNodeName() {
        return nodeName != null ? nodeName : "";
    }

    public String getBatteryStatus() {
        return lastBatteryStatus != null ? lastBatteryStatus : "";
    }

    public String getLatestActivityLogLine() {
        if (activityLogBuf == null || activityLogBuf.length() == 0) return "";
        String s = activityLogBuf.toString();
        int last = s.lastIndexOf('\n');
        if (last < 0) return s.trim();
        if (last == s.length() - 1) {
            s = s.substring(0, last);
            last = s.lastIndexOf('\n');
            return (last < 0 ? s : s.substring(last + 1)).trim();
        }
        return s.substring(last + 1).trim();
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

    public void showMessageSettingsScreen() {
        MessageSettingsScreen ms = new MessageSettingsScreen(this);
        display.setCurrent(ms);
    }

    public void showActivityLogScreen() {
        activityLogScreen = new ActivityLogScreen(this, activityLogBuf);
        display.setCurrent(activityLogScreen);
    }

    public Integer getNoiseFloor() {
        return lastNoiseFloor;
    }

    public void showToolsScreen(Displayable returnTo) {
        toolsScreen = new ToolsScreen(this, returnTo);
        display.setCurrent(toolsScreen);
    }

    public void showNoiseFloorScreen(Displayable returnTo) {
        noiseFloorScreen = new NoiseFloorScreen(this, returnTo);
        display.setCurrent(noiseFloorScreen);
    }

    public void showNotificationsScreen() {
        notificationsScreen = new NotificationsScreen(
                this,
                channelStore.getNames(), channelStore.getUnreadCounts(),
                contactStore.getNames(), contactStore.getUnreadCounts()
        );
        display.setCurrent(notificationsScreen);
    }

    public void showFavoritesScreen() {
        FavoritesScreen fav = new FavoritesScreen(this,
                contactStore.getNames(), contactStore.getTypes(), contactStore.getKeys());
        display.setCurrent(fav);
    }

    public void showMyContactCode() {
        Displayable backTo = mainMenuScreen != null ? (Displayable) mainMenuScreen : (Displayable) this.mainMenuScreen;
        showMyContactCode(backTo);
    }

    public void showMyContactCode(Displayable returnTo) {
        String name = nodeName != null ? nodeName : "";
        // Use ShareContactScreen in "self" mode: idx = -1, type = ADV_TYPE_CHAT (mapped to type=1)
        display.setCurrent(new ShareContactScreen(this, -1, name, ProtocolConstants.ADV_TYPE_CHAT, returnTo));
    }

    public boolean isFavorite(int contactIdx) {
        if (contactIdx < 0 || contactIdx >= contactStore.getKeys().size()) return false;
        byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
        return FavoriteStore.isFavorite(FavoriteStore.keyToHex(key));
    }

    public void addFavorite(int contactIdx) {
        if (contactIdx < 0 || contactIdx >= contactStore.getKeys().size()) return;
        byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
        FavoriteStore.addFavorite(FavoriteStore.keyToHex(key));
    }

    public void removeFavorite(int contactIdx) {
        if (contactIdx < 0 || contactIdx >= contactStore.getKeys().size()) return;
        byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
        FavoriteStore.removeFavorite(FavoriteStore.keyToHex(key));
    }

    // -----------------------------------------------------------------------
    // AppController: actions
    // -----------------------------------------------------------------------
    public void connect(String host, int port) {
        beginNotificationSuppression();
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
                    endNotificationSuppressionAndFlush();
                }
            });
        } catch (Exception e) {
            setConnectTitle("Failed: " + e.getMessage());
            appendActivityLog("[!] Connect failed: " + e.getMessage());
            connected = false;
            notificationSuppressed = false;
            pendingNotificationKeys.removeAllElements();
            pendingNotificationMessages.removeAllElements();
            alertedNotificationKeys.removeAllElements();
        }
    }

    public void connectWithSplash(final String host, final int port) {
        beginNotificationSuppression();
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
                            endNotificationSuppressionAndFlush();
                        }
                    });
                } catch (Exception e) {
                    final String err = e.getMessage();
                    appendActivityLog("[!] Connect failed: " + err);
                    connected = false;
                    notificationSuppressed = false;
                    pendingNotificationKeys.removeAllElements();
                    pendingNotificationMessages.removeAllElements();
                    alertedNotificationKeys.removeAllElements();
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
        notificationSuppressed = false;
        pendingNotificationKeys.removeAllElements();
        pendingNotificationMessages.removeAllElements();
        alertedNotificationKeys.removeAllElements();
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
            String clean = TextUtils.sanitizeMessage(msg, AppConstants.MAX_BUFFER_LENGTH);
            // Limit to configured UTF-8 byte budget for public chat
            clean = TextUtils.truncateUtf8ToBytes(clean, AppConstants.CHANNEL_MSG_MAX_BYTES);
            MeshProtocolClient.sendChannelMessage(transport, channelIndex, clean, getEpoch());
            appendChannel(channelIndex, "[me] " + TextUtils.escapeNewlines(clean));
        } catch (IOException e) {
            appendActivityLog("[!] Send error");
        }
    }

    public void sendDirectMessage(int idx, String msg) {
        if (!dmSendManager.send(idx, msg)) {
            appendDM(idx, "[!] Send error");
        }
    }

    public void appendSending(int contactIdx, String escapedMessage) {
        appendDMInternal(contactIdx, "[me] " + escapedMessage, true);
    }

    public void updateSendingProgress(int contactIdx, int attempt, int maxAttempts, boolean flood) {
        String status = attempt <= 1
                ? AppConstants.DM_STATUS_SENDING
                : AppConstants.DM_STATUS_SENDING + " " + attempt + "/" + maxAttempts;
        if (flood) {
            // Avoid stacking "(Flood)" multiple times.
            if (!status.endsWith(" (Flood)")) {
                status = status + " (Flood)";
            }
        }
        replaceLastSendingWith(contactIdx, status);
    }

    public boolean doSend(int contactIdx, String message) {
        if (transport == null) return false;
        try {
            byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
            MeshProtocolClient.sendDirectMessage(transport, key, message, getEpoch());
            return true;
        } catch (IOException e) {
            return false;
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

    public void addManualContact(final String name, final String publicKeyHex, final int advType) {
        if (transport == null) {
            appendActivityLog("[!] Cannot add contact while disconnected");
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                byte[] key = FrameUtils.hexDecode(publicKeyHex);
                if (key == null || key.length != 32) {
                    appendActivityLog("[!] Public key must be 64 hex chars (32 bytes)");
                    return;
                }
                String safeName = TextUtils.sanitizeLabel(name, 32);
                try {
                    MeshProtocolClient.sendAddUpdateContact(transport, key, advType, 0, new byte[0], safeName, 0L);
                    appendActivityLog("[*] Add contact requested: " + safeName);
                    sendGetContacts();
                } catch (IOException e) {
                    appendActivityLog("[!] Add contact failed");
                }
            }
        }).start();
    }

    public void resetPath(int contactIdx) {
        if (transport == null || contactIdx < 0 || contactIdx >= contactStore.getKeys().size()) return;
        try {
            byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
            MeshProtocolClient.sendResetPath(transport, key);
            appendActivityLog("[*] Path reset for " + contactStore.getName(contactIdx));
            sendGetContacts();
            sendAdvertFloodForPathDiscovery();
        } catch (IOException e) {
            appendActivityLog("[!] Reset Path failed");
        }
    }

    public void updateContactPath(int contactIdx, byte[] newPath) {
        if (transport == null || contactIdx < 0 || contactIdx >= contactStore.getKeys().size()) return;
        if (newPath == null) newPath = new byte[0];
        if (newPath.length > 64) {
            byte[] trimmed = new byte[64];
            System.arraycopy(newPath, 0, trimmed, 0, 64);
            newPath = trimmed;
        }
        try {
            byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
            int type = contactStore.getType(contactIdx);
            int flags = contactStore.getFlags(contactIdx);
            String name = contactStore.getName(contactIdx);
            long lastAdvert = contactStore.getLastAdvert(contactIdx);
            MeshProtocolClient.sendAddUpdateContact(transport, key, type, flags, newPath, name, lastAdvert);
            appendActivityLog("[*] Path saved for " + name);
            sendGetContacts();
            sendAdvertFloodForPathDiscovery();
        } catch (IOException e) {
            appendActivityLog("[!] Save path failed");
        }
    }

    public void pingRepeaterZeroHop(int contactIdx) {
        if (transport == null || zeroHopPingService == null) return;
        if (contactIdx < 0 || contactIdx >= contactStore.size()) return;

        try {
            byte destPrefix = getRepeaterPathByte(contactIdx);
            String contactName = contactStore.getName(contactIdx);
            Displayable returnTo = (display != null) ? display.getCurrent() : null;
            display.setCurrent(new PingZeroHopScreen(this, returnTo));
            zeroHopPingService.sendPing(transport, contactIdx, contactName, destPrefix, returnTo);
            appendActivityLog("[*] Zero-hop ping sent to " + contactName);
        } catch (IOException e) {
            appendActivityLog("[!] Ping failed");
        }
    }

    public void tracePathManual(byte[] forwardPath, Displayable returnTo) {
        if (transport == null) return;
        if (forwardPath == null || forwardPath.length == 0) return;

        final TracePathResultScreen resultScreen = new TracePathResultScreen(this, returnTo, forwardPath);
        display.setCurrent(resultScreen);
        startTracePathService(resultScreen, forwardPath, returnTo);
    }

    public void tracePathManualRefresh(byte[] forwardPath, TracePathResultScreen resultScreen) {
        if (transport == null) return;
        if (forwardPath == null || forwardPath.length == 0) return;
        if (resultScreen == null) return;

        display.setCurrent(resultScreen);
        resultScreen.renderWaiting();
        // Only forward trace is shown, so do not run a separate back stage here.
        startTracePathService(resultScreen, forwardPath, resultScreen.getReturnTo());
    }

    private void startTracePathService(
            final TracePathResultScreen resultScreen,
            byte[] forwardPath,
            Displayable returnTo
    ) {
        tracePathPingService = new TracePathPingService(new TracePathPingService.Listener() {
            public void onStageResult(
                    final byte[] pathBytes,
                    final byte[] pathSnrs,
                    final int finalSNR4,
                    final long durationMs,
                    final Displayable rt
            ) {
                display.callSerially(new Runnable() {
                    public void run() {
                        if (display.getCurrent() != resultScreen) return;
                        String destName = formatDestinationName(pathBytes);
                        int hops = pathBytes != null ? pathBytes.length : 0;

                        resultScreen.setResult(
                                destName,
                                pathSnrs,
                                finalSNR4,
                                hops,
                                durationMs
                        );
                    }
                });
            }
        });

        try {
            tracePathPingService.start(transport, forwardPath, returnTo);
        } catch (IOException e) {
            appendActivityLog("[!] Trace path failed");
        }
    }

    private String formatDestinationName(byte[] pathBytes) {
        if (pathBytes == null || pathBytes.length == 0) return "";
        byte dest = pathBytes[pathBytes.length - 1];
        String name = getRepeaterNameForPathByte(dest);
        if (name != null && name.length() > 0) return name;
        // Fallback: show hex prefix.
        return "0x" + FrameUtils.bytesToHex(new byte[]{dest}, 0, 1);
    }

    private String formatSnr4ToDb(int snr4) {
        boolean neg = snr4 < 0;
        int abs = neg ? -snr4 : snr4;
        int whole = abs / 4;
        int fracQ = abs % 4; // 0..3
        int fracDec = fracQ * 25; // 0,25,50,75
        String fracStr = fracDec < 10 ? "0" + fracDec : String.valueOf(fracDec);
        return (neg ? "-" : "") + whole + "." + fracStr + " dB";
    }

    private String formatSnrList(byte[] pathSnrs) {
        if (pathSnrs == null || pathSnrs.length == 0) return "n/a";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < pathSnrs.length; i++) {
            if (i > 0) sb.append(", ");
            int snr4 = (int) pathSnrs[i];
            sb.append(i + 1).append(": ").append(formatSnr4ToDb(snr4));
        }
        return sb.toString();
    }

    /** Send one flood advert so the network can re-learn paths (e.g. after path reset or path edit). */
    private void sendAdvertFloodForPathDiscovery() {
        try {
            MeshProtocolClient.sendAdvert(transport, ProtocolConstants.ADVERT_FLOOD);
            appendActivityLog("[*] Flood advert sent (path discovery)");
        } catch (IOException ignore) {}
    }

    public void sendRefreshSettings() {
        if (transport == null) {
            appendActivityLog("[!] Cannot refresh settings while disconnected");
            return;
        }
        try {
            MeshProtocolClient.sendRefreshSettings(transport, myName);
        } catch (IOException ignore) {}
    }

    public void sendGetBattery() {
        if (transport == null) {
            appendActivityLog("[!] Cannot get battery while disconnected");
            return;
        }
        try {
            MeshProtocolClient.sendGetBattery(transport);
        } catch (IOException ignore) {}
    }

    public void sendGetStats() {
        if (transport == null) {
            appendActivityLog("[!] Cannot get stats while disconnected");
            return;
        }
        try {
            MeshProtocolClient.sendGetStats(transport);
        } catch (IOException e) {
            appendActivityLog("[!] Stats failed");
        }
    }

    public void sendGetRadioStats() {
        if (transport == null) {
            appendActivityLog("[!] Cannot get radio stats while disconnected");
            return;
        }
        try {
            MeshProtocolClient.sendGetRadioStats(transport);
        } catch (IOException e) {
            appendActivityLog("[!] Radio stats failed");
        }
    }

    public void sendGetDeviceTime() {
        if (transport == null) {
            appendActivityLog("[!] Cannot get device time while disconnected");
            return;
        }
        try {
            MeshProtocolClient.sendGetDeviceTime(transport);
        } catch (IOException e) {
            appendActivityLog("[!] Time failed");
        }
    }

    public void sendAdvert() {
        if (transport == null) {
            appendActivityLog("[!] Cannot send advert while disconnected");
            return;
        }
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
        if (transport == null) {
            appendActivityLog("[!] Cannot save settings while disconnected");
            return;
        }
        try {
            String newName = settingsScreen.getNodeName();
            if (newName.length() > 0 && !newName.equals(nodeName)) {
                // Trim to 32 UTF-8 bytes so advert name fits protocol expectation.
                String trimmed = meshcore.util.TextUtils.truncateUtf8ToBytes(newName, 32);
                byte[] nb = trimmed.getBytes("UTF-8");
                byte[] f = new byte[1 + nb.length];
                f[0] = (byte) ProtocolConstants.CMD_SET_ADVERT_NAME;
                System.arraycopy(nb, 0, f, 1, nb.length);
                transport.sendFrame(f);
                nodeName = trimmed;
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
        String stamped = TextUtils.sanitizeMessage(line, AppConstants.MAX_BUFFER_LENGTH);
        String ts = TextUtils.formatNowDateTime();
        if (ts != null && ts.length() > 0) {
            stamped = stamped + " (" + ts + ")";
        }
        channelStore.appendLine(channelIndex, stamped);
        try {
            meshcore.util.HistoryStore.appendChannelLine(channelIndex, stamped);
        } catch (Throwable ignore) {}
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
                    queueOrShowNotification("ch:" + chIdx, "New message in " + chName);
                    updateNotificationBell();
                }
            }
        });
    }

    /** Periodically refresh noise floor while main menu is visible. */
    private void startMainMenuNoisePolling() {
        if (mainMenuNoisePollingRunning) return;
        mainMenuNoisePollingRunning = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (running && connected) {
                        try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
                        if (!running || !connected) break;
                        display.callSerially(new Runnable() {
                            public void run() {
                                if (mainMenuScreen != null && display.getCurrent() == mainMenuScreen) {
                                    sendGetRadioStats();
                                } else {
                                    // Stop polling when we leave main menu.
                                    mainMenuNoisePollingRunning = false;
                                }
                            }
                        });
                    }
                } finally {
                    mainMenuNoisePollingRunning = false;
                }
            }
        }).start();
    }

    public void appendActivityLog(final String line) {
        ActivityLogHelper.append(activityLogBuf, line, activityLogScreen, display);
    }

    public void appendDM(final int contactIdx, final String line) {
        String clean = TextUtils.sanitizeMessage(line, AppConstants.MAX_BUFFER_LENGTH);
        appendDMInternal(contactIdx, clean, false);
    }

    private void appendDMInternal(final int contactIdx, final String line, boolean fromMe) {
        String stamped = TextUtils.sanitizeMessage(line, AppConstants.MAX_BUFFER_LENGTH);
        String ts = TextUtils.formatNowDateTime();
        if (fromMe) {
            ts = ts + " | " + AppConstants.DM_STATUS_SENDING;
        }
        if (ts != null && ts.length() > 0) {
            stamped = stamped + " (" + ts + ")";
        }
        contactStore.appendDmLine(contactIdx, stamped);
        try {
            meshcore.util.HistoryStore.appendDmLine(contactIdx, stamped);
        } catch (Throwable ignore) {}
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
                    queueOrShowNotification(
                            "dm:" + cIdx,
                            "New DM" + (from.length() > 0 ? " from " + from : "")
                    );
                    updateNotificationBell();
                }
            }
        });
    }

    public void clearChannelHistory(final int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channelStore.size()) return;
        try {
            meshcore.util.HistoryStore.clearChannelHistory(channelIndex);
        } catch (Throwable ignore) {}
        StringBuffer buf = channelStore.getBuffer(channelIndex);
        buf.setLength(0);
        final ChannelScreen ch = channelScreen;
        display.callSerially(new Runnable() {
            public void run() {
                if (ch != null && display.getCurrent() == ch && ch.getChannelIndex() == channelIndex) {
                    ch.refreshLog();
                }
            }
        });
    }

    public int getContactPathHops(int contactIdx) {
        return contactStore.getPathHopsCount(contactIdx);
    }

    public byte[] getContactPathBytes(int contactIdx) {
        return contactStore.getPathBytes(contactIdx);
    }

    public long getContactLastAdvertSecs(int contactIdx) {
        return contactStore.getLastAdvert(contactIdx);
    }

    public int getContactAdvLatE6(int contactIdx) {
        return contactStore.getAdvLatE6(contactIdx);
    }

    public int getContactAdvLonE6(int contactIdx) {
        return contactStore.getAdvLonE6(contactIdx);
    }

    public int getNodeAdvLatE6() {
        return nodeAdvLatE6;
    }

    public int getNodeAdvLonE6() {
        return nodeAdvLonE6;
    }

    public String getRepeaterNameForPathByte(byte pathByte) {
        Vector keys = contactStore.getKeys();
        Vector names = contactStore.getNames();
        Vector types = contactStore.getTypes();
        for (int i = 0; i < keys.size(); i++) {
            if (i < types.size() && ((Integer) types.elementAt(i)).intValue() != ProtocolConstants.ADV_TYPE_REPEATER) continue;
            byte[] key = (byte[]) keys.elementAt(i);
            if (key != null && key.length > 0 && key[0] == pathByte) {
                return (i < names.size()) ? (String) names.elementAt(i) : null;
            }
        }
        return null;
    }

    public Vector getRepeaterIndices() {
        Vector indices = new Vector();
        Vector types = contactStore.getTypes();
        for (int i = 0; i < types.size(); i++) {
            if (((Integer) types.elementAt(i)).intValue() == ProtocolConstants.ADV_TYPE_REPEATER) {
                indices.addElement(new Integer(i));
            }
        }
        return indices;
    }

    public byte getRepeaterPathByte(int contactIdx) {
        if (contactIdx < 0 || contactIdx >= contactStore.getKeys().size()) return 0;
        byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
        return (key != null && key.length > 0) ? key[0] : 0;
    }

    public String getRepeaterPublicKeyHex(int contactIdx) {
        if (contactIdx < 0 || contactIdx >= contactStore.getKeys().size()) return "";
        byte[] key = (byte[]) contactStore.getKeys().elementAt(contactIdx);
        if (key == null || key.length == 0) return "";
        String hex = FrameUtils.bytesToHex(key, 0, key.length);
        return TextUtils.formatPublicKeyShort(hex);
    }

    public void clearDmHistory(final int contactIdx) {
        if (contactIdx < 0 || contactIdx >= contactStore.size()) return;
        try {
            meshcore.util.HistoryStore.clearDmHistory(contactIdx);
        } catch (Throwable ignore) {}
        contactStore.ensureDmSize(contactIdx + 1);
        StringBuffer buf = contactStore.getDmBuffer(contactIdx);
        buf.setLength(0);
        final DMScreen dm = dmScreen;
        display.callSerially(new Runnable() {
            public void run() {
                if (dm != null && display.getCurrent() == dm && dm.getContactIdx() == contactIdx) {
                    dm.refreshLog();
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

    private void beginNotificationSuppression() {
        notificationSuppressed = true;
        pendingNotificationKeys.removeAllElements();
        pendingNotificationMessages.removeAllElements();
    }

    private void endNotificationSuppressionAndFlush() {
        // Keep suppression ON while flushing so we don't interleave multiple alert storms.
        // While suppressing, we only show a single summary message, because showing
        // many modal alerts back-to-back often results in only the last one being visible.
        boolean hadPending = pendingNotificationKeys.size() > 0;

        Vector keysSnapshot = new Vector();
        for (int i = 0; i < pendingNotificationKeys.size(); i++) {
            keysSnapshot.addElement(pendingNotificationKeys.elementAt(i));
        }
        pendingNotificationKeys.removeAllElements();
        pendingNotificationMessages.removeAllElements();

        for (int i = 0; i < keysSnapshot.size(); i++) {
            String key = (String) keysSnapshot.elementAt(i);
            if (!containsString(alertedNotificationKeys, key)) {
                alertedNotificationKeys.addElement(key);
            }
        }

        if (hadPending) {
            showIncomingNotification("New messages received");
        }
        notificationSuppressed = false;
    }

    private int indexOfString(Vector v, String s) {
        if (v == null || s == null) return -1;
        for (int i = 0; i < v.size(); i++) {
            String cur = (String) v.elementAt(i);
            if (s.equals(cur)) return i;
        }
        return -1;
    }

    private boolean containsString(Vector v, String s) {
        return indexOfString(v, s) >= 0;
    }

    private void clearAlertKey(String key) {
        int idx = indexOfString(alertedNotificationKeys, key);
        if (idx >= 0) {
            alertedNotificationKeys.removeElementAt(idx);
        }
    }

    private void queueOrShowNotification(String notifKey, String message) {
        if (display == null) return;
        if (notifKey == null) notifKey = "key:null";

        if (notificationSuppressed) {
            // Queue one-by-one notifications but de-dup by key.
            int existing = indexOfString(pendingNotificationKeys, notifKey);
            if (existing >= 0) {
                pendingNotificationMessages.setElementAt(message, existing);
            } else {
                pendingNotificationKeys.addElement(notifKey);
                pendingNotificationMessages.addElement(message);
            }
            return;
        }

        // While unread>0, show at most one modal alert per key.
        if (containsString(alertedNotificationKeys, notifKey)) return;
        alertedNotificationKeys.addElement(notifKey);
        showIncomingNotification(message);
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
        if (display == null) return;
        Displayable next = display.getCurrent();
        // If we're still on the Connect screen but the main menu exists, return to main menu after the alert
        // so users aren't bounced back to ConnectScreen when notifications arrive during initial sync.
        if (next instanceof ConnectScreen && mainMenuScreen != null) {
            next = mainMenuScreen;
        }
        NotificationPresenter.showIncoming(display, next, message);
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

    public void onSelfInfo(String name, int txPwr, long freq, long bw, int sf, int cr,
                           byte[] nodePublicKey, int advLatE6, int advLonE6) {
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
        nodeAdvLatE6 = advLatE6;
        nodeAdvLonE6 = advLonE6;
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
                } else if (display.getCurrent() == dmScreen && dmScreen != null) {
                    try {
                        dmScreen.refreshTitle();
                    } catch (Throwable t) {
                        // Nokia can throw InterruptedException in UI lifecycle
                    }
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

    public void onNoiseFloor(final int noiseFloorDbm) {
        lastNoiseFloor = new Integer(noiseFloorDbm);
        final NoiseFloorScreen noise = noiseFloorScreen;
        final MainMenuScreen menu = mainMenuScreen;
        display.callSerially(new Runnable() {
            public void run() {
                if (noise != null && display.getCurrent() == noise) {
                    noise.addSample(noiseFloorDbm);
                }
                if (menu != null && display.getCurrent() == menu) {
                    menu.repaint();
                }
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

    public void onTraceData(final int flags,
                             final long tag,
                             final long authCode,
                             final byte[] pathHashes,
                             final byte[] pathSnrs,
                             final int finalSNR4) {
        if (zeroHopPingService != null) {
            zeroHopPingService.handleTraceData(flags, tag, authCode, pathHashes, pathSnrs, finalSNR4);
        }
        if (tracePathPingService != null) {
            tracePathPingService.handleTraceData(flags, tag, authCode, pathHashes, pathSnrs, finalSNR4);
        }
    }

    public boolean isContactsScreenCurrent() {
        return display.getCurrent() == contactsScreen;
    }

    public void onMessageDelivered() {
        final int idx = dmSendManager.getPendingContactIdx();
        if (idx < 0) return;
        final int attemptCount = dmSendManager.getAttemptCount();
        dmSendManager.setDeliveryConfirmed(true);
        dmSendManager.clearPending();
        replaceLastSendingWith(idx, buildDeliveredStatus(attemptCount));
    }

    public void onSendFailed(int contactIdx) {
        replaceLastSendingWith(contactIdx, AppConstants.DM_STATUS_FAILED);
    }

    public void resetPathFor(int contactIdx) {
        resetPath(contactIdx);
    }

    private String buildDeliveredStatus(int attemptCount) {
        if (attemptCount > 1) {
            return AppConstants.DM_STATUS_DELIVERED + " (" + attemptCount + ")";
        }
        return AppConstants.DM_STATUS_DELIVERED;
    }

    /** Replaces the last "Sending" or "Sending N/M" token in the contact's DM buffer with newStatus. */
    private void replaceLastSendingWith(int contactIdx, String newStatus) {
        contactStore.ensureDmSize(contactIdx + 1);
        final StringBuffer buf = contactStore.getDmBuffer(contactIdx);
        final String current = buf.toString();
        final String sending = AppConstants.DM_STATUS_SENDING;
        final int slen = sending.length();
        int pos = -1;
        for (int i = current.length() - slen; i >= 0; i--) {
            boolean match = true;
            for (int j = 0; j < slen; j++) {
                if (current.charAt(i + j) != sending.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                pos = i;
                break;
            }
        }
        if (pos < 0) return;
        int tokenEnd = pos + slen;
        int clen = current.length();
        if (tokenEnd < clen && current.charAt(tokenEnd) == ' ') {
            tokenEnd++;
            while (tokenEnd < clen && current.charAt(tokenEnd) >= '0' && current.charAt(tokenEnd) <= '9') tokenEnd++;
            if (tokenEnd < clen && current.charAt(tokenEnd) == '/') {
                tokenEnd++;
                while (tokenEnd < clen && current.charAt(tokenEnd) >= '0' && current.charAt(tokenEnd) <= '9') tokenEnd++;
            }
        }
        // Also skip optional " (Flood)" suffix if present so we don't accumulate duplicates.
        final String floodSuffix = " (Flood)";
        if (tokenEnd + floodSuffix.length() <= clen) {
            boolean matchFlood = true;
            for (int i = 0; i < floodSuffix.length(); i++) {
                if (current.charAt(tokenEnd + i) != floodSuffix.charAt(i)) {
                    matchFlood = false;
                    break;
                }
            }
            if (matchFlood) {
                tokenEnd += floodSuffix.length();
            }
        }
        final String updated = current.substring(0, pos) + newStatus + current.substring(tokenEnd);
        buf.setLength(0);
        buf.append(updated);

        try {
            meshcore.util.HistoryStore.updateLastDmStatus(contactIdx, AppConstants.DM_STATUS_SENDING, newStatus);
        } catch (Throwable ignore) {}

        final DMScreen dm = dmScreen;
        final int cIdx = contactIdx;
        display.callSerially(new Runnable() {
            public void run() {
                if (dm != null && display.getCurrent() == dm && dm.getContactIdx() == cIdx) {
                    dm.refreshLog();
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private int getEpoch() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    /** For future options: retry count, timeout; path/flood settings will live elsewhere. */
    public DmSendManager getDmSendManager() {
        return dmSendManager;
    }
}
