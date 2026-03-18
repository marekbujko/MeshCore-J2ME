package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Noise floor viewer: auto-refreshing chart in Tools.
 */
public final class NoiseFloorScreen extends Canvas implements CommandListener {

    private static final int HISTORY_SIZE = 64;
    private static final int POLL_MS = 1000;

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;

    private final int[] history = new int[HISTORY_SIZE];
    private int historyCount = 0;
    private boolean running = true;

    public NoiseFloorScreen(AppController app, Displayable returnTo) {
        this.app = app;
        this.returnTo = returnTo;
        setTitle("Noise Floor");
        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
        startPolling();
    }

    private void startPolling() {
        new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try {
                        Thread.sleep(POLL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                    app.sendGetRadioStats();
                }
            }
        }).start();
    }

    public void addSample(int dBm) {
        if (historyCount < HISTORY_SIZE) {
            history[historyCount++] = dBm;
        } else {
            System.arraycopy(history, 1, history, 0, HISTORY_SIZE - 1);
            history[HISTORY_SIZE - 1] = dBm;
        }
        repaint();
    }

    protected void paint(Graphics g) {
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        int w = getWidth();
        int h = getHeight();
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);

        int marginLeft = 4;
        int marginRight = 4;
        int marginTop = 4;
        int marginBottom = 14;
        Font f = g.getFont();
        int fh = f.getHeight();

        g.setColor(0x000000);
        g.drawString("Noise Floor (dBm)", w / 2, marginTop, Graphics.HCENTER | Graphics.TOP);

        // Reserve space on the left for dB labels.
        int labelAreaW = 32;
        int plotX = marginLeft + labelAreaW;
        int plotY = marginTop + fh + 4;
        int plotW = w - plotX - marginRight;
        int plotH = h - plotY - marginBottom;

        g.setColor(0xE0E0E0);
        g.drawRect(plotX, plotY, plotW, plotH);

        // Fixed dBm range for Y axis.
        int min = -120;
        int max = -60;

        if (historyCount == 0) {
            // Draw dB scale even before we have samples.
            g.setColor(0x808080);
            int labelX0 = plotX - 2;
            for (int v = max; v >= min; v -= 5) {
                int ty = valueToY(v, min, max, plotY, plotH) - fh / 2;
                g.drawString(Integer.toString(v), labelX0, ty, Graphics.RIGHT | Graphics.TOP);
            }
            g.setColor(0x000000);
            g.drawString("Waiting for data...", w / 2, plotY + plotH / 2, Graphics.HCENTER | Graphics.BASELINE);
            return;
        }

        g.setColor(0xC0C0C0);
        int midY = plotY + plotH / 2;
        g.drawLine(plotX, midY, plotX + plotW, midY);

        g.setColor(0x000000);
        String latestLabel = history[historyCount - 1] + " dBm";
        g.drawString(latestLabel, plotX + plotW - 2, plotY + 2, Graphics.RIGHT | Graphics.TOP);

        g.setColor(0x0060C0);
        int points = historyCount;
        if (points == 1) {
            int x = plotX + plotW - 2;
            int y = valueToY(history[0], min, max, plotY, plotH);
            g.fillRect(x - 1, y - 1, 3, 3);
        } else {
            for (int i = 1; i < points; i++) {
                int x1 = plotX + (plotW * (i - 1)) / (HISTORY_SIZE - 1);
                int x2 = plotX + (plotW * i) / (HISTORY_SIZE - 1);
                int y1 = valueToY(history[Math.max(0, historyCount - points + i - 1)], min, max, plotY, plotH);
                int y2 = valueToY(history[Math.max(0, historyCount - points + i)], min, max, plotY, plotH);
                g.drawLine(x1, y1, x2, y2);
            }
        }

        g.setColor(0x808080);
        // Tick labels every 5 dB from -120 to -60, drawn in the reserved left margin.
        int labelX = marginLeft + labelAreaW - 2;
        for (int v = max; v >= min; v -= 5) {
            int ty = valueToY(v, min, max, plotY, plotH) - fh / 2;
            g.drawString(Integer.toString(v), labelX, ty, Graphics.RIGHT | Graphics.TOP);
        }
    }

    private int valueToY(int v, int min, int max, int plotY, int plotH) {
        int range = max - min;
        if (range <= 0) range = 1;
        int rel = v - min;
        int y = plotY + plotH - (rel * plotH) / range;
        if (y < plotY) y = plotY;
        if (y > plotY + plotH) y = plotY + plotH;
        return y;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            running = false;
            app.getDisplay().setCurrent(returnTo);
        }
    }
}

