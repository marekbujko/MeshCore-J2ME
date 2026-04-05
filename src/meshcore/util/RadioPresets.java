package meshcore.util;

/**
 * Regional LoRa radio presets (frequency in {@link #FREQ_K} = MHz * 1000, bandwidth in Hz).
 */
public final class RadioPresets {

    private RadioPresets() {}

    public static final int COUNT = 19;

    private static final String[] NAMES = {
        "Australia",
        "Australia (Narrow)",
        "Australia SA, WA, QLD",
        "Czech Republic",
        "EU 433MHz",
        "EU/UK (Long Range)",
        "EU/UK (Medium Range)",
        "EU/UK (Narrow)",
        "New Zealand",
        "New Zealand (Narrow)",
        "Portugal 433",
        "Portugal 869",
        "Switzerland",
        "USA Arizona",
        "USA/Canada",
        "Vietnam",
        "Off-Grid 433",
        "Off-Grid 869",
        "Off-Grid 918",
    };

    /** Frequency as MHz * 1000 (matches protocol / {@link ParseUtils#parseFreqBw}). */
    private static final long[] FREQ_K = {
        915800L, 916575L, 923125L, 869432L, 433650L, 869525L, 869525L, 869618L,
        917375L, 917375L, 433375L, 869618L, 869618L, 908205L, 910525L, 920250L,
        433000L, 869000L, 918000L,
    };

    private static final long[] BW_HZ = {
        250000L, 62500L, 62500L, 62500L, 250000L, 250000L, 250000L, 62500L,
        250000L, 62500L, 62500L, 62500L, 62500L, 62500L, 62500L, 250000L,
        250000L, 250000L, 250000L,
    };

    private static final int[] SF = {
        10, 7, 8, 7, 11, 11, 10, 8, 11, 7, 9, 7, 8, 10, 7, 11, 11, 11, 11,
    };

    private static final int[] CR = {
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
    };

    private static final int[] TX_DBM = {
        22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    };

    public static String getName(int index) {
        if (index < 0 || index >= COUNT) {
            return "";
        }
        return NAMES[index];
    }

    public static long getFreqK(int index) {
        if (index < 0 || index >= COUNT) {
            return 0;
        }
        return FREQ_K[index];
    }

    public static long getBandwidthHz(int index) {
        if (index < 0 || index >= COUNT) {
            return 0;
        }
        return BW_HZ[index];
    }

    public static int getSf(int index) {
        if (index < 0 || index >= COUNT) {
            return 0;
        }
        return SF[index];
    }

    public static int getCr(int index) {
        if (index < 0 || index >= COUNT) {
            return 0;
        }
        return CR[index];
    }

    public static int getTxDbm(int index) {
        if (index < 0 || index >= COUNT) {
            return 0;
        }
        return TX_DBM[index];
    }
}
