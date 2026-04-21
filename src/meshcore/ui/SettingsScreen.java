package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import meshcore.util.ParseUtils;
import meshcore.util.RadioPresets;
import meshcore.util.SettingsExtrasStore;
import meshcore.util.TextUtils;
/**
 * Settings: Canvas layout (Public info, Radio). Device details live on {@link DeviceInfoScreen}.
 * No in-canvas title bar; MIDlet title may still appear per device.
 */
public final class SettingsScreen extends Canvas implements CommandListener {

    /** Scroll-only mode (LEFT from node name); no focus ring until you Select or Fire to focus a row. */
    private static final int FOCUS_NONE = -1;
    private static final int FOCUS_NODE = 0;
    private static final int FOCUS_LAT = 1;
    private static final int FOCUS_LON = 2;
    private static final int FOCUS_SHARE = 3;
    private static final int FOCUS_DEVICE_INFO = 4;
    private static final int FOCUS_SAVE_TOP = 5;
    /** Before Frequency; matches vertical order in Radio Settings. */
    private static final int FOCUS_PRESET = 6;
    private static final int FOCUS_FREQ = 7;
    private static final int FOCUS_BW = 8;
    private static final int FOCUS_SF = 9;
    private static final int FOCUS_CR = 10;
    private static final int FOCUS_TX = 11;
    private static final int FOCUS_SAVE = 12;

    private final AppController app;
    private final Displayable settingsReturnTo;
    private final Command cmdSelect;
    private final Command cmdSave;
    private final Command cmdRefresh;
    private final Command cmdMsgSettings;
    private final Command cmdBack;

    private Font bodyFont;
    private Font smallFont;
    private Font sectionHeaderFont;

    private final UiScrollController scrollCtrl = new UiScrollController();
    private final UiScrollRepaintThrottler scrollDragRepaint = new UiScrollRepaintThrottler();

    private String editNodeName;
    private String editLat;
    private String editLon;
    private boolean sharePositionInAdvert;

    private String editFreq;
    private String editBw;
    private String editSf;
    private String editCr;
    private String editTx;

    /** Start on first field so the focus ring is visible; LEFT from node switches to scroll-only mode. */
    private int focusIndex = FOCUS_NODE;

    private int contentPadLeft;
    private int contentMaxW;

    private int hitNameT, hitNameB;
    private int hitLatT, hitLatB;
    private int hitLonT, hitLonB;
    private int hitCbT, hitCbB;
    private int hitFreqT, hitFreqB;
    private int hitBwT, hitBwB;
    private int hitSfT, hitSfB;
    private int hitCrT, hitCrB;
    private int hitTxT, hitTxB;
    private int hitDeviceInfoT, hitDeviceInfoB;
    private int hitSaveTopT, hitSaveTopB;
    private int hitPresetT, hitPresetB;
    private int hitSaveT, hitSaveB;
    private int cbRowH;
    private int deviceInfoBtnH;
    private int saveTopBtnH;
    private int presetBtnH;
    private int saveBtnH;

    private int nameCt, nameCb, latCt, latCb, lonCt, lonCb;
    private int cbCt, cbCb;
    private int fqCt, fqCb, bwCt, bwCb, sfCt, sfCb, crCt, crCb, txCt, txCb;
    private int diCt, diCb;
    private int svTopCt, svTopCb;
    private int prCt, prCb;
    private int svCt, svCb;

    public SettingsScreen(AppController app, Displayable settingsReturnTo, String nodeName,
            long nodeFreq, long nodeBw, int nodeSf, int nodeCr, int nodeTxPwr) {
        this.app = app;
        this.settingsReturnTo = settingsReturnTo;
        setFullScreenMode(false);
        setTitle("Settings");

        String nn = (nodeName != null ? nodeName : "");

        String nameInit = TextUtils.sanitizeLabel(nn, 32);
        if (nameInit.length() == 0) {
            nameInit = "Node0";
        }
        editNodeName = nameInit;
        editLat = formatCoordE6(app.getNodeAdvLatE6());
        editLon = formatCoordE6(app.getNodeAdvLonE6());
        sharePositionInAdvert = SettingsExtrasStore.loadSharePositionInAdvert();

        editFreq = formatFreqBw(nodeFreq);
        editBw = formatFreqBw(nodeBw);
        editSf = String.valueOf(nodeSf);
        editCr = String.valueOf(nodeCr);
        editTx = String.valueOf(nodeTxPwr);

        cmdSelect = new Command("Select", Command.SCREEN, 1);
        cmdSave = new Command("Save", Command.SCREEN, 2);
        cmdRefresh = new Command("Refresh", Command.SCREEN, 3);
        cmdMsgSettings = new Command("Message settings", Command.SCREEN, 4);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdSelect);
        addCommand(cmdSave);
        addCommand(cmdRefresh);
        addCommand(cmdMsgSettings);
        addCommand(cmdBack);
        setCommandListener(this);
        scrollCtrl.reset();
    }

    private static String formatFreqBw(long raw) {
        if (raw < 0) {
            raw = 0;
        }
        long whole = raw / 1000;
        int frac = (int) (raw % 1000);
        String f = (frac < 10) ? "00" : (frac < 100) ? "0" : "";
        f += frac;
        while (f.length() > 1 && f.charAt(f.length() - 1) == '0') {
            f = f.substring(0, f.length() - 1);
        }
        String s = whole + "." + f;
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

    private static String formatCoordE6(int e6) {
        if (e6 == Integer.MIN_VALUE) {
            return "";
        }
        boolean neg = e6 < 0;
        int abs = neg ? -e6 : e6;
        int whole = abs / 1000000;
        int frac = abs % 1000000;
        String fracStr = String.valueOf(frac);
        while (fracStr.length() < 6) {
            fracStr = "0" + fracStr;
        }
        return (neg ? "-" : "") + whole + "." + fracStr;
    }

    private void ensureFonts() {
        if (bodyFont == null) {
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            sectionHeaderFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        }
    }

    private static void drawSectionDivider(Graphics g, int x, int y, int maxW) {
        g.setColor(UiTheme.CARD_BORDER);
        g.drawLine(x + 4, y, x + maxW - 4, y);
    }

    private static void drawBoldCheckmark(Graphics g, int bx, int by, int box) {
        g.setColor(UiTheme.TEXT_DARK);
        int ax = bx + 3;
        int ay = by + 8;
        int mx = bx + 6;
        int my = by + 11;
        int ex = bx + box - 2;
        int ey = by + 4;
        for (int d = -1; d <= 1; d++) {
            g.drawLine(ax + d, ay, mx + d, my);
            g.drawLine(ax, ay + d, mx, my + d);
        }
        for (int d = -1; d <= 1; d++) {
            g.drawLine(mx + d, my, ex + d, ey);
            g.drawLine(mx, my + d, ex, ey + d);
        }
    }

    /**
     * Push latest radio / advert position from the node (when self-info refresh arrives).
     */
    public void syncRadioAndPositionFromNode(long freq, long bw, int sf, int cr, int txPwr,
            int latE6, int lonE6) {
        editFreq = formatFreqBw(freq);
        editBw = formatFreqBw(bw);
        editSf = String.valueOf(sf);
        editCr = String.valueOf(cr);
        editTx = String.valueOf(txPwr);
        editLat = formatCoordE6(latE6);
        editLon = formatCoordE6(lonE6);
    }

    public void applyPreset(int index) {
        if (index < 0 || index >= RadioPresets.COUNT) {
            return;
        }
        editFreq = formatFreqBw(RadioPresets.getFreqK(index));
        editBw = formatFreqBw(RadioPresets.getBandwidthHz(index));
        editSf = String.valueOf(RadioPresets.getSf(index));
        editCr = String.valueOf(RadioPresets.getCr(index));
        editTx = String.valueOf(RadioPresets.getTxDbm(index));
    }

    public void showInfo(String title, String text) {
        Alerts.info(app.getDisplay(), this, title, text);
    }

    public String getNodeName() {
        return editNodeName != null ? editNodeName.trim() : "";
    }

    public String getFreq() {
        return editFreq != null ? editFreq : "";
    }

    public String getBw() {
        return editBw != null ? editBw : "";
    }

    public String getSf() {
        return editSf != null ? editSf : "";
    }

    public String getCr() {
        return editCr != null ? editCr : "";
    }

    public String getTxPwr() {
        return editTx != null ? editTx : "";
    }

    public boolean getSharePositionInAdvert() {
        return sharePositionInAdvert;
    }

    /** Parsed advert latitude (degrees * 1E6); {@link Integer#MIN_VALUE} if field empty / invalid. */
    public int getAdvertLatE6Parsed() {
        return ParseUtils.parseCoordDegreesToE6(editLat, Integer.MIN_VALUE);
    }

    /** Parsed advert longitude (degrees * 1E6); {@link Integer#MIN_VALUE} if field empty / invalid. */
    public int getAdvertLonE6Parsed() {
        return ParseUtils.parseCoordDegreesToE6(editLon, Integer.MIN_VALUE);
    }

    /** Clamp focused row into the scroll viewport (full canvas; no in-canvas title bar). */
    private void ensureFocusVisible() {
        ensureFonts();
        int h = getHeight();
        int vh = h < 1 ? 1 : h;
        ensureFocusVisible(0, vh);
    }

    private void ensureFocusVisible(int headerBarH, int viewportH) {
        if (focusIndex == FOCUS_NONE) {
            return;
        }
        int edge = 10;
        int top;
        int bot;
        switch (focusIndex) {
            case FOCUS_NODE:
                top = nameCt;
                bot = nameCb;
                break;
            case FOCUS_LAT:
                top = latCt;
                bot = latCb;
                break;
            case FOCUS_LON:
                top = lonCt;
                bot = lonCb;
                break;
            case FOCUS_SHARE:
                top = cbCt;
                bot = cbCb;
                break;
            case FOCUS_DEVICE_INFO:
                top = diCt;
                bot = diCb;
                break;
            case FOCUS_SAVE_TOP:
                top = svTopCt;
                bot = svTopCb;
                break;
            case FOCUS_PRESET:
                top = prCt;
                bot = prCb;
                break;
            case FOCUS_FREQ:
                top = fqCt;
                bot = fqCb;
                break;
            case FOCUS_BW:
                top = bwCt;
                bot = bwCb;
                break;
            case FOCUS_SF:
                top = sfCt;
                bot = sfCb;
                break;
            case FOCUS_CR:
                top = crCt;
                bot = crCb;
                break;
            case FOCUS_TX:
                top = txCt;
                bot = txCb;
                break;
            case FOCUS_SAVE:
                top = svCt;
                bot = svCb;
                break;
            default:
                return;
        }
        int sy = scrollCtrl.getScrollY();
        if (top - sy < edge) {
            scrollCtrl.setScrollY(Math.max(0, top - edge));
        } else if (bot - sy > viewportH - edge) {
            int max = scrollCtrl.getMaxScroll(viewportH);
            int want = bot - viewportH + edge;
            scrollCtrl.setScrollY(want < 0 ? 0 : (want > max ? max : want));
        }
        scrollCtrl.clamp(viewportH);
    }

    private void moveFocus(int delta) {
        int n = focusIndex + delta;
        if (n < FOCUS_NODE) {
            n = FOCUS_NODE;
        }
        if (n > FOCUS_SAVE) {
            n = FOCUS_SAVE;
        }
        focusIndex = n;
        ensureFocusVisible();
        repaint();
    }

    private void activateFocused() {
        switch (focusIndex) {
            case FOCUS_NODE:
                openEditor("Node name", editNodeName, 32, TextField.ANY, 0);
                break;
            case FOCUS_LAT:
                openEditor("Latitude", editLat, 16, TextField.ANY, 1);
                break;
            case FOCUS_LON:
                openEditor("Longitude", editLon, 16, TextField.ANY, 2);
                break;
            case FOCUS_SHARE:
                sharePositionInAdvert = !sharePositionInAdvert;
                repaint();
                break;
            case FOCUS_DEVICE_INFO:
                app.showDeviceInfoScreen(SettingsScreen.this);
                break;
            case FOCUS_SAVE_TOP:
                performSave();
                break;
            case FOCUS_PRESET:
                openPresetList();
                break;
            case FOCUS_FREQ:
                openEditor("Frequency (MHz)", editFreq, 12, TextField.ANY, 3);
                break;
            case FOCUS_BW:
                openEditor("Bandwidth (kHz)", editBw, 12, TextField.ANY, 4);
                break;
            case FOCUS_SF:
                openEditor("Spreading Factor", editSf, 3, TextField.NUMERIC, 5);
                break;
            case FOCUS_CR:
                openEditor("Coding Rate", editCr, 3, TextField.NUMERIC, 6);
                break;
            case FOCUS_TX:
                openEditor("TX power (dBm)", editTx, 4, TextField.NUMERIC, 7);
                break;
            case FOCUS_SAVE:
                performSave();
                break;
            default:
                break;
        }
    }

    private void performSave() {
        new Thread(new Runnable() {
            public void run() {
                app.saveSettings();
            }
        }).start();
    }

    private void openEditor(final String title, final String value, final int maxLen, final int constraints,
            final int which) {
        final SettingsScreen self = this;
        final TextBox tb = new TextBox(title, value != null ? value : "", maxLen, constraints);
        Command ok = new Command("OK", Command.OK, 1);
        Command cancel = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok);
        tb.addCommand(cancel);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    String s = tb.getString();
                    if (s == null) {
                        s = "";
                    }
                    switch (which) {
                        case 0:
                            editNodeName = s;
                            break;
                        case 1:
                            editLat = s;
                            break;
                        case 2:
                            editLon = s;
                            break;
                        case 3:
                            editFreq = s;
                            break;
                        case 4:
                            editBw = s;
                            break;
                        case 5:
                            editSf = s;
                            break;
                        case 6:
                            editCr = s;
                            break;
                        case 7:
                            editTx = s;
                            break;
                        default:
                            break;
                    }
                }
                app.getDisplay().setCurrent(self);
                repaint();
            }
        });
        app.getDisplay().setCurrent(tb);
    }

    private void openPresetList() {
        final SettingsScreen self = this;
        final List lst = new List("Preset", List.IMPLICIT);
        for (int i = 0; i < RadioPresets.COUNT; i++) {
            lst.append(RadioPresets.getName(i), null);
        }
        final Command back = new Command("Back", Command.BACK, 1);
        lst.addCommand(back);
        lst.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c == back) {
                    app.getDisplay().setCurrent(self);
                    repaint();
                    return;
                }
                if (c == List.SELECT_COMMAND || c.getCommandType() == Command.OK) {
                    int idx = lst.getSelectedIndex();
                    if (idx >= 0) {
                        applyPreset(idx);
                    }
                    app.getDisplay().setCurrent(self);
                    repaint();
                }
            }
        });
        app.getDisplay().setCurrent(lst);
    }

    /**
     * @param hitTopOut optional screen Y (inclusive) top of tappable field card
     * @param hitBotOut optional screen Y (exclusive) bottom of field card
     */
    private int drawLabeledField(Graphics g, int x, int y, int maxW, String label, String value,
            int fieldH, int hdrH, int scrollY,
            int[] contentTopOut, int[] contentBotOut, int[] hitTopOut, int[] hitBotOut) {
        int rowDocTop = y - hdrH + scrollY;
        g.setFont(smallFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, label, x + 2, y, maxW - 2);
        y += 4;
        int rowTop = y;
        g.setColor(UiTheme.CARD_FILL);
        g.fillRoundRect(x + 2, y, maxW - 4, fieldH, 8, 8);
        g.setColor(UiTheme.CARD_BORDER);
        g.drawRoundRect(x + 2, y, maxW - 4, fieldH, 8, 8);
        g.setFont(bodyFont);
        int textY = y + (fieldH - bodyFont.getHeight()) / 2;
        if (value != null && value.length() > 0) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawString(value, x + 10, textY, Graphics.LEFT | Graphics.TOP);
        } else {
            g.setColor(0x888888);
            g.drawString("Tap to edit", x + 10, textY, Graphics.LEFT | Graphics.TOP);
        }
        y += fieldH;
        if (contentTopOut != null && contentTopOut.length > 0) {
            contentTopOut[0] = rowDocTop;
        }
        if (contentBotOut != null && contentBotOut.length > 0) {
            contentBotOut[0] = y - hdrH + scrollY;
        }
        if (hitTopOut != null && hitTopOut.length > 0) {
            hitTopOut[0] = rowTop;
        }
        if (hitBotOut != null && hitBotOut.length > 0) {
            hitBotOut[0] = y;
        }
        return y;
    }

    protected void paint(Graphics g) {
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int pad = 8;
        int scrollY = scrollCtrl.getScrollY();
        int x = pad;
        int maxW = w - (pad * 2) - 6;
        contentPadLeft = x;
        contentMaxW = maxW;

        final int headerBarH = 0;
        int viewportH = h < 1 ? 1 : h;

        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        g.clipRect(0, 0, w, viewportH);
        int y = 10 - scrollY;

        int fieldH = bodyFont.getHeight() + 14;
        int[] ct = new int[1];
        int[] cb = new int[1];
        int[] ht = new int[1];
        int[] hb = new int[1];

        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.BAR_BG);
        g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
        g.setColor(UiTheme.TEXT_DARK);
        y += UiCanvasUtil.drawWrapped(g, "Public Info", x + 10, y, maxW - 10);
        y += 8;

        y = drawLabeledField(g, x, y, maxW, "Node name", editNodeName, fieldH, headerBarH, scrollY, ct, cb, ht, hb);
        nameCt = ct[0];
        nameCb = cb[0];
        hitNameT = ht[0];
        hitNameB = hb[0];

        y = drawLabeledField(g, x, y + 6, maxW, "Latitude (decimal degrees)", editLat, fieldH, headerBarH, scrollY, ct, cb, ht, hb);
        latCt = ct[0];
        latCb = cb[0];
        hitLatT = ht[0];
        hitLatB = hb[0];

        y = drawLabeledField(g, x, y + 6, maxW, "Longitude (decimal degrees)", editLon, fieldH, headerBarH, scrollY, ct, cb, ht, hb);
        lonCt = ct[0];
        lonCb = cb[0];
        hitLonT = ht[0];
        hitLonB = hb[0];

        y += 6;
        int box = 14;
        hitCbT = y;
        cbCt = hitCbT - headerBarH + scrollY;
        g.setColor(UiTheme.CARD_FILL);
        g.fillRect(x + 2, y, box, box);
        g.setColor(UiTheme.CARD_BORDER);
        g.drawRect(x + 2, y, box, box);
        if (sharePositionInAdvert) {
            drawBoldCheckmark(g, x + 2, y, box);
        }
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        g.drawString("Share Position in Advert", x + box + 14, y + 1, Graphics.LEFT | Graphics.TOP);
        cbRowH = Math.max(box, bodyFont.getHeight());
        y += cbRowH;
        hitCbB = y;
        cbCb = hitCbB - headerBarH + scrollY;

        y += 10;
        deviceInfoBtnH = sectionHeaderFont.getHeight() + 14;
        hitDeviceInfoT = y;
        diCt = hitDeviceInfoT - headerBarH + scrollY;
        g.setColor(UiTheme.CARD_FILL);
        g.fillRoundRect(x + 2, y, maxW - 4, deviceInfoBtnH, 8, 8);
        g.setColor(UiTheme.CARD_BORDER);
        g.drawRoundRect(x + 2, y, maxW - 4, deviceInfoBtnH, 8, 8);
        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.TEXT_DARK);
        String dil = "Device Info";
        int dw = sectionHeaderFont.stringWidth(dil);
        int dx = x + 2 + (maxW - 4 - dw) / 2;
        if (dx < x + 2) {
            dx = x + 2;
        }
        g.drawString(dil, dx, y + (deviceInfoBtnH - sectionHeaderFont.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
        y += deviceInfoBtnH;
        hitDeviceInfoB = y;
        diCb = hitDeviceInfoB - headerBarH + scrollY;

        y += 10;
        saveTopBtnH = bodyFont.getHeight() + 14;
        hitSaveTopT = y;
        svTopCt = hitSaveTopT - headerBarH + scrollY;
        g.setColor(UiTheme.BAR_BG);
        g.fillRoundRect(x + 2, y, maxW - 4, saveTopBtnH, 8, 8);
        g.setColor(UiTheme.BG_WHITE);
        g.setFont(bodyFont);
        String st = "Save";
        int stw = bodyFont.stringWidth(st);
        int stx = x + 2 + (maxW - 4 - stw) / 2;
        if (stx < x + 2) {
            stx = x + 2;
        }
        g.drawString(st, stx, y + (saveTopBtnH - bodyFont.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
        y += saveTopBtnH;
        hitSaveTopB = y;
        svTopCb = hitSaveTopB - headerBarH + scrollY;

        y += 10;
        drawSectionDivider(g, x, y, maxW);
        y += 12;

        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.BAR_BG);
        g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
        g.setColor(UiTheme.TEXT_DARK);
        y += UiCanvasUtil.drawWrapped(g, "Radio Settings", x + 10, y, maxW - 10);
        y += 8;
        y += 6;

        presetBtnH = sectionHeaderFont.getHeight() + 14;
        hitPresetT = y;
        prCt = hitPresetT - headerBarH + scrollY;
        g.setColor(UiTheme.CARD_FILL);
        g.fillRoundRect(x + 2, y, maxW - 4, presetBtnH, 8, 8);
        g.setColor(UiTheme.CARD_BORDER);
        g.drawRoundRect(x + 2, y, maxW - 4, presetBtnH, 8, 8);
        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.TEXT_DARK);
        String pl = "Choose Preset";
        int pw = sectionHeaderFont.stringWidth(pl);
        int px = x + 2 + (maxW - 4 - pw) / 2;
        if (px < x + 2) {
            px = x + 2;
        }
        g.drawString(pl, px, y + (presetBtnH - sectionHeaderFont.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
        y += presetBtnH;
        hitPresetB = y;
        prCb = hitPresetB - headerBarH + scrollY;

        y = drawLabeledField(g, x, y + 8, maxW, "Frequency (MHz)", editFreq, fieldH, headerBarH, scrollY, ct, cb, ht, hb);
        fqCt = ct[0];
        fqCb = cb[0];
        hitFreqT = ht[0];
        hitFreqB = hb[0];

        y = drawLabeledField(g, x, y + 6, maxW, "Bandwidth (kHz)", editBw, fieldH, headerBarH, scrollY, ct, cb, ht, hb);
        bwCt = ct[0];
        bwCb = cb[0];
        hitBwT = ht[0];
        hitBwB = hb[0];

        y = drawLabeledField(g, x, y + 6, maxW, "Spreading Factor (SF)", editSf, fieldH, headerBarH, scrollY, ct, cb, ht, hb);
        sfCt = ct[0];
        sfCb = cb[0];
        hitSfT = ht[0];
        hitSfB = hb[0];

        y = drawLabeledField(g, x, y + 6, maxW, "Coding Rate (CR)", editCr, fieldH, headerBarH, scrollY, ct, cb, ht, hb);
        crCt = ct[0];
        crCb = cb[0];
        hitCrT = ht[0];
        hitCrB = hb[0];

        y = drawLabeledField(g, x, y + 6, maxW, "Transmit Power (dBm)", editTx, fieldH, headerBarH, scrollY, ct, cb, ht, hb);
        txCt = ct[0];
        txCb = cb[0];
        hitTxT = ht[0];
        hitTxB = hb[0];

        /* Match gap below Save (y += 16 before Device Info divider). */
        y += 16;
        saveBtnH = bodyFont.getHeight() + 14;
        hitSaveT = y;
        svCt = hitSaveT - headerBarH + scrollY;
        g.setColor(UiTheme.BAR_BG);
        g.fillRoundRect(x + 2, y, maxW - 4, saveBtnH, 8, 8);
        g.setColor(UiTheme.BG_WHITE);
        String sl = "Save";
        int sw = bodyFont.stringWidth(sl);
        int sx = x + 2 + (maxW - 4 - sw) / 2;
        if (sx < x + 2) {
            sx = x + 2;
        }
        g.drawString(sl, sx, y + (saveBtnH - bodyFont.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
        y += saveBtnH;
        hitSaveB = y;
        svCb = hitSaveB - headerBarH + scrollY;

        drawFocusOutline(g, x, maxW, fieldH);

        y += 20;

        int contentHeight = y - headerBarH + scrollY + pad;
        if (contentHeight < viewportH) {
            contentHeight = viewportH;
        }
        scrollCtrl.setContentHeight(contentHeight);
        scrollCtrl.clamp(viewportH);

        g.setClip(0, 0, w, h);

        int maxScroll = scrollCtrl.getMaxScroll(viewportH);
        if (maxScroll > 0) {
            int barX = w - 4;
            int barTop = 2;
            int barLen = h - barTop - 4;
            g.setColor(UiTheme.SCROLL_BAR_BG);
            g.drawLine(barX, barTop, barX, barTop + barLen);
            int thumbH = Math.max(10, (barLen * viewportH) / Math.max(contentHeight, 1));
            int syC = scrollCtrl.getScrollY();
            int thumbY = barTop + ((barLen - thumbH) * syC) / maxScroll;
            g.setColor(UiTheme.SCROLL_BAR_THUMB);
            g.fillRect(barX - 1, thumbY, 3, thumbH);
        }
    }

    /** Double-stroke focus ring; light cards use dark outer stroke for visibility. */
    private void drawFocusOutline(Graphics g, int x, int maxW, int fieldH) {
        if (focusIndex == FOCUS_NONE) {
            return;
        }
        if (focusIndex == FOCUS_SAVE_TOP) {
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x, hitSaveTopT - 2, maxW, saveTopBtnH + 4, 10, 10);
            g.drawRoundRect(x - 1, hitSaveTopT - 3, maxW + 2, saveTopBtnH + 6, 11, 11);
            return;
        }
        if (focusIndex == FOCUS_SAVE) {
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x, hitSaveT - 2, maxW, saveBtnH + 4, 10, 10);
            g.drawRoundRect(x - 1, hitSaveT - 3, maxW + 2, saveBtnH + 6, 11, 11);
            return;
        }
        if (focusIndex == FOCUS_NODE) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitNameT - 2, maxW, hitNameB - hitNameT + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitNameT - 3, maxW + 2, hitNameB - hitNameT + 6, 11, 11);
        } else if (focusIndex == FOCUS_LAT) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitLatT - 2, maxW, hitLatB - hitLatT + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitLatT - 3, maxW + 2, hitLatB - hitLatT + 6, 11, 11);
        } else if (focusIndex == FOCUS_LON) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitLonT - 2, maxW, hitLonB - hitLonT + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitLonT - 3, maxW + 2, hitLonB - hitLonT + 6, 11, 11);
        } else if (focusIndex == FOCUS_SHARE) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRect(x, hitCbT - 2, maxW, cbRowH + 4);
            g.setColor(UiTheme.BAR_BG);
            g.drawRect(x - 1, hitCbT - 3, maxW + 2, cbRowH + 6);
        } else if (focusIndex == FOCUS_DEVICE_INFO) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitDeviceInfoT - 2, maxW, deviceInfoBtnH + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitDeviceInfoT - 3, maxW + 2, deviceInfoBtnH + 6, 11, 11);
        } else if (focusIndex == FOCUS_PRESET) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitPresetT - 2, maxW, presetBtnH + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitPresetT - 3, maxW + 2, presetBtnH + 6, 11, 11);
        } else if (focusIndex == FOCUS_FREQ) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitFreqT - 2, maxW, hitFreqB - hitFreqT + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitFreqT - 3, maxW + 2, hitFreqB - hitFreqT + 6, 11, 11);
        } else if (focusIndex == FOCUS_BW) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitBwT - 2, maxW, hitBwB - hitBwT + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitBwT - 3, maxW + 2, hitBwB - hitBwT + 6, 11, 11);
        } else if (focusIndex == FOCUS_SF) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitSfT - 2, maxW, hitSfB - hitSfT + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitSfT - 3, maxW + 2, hitSfB - hitSfT + 6, 11, 11);
        } else if (focusIndex == FOCUS_CR) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitCrT - 2, maxW, hitCrB - hitCrT + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitCrT - 3, maxW + 2, hitCrB - hitCrT + 6, 11, 11);
        } else if (focusIndex == FOCUS_TX) {
            g.setColor(UiTheme.TEXT_DARK);
            g.drawRoundRect(x, hitTxT - 2, maxW, hitTxB - hitTxT + 4, 10, 10);
            g.setColor(UiTheme.BAR_BG);
            g.drawRoundRect(x - 1, hitTxT - 3, maxW + 2, hitTxB - hitTxT + 6, 11, 11);
        }
    }

    private boolean inFieldX(int px) {
        return px >= contentPadLeft && px < contentPadLeft + contentMaxW;
    }

    protected void pointerPressed(int x, int y) {
        scrollCtrl.pointerPressed(y);
        if (!inFieldX(x)) {
            return;
        }
        if (y >= hitNameT && y < hitNameB) {
            focusIndex = FOCUS_NODE;
            ensureFocusVisible();
            openEditor("Node name", editNodeName, 32, TextField.ANY, 0);
            return;
        }
        if (y >= hitLatT && y < hitLatB) {
            focusIndex = FOCUS_LAT;
            ensureFocusVisible();
            openEditor("Latitude", editLat, 16, TextField.ANY, 1);
            return;
        }
        if (y >= hitLonT && y < hitLonB) {
            focusIndex = FOCUS_LON;
            ensureFocusVisible();
            openEditor("Longitude", editLon, 16, TextField.ANY, 2);
            return;
        }
        if (y >= hitCbT && y < hitCbB) {
            focusIndex = FOCUS_SHARE;
            sharePositionInAdvert = !sharePositionInAdvert;
            ensureFocusVisible();
            repaint();
            return;
        }
        if (y >= hitDeviceInfoT && y < hitDeviceInfoB) {
            focusIndex = FOCUS_DEVICE_INFO;
            ensureFocusVisible();
            app.showDeviceInfoScreen(this);
            return;
        }
        if (y >= hitSaveTopT && y < hitSaveTopB) {
            focusIndex = FOCUS_SAVE_TOP;
            ensureFocusVisible();
            performSave();
            repaint();
            return;
        }
        if (y >= hitPresetT && y < hitPresetB) {
            focusIndex = FOCUS_PRESET;
            ensureFocusVisible();
            openPresetList();
            return;
        }
        if (y >= hitFreqT && y < hitFreqB) {
            focusIndex = FOCUS_FREQ;
            ensureFocusVisible();
            openEditor("Frequency (MHz)", editFreq, 12, TextField.ANY, 3);
            return;
        }
        if (y >= hitBwT && y < hitBwB) {
            focusIndex = FOCUS_BW;
            ensureFocusVisible();
            openEditor("Bandwidth (kHz)", editBw, 12, TextField.ANY, 4);
            return;
        }
        if (y >= hitSfT && y < hitSfB) {
            focusIndex = FOCUS_SF;
            ensureFocusVisible();
            openEditor("Spreading Factor", editSf, 3, TextField.NUMERIC, 5);
            return;
        }
        if (y >= hitCrT && y < hitCrB) {
            focusIndex = FOCUS_CR;
            ensureFocusVisible();
            openEditor("Coding Rate", editCr, 3, TextField.NUMERIC, 6);
            return;
        }
        if (y >= hitTxT && y < hitTxB) {
            focusIndex = FOCUS_TX;
            ensureFocusVisible();
            openEditor("TX power (dBm)", editTx, 4, TextField.NUMERIC, 7);
            return;
        }
        if (y >= hitSaveT && y < hitSaveB) {
            focusIndex = FOCUS_SAVE;
            ensureFocusVisible();
            performSave();
            repaint();
        }
    }

    protected void pointerDragged(int x, int y) {
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int vh = h < 1 ? 1 : h;
        if (scrollCtrl.onDrag(y, vh)) {
            scrollDragRepaint.repaintDrag(this, UiScrollRepaintThrottler.DEFAULT_DRAG_INTERVAL_MS,
                    0, 0, w, h);
        }
    }

    protected void pointerReleased(int x, int y) {
        scrollCtrl.pointerReleased();
        int w = getWidth();
        int h = getHeight();
        scrollDragRepaint.flushRepaint(this, 0, 0, w, h);
    }

    protected void keyPressed(int keyCode) {
        ensureFonts();
        int h = getHeight();
        int vh = h < 1 ? 1 : h;
        int action = 0;
        try {
            action = getGameAction(keyCode);
        } catch (IllegalArgumentException e) {
            action = 0;
        }
        int step = UiScrollController.computeStep(bodyFont);
        boolean up = (action == Canvas.UP || keyCode == Canvas.KEY_NUM2);
        boolean down = (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8);
        boolean fire = (action == Canvas.FIRE || keyCode == Canvas.KEY_NUM5);
        boolean left = (action == Canvas.LEFT || keyCode == Canvas.KEY_NUM4);
        if (left) {
            if (focusIndex == FOCUS_NODE) {
                focusIndex = FOCUS_NONE;
                repaint();
                return;
            }
        }
        if (up) {
            if (focusIndex == FOCUS_NONE) {
                if (scrollCtrl.onKey(Canvas.UP, step, vh)) {
                    repaint();
                }
                return;
            }
            if (focusIndex > FOCUS_NODE) {
                moveFocus(-1);
            } else if (scrollCtrl.onKey(Canvas.UP, step, vh)) {
                repaint();
            }
            return;
        }
        if (down) {
            if (focusIndex == FOCUS_NONE) {
                if (scrollCtrl.onKey(Canvas.DOWN, step, vh)) {
                    repaint();
                }
                return;
            }
            if (focusIndex < FOCUS_SAVE) {
                moveFocus(1);
            } else if (scrollCtrl.onKey(Canvas.DOWN, step, vh)) {
                repaint();
            }
            return;
        }
        if (fire) {
            if (focusIndex == FOCUS_NONE) {
                focusIndex = FOCUS_NODE;
                ensureFocusVisible();
                repaint();
                return;
            }
            activateFocused();
            return;
        }
        if (scrollCtrl.onKey(action, step, vh)) {
            repaint();
        }
    }

    protected void sizeChanged(int w, int h) {
        ensureFonts();
        int vh = h < 1 ? 1 : h;
        scrollCtrl.clamp(vh);
        ensureFocusVisible();
        repaint();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            if (settingsReturnTo != null) {
                app.getDisplay().setCurrent(settingsReturnTo);
            } else {
            app.showMainMenu();
            }
            return;
        }
        if (c == cmdSelect) {
            if (focusIndex == FOCUS_NONE) {
                focusIndex = FOCUS_NODE;
                ensureFocusVisible();
                repaint();
                return;
            }
            activateFocused();
            return;
        }
        if (c == cmdSave) {
            performSave();
            return;
        }
        if (c == cmdRefresh) {
            new Thread(new Runnable() {
                public void run() {
                    app.sendRefreshSettings();
                    app.requestSettingsLiveReadings();
                }
            }).start();
            return;
        }
        if (c == cmdMsgSettings) {
            app.showMessageSettingsScreen();
        }
    }
}
