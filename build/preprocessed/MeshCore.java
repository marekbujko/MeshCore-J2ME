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
import meshcore.util.ParseUtils;
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
    private StringBuffer activityLogBuf = new StringBuffer();
    private StringBuffer dmBuf = new StringBuffer();

    private Vector contactNames = new Vector();
    private Vector contactKeys = new Vector();

    private long nodeFreq = 915000000L;
    private long nodeBw = 250000L;
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
        channelNames.addElement(ChannelListScreen.PUBLIC_CHANNEL);
        channelBuffers.addElement(new StringBuffer());
        Vector custom = ChannelStorage.load();
        for (int i = 0; i < custom.size(); i++) {
            channelNames.addElement(custom.elementAt(i));
            channelBuffers.addElement(new StringBuffer());
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
        display.setCurrent(mainMenuScreen);
    }

    public void showConnectScreen() {
        connectScreen = new ConnectScreen(this);
        display.setCurrent(connectScreen);
    }

    public void showChannelListScreen() {
        channelListScreen = new ChannelListScreen(this, channelNames);
        display.setCurrent(channelListScreen);
    }

    public void showChannelScreen(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channelBuffers.size()) return;
        String name = (String) channelNames.elementAt(channelIndex);
        StringBuffer buf = (StringBuffer) channelBuffers.elementAt(channelIndex);
        channelScreen = new ChannelScreen(this, channelIndex, name, buf);
        display.setCurrent(channelScreen);
        trySyncMessages();
    }

    public void addChannel(String name) {
        for (int i = 0; i < channelNames.size(); i++) {
            if (name.equalsIgnoreCase((String) channelNames.elementAt(i))) {
                return;
            }
        }
        int newIdx = channelNames.size();
        channelNames.addElement(name);
        channelBuffers.addElement(new StringBuffer());
        saveCustomChannels();
        if (connected && transport != null) {
            sendSetChannel(newIdx, name);
        }
    }

    public void removeChannel(int index) {
        if (index <= 0 || index >= channelNames.size()) return;
        channelNames.removeElementAt(index);
        channelBuffers.removeElementAt(index);
        saveCustomChannels();
    }

    private void saveCustomChannels() {
        Vector custom = new Vector();
        for (int i = 1; i < channelNames.size(); i++) {
            custom.addElement(channelNames.elementAt(i));
        }
        ChannelStorage.save(custom);
    }

    public void showContactsScreen() {
        contactsScreen = new ContactsScreen(this, contactNames);
        display.setCurrent(contactsScreen);
    }

    public void showDMScreen(int idx) {
        dmBuf.delete(0, dmBuf.length());
        String name = (String) contactNames.elementAt(idx);
        dmScreen = new DMScreen(this, idx, name, dmBuf);
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

            running = true;
            new Thread(new Runnable() {
                public void run() {
                    receiveLoop();
                }
            }).start();

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
            f[1] = 0; // txt_type plain
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
            appendDM("[me] " + msg);
        } catch (IOException e) {
            appendDM("[!] Send error");
        }
    }

    public void sendGetContacts() {
        try {
            transport.sendFrame(new byte[]{(byte) ProtocolConstants.CMD_GET_CONTACTS});
        } catch (IOException e) {
            appendActivityLog("[!] Sync failed");
        }
    }

    /** Sends CMD_SET_CHANNEL to create/update a channel on the radio. */
    public void sendSetChannel(int channelIndex, String name) {
        try {
            byte[] nb = name.getBytes("UTF-8");
            byte[] psk = deriveChannelPSK(name);
            byte[] f = new byte[1 + 1 + 4 + 32 + 32];
            int i = 0;
            f[i++] = (byte) ProtocolConstants.CMD_SET_CHANNEL;
            f[i++] = (byte) (channelIndex & 0xFF);
            f[i++] = 0;
            f[i++] = 0;
            f[i++] = 0;
            f[i++] = 0;
            for (int j = 0; j < 32; j++) {
                f[i + j] = (j < nb.length) ? nb[j] : 0;
            }
            i += 32;
            System.arraycopy(psk, 0, f, i, 32);
            transport.sendFrame(f);
            appendActivityLog("[*] Channel '" + name + "' sent to radio");
        } catch (IOException e) {
            appendActivityLog("[!] Set channel failed");
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** Deterministic 32-byte PSK from channel name, with overrides for known channels. */
    private static byte[] deriveChannelPSK(String name) {
        if ("#plovdivbot".equalsIgnoreCase(name)) {
            byte[] key16 = hexToBytes("165c2e70200ae1a37f8c21e40f5e9a7c");
            byte[] psk = new byte[32];
            System.arraycopy(key16, 0, psk, 0, key16.length);
            return psk;
        }
        byte[] nb;
        try {
            nb = name.getBytes("UTF-8");
        } catch (Exception e) {
            nb = name.getBytes();
        }
        byte[] psk = new byte[32];
        for (int i = 0; i < 32; i++) {
            psk[i] = (byte) ((nb[i % nb.length] & 0xFF) ^ (i * 31));
        }
        return psk;
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
            nodeFreq = ParseUtils.parseLong(settingsScreen.getFreq(), nodeFreq);
            nodeBw = ParseUtils.parseLong(settingsScreen.getBw(), nodeBw);
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
        display.callSerially(new Runnable() {
            public void run() {
                if (ch != null && ch.getChannelIndex() == chIdx) ch.refreshLog();
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

    public void appendDM(final String line) {
        dmBuf.append(line).append("\n");
        if (dmBuf.length() > 2000) {
            dmBuf.delete(0, dmBuf.length() - 2000);
        }
        final DMScreen dm = dmScreen;
        display.callSerially(new Runnable() {
            public void run() {
                if (dm != null) dm.refreshLog();
            }
        });
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
    }

    // -----------------------------------------------------------------------
    // FrameHandlerListener implementation
    // -----------------------------------------------------------------------
    public void onDeviceInfo(String ver) {
        firmwareVer = ver;
        appendActivityLog("[*] " + firmwareVer);
    }

    public void onSelfInfo(String name, int txPwr, long freq, long bw, int sf, int cr) {
        nodeName = name;
        nodeTxPwr = txPwr;
        nodeFreq = freq;
        nodeBw = bw;
        nodeSf = sf;
        nodeCr = cr;
        appendActivityLog("[*] Node: " + nodeName);
        trySyncMessages();
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

    public void onBatteryUpdate(final String info) {
        final SettingsScreen set = settingsScreen;
        display.callSerially(new Runnable() {
            public void run() {
                if (set != null) set.setBattInfo(info);
            }
        });
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

    public void onChannelInfo(int channelIdx, String name) {
        if (name == null || name.length() == 0 || channelIdx == 0) return;
        for (int i = 0; i < channelNames.size(); i++) {
            if (name.equalsIgnoreCase((String) channelNames.elementAt(i))) return;
        }
        while (channelNames.size() <= channelIdx) {
            channelNames.addElement("");
            channelBuffers.addElement(new StringBuffer());
        }
        channelNames.setElementAt(name, channelIdx);
        saveCustomChannels();
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
