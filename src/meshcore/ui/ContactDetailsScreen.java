package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import meshcore.protocol.ProtocolConstants;
import meshcore.util.TextUtils;

/**
 * Read-only details view for contacts and repeaters using Canvas.
 * Shows: Name, Public key, Position, Type.
 */
public final class ContactDetailsScreen extends Canvas implements CommandListener {

    private final AppController app;
    private final int contactIdx;
    private final Displayable returnTo;
    private final String name;
    private final int type;
    private final String publicKeyHex;
    private final String gpsText;
    private final String distanceText;
    private final String lastAdvertText;
    private final String hopsText;
    private final String outPathText;
    private final String outPathRepeatersText;
    private final UiScrollController scrollCtrl = new UiScrollController();

    private final Command cmdBack;
    private final Command cmdCopyKey;
    private final Command cmdCopyCoordinates;

    private Font titleFont;
    private Font bodyFont;
    private Font smallFont;
    private Font sectionHeaderFont;

    /** Set each paint; used for pointer hit-testing below the fixed header. */
    private int headerBarHeight = 44;

    public ContactDetailsScreen(AppController app, int contactIdx, String name, int type, Displayable returnTo) {
        this.app = app;
        this.contactIdx = contactIdx;
        this.returnTo = returnTo;
        this.name = (name != null) ? name : "";
        this.type = type;

        setFullScreenMode(false);
        if (this.name.length() > 0) setTitle(this.name);
        else setTitle("Details");

        publicKeyHex = safe(app.getContactPublicKeyHex(contactIdx));
        int cLat = app.getContactAdvLatE6(contactIdx);
        int cLon = app.getContactAdvLonE6(contactIdx);
        int nLat = app.getNodeAdvLatE6();
        int nLon = app.getNodeAdvLonE6();
        gpsText = formatGps(cLat, cLon);
        distanceText = formatDistanceKm(nLat, nLon, cLat, cLon);
        lastAdvertText = formatLastAdvert(app.getContactLastAdvertSecs(contactIdx));
        hopsText = formatHops(app.getContactPathHops(contactIdx));
        byte[] outPath = app.getContactPathBytes(contactIdx);
        outPathText = formatOutPath(outPath);
        outPathRepeatersText = formatOutPathRepeaters(outPath);

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdCopyKey = new Command("Copy Key", Command.SCREEN, 2);
        cmdCopyCoordinates = new Command("Copy Coordinates", Command.SCREEN, 3);
        addCommand(cmdBack);
        addCommand(cmdCopyKey);
        addCommand(cmdCopyCoordinates);
        setCommandListener(this);
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private static String getTypeLabel(int type) {
        if (type == ProtocolConstants.ADV_TYPE_CHAT) return "Client";
        if (type == ProtocolConstants.ADV_TYPE_REPEATER) return "Repeater";
        if (type == ProtocolConstants.ADV_TYPE_ROOM) return "Room Server";
        if (type == ProtocolConstants.ADV_TYPE_SENSOR) return "Sensor";
        if (type == ProtocolConstants.ADV_TYPE_NONE) return "Unknown";
        return "Unknown";
    }

    private static String formatGps(int latE6, int lonE6) {
        if (latE6 == Integer.MIN_VALUE || lonE6 == Integer.MIN_VALUE) return "n/a";
        return formatE6(latE6) + ", " + formatE6(lonE6);
    }

    private static String formatHops(int hops) {
        if (hops < 0) return "Unknown";
        if (hops == 0) return "0 (Direct)";
        return String.valueOf(hops);
    }

    private static String formatOutPath(byte[] pathBytes) {
        if (pathBytes == null || pathBytes.length == 0) return "(Direct)";
        return PathHexCodec.formatBytesCsv(pathBytes);
    }

    private String formatOutPathRepeaters(byte[] pathBytes) {
        if (pathBytes == null || pathBytes.length == 0) return "(Direct)";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < pathBytes.length; i++) {
            if (i > 0) sb.append(" >> ");
            String rep = app.getRepeaterNameForPathByte(pathBytes[i]);
            if (rep != null && rep.length() > 0) {
                sb.append(rep);
            } else {
                int v = pathBytes[i] & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v).toUpperCase());
            }
        }
        return sb.toString();
    }

    private static String formatLastAdvert(long epochSecs) {
        if (epochSecs <= 0) return "n/a";
        long now = System.currentTimeMillis() / 1000L;
        long age = now - epochSecs;
        if (age < 0) age = 0;
        return TextUtils.formatEpochDateTimeVerbose(epochSecs) + " (" + TextUtils.formatAgeCompact(age) + " ago)";
    }

    private static String formatDistanceKm(int fromLatE6, int fromLonE6, int toLatE6, int toLonE6) {
        if (fromLatE6 == Integer.MIN_VALUE || fromLonE6 == Integer.MIN_VALUE
                || toLatE6 == Integer.MIN_VALUE || toLonE6 == Integer.MIN_VALUE) {
            return "n/a";
        }
        // CLDC-safe approximation:
        // - 1 degree latitude ~= 111.32 km
        // - 1 degree longitude ~= 111.32 * cos(mean latitude) km
        // This is closer to Android's value than using equal scaling for lat/lon.
        double dLatDeg = (toLatE6 - fromLatE6) / 1000000.0;
        double dLonDeg = (toLonE6 - fromLonE6) / 1000000.0;
        double kmPerDeg = 111.32;
        double meanLatDeg = ((fromLatE6 + toLatE6) / 2.0) / 1000000.0;
        double lonScale = kmPerDeg * cosDegApprox(meanLatDeg);
        double dx = dLatDeg * kmPerDeg;
        double dy = dLonDeg * lonScale;
        double km = Math.sqrt(dx * dx + dy * dy);
        return "~" + format1Dec(km) + " km";
    }

    /** Cosine approximation in degrees without using Math.cos (CLDC-safe). */
    private static double cosDegApprox(double deg) {
        // Normalize to [0, 360)
        while (deg < 0) deg += 360.0;
        while (deg >= 360.0) deg -= 360.0;

        // Map to [0, 180]
        if (deg > 180.0) deg = 360.0 - deg;

        // Determine sign and fold to [0, 90]
        double sign = 1.0;
        if (deg > 90.0) {
            deg = 180.0 - deg;
            sign = -1.0;
        }

        // 4th-order Taylor around 0 for radians: cos(x) ~= 1 - x^2/2 + x^4/24
        double x = deg * 0.017453292519943295; // PI / 180
        double x2 = x * x;
        double x4 = x2 * x2;
        double c = 1.0 - (x2 / 2.0) + (x4 / 24.0);
        if (c < -1.0) c = -1.0;
        if (c > 1.0) c = 1.0;
        return sign * c;
    }

    private static String format1Dec(double v) {
        if (v < 0) v = 0;
        int i = (int) (v * 10.0 + 0.5);
        int whole = i / 10;
        int dec = i % 10;
        return whole + "." + dec;
    }

    /** Format fixed-point degrees value where 1 degree == 1E6. */
    private static String formatE6(int e6) {
        boolean neg = e6 < 0;
        int abs = neg ? -e6 : e6;
        int whole = abs / 1000000;
        int frac = abs % 1000000; // 0..999999

        String fracStr = String.valueOf(frac);
        // Left-pad to 6 digits (J2ME doesn't have String.format reliably everywhere).
        while (fracStr.length() < 6) fracStr = "0" + fracStr;
        return (neg ? "-" : "") + whole + "." + fracStr;
    }

    private void ensureDetailFonts() {
        if (titleFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            smallFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            sectionHeaderFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        }
    }

    private static void drawSectionDivider(Graphics g, int x, int y, int maxW) {
        g.setColor(UiTheme.CARD_BORDER);
        g.drawLine(x + 4, y, x + maxW - 4, y);
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        ensureDetailFonts();

        int headerBarH = UiScreenHeader.measureHeight(w, "Details", null, titleFont, smallFont);
        int viewportH = h - headerBarH;
        if (viewportH < 1) {
            viewportH = 1;
        }

        int pad = 10;
        int x = pad;
        int maxW = w - (pad * 2) - 6;
        int scrollY = scrollCtrl.getScrollY();

        g.setColor(UiTheme.PANEL_BG);
        g.fillRect(0, 0, w, h);

        g.clipRect(0, headerBarH, w, viewportH);
        int y = headerBarH - scrollY + 10;

        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.BAR_BG);
        g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
        g.setColor(UiTheme.TEXT_DARK);
        y += UiCanvasUtil.drawWrapped(g, "Identity", x + 10, y, maxW - 10);
        y += 6;
        g.setFont(bodyFont);
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Name", x + 10, y, maxW - 10);
        y += 2;
        g.setColor(UiTheme.TEXT_DARK);
        y += UiCanvasUtil.drawWrapped(g, safe(name), x + 10, y, maxW - 10);
        y += 8;
        g.setColor(0x555555);
        y += UiCanvasUtil.drawWrapped(g, "Public key", x + 10, y, maxW - 10);
        y += 2;
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, publicKeyHex, x + 10, y, maxW - 10);
        y += 14;
        drawSectionDivider(g, x, y, maxW);
        y += 12;

        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.BAR_BG);
        g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
        g.setColor(UiTheme.TEXT_DARK);
        y += UiCanvasUtil.drawWrapped(g, "Location", x + 10, y, maxW - 10);
        y += 6;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, "GPS  " + gpsText, x + 10, y, maxW - 10);
        y += 6;
        y += UiCanvasUtil.drawWrapped(g, "Distance  " + distanceText, x + 10, y, maxW - 10);
        y += 6;
        y += UiCanvasUtil.drawWrapped(g, "Type  " + getTypeLabel(type), x + 10, y, maxW - 10);
        y += 14;
        drawSectionDivider(g, x, y, maxW);
        y += 12;

        g.setFont(sectionHeaderFont);
        g.setColor(UiTheme.BAR_BG);
        g.fillRect(x, y, 4, sectionHeaderFont.getHeight() + 2);
        g.setColor(UiTheme.TEXT_DARK);
        y += UiCanvasUtil.drawWrapped(g, "Radio", x + 10, y, maxW - 10);
        y += 6;
        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        y += UiCanvasUtil.drawWrapped(g, "Last advert  " + lastAdvertText, x + 10, y, maxW - 10);
        y += 8;
        y += UiCanvasUtil.drawWrapped(g, "Hops  " + hopsText, x + 10, y, maxW - 10);
        y += 8;
        y += UiCanvasUtil.drawWrapped(g, "Path (hex)  " + outPathText, x + 10, y, maxW - 10);
        y += 6;
        y += UiCanvasUtil.drawWrapped(g, outPathRepeatersText, x + 10, y, maxW - 10);

        y += 16;

        int contentHeight = y - headerBarH + scrollY + pad;
        if (contentHeight < viewportH) {
            contentHeight = viewportH;
        }
        scrollCtrl.setContentHeight(contentHeight);
        scrollCtrl.clamp(viewportH);

        g.setClip(0, 0, w, h);
        headerBarHeight = UiScreenHeader.paint(g, w, "Details", null, titleFont, smallFont);

        int maxScroll = scrollCtrl.getMaxScroll(viewportH);
        if (maxScroll > 0) {
            int barX = w - 4;
            int barTop = headerBarH + 2;
            int barLen = h - barTop - 4;
            g.setColor(UiTheme.SCROLL_BAR_BG);
            g.drawLine(barX, barTop, barX, barTop + barLen);
            int thumbH = Math.max(10, (barLen * viewportH) / Math.max(contentHeight, 1));
            int scrollYClamped = scrollCtrl.getScrollY();
            int thumbY = barTop + ((barLen - thumbH) * scrollYClamped) / maxScroll;
            g.setColor(UiTheme.SCROLL_BAR_THUMB);
            g.fillRect(barX - 1, thumbY, 3, thumbH);
        }
    }

    protected void keyPressed(int keyCode) {
        ensureDetailFonts();
        int w = getWidth();
        int hh = UiScreenHeader.measureHeight(w, "Details", null, titleFont, smallFont);
        int vh = getHeight() - hh;
        if (vh < 1) {
            vh = 1;
        }
        int action = getGameAction(keyCode);
        int step = UiScrollController.computeStep(bodyFont);
        if (scrollCtrl.onKey(action, step, vh)) {
            repaint();
        }
    }

    protected void pointerPressed(int x, int y) {
        if (y < headerBarHeight) {
            return;
        }
        scrollCtrl.pointerPressed(y);
    }

    protected void pointerDragged(int x, int y) {
        ensureDetailFonts();
        int w = getWidth();
        int hh = UiScreenHeader.measureHeight(w, "Details", null, titleFont, smallFont);
        int vh = getHeight() - hh;
        if (vh < 1) {
            vh = 1;
        }
        if (scrollCtrl.onDrag(y, vh)) {
            repaint();
        }
    }

    protected void pointerReleased(int x, int y) {
        scrollCtrl.pointerReleased();
    }

    private void openCopyBox(String title, String value) {
        final ContactDetailsScreen self = this;
        TextBox tb = new TextBox(title, safe(value), 256, TextField.ANY);
        Command back = new Command("Back", Command.BACK, 1);
        tb.addCommand(back);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                app.getDisplay().setCurrent(self);
            }
        });
        app.getDisplay().setCurrent(tb);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdCopyKey) {
            openCopyBox("Copy Key", publicKeyHex);
            return;
        }
        if (c == cmdCopyCoordinates) {
            if ("n/a".equals(gpsText)) {
                Alerts.info(app.getDisplay(), this, "Coordinates", "No GPS coordinates for this contact.");
                return;
            }
            openCopyBox("Copy Coordinates", gpsText);
        }
    }
}

