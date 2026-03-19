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
    void onSelfInfo(String nodeName, int txPwr, long freq, long bw, int sf, int cr,
                    byte[] nodePublicKey, int advLatE6, int advLonE6);
    void onChannelInfo(int channelIndex, String name);
    void onContactsStart();
    void onContactsEnd();
    void onStats(String title, String content);
    /** RADIO stats (sub_type=1): noise floor in dBm. */
    void onNoiseFloor(int noiseFloorDbm);
    void onDeviceTime(String content);
    void onBatteryUpdate(String info);
    void onError(int code);

    /**
     * Trace path / ping result.
     * PUSH_TRACE_DATA frame payload (after push code):
     *  [1]    reserved
     *  [2]    path_len
     *  [3]    flags
     *  [4..7]  tag (uint32 LE)
     *  [8..11] auth_code (uint32 LE)
     *  [12..] path_hashes[path_len], path_snrs[path_len], final_snr (int8)
     */
    void onTraceData(int flags, long tag, long authCode, byte[] pathHashes, byte[] pathSnrs, int finalSNR4);

    boolean isContactsScreenCurrent();
    void showContactsScreen();

    /** Called when the node confirms a message was delivered. */
    void onMessageDelivered();
}
