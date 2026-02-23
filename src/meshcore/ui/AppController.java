package meshcore.ui;

import javax.microedition.lcdui.Display;

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
    void showDMScreen(int idx);
    void showSettingsScreen();
    void showActivityLogScreen();

    void connect(String host, int port);
    void disconnect();
    void sendChannelMessage(int channelIndex, String msg);
    void sendDirectMessage(int idx, String msg);
    void sendGetContacts();
    void removeContact(int idx);
    void saveSettings();
    void sendGetBattery();
    void sendGetStats();
    void sendGetDeviceTime();
    void sendAdvert();
    void trySyncMessages();

    void appendChannel(int channelIndex, String line);
    void appendActivityLog(String line);
    void appendDM(String line);
}
