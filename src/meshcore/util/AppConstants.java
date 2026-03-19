package meshcore.util;

public final class AppConstants {

    private AppConstants() {}

    public static final int SPLASH_DELAY_MS = 500; //3500
    public static final int POST_CONNECT_DELAY_MS = 500; //1500

    public static final int KEEPALIVE_INTERVAL_MS = 15000;
    
    public static final int RECONNECT_ATTEMPTS = 5;
    public static final int RECONNECT_DELAY_MS = 3000;
    public static final int TITLE_ROTATION_MS = 3000;

    public static final int CHANNELS_SYNCED_LOG_DELAY_MS = 2000;
    public static final int NOTIFICATION_DURATION_MS = 2000;

    public static final int MAX_BUFFER_LENGTH = 2000;
    public static final int NOTIFICATION_BLINK_INTERVAL_MS = 750;

    /** Max UTF-8 bytes for messages. */
    public static final int CHANNEL_MSG_MAX_BYTES = 130;
    public static final int DM_MSG_MAX_BYTES = 150;

    /** DM send retry (used by DmSendManager). */
    public static final int DM_SEND_MAX_ATTEMPTS = 3;
    /** Extra flood-mode retries after path reset. */
    public static final int DM_FLOOD_EXTRA_ATTEMPTS = 1;
    public static final int DM_SEND_TIMEOUT_MS = 10000;

    /** DM status: "Sending" then "Sending 2/3", "Sending 3/3" etc. */
    public static final String DM_STATUS_SENDING = "Sending";
    public static final String DM_STATUS_DELIVERED = "Delivered";
    public static final String DM_STATUS_FAILED = "Failed";

    /** RMS history caps per conversation. */
    public static final int HISTORY_MAX_DM_MESSAGES = 200;
    public static final int HISTORY_MAX_CHANNEL_MESSAGES = 100;

    /** How many recent lines to load into memory when opening a chat. */
    public static final int HISTORY_MAX_LOADED_LINES = 10;

    /** Warn when history storage exceeds this (KB). Some devices struggle above 32–64 KB. */
    public static final int HISTORY_STORAGE_WARN_KB = 64;
}

