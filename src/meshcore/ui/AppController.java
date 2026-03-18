package meshcore.ui;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;

/**
 * Callback interface for screens to trigger app actions.
 * MeshCore implements this.
 */
public interface AppController {

    Display getDisplay();

    void showMainMenu();
    void showConnectScreen();
    void showChannelListScreen();
    void showChannelScreen(int channelIndex);
    void addChannel(String name);
    void addPrivateChannel(String name, String secretHex);
    void removeChannel(int index);
    void showContactsScreen();
    void showRepeatersScreen();
    void showDMScreen(int idx);
    void showDMScreen(int idx, Displayable returnTo);
    void showNotificationsScreen();
    void showFavoritesScreen();
    void showMyContactCode();
    /** Show Share My Contact and return to the given screen on Back. */
    void showMyContactCode(Displayable returnTo);
    void showSettingsScreen();
    void showActivityLogScreen();

    void showMessageSettingsScreen();

    void connect(String host, int port);
    void connectWithSplash(String host, int port);
    void disconnect();
    void sendChannelMessage(int channelIndex, String msg);
    void sendDirectMessage(int idx, String msg);
    void sendGetContacts();
    void removeContact(int idx);
    void addManualContact(String name, String publicKeyHex, int advType);
    boolean isFavorite(int contactIdx);
    void addFavorite(int contactIdx);
    void removeFavorite(int contactIdx);
    void resetPath(int contactIdx);
    void updateContactPath(int contactIdx, byte[] newPath);
    void saveSettings();
    void sendGetBattery();
    void sendRefreshSettings();
    void sendGetStats();
    void sendGetRadioStats();
    void sendGetDeviceTime();
    void sendAdvert();
    void setAdvertType(int type);
    int getAdvertType();
    void trySyncMessages();

    /** Full 32-byte public key for a contact, as hex. */
    String getContactPublicKeyHex(int contactIdx);

    /** JAD/manifest property (e.g. MIDlet-Name, MIDlet-Version, MIDlet-Vendor). */
    String getAppProperty(String key);

    /** Channel name at index. */
    String getChannelName(int channelIndex);
    /** Display name for channel (private channels get a prefix, e.g. "[P] name"). */
    String getChannelDisplayName(int channelIndex);
    /** Channel secret as 32 hex chars (stored or derived from name for public/hashtag). */
    String getChannelSecretHex(int channelIndex);

    /** Current node name (from radio / Settings). */
    String getNodeName();
    /** Last battery/voltage string (e.g. "(4.18V)" or "4100mV"). */
    String getBatteryStatus();
    /** Latest line from the activity log (for dashboard). */
    String getLatestActivityLogLine();
    /** Last noise floor from stats (dBm), or null if unknown. */
    Integer getNoiseFloor();
    /** Show Noise Floor screen from Tools. */
    void showNoiseFloorScreen(Displayable returnTo);
    /** Show Tools screen (so app can refresh noise when stats arrive). */
    void showToolsScreen(Displayable returnTo);

    void appendChannel(int channelIndex, String line);
    void appendActivityLog(String line);
    void appendDM(int contactIdx, String line);

    void clearChannelHistory(int channelIndex);
    void clearDmHistory(int contactIdx);
    int getContactPathHops(int contactIdx);
    byte[] getContactPathBytes(int contactIdx);
    String getRepeaterNameForPathByte(byte pathByte);
    java.util.Vector getRepeaterIndices();
    byte getRepeaterPathByte(int contactIdx);
    String getRepeaterPublicKeyHex(int contactIdx);
}
