package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Result view for manual TRACE (forward only).
 */
public final class TracePathResultScreen extends Canvas implements CommandListener {

    private static final long TRACE_TIMEOUT_MS = 20000;

    private final AppController app;
    private final Displayable returnTo;
    private final byte[] forwardPath;
    private final Command cmdBack;
    private final Command cmdRefresh;

    private String forwardHops = "";
    private String forwardDuration = "";

    private byte[] lastPathSnrs;
    private int lastFinalSNR4;

    private boolean forwardDone = false;
    private boolean waiting = false;
    private boolean timedOut = false;
    private int waitingDots = 0;
    private int scrollY = 0;
    private int contentHeight = 0;
    private int lastPointerY = -1;

    private Font titleFont;
    private Font bodyFont;

    public TracePathResultScreen(AppController app, Displayable returnTo, byte[] forwardPath) {
        this.app = app;
        this.returnTo = returnTo;
        this.forwardPath = forwardPath;
        setFullScreenMode(false);
        setTitle("Trace Path");

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdRefresh = new Command("Refresh", Command.SCREEN, 2);
        addCommand(cmdBack);
        addCommand(cmdRefresh);
        setCommandListener(this);

        renderWaiting();
    }

    public Displayable getReturnTo() {
        return returnTo;
    }

    public void renderWaiting() {
        forwardDone = false;
        waiting = true;
        timedOut = false;
        waitingDots = 0;
        scrollY = 0;
        startWaitingDots();
        startTimeoutWatcher();
        repaint();
    }

    public void setResult(
            String destName,
            byte[] pathSnrs,
            int finalSNR4,
            int pathHops,
            long durationMs
    ) {
        forwardDone = true;
        waiting = false;
        timedOut = false;
        lastPathSnrs = pathSnrs;
        lastFinalSNR4 = finalSNR4;
        forwardHops = "Path hops: " + pathHops;
        forwardDuration = (durationMs >= 0) ? ("Duration: " + durationMs + " ms") : "";
        scrollY = 0;
        render();
    }

    private void render() {
        repaint();
    }

    private String getRepeaterLabel(int i) {
        if (forwardPath == null || i < 0 || i >= forwardPath.length) {
            return "Repeater " + (i + 1);
        }
        String repName = app.getRepeaterNameForPathByte(forwardPath[i]);
        if (repName == null || repName.length() == 0) {
            repName = "Repeater " + (i + 1);
        }
        return repName;
    }

    private String getHopSnr(int i) {
        if (lastPathSnrs == null || i < 0 || i >= lastPathSnrs.length) {
            return "n/a";
        }
        return formatSnr4ToDb((int) lastPathSnrs[i]);
    }

    private String formatSnr4ToDb(int snr4) {
        boolean neg = snr4 < 0;
        int abs = neg ? -snr4 : snr4;
        int whole = abs / 4;
        int fracQ = abs % 4; // 0..3
        int fracDec = fracQ * 25; // 0,25,50,75
        String fracStr = fracDec < 10 ? "0" + fracDec : String.valueOf(fracDec);
        return (neg ? "-" : "") + whole + "." + fracStr + " dB";
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        if (titleFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        // Background
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);
        int pad = 8;
        int x = pad;
        int maxW = w - (pad * 2) - 6; // leave room for scroll bar
        int y = pad - scrollY;

        g.setFont(titleFont);
        g.setColor(0x1F1F1F);
        y += drawWrappedCentered(g, "Trace Path", x, y, maxW) + 2;
        g.setColor(0xD0D0D0);
        g.drawLine(x, y, x + maxW, y);
        y += 3;

        g.setFont(bodyFont);
        g.setColor(0x333333);

        if (!forwardDone) {
            if (timedOut) {
                y += drawWrappedCentered(g, "No reply (timeout).", x, y, maxW);
                y += drawWrappedCentered(g, "Press Refresh to try again.", x, y + 2, maxW);
                contentHeight = y + scrollY + pad;
                clampScroll(h);
                return;
            }
            String dots = (waitingDots == 0) ? "" : (waitingDots == 1 ? "." : (waitingDots == 2 ? ".." : "..."));
            y += drawWrappedCentered(g, "Waiting for reply" + dots, x, y, maxW);
            contentHeight = y + scrollY + pad;
            clampScroll(h);
            return;
        }

        y += drawWrappedCentered(g, forwardHops, x, y, maxW);
        if (forwardDuration != null && forwardDuration.length() > 0) {
            y += drawWrappedCentered(g, forwardDuration, x, y, maxW);
        }
        y += 3;
        g.setColor(0xD0D0D0);
        g.drawLine(x, y, x + maxW, y);
        y += 3;
        y += 4;

        String myNodeLabel = getMyNodeLabel();
        y += drawNodeBubble(g, x, y, maxW, myNodeLabel);

        int hopCount = (forwardPath != null) ? forwardPath.length : 0;
        for (int i = 0; i < hopCount; i++) {
            y += drawSnrArrow(g, x, y, maxW, getHopSnr(i));
            y += drawNodeBubble(g, x, y, maxW, getRepeaterLabel(i));
        }

        String finalSnr = formatSnr4ToDb(lastFinalSNR4);
        y += drawSnrArrow(g, x, y, maxW, finalSnr);
        y += drawNodeBubble(g, x, y, maxW, myNodeLabel);

        contentHeight = y + scrollY + pad;
        clampScroll(h);

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
        // Match main menu card gray tone.
        g.setColor(0xF8F8F8);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(0x9E9E9E);
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setColor(0x1E344A);
        g.drawString(text != null ? text : "", x + 6, y + 4, Graphics.LEFT | Graphics.TOP);
        return h + 4;
    }

    private int drawSnrArrow(Graphics g, int x, int y, int w, String snr) {
        int cx = x + (w / 2);
        String label = "SNR " + (snr != null ? snr : "n/a");
        g.setColor(0x444444);
        int tw = g.getFont().stringWidth(label);
        g.drawString(label, cx - (tw / 2), y - 3, Graphics.LEFT | Graphics.TOP);
        int yLine = y + bodyFont.getHeight() - 3;
        g.setColor(0x7F878E);
        // Slightly bolder arrow: double-stroke shaft + larger head.
        g.drawLine(cx, yLine, cx, yLine + 8);
        g.drawLine(cx + 1, yLine, cx + 1, yLine + 8);
        g.drawLine(cx, yLine + 8, cx - 4, yLine + 4);
        g.drawLine(cx, yLine + 8, cx + 4, yLine + 4);
        g.drawLine(cx + 1, yLine + 8, cx - 3, yLine + 4);
        g.drawLine(cx + 1, yLine + 8, cx + 5, yLine + 4);
        return bodyFont.getHeight() + 11;
    }

    private String getMyNodeLabel() {
        String n = app.getNodeName();
        if (n == null) return "My Node";
        n = n.trim();
        if (n.length() == 0) return "My Node";
        return n;
    }

    private int drawWrapped(Graphics g, String text, int x, int y, int maxWidth) {
        if (text == null) text = "";
        int lineH = g.getFont().getHeight();
        int startY = y;
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && text.charAt(i) == ' ') i++;
            if (i >= n) break;
            int end = i;
            int lastFitSpace = -1;
            while (end < n) {
                if (text.charAt(end) == ' ') lastFitSpace = end;
                String s = text.substring(i, end + 1);
                if (g.getFont().stringWidth(s) > maxWidth) {
                    break;
                }
                end++;
            }
            int cut;
            if (end >= n) {
                cut = n;
            } else if (lastFitSpace >= i) {
                cut = lastFitSpace;
            } else {
                cut = end > i ? end : (i + 1);
            }
            String line = text.substring(i, cut);
            g.drawString(line, x, y, Graphics.LEFT | Graphics.TOP);
            y += lineH;
            i = (cut < n && text.charAt(cut) == ' ') ? cut + 1 : cut;
        }
        return y - startY;
    }

    private int drawWrappedCentered(Graphics g, String text, int x, int y, int maxWidth) {
        if (text == null) text = "";
        int lineH = g.getFont().getHeight();
        int startY = y;
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && text.charAt(i) == ' ') i++;
            if (i >= n) break;
            int end = i;
            int lastFitSpace = -1;
            while (end < n) {
                if (text.charAt(end) == ' ') lastFitSpace = end;
                String s = text.substring(i, end + 1);
                if (g.getFont().stringWidth(s) > maxWidth) {
                    break;
                }
                end++;
            }
            int cut;
            if (end >= n) {
                cut = n;
            } else if (lastFitSpace >= i) {
                cut = lastFitSpace;
            } else {
                cut = end > i ? end : (i + 1);
            }
            String line = text.substring(i, cut);
            int tw = g.getFont().stringWidth(line);
            int drawX = x + ((maxWidth - tw) / 2);
            if (drawX < x) drawX = x;
            g.drawString(line, drawX, y, Graphics.LEFT | Graphics.TOP);
            y += lineH;
            i = (cut < n && text.charAt(cut) == ' ') ? cut + 1 : cut;
        }
        return y - startY;
    }

    private void startWaitingDots() {
        final TracePathResultScreen self = this;
        final javax.microedition.lcdui.Display display = app.getDisplay();
        new Thread(new Runnable() {
            public void run() {
                int i = 0;
                while (display != null) {
                    if (forwardDone || !waiting || timedOut) break;
                    final int d = i % 4;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (display.getCurrent() == self && !forwardDone && waiting && !timedOut) {
                                waitingDots = d;
                                repaint();
                            }
                        }
                    });
                    i++;
                    try { Thread.sleep(300); } catch (InterruptedException e) { break; }
                }
            }
        }).start();
    }

    private void startTimeoutWatcher() {
        final TracePathResultScreen self = this;
        final javax.microedition.lcdui.Display display = app.getDisplay();
        final long start = System.currentTimeMillis();
        new Thread(new Runnable() {
            public void run() {
                while (display != null) {
                    if (forwardDone || !waiting || timedOut) break;
                    if (System.currentTimeMillis() - start > TRACE_TIMEOUT_MS) break;
                    try { Thread.sleep(200); } catch (InterruptedException e) { break; }
                }
                if (display != null) {
                    if (forwardDone || !waiting || timedOut) return;
                    if (System.currentTimeMillis() - start <= TRACE_TIMEOUT_MS) return;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (display.getCurrent() != self) return;
                            if (forwardDone || !waiting || timedOut) return;
                            waiting = false;
                            timedOut = true;
                            repaint();
                        }
                    });
                }
            }
        }).start();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            if (returnTo != null) {
                app.getDisplay().setCurrent(returnTo);
            } else {
                app.showMainMenu();
            }
            return;
        }
        if (c == cmdRefresh && forwardPath != null && forwardPath.length > 0) {
            app.tracePathManualRefresh(forwardPath, this);
        }
    }

    protected void keyPressed(int keyCode) {
        if (!forwardDone) return;
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
        if (!forwardDone) return;
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

