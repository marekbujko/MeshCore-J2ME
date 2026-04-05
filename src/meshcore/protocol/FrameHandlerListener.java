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
    /**
     * Extended RESP_SELF_INFO (v7+): offsets 44..47 after pubkey, lat, lon — multi_acks, advert_loc_policy,
     * telemetry mode byte, manual_add_contacts. Only called when the frame is long enough.
     */
    void onCompanionOtherParams(int multiAcks, int advertLocPolicy, int telemetryPacked, int manualAddContacts);
    void onChannelInfo(int channelIndex, String name);
    void onContactsStart();
    void onContactsEnd();
    /** STATS sub_type=0: node uptime in seconds. */
    void onNodeMetrics(long uptimeSeconds);
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

    /**
     * PUSH_CODE_TELEMETRY_RESPONSE: [0]=0x8B, [1]=reserved, [2..7] pubkey prefix, [8..] LPP payload.
     */
    void onTelemetryResponse(byte[] pubKeyPrefix6, byte[] lppPayload);

    /**
     * PUSH_LOGIN_SUCCESS (0x85) or PUSH_LOGIN_FAIL (0x86).
     * Success: permissions at [1], prefix at [2..7]; serverTag from [8..11] when frame length is at least 12;
     * newPermissions at [12] when length is at least 13, else -1.
     * Fail: permissions is the reserved byte at [1]; serverTag and newPermissions are unused.
     */
    void onLoginPush(boolean success, byte[] pubKeyPrefix6, int permissions, long serverTag, int newPermissions);

    boolean isContactsScreenCurrent();
    void showContactsScreen();

    /** Called when the node confirms a message was delivered. */
    void onMessageDelivered();
}
