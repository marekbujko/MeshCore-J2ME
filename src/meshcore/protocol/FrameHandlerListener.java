package meshcore.protocol;

/**
 * Callbacks for frame handling. Implemented by MeshCore.
 */
public interface FrameHandlerListener {

    void appendChannel(int channelIndex, String line);
    void appendActivityLog(String line);
    void appendDM(int contactIdx, String line);
    void trySyncMessages();
    void sendGetContacts();

    void onDeviceInfo(String firmwareVer);
    void onSelfInfo(String nodeName, int txPwr, long freq, long bw, int sf, int cr);
    void onChannelInfo(int channelIndex, String name);
    void onContactsStart();
    void onContactsEnd();
    void onStats(String title, String content);
    void onDeviceTime(String content);
    void onBatteryUpdate(String info);
    void onError(int code);

    boolean isContactsScreenCurrent();
    void showContactsScreen();
}
