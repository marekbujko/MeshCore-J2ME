package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Displayable;

/**
 * Single-screen UI for "Ping (Zero Hop)".
 * Shows waiting text first, then replaces it with the result when it arrives.
 */
public final class PingZeroHopScreen extends Canvas implements CommandListener {

    private static final long PING_TIMEOUT_MS = 20000; // match service expectations

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;
    private volatile boolean resolved = false;
    private volatile boolean waiting = true;

    private volatile int dots = 0;
    private volatile String repeaterName = "";
    private volatile String snrForward = "n/a";
    private volatile String snrBack = "n/a";
    private volatile String hopsLabel = "";
    private volatile String durationLabel = "";
    private int scrollY = 0;
    private int contentHeight = 0;
    private int lastPointerY = -1;

    private Font titleFont;
    private Font bodyFont;

    public PingZeroHopScreen(AppController app, Displayable returnTo) {
        this.app = app;
        this.returnTo = returnTo;
        setFullScreenMode(false);
        setTitle("Ping (Zero Hop)");

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);

        showWaiting();
        startTimeoutWatcher();
        startWaitingDots();
    }

    private void showWaiting() {
        resolved = false;
        waiting = true;
        dots = 0;
        scrollY = 0;
        repaint();
    }

    public void showResult(String repeaterName,
                              String snrForward,
                              String snrBack,
                              int pathHops,
                              long durationMs) {
        resolved = true;
        waiting = false;

        this.repeaterName = repeaterName != null ? repeaterName : "";
        this.snrForward = snrForward != null ? snrForward : "n/a";
        this.snrBack = snrBack != null ? snrBack : "n/a";
        String hopsLabel = (pathHops <= 1)
                ? "0 hops (Direct)"
                : (String.valueOf(pathHops) + " hops");
        this.hopsLabel = hopsLabel;
        this.durationLabel = (durationMs >= 0) ? (durationMs + " ms") : "";
        scrollY = 0;
        repaint();
    }

    private void startTimeoutWatcher() {
        final PingZeroHopScreen self = this;
        final javax.microedition.lcdui.Display display = app.getDisplay();
        final long start = System.currentTimeMillis();
        new Thread(new Runnable() {
            public void run() {
                while (display != null) {
                    if (self.resolved) break;
                    if (System.currentTimeMillis() - start > PING_TIMEOUT_MS) break;
                    try { Thread.sleep(200); } catch (InterruptedException e) { break; }
                }
                if (display != null) {
                    if (self.resolved) return;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (display.getCurrent() != self) return;
                            if (self.resolved) return;
                            resolved = true;
                            waiting = false;
                            repeaterName = "";
                            snrForward = "n/a";
                            snrBack = "n/a";
                            hopsLabel = "—";
                            durationLabel = "";
                            repaint();
                        }
                    });
                }
            }
        }).start();
    }

    private void startWaitingDots() {
        final PingZeroHopScreen self = this;
        final javax.microedition.lcdui.Display display = app.getDisplay();
        new Thread(new Runnable() {
            public void run() {
                int i = 0;
                while (display != null) {
                    if (self.resolved) break;
                    if (!self.waiting) break;
                    final int vv = i % 4;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (display.getCurrent() == self && !self.resolved && self.waiting) {
                                self.dots = vv;
                                self.repaint();
                            }
                        }
                    });
                    i++;
                    try { Thread.sleep(300); } catch (InterruptedException e) { break; }
                }
            }
        }).start();
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        // Background (same spirit as trace path canvas).
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);

        if (bodyFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        int pad = 8;
        int x = pad;
        int maxW = w - (pad * 2);
        int y = 0 - scrollY;

        g.setFont(titleFont);
        g.setColor(0x1F1F1F);

        g.setFont(bodyFont);
        int lineH = bodyFont.getHeight();
        g.setColor(0x333333);

        if (waiting && !resolved) {
            String base = "Waiting for reply";
            String dotsStr = "";
            int di = dots;
            if (di == 0) dotsStr = "";
            else if (di == 1) dotsStr = ".";
            else if (di == 2) dotsStr = "..";
            else dotsStr = "...";
            y += UiCanvasUtil.drawWrappedCentered(g, base + dotsStr, x, y, maxW);
            contentHeight = y + scrollY + pad;
            clampScroll(h);
        } else {
            if (repeaterName != null && repeaterName.length() > 0) {
                if (durationLabel != null && durationLabel.length() > 0) {
                    y += UiCanvasUtil.drawWrappedCentered(g, "Duration: " + durationLabel, x, y, maxW);
                }
                y += 3;
                g.setColor(0xD0D0D0);
                g.drawLine(x, y, x + maxW, y);
                y += 7;

                String myNode = UiCanvasUtil.getNodeLabel(app);
                y += drawNodeBubble(g, x, y, maxW, myNode);
                y += drawSnrDualArrows(g, x, y, maxW, snrForward, snrBack);
                y += drawNodeBubble(g, x, y, maxW, repeaterName);
            } else {
                y += UiCanvasUtil.drawWrappedCentered(g, "No reply (timeout).", x, y, maxW);
            }
            contentHeight = y + scrollY + pad;
            clampScroll(h);
        }

        int maxScroll = getMaxScroll(h);
        if (maxScroll > 0) {
            int barX = w - 4;
            int barTop = 4;
            int barH = h - 8;
            g.setColor(0xBBBBBB);
            g.drawLine(barX, barTop, barX, barTop + barH);
            int thumbH = Math.max(10, (barH * h) / contentHeight);
            int thumbY = barTop + ((barH - thumbH) * scrollY) / maxScroll;
            g.setColor(0x666666);
            g.fillRect(barX - 1, thumbY, 3, thumbH);
        }
    }

    private int drawNodeBubble(Graphics g, int x, int y, int w, String text) {
        int h = bodyFont.getHeight() + 8;
        g.setColor(0xF8F8F8);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(0x9E9E9E);
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setColor(0x1E344A);
        g.drawString(text != null ? text : "", x + 6, y + 4, Graphics.LEFT | Graphics.TOP);
        return h + 4;
    }

    // Connector block: left SNR + down arrow, right SNR + up arrow.
    private int drawSnrDualArrows(Graphics g, int x, int y, int w, String snrThere, String snrBackVal) {
        int leftX = x + (w / 4);
        int rightX = x + ((w * 3) / 4);
        String leftLabel = "SNR " + (snrThere != null ? snrThere : "n/a");
        String rightLabel = "SNR " + (snrBackVal != null ? snrBackVal : "n/a");
        g.setColor(0x444444);
        int twL = g.getFont().stringWidth(leftLabel);
        int twR = g.getFont().stringWidth(rightLabel);
        g.drawString(leftLabel, leftX - (twL / 2), y - 3, Graphics.LEFT | Graphics.TOP);
        g.drawString(rightLabel, rightX - (twR / 2), y - 3, Graphics.LEFT | Graphics.TOP);

        int yLineTop = y + bodyFont.getHeight() - 3;
        int yLineBottom = yLineTop + 8;
        g.setColor(0x7F878E);
        // Left: down arrow (there)
        g.drawLine(leftX, yLineTop, leftX, yLineBottom);
        g.drawLine(leftX + 1, yLineTop, leftX + 1, yLineBottom);
        g.drawLine(leftX, yLineBottom, leftX - 4, yLineBottom - 4);
        g.drawLine(leftX, yLineBottom, leftX + 4, yLineBottom - 4);
        g.drawLine(leftX + 1, yLineBottom, leftX - 3, yLineBottom - 4);
        g.drawLine(leftX + 1, yLineBottom, leftX + 5, yLineBottom - 4);
        // Right: up arrow (back)
        g.drawLine(rightX, yLineBottom, rightX, yLineTop);
        g.drawLine(rightX + 1, yLineBottom, rightX + 1, yLineTop);
        g.drawLine(rightX, yLineTop, rightX - 4, yLineTop + 4);
        g.drawLine(rightX, yLineTop, rightX + 4, yLineTop + 4);
        g.drawLine(rightX + 1, yLineTop, rightX - 3, yLineTop + 4);
        g.drawLine(rightX + 1, yLineTop, rightX + 5, yLineTop + 4);
        return bodyFont.getHeight() + 11;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            if (returnTo != null) {
                app.getDisplay().setCurrent(returnTo);
            } else {
                app.showMainMenu();
            }
        }
    }

    protected void keyPressed(int keyCode) {
        if (waiting && !resolved) return;
        int action = getGameAction(keyCode);
        int step = Math.max(12, bodyFont != null ? bodyFont.getHeight() + 6 : 16);
        int maxScroll = getMaxScroll(getHeight());
        if (action == Canvas.UP) {
            scrollY -= step;
            if (scrollY < 0) scrollY = 0;
            repaint();
        } else if (action == Canvas.DOWN) {
            scrollY += step;
            if (scrollY > maxScroll) scrollY = maxScroll;
            repaint();
        }
    }

    protected void pointerPressed(int x, int y) {
        lastPointerY = y;
    }

    protected void pointerDragged(int x, int y) {
        if (waiting && !resolved) return;
        if (lastPointerY < 0) {
            lastPointerY = y;
            return;
        }
        int dy = y - lastPointerY;
        lastPointerY = y;
        int maxScroll = getMaxScroll(getHeight());
        scrollY -= dy;
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;
        repaint();
    }

    protected void pointerReleased(int x, int y) {
        lastPointerY = -1;
    }

    private int getMaxScroll(int viewportH) {
        int max = contentHeight - viewportH + 6;
        return max > 0 ? max : 0;
    }

    private void clampScroll(int viewportH) {
        int max = getMaxScroll(viewportH);
        if (scrollY < 0) scrollY = 0;
        if (scrollY > max) scrollY = max;
    }
}

