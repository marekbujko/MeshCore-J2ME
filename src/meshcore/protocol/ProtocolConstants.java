package meshcore.protocol;

/**
 * MeshCore Companion Radio Protocol constants.
 * Reference: https://github.com/meshcore-dev/MeshCore/wiki/Companion-Radio-Protocol
 */
public final class ProtocolConstants {

    private ProtocolConstants() {}

    // Commands (app -> radio)
    public static final int CMD_APP_START         = 1;
    public static final int CMD_SEND_TXT_MSG     = 2;
    public static final int CMD_SEND_CHANNEL_MSG = 3;
    public static final int CMD_GET_CONTACTS     = 4;
    public static final int CMD_SET_DEVICE_TIME  = 6;
    public static final int CMD_SEND_SELF_ADVERT  = 7;
    public static final int CMD_SET_ADVERT_NAME  = 8;

    /** Advert type: second byte in CMD_SEND_SELF_ADVERT frame (0 = zero-hop, 1 = flood) */
    public static final int ADVERT_ZERO_HOP = 0;
    public static final int ADVERT_FLOOD    = 1;
    public static final int CMD_SYNC_NEXT_MESSAGE = 10;
    public static final int CMD_SET_RADIO_PARAMS  = 11;
    public static final int CMD_SET_RADIO_TX_PWR  = 12;
    public static final int CMD_RESET_PATH        = 13;
    public static final int CMD_REMOVE_CONTACT    = 15;
    public static final int CMD_GET_CHANNEL       = 31;
    public static final int CMD_SET_CHANNEL       = 32;
    public static final int CMD_GET_BATT_STORAGE  = 20;
    public static final int CMD_GET_STATS         = 56;
    public static final int CMD_DEVICE_QUERY      = 22;
    public static final int CMD_GET_DEVICE_TIME   = 5;

    // Responses (radio -> app)
    public static final int RESP_OK             = 0;
    public static final int RESP_ERR            = 1;
    public static final int RESP_CONTACTS_START  = 2;
    public static final int RESP_CONTACT         = 3;
    public static final int RESP_END_CONTACTS    = 4;
    public static final int RESP_SELF_INFO       = 5;
    public static final int RESP_SENT            = 6;
    public static final int RESP_CONTACT_MSG     = 7;
    public static final int RESP_CHANNEL_MSG     = 8;
    public static final int RESP_NO_MORE_MSGS    = 10;
    public static final int RESP_BATT_STORAGE    = 12;
    public static final int RESP_DEVICE_INFO     = 13;
    public static final int RESP_CURR_TIME       = 9;
    public static final int RESP_STATS           = 24;
    public static final int RESP_CHANNEL_INFO    = 18;
    public static final int RESP_CONTACT_MSG_V3  = 16;
    public static final int RESP_CHANNEL_MSG_V3   = 17;

    // Push notifications
    public static final int PUSH_MSG_WAITING    = 0x83;
    public static final int PUSH_SEND_CONFIRMED = 0x82;
    public static final int PUSH_ADVERT         = 0x80;
    public static final int PUSH_PATH_UPDATED   = 0x81;

    // Advert types (in RESP_CONTACT)
    public static final int ADV_TYPE_NONE    = 0;
    public static final int ADV_TYPE_CHAT    = 1;
    public static final int ADV_TYPE_REPEATER = 2;
    public static final int ADV_TYPE_ROOM    = 3;

    // Error codes (byte in RESP_ERR frame)
    public static final int ERR_UNSUPPORTED_CMD = 1;
    public static final int ERR_NOT_FOUND        = 2;
    public static final int ERR_TABLE_FULL       = 3;
    public static final int ERR_BAD_STATE        = 4;
    public static final int ERR_FILE_IO          = 5;
    public static final int ERR_ILLEGAL_ARG      = 6;

    // Default node limits (channel slots and contacts)
    public static final int MAX_CHANNEL_SLOTS = 40;
    public static final int MAX_CONTACTS = 350;

    // App protocol version
    public static final int APP_VER = 3;

    // Frame framing
    public static final byte FRAME_OUTBOUND_MARKER = (byte) '>';
    public static final byte FRAME_INBOUND_MARKER = (byte) '<';
    public static final int FRAME_MAX_LEN = 512;
}
