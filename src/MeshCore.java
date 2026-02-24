import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Vector;

import meshcore.protocol.FrameHandler;
import meshcore.protocol.FrameHandlerListener;
import meshcore.protocol.ProtocolConstants;
import meshcore.net.FrameTransport;
import meshcore.util.ChannelStorage;
import meshcore.util.ConnectStorage;
import meshcore.util.FrameUtils;
import meshcore.util.ParseUtils;
import meshcore.util.SHA256;
import meshcore.ui.AppController;
import meshcore.ui.ActivityLogScreen;
import meshcore.ui.ChannelListScreen;
import meshcore.ui.ConnectScreen;
import meshcore.ui.MainMenuScreen;
import meshcore.ui.ChannelScreen;
import meshcore.ui.ContactsScreen;
import meshcore.ui.DMScreen;
import meshcore.ui.SettingsScreen;

/**
 * MeshCore WiFi TCP Client for Nokia Asha 210 (J2ME MIDP 2.0 / CLDC 1.1)
 *
 * Screens:
 *   1. ConnectScreen   - IP/port entry
 *   2. MainMenu        - Channel Chat / Contacts / Settings / Disconnect
 *   3. ChannelScreen   - Public broadcast chat
 *   4. ContactsScreen  - Contact list, tap to open DM
 *   5. DMScreen        - Direct message with a contact
 *   6. SettingsScreen  - Radio params, node name, battery
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

    private String myName = "NokiaUser";
    private String nodeName = "";
    private String firmwareVer = "";

    private Vector channelNames = new Vector();
    private Vector channelBuffers = new Vector();
    private Vector channelUnreadCount = new Vector();
    private StringBuffer activityLogBuf = new StringBuffer();
    private Vector dmBuffers = new Vector();
    private Vector contactUnreadCount = new Vector();

    private Vector contactNames = new Vector();
    private Vector contactKeys = new Vector();

    private long nodeFreq = 915000L;
    private long nodeBw = 250L;
    private int nodeSf = 10;
    private int nodeCr = 5;
    private int nodeTxPwr = 20;

    // Screen references (for UI updates from background threads)
    private ConnectScreen connectScreen;
    private MainMenuScreen mainMenuScreen;
    private ChannelListScreen channelListScreen;
    private ChannelScreen channelScreen;
    private ContactsScreen contactsScreen;
    private DMScreen dmScreen;
    private SettingsScreen settingsScreen;
    private ActivityLogScreen activityLogScreen;

    private FrameHandler frameHandler;
    private String lastBatteryStatus = "";
    private volatile boolean keepAliveRunning = false;

    // -----------------------------------------------------------------------
    // MIDlet lifecycle
    // -----------------------------------------------------------------------
    public void startApp() {
        display = Display.getDisplay(this);
        frameHandler = new FrameHandler(this, contactNames, contactKeys);
        initChannels();
        showConnectScreen();
    }

    private void initChannels() {
        channelNames.removeAllElements();
        channelBuffers.removeAllElements();
        channelUnreadCount.removeAllElements();
        channelNames.addElement(ChannelListScreen.PUBLIC_CHANNEL);
        channelBuffers.addElement(new StringBuffer());
        channelUnreadCount.addElement(new Integer(0));
        Vector custom = ChannelStorage.load();
        lastSavedCustom = copyVector(custom);
        for (int i = 0; i < custom.size(); i++) {
            channelNames.addElement(custom.elementAt(i));
            channelBuffers.addElement(new StringBuffer());
            channelUnreadCount.addElement(new Integer(0));
        }
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
        mainMenuScreen = new MainMenuScreen(this);
        if (lastBatteryStatus != null && lastBatteryStatus.length() > 0) {
            mainMenuScreen.setTitle("MeshCore " + lastBatteryStatus);
        }
        display.setCurrent(mainMenuScreen);
    }

    public void showConnectScreen() {
        connectScreen = new ConnectScreen(this);
        display.setCurrent(connectScreen);
    }

    public void showChannelListScreen() {
        channelListScreen = new ChannelListScreen(this, channelNames, channelUnreadCount);
        display.setCurrent(channelListScreen);
    }

    public void showChannelScreen(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channelBuffers.size()) return;
        setChannelUnread(channelIndex, 0);
        String name = (String) channelNames.elementAt(channelIndex);
        StringBuffer buf = (StringBuffer) channelBuffers.elementAt(channelIndex);
        channelScreen = new ChannelScreen(this, channelIndex, name, buf);
        display.setCurrent(channelScreen);
        trySyncMessages();
    }

    public void addChannel(String name) {
        addChannel(name, null);
    }

    public void addChannel(String name, byte[] secretBytes) {
        for (int i = 0; i < channelNames.size(); i++) {
            if (name.equalsIgnoreCase((String) channelNames.elementAt(i))) {
                return;
            }
        }
        channelNames.addElement(name);
        channelBuffers.addElement(new StringBuffer());
        channelUnreadCount.addElement(new Integer(0));
        saveCustomChannels();
        sendSetChannel(channelNames.size() - 1, name, secretBytes);
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
        if (index <= 0 || index >= channelNames.size()) return;
        channelNames.removeElementAt(index);
        channelBuffers.removeElementAt(index);
        channelUnreadCount.removeElementAt(index);
        saveCustomChannels();
    }

    private Vector lastSavedCustom;

    private void saveCustomChannels() {
        saveCustomChannels(true);
    }

    private void saveCustomChannels(boolean immediate) {
        Vector custom = new Vector();
        for (int i = 1; i < channelNames.size(); i++) {
            custom.addElement(channelNames.elementAt(i));
        }
        if (lastSavedCustom != null && vectorsEqual(lastSavedCustom, custom)) return;
        lastSavedCustom = copyVector(custom);
        ChannelStorage.save(custom);
    }

    private static boolean vectorsEqual(Vector a, Vector b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!eq(a.elementAt(i), b.elementAt(i))) return false;
        }
        return true;
    }

    private static boolean eq(Object a, Object b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private static Vector copyVector(Vector v) {
        Vector c = new Vector(v.size());
        for (int i = 0; i < v.size(); i++) c.addElement(v.elementAt(i));
        return c;
    }

    private volatile Thread debounceSaveThread;

    private void scheduleDebouncedSave() {
        if (debounceSaveThread != null && debounceSaveThread.isAlive()) return;
        debounceSaveThread = new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(450); } catch (InterruptedException e) { return; }
                debounceSaveThread = null;
                display.callSerially(new Runnable() {
                    public void run() {
                        saveCustomChannels(false);
                    }
                });
            }
        });
        debounceSaveThread.start();
    }

    public void showContactsScreen() {
        ensureContactUnreadSize(contactNames.size());
        contactsScreen = new ContactsScreen(this, contactNames, contactUnreadCount);
        display.setCurrent(contactsScreen);
    }

    public void showDMScreen(int idx) {
        ensureDmBuffersSize(contactNames.size());
        ensureContactUnreadSize(contactNames.size());
        setContactUnread(idx, 0);
        String name = (String) contactNames.elementAt(idx);
        StringBuffer buf = (StringBuffer) dmBuffers.elementAt(idx);
        dmScreen = new DMScreen(this, idx, name, buf);
        display.setCurrent(dmScreen);
        trySyncMessages();
    }

    public void showSettingsScreen() {
        settingsScreen = new SettingsScreen(this, nodeName, firmwareVer,
                nodeFreq, nodeBw, nodeSf, nodeCr, nodeTxPwr);
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

    // -----------------------------------------------------------------------
    // AppController: actions
    // -----------------------------------------------------------------------
    public void connect(String host, int port) {
        setConnectTitle("Connecting...");
        try {
            conn = (StreamConnection) Connector.open("socket://" + host + ":" + port);
            transport = new FrameTransport(conn.openInputStream(), conn.openOutputStream());
            connected = true;
            ConnectStorage.save(host, String.valueOf(port));
            setConnectTitle("Connected!");
            appendActivityLog("[*] Connected");

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

            // Kick off initial battery query so main menu shows voltage quickly
            sendGetBattery();

            running = true;
            new Thread(new Runnable() {
                public void run() {
                    receiveLoop();
                }
            }).start();

            startKeepAlive();

            display.callSerially(new Runnable() {
                public void run() {
                    showMainMenu();
                }
            });

        } catch (Exception e) {
            setConnectTitle("Failed: " + e.getMessage());
            appendActivityLog("[!] Connect failed: " + e.getMessage());
            connected = false;
        }
    }

    public void disconnect() {
        running = false;
        connected = false;
        keepAliveRunning = false;
        transport = null;
        try {
            if (conn != null) conn.close();
        } catch (IOException ignore) {}
        conn = null;
    }

    public void sendChannelMessage(int channelIndex, String msg) {
        if (channelIndex < 0 || channelIndex >= channelBuffers.size()) return;
        try {
            byte[] tb = msg.getBytes("UTF-8");
            byte[] f = new byte[7 + tb.length];
            f[0] = (byte) ProtocolConstants.CMD_SEND_CHANNEL_MSG;
            f[1] = 0;
            f[2] = (byte) (channelIndex & 0xFF);
            FrameTransport.writeUint32LE(f, 3, getEpoch());
            System.arraycopy(tb, 0, f, 7, tb.length);
            transport.sendFrame(f);
            appendChannel(channelIndex, "[me] " + msg);
        } catch (IOException e) {
            appendActivityLog("[!] Send error");
        }
    }

    public void sendDirectMessage(int idx, String msg) {
        try {
            byte[] key = (byte[]) contactKeys.elementAt(idx);
            byte[] tb = msg.getBytes("UTF-8");
            byte[] f = new byte[13 + tb.length];
            f[0] = (byte) ProtocolConstants.CMD_SEND_TXT_MSG;
            f[1] = 0;
            f[2] = 0;
            FrameTransport.writeUint32LE(f, 3, getEpoch());
            System.arraycopy(key, 0, f, 7, 6);
            System.arraycopy(tb, 0, f, 13, tb.length);
            transport.sendFrame(f);
            appendDM(idx, "[me] " + msg);
        } catch (IOException e) {
            appendDM(idx, "[!] Send error");
        }
    }

    public void sendGetContacts() {
        try {
            transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_CONTACTS});
        } catch (IOException e) {
            appendActivityLog("[!] Sync failed");
        }
    }

    public void sendGetChannels() {
        try {
            for (int i = 0; i < 8; i++) {
                transport.sendFrame(new byte[]{
                    (byte) ProtocolConstants.CMD_GET_CHANNEL,
                    (byte) (i & 0xFF)
                });
            }
        } catch (IOException e) {
            appendActivityLog("[!] Get channels failed");
        }
    }

    public void sendSetChannel(int channelIndex, String name) {
        sendSetChannel(channelIndex, name, null);
    }

    public void sendSetChannel(int channelIndex, String name, byte[] secretBytes) {
        try {
            byte[] nb = name.getBytes("UTF-8");
            if (nb.length > 32) {
                byte[] trimmed = new byte[32];
                System.arraycopy(nb, 0, trimmed, 0, 32);
                nb = trimmed;
            }
            byte[] name32 = new byte[32];
            System.arraycopy(nb, 0, name32, 0, nb.length);
            byte[] secret = (secretBytes != null && secretBytes.length == 16)
                ? secretBytes : SHA256.channelSecret(name);
            byte[] f = new byte[2 + 32 + 16];
            f[0] = (byte) ProtocolConstants.CMD_SET_CHANNEL;
            f[1] = (byte) (channelIndex & 0xFF);
            System.arraycopy(name32, 0, f, 2, 32);
            System.arraycopy(secret, 0, f, 34, 16);
            transport.sendFrame(f);
        } catch (Exception e) {
            appendActivityLog("[!] Set channel failed");
        }
    }

    public void removeContact(int idx) {
        if (idx < 0 || idx >= contactKeys.size()) return;
        try {
            byte[] key = (byte[]) contactKeys.elementAt(idx);
            byte[] f = new byte[1 + 32];
            f[0] = (byte) ProtocolConstants.CMD_REMOVE_CONTACT;
            System.arraycopy(key, 0, f, 1, 32);
            transport.sendFrame(f);
            sendGetContacts();
        } catch (IOException e) {
            appendActivityLog("[!] Remove failed");
        }
    }

    public void sendRefreshSettings() {
        try {
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
            transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_BATT_STORAGE});
        } catch (IOException ignore) {}
    }

    public void sendGetBattery() {
        try {
            transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_BATT_STORAGE});
        } catch (IOException ignore) {}
    }

    public void sendGetStats() {
        try {
            transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_STATS, 0});
        } catch (IOException e) {
            appendActivityLog("[!] Stats failed");
        }
    }

    public void sendGetDeviceTime() {
        try {
            transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_DEVICE_TIME});
        } catch (IOException e) {
            appendActivityLog("[!] Time failed");
        }
    }

    public void sendAdvert() {
        try {
            byte[] f = {(byte) ProtocolConstants.CMD_SEND_SELF_ADVERT, 0};
            transport.sendFrame(f);
            appendActivityLog("[*] Advertisement sent");
        } catch (IOException ignore) {}
    }

    public void trySyncMessages() {
        if (!connected) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_SYNC_NEXT_MESSAGE});
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
            nodeFreq = ParseUtils.parseFreqBw(settingsScreen.getFreq(), nodeFreq);
            nodeBw = ParseUtils.parseFreqBw(settingsScreen.getBw(), nodeBw);
            nodeSf = ParseUtils.parseInt(settingsScreen.getSf(), nodeSf);
            nodeCr = ParseUtils.parseInt(settingsScreen.getCr(), nodeCr);
            nodeTxPwr = ParseUtils.parseInt(settingsScreen.getTxPwr(), nodeTxPwr);

            byte[] rp = new byte[11];
            rp[0] = (byte) ProtocolConstants.CMD_SET_RADIO_PARAMS;
            FrameTransport.writeUint32LE(rp, 1, nodeFreq);
            FrameTransport.writeUint32LE(rp, 5, nodeBw);
            rp[9] = (byte) nodeSf;
            rp[10] = (byte) nodeCr;
            transport.sendFrame(rp);

            transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_SET_RADIO_TX_PWR, (byte) nodeTxPwr});
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
        int idx = channelIndex;
        if (idx < 0 || idx >= channelBuffers.size()) idx = 0;
        StringBuffer buf = (StringBuffer) channelBuffers.elementAt(idx);
        buf.append(line).append("\n");
        if (buf.length() > 2000) {
            buf.delete(0, buf.length() - 2000);
        }
        final ChannelScreen ch = channelScreen;
        final int chIdx = idx;
        final String chName = idx < channelNames.size() ? (String) channelNames.elementAt(idx) : "#" + idx;
        display.callSerially(new Runnable() {
            public void run() {
                if (ch != null && display.getCurrent() == ch && ch.getChannelIndex() == chIdx) {
                    ch.refreshLog();
                } else {
                    incChannelUnread(chIdx);
                    refreshChannelListIfShown();
                    showIncomingNotification("New message in " + chName);
                }
            }
        });
    }

    public void appendActivityLog(final String line) {
        activityLogBuf.append(line).append("\n");
        if (activityLogBuf.length() > 2000) {
            activityLogBuf.delete(0, activityLogBuf.length() - 2000);
        }
        final ActivityLogScreen act = activityLogScreen;
        display.callSerially(new Runnable() {
            public void run() {
                if (act != null) act.refreshLog();
            }
        });
    }

    public void appendDM(final int contactIdx, final String line) {
        if (contactIdx < 0) return;
        ensureDmBuffersSize(contactIdx + 1);
        StringBuffer buf = (StringBuffer) dmBuffers.elementAt(contactIdx);
        buf.append(line).append("\n");
        if (buf.length() > 2000) {
            buf.delete(0, buf.length() - 2000);
        }
        final DMScreen dm = dmScreen;
        final int cIdx = contactIdx;
        display.callSerially(new Runnable() {
            public void run() {
                if (dm != null && display.getCurrent() == dm && dm.getContactIdx() == cIdx) {
                    dm.refreshLog();
                } else {
                    incContactUnread(cIdx);
                    refreshContactsListIfShown();
                    String from = extractDMSender(line);
                    showIncomingNotification("New DM" + (from.length() > 0 ? " from " + from : ""));
                }
            }
        });
    }

    private void ensureDmBuffersSize(int size) {
        while (dmBuffers.size() < size) {
            dmBuffers.addElement(new StringBuffer());
        }
    }

    private void ensureChannelUnreadSize(int size) {
        while (channelUnreadCount.size() < size) {
            channelUnreadCount.addElement(new Integer(0));
        }
    }

    private void ensureContactUnreadSize(int size) {
        while (contactUnreadCount.size() < size) {
            contactUnreadCount.addElement(new Integer(0));
        }
    }

    private void incChannelUnread(int idx) {
        ensureChannelUnreadSize(idx + 1);
        int n = ((Integer) channelUnreadCount.elementAt(idx)).intValue();
        channelUnreadCount.setElementAt(new Integer(n + 1), idx);
    }

    private void setChannelUnread(int idx, int val) {
        ensureChannelUnreadSize(idx + 1);
        channelUnreadCount.setElementAt(new Integer(val), idx);
    }

    private void incContactUnread(int idx) {
        ensureContactUnreadSize(idx + 1);
        int n = ((Integer) contactUnreadCount.elementAt(idx)).intValue();
        contactUnreadCount.setElementAt(new Integer(n + 1), idx);
    }

    private void setContactUnread(int idx, int val) {
        ensureContactUnreadSize(idx + 1);
        contactUnreadCount.setElementAt(new Integer(val), idx);
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

    private static String extractDMSender(String line) {
        int s = line.indexOf('[');
        int e = (s >= 0) ? line.indexOf(']', s) : -1;
        return (s >= 0 && e > s) ? line.substring(s + 1, e) : "";
    }

    private void showIncomingNotification(String message) {
        Displayable current = display.getCurrent();
        if (current == null) return;
        try {
            String safe = sanitizeAlertMessage(message, 60);
            Alert a = new Alert("Message", safe, null, AlertType.INFO);
            a.setTimeout(2000);
            display.setCurrent(a, current);
        } catch (Exception e) {
            /* Nokia S40 can throw on Alert with empty/long/special chars */
        }
    }

    private void startKeepAlive() {
        keepAliveRunning = true;
        new Thread(new Runnable() {
            public void run() {
                while (keepAliveRunning) {
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ignore) {}
                    if (!keepAliveRunning || !running || !connected || transport == null) continue;
                    try {
                        byte[] cmd = {(byte) ProtocolConstants.CMD_GET_BATT_STORAGE};
                        transport.sendFrame(cmd);
                    } catch (IOException e) {
                        appendActivityLog("[!] Keepalive failed, stopping connection");
                        connected = false;
                        running = false;
                        keepAliveRunning = false;
                    }
                }
            }
        }).start();
    }

    private static String sanitizeAlertMessage(String s, int maxLen) {
        if (s == null || s.length() == 0) return "New message";
        StringBuffer sb = new StringBuffer();
        int len = Math.min(s.length(), maxLen);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127) sb.append(c);
        }
        return sb.length() > 0 ? sb.toString() : "New message";
    }

    private void setConnectTitle(final String t) {
        display.callSerially(new Runnable() {
            public void run() {
                if (connectScreen != null) connectScreen.setTitle(t);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Receive loop
    // -----------------------------------------------------------------------
    private void receiveLoop() {
        try {
            while (running && connected) {
                byte[] frame = transport.readFrame();
                if (frame != null && frame.length > 0) {
                    frameHandler.handleFrame(frame);
                }
            }
        } catch (IOException e) {
            if (running) appendActivityLog("[!] Connection lost");
        }
        connected = false;
        running = false;
        keepAliveRunning = false;
    }

    // -----------------------------------------------------------------------
    // FrameHandlerListener implementation
    // -----------------------------------------------------------------------
    public void onDeviceInfo(String ver) {
        firmwareVer = ver;
        appendActivityLog("[*] " + firmwareVer);
        refreshSettingsNodeIfShown();
    }

    public void onSelfInfo(String name, int txPwr, long freq, long bw, int sf, int cr) {
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
        display.callSerially(new Runnable() {
            public void run() {
                if (display.getCurrent() == set) {
                    set.setNodeInfo(nodeName, firmwareVer);
                }
            }
        });
    }

    public void onContactsStart() {
    }

    public void onContactsEnd() {
        display.callSerially(new Runnable() {
            public void run() {
                if (display.getCurrent() == contactsScreen) {
                    showContactsScreen();
                }
            }
        });
    }

    public void onChannelInfo(final int chIdx, final String name) {
        display.callSerially(new Runnable() {
            public void run() {
                while (channelNames.size() <= chIdx) {
                    channelNames.addElement(chIdx == 0 ? ChannelListScreen.PUBLIC_CHANNEL : "");
                    channelBuffers.addElement(new StringBuffer());
                    channelUnreadCount.addElement(new Integer(0));
                }
                channelNames.setElementAt(name, chIdx);
                ensureChannelUnreadSize(channelNames.size());
                if (chIdx >= 1) {
                    scheduleDebouncedSave();
                }
            }
        });
    }

    public void onBatteryUpdate(final String info) {
        final SettingsScreen set = settingsScreen;
        final MainMenuScreen menu = mainMenuScreen;
        final String status = formatBatteryStatus(info);
        lastBatteryStatus = status;
        display.callSerially(new Runnable() {
            public void run() {
                if (set != null) set.setBattInfo(info);
                if (menu != null) {
                    String title = "MeshCore";
                    if (status.length() > 0) title += " " + status;
                    menu.setTitle(title);
                }
            }
        });
    }

    private static String formatBatteryStatus(String info) {
        if (info == null) return "";
        int p1 = info.indexOf('(');
        int p2 = (p1 >= 0) ? info.indexOf(')', p1) : -1;
        if (p1 >= 0 && p2 > p1) {
            return info.substring(p1, p2 + 1).trim(); // e.g. "(4.18V)"
        }
        int m = info.indexOf("mV");
        if (m > 0) {
            return info.substring(0, m + 2).trim(); // e.g. "4100mV"
        }
        return "";
    }

    public void onStats(final String title, final String content) {
        final SettingsScreen set = settingsScreen;
        display.callSerially(new Runnable() {
            public void run() {
                if (set != null) set.showInfo(title, content);
            }
        });
    }

    public void onDeviceTime(final String content) {
        final SettingsScreen set = settingsScreen;
        display.callSerially(new Runnable() {
            public void run() {
                if (set != null) set.showInfo("Device Time", content);
            }
        });
    }

    public void onError(int code) {
        appendActivityLog("[!] Error " + code);
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
