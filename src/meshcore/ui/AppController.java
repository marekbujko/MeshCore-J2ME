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
    boolean isFavorite(int contactIdx);
    void addFavorite(int contactIdx);
    void removeFavorite(int contactIdx);
    void resetPath(int contactIdx);
    void updateContactPath(int contactIdx, byte[] newPath);
    void saveSettings();
    void sendGetBattery();
    void sendRefreshSettings();
    void sendGetStats();
    void sendGetDeviceTime();
    void sendAdvert();
    void setAdvertType(int type);
    int getAdvertType();
    void trySyncMessages();

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
