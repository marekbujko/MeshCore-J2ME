package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Activity log screen: status messages, errors, notifications.
 * Implemented as a scrollable Canvas, always scrolled to the bottom.
 */
public class ActivityLogScreen extends Canvas implements CommandListener {

    private final AppController app;
    private final StringBuffer logBuf;
    private final Displayable returnTo;
    private final Command cmdBack;
    private Font font;
    private String[] lines = new String[0];
    private int totalHeight = 0;
    private int scrollOffset = 0;

    public ActivityLogScreen(AppController app, StringBuffer logBuf, Displayable returnTo) {
        this.app = app;
        this.logBuf = logBuf;
        this.returnTo = returnTo;
        setTitle("Activity Log");
        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
        rebuildLines();
        scrollToBottom();
    }

    public void refreshLog() {
        rebuildLines();
        scrollToBottom();
        repaint();
    }

    private void rebuildLines() {
        String text = logBuf.toString();
        if (text == null || text.length() == 0) {
            lines = new String[0];
            totalHeight = 0;
            return;
        }
        // Split on '\n'
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        String[] tmp = new String[count];
        int idx = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                tmp[idx++] = text.substring(start, i);
                start = i + 1;
            }
        }
        if (start <= text.length()) {
            tmp[idx++] = text.substring(start);
        }
        lines = tmp;

        if (font == null) {
            font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        totalHeight = lines.length * font.getHeight() + 4;
    }

    private void scrollToBottom() {
        if (font == null) {
            font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        int h = getHeight();
        if (h <= 0) {
            scrollOffset = 0;
            return;
        }
        int maxScroll = Math.max(0, totalHeight - h);
        scrollOffset = maxScroll;
    }

    protected void showNotify() {
        super.showNotify();
        rebuildLines();
        scrollToBottom();
        repaint();
    }

    protected void paint(Graphics g) {
        if (font == null) {
            font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        int w = getWidth();
        int h = getHeight();
        g.setFont(font);

        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);

        int y = 2 - scrollOffset;
        g.setColor(0x000000);
        for (int i = 0; i < lines.length; i++) {
            if (y > h) break;
            if (y + font.getHeight() >= 0) {
                g.drawString(lines[i], 2, y, Graphics.TOP | Graphics.LEFT);
            }
            y += font.getHeight();
        }
    }

    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        if (font == null) {
            font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        int step = font.getHeight() * 2;
        if (action == UP || keyCode == KEY_NUM2) {
            scrollOffset -= step;
        } else if (action == DOWN || keyCode == KEY_NUM8) {
            scrollOffset += step;
        } else {
            super.keyPressed(keyCode);
        }
        int h = getHeight();
        int maxScroll = Math.max(0, totalHeight - h);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        repaint();
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
}
