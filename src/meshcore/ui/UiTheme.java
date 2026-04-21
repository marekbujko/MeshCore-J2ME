package meshcore.ui;

/**
 * Shared UI theme constants for custom Canvas screens.
 */
public final class UiTheme {

    private UiTheme() {}

    public static final int BG_WHITE = 0xFFFFFF;

    // Bubble styling.
    public static final int BUBBLE_FILL = 0xF8F8F8;
    public static final int BUBBLE_BORDER = 0x9E9E9E;
    public static final int BUBBLE_TEXT = 0x1E344A;

    // Text + arrows.
    public static final int TEXT_DARK = 0x1F1F1F;
    public static final int TEXT_GRAY = 0x333333;
    public static final int LINE_GRAY = 0xD0D0D0;

    /** Primary action / header bar (matches pill buttons). */
    public static final int BAR_BG = 0x212121;
    public static final int BAR_TEXT = 0xFFFFFF;
    public static final int BAR_MUTED = 0xB8B8B8;

    /** Content area behind cards. */
    public static final int PANEL_BG = 0xEEEEEE;
    public static final int CARD_FILL = 0xFFFFFF;
    public static final int CARD_BORDER = 0xD8D8D8;

    public static final int SNR_TEXT = 0x444444;
    public static final int ARROW = 0x7F878E;

    /** Ping/trace repeater chips: forest terminal (deep pine + mint accent). */
    public static final int TIMELINE_NODE_FILL = 0x132A22;
    public static final int TIMELINE_NODE_BORDER = 0x3A6B5A;
    public static final int TIMELINE_NODE_ACCENT = 0x6BCB77;
    public static final int TIMELINE_NODE_TEXT = 0xD8F3DC;
    public static final int TIMELINE_SNR_TEXT = 0x2A4A3E;
    public static final int TIMELINE_ARROW = 0x4A9B6B;

    // Scroll bar.
    public static final int SCROLL_BAR_BG = 0xBBBBBB;
    public static final int SCROLL_BAR_THUMB = 0x666666;

    /**
     * Map viewer with no tile URL: soft mint fill (same family as selected tiles / 0xD4EDE0 on main menu).
     */
    public static final int MAP_EMPTY_BG = 0xE6F3EC;
    /** Grid lines on empty map (subtle, readable on {@link #MAP_EMPTY_BG}). */
    public static final int MAP_EMPTY_GRID = 0xB8D8C4;
}

