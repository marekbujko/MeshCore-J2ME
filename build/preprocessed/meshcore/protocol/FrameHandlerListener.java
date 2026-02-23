package meshcore.protocol;

/**
 * Callbacks for frame handling. Implemented by MeshCore.
 */
public interface FrameHandlerListener {

    void appendChannel(int channelIndex, String line);
    void appendActivityLog(String line);
    void appendDM(String line);
    void trySyncMessages();
    void sendGetContacts();

    void onDeviceInfo(String firmwareVer);
    void onSelfInfo(String nodeName, int txPwr, long freq, long bw, int sf, int cr);
    void onContactsEnd();
    void onStats(String title, String content);
    void onDeviceTime(String content);
    void onBatteryUpdate(String info);
    void onError(int code);
    void onChannelInfo(int channelIdx, String name);

    boolean isContactsScreenCurrent();
    void showContactsScreen();
}
