package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

import meshcore.util.TextUtils;
import meshcore.util.TimeFormat;

/**
 * Read-only device status: name, firmware, key, battery, storage, uptime, clock.
 */
public final class DeviceInfoScreen extends Canvas implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;
    private final Command cmdRefresh;

    private Font bodyFont;
    private Font smallFont;

    private final UiScrollController scrollCtrl = new UiScrollController();
    private final UiScrollRepaintThrottler scrollDragRepaint = new UiScrollRepaintThrottler();

    private String dispNodeName;
    private String dispFirmware;
    private String dispPublicKey;
    private String batteryLine = "Loading...";
    private String storageLine = "Loading...";
    private String uptimeLine = "-";
    private String nodeClockLine = "-";

    public DeviceInfoScreen(AppController app, Displayable returnTo,
            String nodeName, String firmwareVer, String publicKeyHex) {
        this.app = app;
        this.returnTo = returnTo;
        setFullScreenMode(false);
        setTitle("Device info");

        String nn = nodeName != null ? nodeName : "";
        String fw = firmwareVer != null ? firmwareVer : "";
        dispNodeName = TextUtils.sanitizeLabel(nn, 48);
        dispFirmware = TextUtils.sanitizeLabel(fw, 48);
        dispPublicKey = formatPublicKey(publicKeyHex);

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdRefresh = new Command("Refresh", Command.SCREEN, 1);
        addCommand(cmdRefresh);
        addCommand(cmdBack);
        setCommandListener(this);
        scrollCtrl.reset();
    }

    private static String formatPublicKey(String hex) {
        String s = TextUtils.formatPublicKeyShort(hex);
        return s.length() > 0 ? s : "n/a";
    }

    private void ensureFonts() {
        if (bodyFont == null) {
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
    }

    private void performRefresh() {
        new Thread(new Runnable() {
            public void run() {
                app.sendRefreshSettings();
                app.requestSettingsLiveReadings();
            }
        }).start();
    }

    public void setNodeInfo(String nodeName, String firmwareVer) {
        String nn = nodeName != null ? nodeName : "";
        String fw = firmwareVer != null ? firmwareVer : "";
        dispNodeName = TextUtils.sanitizeLabel(nn, 48);
        dispFirmware = TextUtils.sanitizeLabel(fw, 48);
        repaint();
    }

    public void setPublicKey(String hex) {
        dispPublicKey = formatPublicKey(hex);
        repaint();
    }

    public void setBatteryAndStorage(String battery, String storage) {
        batteryLine = battery != null && battery.length() > 0 ? battery : "n/a";
        storageLine = storage != null && storage.length() > 0 ? storage : "n/a";
        repaint();
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        uptimeLine = TimeFormat.formatUptimeDHMS(uptimeSeconds);
        repaint();
    }

    public void setNodeClockUtc(String utcFormatted) {
        nodeClockLine = utcFormatted != null && utcFormatted.length() > 0 ? utcFormatted : "-";
        repaint();
    }

    protected void showNotify() {
        scrollCtrl.reset();
        super.showNotify();
        new Thread(new Runnable() {
            public void run() {
                app.requestSettingsLiveReadings();
            }
        }).start();
    }

    protected void paint(Graphics g) {
        ensureFonts();
        int w = getWidth();
        int h = getHeight();
        int pad = 8;
        int scrollY = scrollCtrl.getScrollY();
        int x = pad;
        int maxW = w - (pad * 2) - 6;
        final int headerBarH = 0;
        int viewportH = h < 1 ? 1 : h;

        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        g.clipRect(0, 0, w, viewportH);
        int y = 10 - scrollY;

        g.setFont(smallFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Node Name", x + 10, y, maxW - 10);
        y += 2;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, dispNodeName, x + 10, y, maxW - 10);
        y += 8;
        g.setFont(smallFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Firmware", x + 10, y, maxW - 10);
        y += 2;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, dispFirmware.length() > 0 ? dispFirmware : "n/a", x + 10, y, maxW - 10);
        y += 8;
        g.setFont(smallFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Public key", x + 10, y, maxW - 10);
        y += 2;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, dispPublicKey, x + 10, y, maxW - 10);
        y += 8;
        g.setFont(smallFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Battery", x + 10, y, maxW - 10);
        y += 2;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, batteryLine, x + 10, y, maxW - 10);
        y += 8;
        g.setFont(smallFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Storage", x + 10, y, maxW - 10);
        y += 2;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, storageLine, x + 10, y, maxW - 10);
        y += 8;
        g.setFont(smallFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Uptime", x + 10, y, maxW - 10);
        y += 2;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, uptimeLine, x + 10, y, maxW - 10);
        y += 8;
        g.setFont(smallFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Node time (UTC)", x + 10, y, maxW - 10);
        y += 2;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, nodeClockLine, x + 10, y, maxW - 10);
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

    protected void pointerPressed(int x, int y) {
        scrollCtrl.pointerPressed(y);
    }

    protected void pointerDragged(int x, int y) {
        ensureFonts();
        int w = getWidth();
        int hh = getHeight();
        int vh = hh < 1 ? 1 : hh;
        if (scrollCtrl.onDrag(y, vh)) {
            scrollDragRepaint.repaintDrag(this, UiScrollRepaintThrottler.DEFAULT_DRAG_INTERVAL_MS,
                    0, 0, w, hh);
        }
    }

    protected void pointerReleased(int x, int y) {
        scrollCtrl.pointerReleased();
        int w = getWidth();
        int hh = getHeight();
        scrollDragRepaint.flushRepaint(this, 0, 0, w, hh);
    }

    protected void keyPressed(int keyCode) {
        ensureFonts();
        int vh = getHeight();
        if (vh < 1) {
            vh = 1;
        }
        int action = 0;
        try {
            action = getGameAction(keyCode);
        } catch (IllegalArgumentException e) {
            action = 0;
        }
        int step = UiScrollController.computeStep(bodyFont);
        if (scrollCtrl.onKey(action, step, vh)) {
            repaint();
        }
    }

    protected void sizeChanged(int w, int h) {
        ensureFonts();
        int vh = h < 1 ? 1 : h;
        scrollCtrl.clamp(vh);
        repaint();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.notifyDeviceInfoClosed();
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdRefresh) {
            performRefresh();
        }
    }
}
