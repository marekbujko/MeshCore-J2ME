package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import java.util.Vector;
import meshcore.util.ImageCache;
import meshcore.util.TextUtils;

/**
 * Shared Canvas-based bubble chat UI for channels and DMs.
 */
public abstract class AbstractChatCanvas extends Canvas implements CommandListener {

    private static final int BUBBLE_PADDING = 4;
    private static final int BUBBLE_MARGIN = 4;
    private static final int MESSAGE_SPACING = 10;

    protected final AppController app;
    protected final StringBuffer buf;
    private final Vector messages = new Vector(); // ChatMessage

    private final Command cmdWrite;
    private final Command cmdBack;
    private final Command cmdClear;
    private TextBox inputBox;
    private Font font;

    // Simple scrolling state (pixel-based)
    private int scrollOffset = 0;
    private int totalContentHeight = 0;
    private int lastPointerY = -1;
    private boolean isDragging = false;
    private int dragStartY = -1;
    private int dragStartScrollOffset = 0;

    // Cached layout to avoid re-wrapping text on every scroll/paint
    private int[] messageHeights;
    private int layoutWidth = -1;
    private boolean layoutDirty = true;

    // Lazy history loading state
    private boolean hasMoreHistory = true;

    private static Image emptyStateIcon;

    protected AbstractChatCanvas(AppController app, String title, StringBuffer buf) {
        this.app = app;
        this.buf = buf;
        setTitle(title);
        cmdWrite = new Command("Write", Command.OK, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        // Use higher priority value so Clear History appears at the end of the Options menu.
        cmdClear = new Command("Clear History", Command.SCREEN, 10);
        addCommand(cmdWrite);
        addCommand(cmdBack);
        addCommand(cmdClear);
        setCommandListener(this);
        rebuildMessagesFromBuffer();
    }

    /** Called by MeshCore when buffer changes. */
    public void refreshLog() {
        rebuildMessagesFromBuffer();
        scrollToBottom();
        repaint();
    }

    private void rebuildMessagesFromBuffer() {
        messages.removeAllElements();
        messageHeights = null;
        layoutDirty = true;
        String text = buf.toString();
        int len = text.length();
        int start = 0;
        while (start < len) {
            int nl = text.indexOf('\n', start);
            String line;
            if (nl >= 0) {
                line = text.substring(start, nl);
                start = nl + 1;
            } else {
                line = text.substring(start);
                start = len;
            }
            line = line.trim();
            if (line.length() == 0) continue;
            boolean fromMe = line.startsWith("[me]");
            String sender = null;
            if (line.startsWith("[") && line.indexOf(']') > 1) {
                int end = line.indexOf(']');
                String label = line.substring(1, end);
                sender = fromMe ? null : label; // no "Me" label above own bubbles
                line = line.substring(end + 1).trim();
            } else if (!fromMe) {
                // Channel messages: try "Name: message" pattern
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String possibleName = line.substring(0, colon).trim();
                    String possibleBody = line.substring(colon + 1).trim();
                    if (possibleName.length() > 0 && possibleBody.length() > 0) {
                        sender = possibleName;
                        line = possibleBody;
                    }
                }
            }
            String timestamp = null;
            int tsStart = -1;
            int tsEnd = -1;
            int depth = 0;
            for (int i = line.length() - 1; i >= 0; i--) {
                char c = line.charAt(i);
                if (c == ')') {
                    if (tsEnd < 0) tsEnd = i;
                    depth++;
                } else if (c == '(') {
                    if (depth > 0) {
                        depth--;
                        if (depth == 0 && tsEnd >= 0) {
                            tsStart = i;
                            break;
                        }
                    }
                }
            }
            if (tsStart >= 0 && tsEnd > tsStart) {
                timestamp = line.substring(tsStart + 1, tsEnd);
                line = line.substring(0, tsStart).trim();
            }
            line = TextUtils.unescapeNewlines(line);
            messages.addElement(new ChatMessage(line, fromMe, sender, timestamp));
        }
    }

    protected void paint(Graphics g) {
        if (font == null) {
            font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        int w = getWidth();
        int h = getHeight();
        g.setFont(font);

        // Background
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);

        // Empty state
        if (messages.size() == 0) {
            if (emptyStateIcon == null) {
                try {
                    emptyStateIcon = ImageCache.get("/messages.png");
                } catch (Throwable ignore) {}
            }
            Font boldFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
            String sub = getEmptyStateSubtitle();
            Vector subLines = (sub != null && sub.length() > 0)
                    ? wrapText(sub, w - (BUBBLE_MARGIN * 4)) : new Vector();
            int lineH = boldFont.getHeight();
            int iconH = (emptyStateIcon != null) ? emptyStateIcon.getHeight() + 8 : 0;
            int totalH = iconH + lineH + 4 + subLines.size() * font.getHeight();
            int yStart = (h - totalH) / 2;
            if (emptyStateIcon != null) {
                g.drawImage(emptyStateIcon, w / 2, yStart, Graphics.HCENTER | Graphics.TOP);
            }
            int textY = yStart + iconH;
            g.setFont(boldFont);
            g.setColor(0x666666);
            g.drawString("No Messages", w / 2, textY, Graphics.TOP | Graphics.HCENTER);
            if (subLines.size() > 0) {
                g.setFont(font);
                g.setColor(0x999999);
                int subY = textY + lineH + 4;
                for (int i = 0; i < subLines.size(); i++) {
                    g.drawString((String) subLines.elementAt(i), w / 2, subY, Graphics.TOP | Graphics.HCENTER);
                    subY += font.getHeight();
                }
            }
            return;
        }

        // Top hint for history loading (only when no more history)
        int hintHeight = 0;
        if (scrollOffset == 0 && !hasMoreHistory) {
            String hint = "No older messages";
            g.setColor(0x999999);
            int yHint = BUBBLE_MARGIN / 2;
            g.drawString(hint, w / 2, yHint, Graphics.TOP | Graphics.HCENTER);
            hintHeight = font.getHeight();
        }

        int maxBubbleWidth = w - (BUBBLE_MARGIN * 2);
        ensureLayout(maxBubbleWidth);

        // Clamp scrollOffset
        int maxScroll = Math.max(0, totalContentHeight - h);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        int y = BUBBLE_MARGIN + hintHeight - scrollOffset;
        for (int idx = 0; idx < messages.size(); idx++) {
            ChatMessage m = (ChatMessage) messages.elementAt(idx);
            int mh = getMessageHeight(m, maxBubbleWidth);

            // Only draw if within viewport (with small buffer)
            if (y + mh >= -20 && y <= h + 20) {
                drawMessageBubble(g, m, y, maxBubbleWidth, w);
            }
            y += mh + MESSAGE_SPACING;
            if (y > h + 100) {
                break;
            }
        }
    }

    protected void showNotify() {
        super.showNotify();
        refreshLog();
    }

    private int getMessageHeight(ChatMessage m, int maxBubbleWidth) {
        if (font == null) {
            font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        Vector lines = wrapText(m.text, maxBubbleWidth - (BUBBLE_PADDING * 2));
        int textHeight = lines.size() * font.getHeight();
        int bubbleHeight = textHeight + (BUBBLE_PADDING * 2);
        if (m.sender != null && m.sender.length() > 0) {
            bubbleHeight += font.getHeight();
        }
        if (m.timestamp != null && m.timestamp.length() > 0) {
            bubbleHeight += font.getHeight();
        }
        return bubbleHeight;
    }

    private int drawMessageBubble(Graphics g, ChatMessage m, int y, int maxBubbleWidth, int screenWidth) {
        Vector lines = wrapText(m.text, maxBubbleWidth - (BUBBLE_PADDING * 2));
        int textHeight = lines.size() * font.getHeight();
        int bubbleHeight = textHeight + (BUBBLE_PADDING * 2);
        int labelHeight = (m.sender != null && m.sender.length() > 0) ? font.getHeight() : 0;
        int timeHeight = (m.timestamp != null && m.timestamp.length() > 0) ? font.getHeight() : 0;

        int bubbleWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = (String) lines.elementAt(i);
            int lw = font.stringWidth(line);
            if (lw > bubbleWidth) bubbleWidth = lw;
        }
        bubbleWidth += (BUBBLE_PADDING * 2);
        if (bubbleWidth < 40) bubbleWidth = 40;

        int x = m.fromMe
                ? (screenWidth - bubbleWidth - BUBBLE_MARGIN)
                : BUBBLE_MARGIN;

        int bubbleY = y + labelHeight;

        // Sender label slightly above bubble
        if (m.sender != null && m.sender.length() > 0) {
            g.setColor(0x666666);
            if (m.fromMe) {
                // Align own name to the right edge of the bubble
                g.drawString(m.sender, x + bubbleWidth, y - 2, Graphics.TOP | Graphics.RIGHT);
            } else {
                // Others aligned to left edge
                g.drawString(m.sender, x, y - 2, Graphics.TOP | Graphics.LEFT);
            }
        }

        // Bubble background
        g.setColor(m.fromMe ? 0x0078D7 : 0xDDDDDD); // blue vs grey
        g.fillRoundRect(x, bubbleY, bubbleWidth, bubbleHeight, 8, 8);

        // Text
        g.setColor(m.fromMe ? 0xFFFFFF : 0x000000);
        int ty = bubbleY + BUBBLE_PADDING;
        for (int i = 0; i < lines.size(); i++) {
            String line = (String) lines.elementAt(i);
            g.drawString(line, x + BUBBLE_PADDING, ty, Graphics.TOP | Graphics.LEFT);
            ty += font.getHeight();
        }

        // Timestamp below the bubble
        if (m.timestamp != null && m.timestamp.length() > 0) {
            g.setColor(0x666666);
            int tsY = bubbleY + bubbleHeight + 2;
            if (m.fromMe) {
                g.drawString(m.timestamp, x + bubbleWidth, tsY, Graphics.TOP | Graphics.RIGHT);
            } else {
                g.drawString(m.timestamp, x, tsY, Graphics.TOP | Graphics.LEFT);
            }
        }

        return bubbleHeight + labelHeight + timeHeight;
    }

    private Vector wrapText(String text, int maxWidth) {
        Vector lines = new Vector();
        Vector tokens = new Vector();

        // Split into words and explicit newlines as separate tokens.
        StringBuffer word = new StringBuffer();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                if (word.length() > 0) {
                    tokens.addElement(word.toString());
                    word.setLength(0);
                }
            } else if (c == '\n' || c == '\r') {
                if (word.length() > 0) {
                    tokens.addElement(word.toString());
                    word.setLength(0);
                }
                tokens.addElement("\n");
            } else {
                word.append(c);
            }
        }
        if (word.length() > 0) {
            tokens.addElement(word.toString());
        }

        StringBuffer current = new StringBuffer();
        for (int i = 0; i < tokens.size(); i++) {
            String tok = (String) tokens.elementAt(i);
            if ("\n".equals(tok)) {
                if (current.length() > 0) {
                    lines.addElement(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            String wordTok = tok;
            int wordWidth = font.stringWidth(wordTok);
            if (wordWidth > maxWidth) {
                // Break long word character by character.
                for (int j = 0; j < wordTok.length(); j++) {
                    char ch = wordTok.charAt(j);
                    String test = current.toString() + ch;
                    if (font.stringWidth(test) > maxWidth && current.length() > 0) {
                        lines.addElement(current.toString());
                        current.setLength(0);
                    }
                    current.append(ch);
                }
                continue;
            }

            String candidate;
            if (current.length() == 0) {
                candidate = wordTok;
            } else {
                candidate = current.toString() + " " + wordTok;
            }

            if (font.stringWidth(candidate) > maxWidth && current.length() > 0) {
                lines.addElement(current.toString());
                current.setLength(0);
                current.append(wordTok);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }

        if (current.length() > 0) {
            lines.addElement(current.toString());
        }
        if (lines.size() == 0) {
            lines.addElement(text);
        }
        return lines;
    }

    private void scrollBy(int delta) {
        int h = getHeight();
        int maxBubbleWidth = getWidth() - (BUBBLE_MARGIN * 2);
        ensureLayout(maxBubbleWidth);

        int maxScroll = Math.max(0, totalContentHeight - h);
        int newOffset = scrollOffset + delta;
        if (newOffset < 0) {
            newOffset = 0;
            if (delta < 0 && hasMoreHistory) {
                int before = totalContentHeight;
                if (loadOlderHistoryBatch()) {
                    rebuildMessagesFromBuffer();
                    ensureLayout(maxBubbleWidth);
                    int added = totalContentHeight - before;
                    if (added > 0) {
                        scrollOffset = added;
                        repaint();
                        return;
                    }
                } else {
                    hasMoreHistory = false;
                }
            }
        }
        if (newOffset > maxScroll) newOffset = maxScroll;
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            repaint();
        }
    }

    private void scrollToBottom() {
        int h = getHeight();
        if (h <= 0) {
            // Will be recomputed on first paint
            scrollOffset = 0;
            return;
        }
        int maxBubbleWidth = getWidth() - (BUBBLE_MARGIN * 2);
        ensureLayout(maxBubbleWidth);
        int maxScroll = Math.max(0, totalContentHeight - h);
        scrollOffset = maxScroll;
    }

    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        if (action == UP || keyCode == KEY_NUM2) {
            scrollBy(-font.getHeight() * 2);
        } else if (action == DOWN || keyCode == KEY_NUM8) {
            scrollBy(font.getHeight() * 2);
        } else {
            super.keyPressed(keyCode);
        }
    }

    protected void pointerPressed(int x, int y) {
        lastPointerY = y;
        isDragging = false;
        dragStartY = y;
        dragStartScrollOffset = scrollOffset;
    }

    protected void pointerDragged(int x, int y) {
        if (lastPointerY != -1) {
            int deltaY = y - dragStartY;
            if (!isDragging && Math.abs(deltaY) > 5) {
                isDragging = true;
            }
            if (isDragging) {
                int h = getHeight();
                int maxBubbleWidth = getWidth() - (BUBBLE_MARGIN * 2);
                ensureLayout(maxBubbleWidth);
                int maxScroll = Math.max(0, totalContentHeight - h);

                int newOffset = dragStartScrollOffset - deltaY;
                if (newOffset < 0) newOffset = 0;
                if (newOffset > maxScroll) newOffset = maxScroll;
                if (newOffset != scrollOffset) {
                    scrollOffset = newOffset;
                    repaint();
                }
            }
        }
        lastPointerY = y;
    }

    protected void pointerReleased(int x, int y) {
        lastPointerY = -1;
        boolean wasDragging = isDragging;
        isDragging = false;
        dragStartY = -1;
        dragStartScrollOffset = 0;
        if (wasDragging && scrollOffset == 0 && hasMoreHistory) {
            int h = getHeight();
            int maxBubbleWidth = getWidth() - (BUBBLE_MARGIN * 2);
            ensureLayout(maxBubbleWidth);
            int before = totalContentHeight;
            if (loadOlderHistoryBatch()) {
                rebuildMessagesFromBuffer();
                ensureLayout(maxBubbleWidth);
                int added = totalContentHeight - before;
                if (added > 0) {
                    scrollOffset = added;
                }
                repaint();
            } else {
                hasMoreHistory = false;
                repaint();
            }
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            onBack();
        } else if (c == cmdWrite) {
            openInputBox();
        } else if (c == cmdClear && d == this) {
            openClearHistoryConfirm();
        } else if (d == inputBox && c.getCommandType() == Command.OK) {
            String msg = inputBox.getString().trim();
            if (msg.length() > 0) {
                onSendMessage(msg);
            }
            app.getDisplay().setCurrent(this);
        } else if (d == inputBox && c.getCommandType() == Command.BACK) {
            app.getDisplay().setCurrent(this);
        }
    }

    private void openInputBox() {
        inputBox = new TextBox("Write", "", getMaxMessageLength(), TextField.ANY);
        inputBox.addCommand(new Command("Send", Command.OK, 1));
        inputBox.addCommand(new Command("Back", Command.BACK, 2));
        inputBox.setCommandListener(this);
        app.getDisplay().setCurrent(inputBox);
    }

    private void openClearHistoryConfirm() {
        javax.microedition.lcdui.Alert a =
                Alerts.confirm("Delete history", "Delete all messages in this chat?");
        a.addCommand(new Command("Yes", Command.OK, 1));
        a.addCommand(new Command("No", Command.BACK, 2));
        a.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    onClearHistoryConfirmed();
                }
                app.getDisplay().setCurrent(AbstractChatCanvas.this);
            }
        });
        app.getDisplay().setCurrent(a, this);
    }

    /**
     * Ensure per-message heights and totalContentHeight are computed for the current width.
     * This avoids re-wrapping all messages on every scroll and paint.
     */
    private void ensureLayout(int maxBubbleWidth) {
        if (font == null) {
            font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        int w = getWidth();
        if (w <= 0) {
            return;
        }
        if (!layoutDirty && layoutWidth == w && messageHeights != null && messageHeights.length == messages.size()) {
            return;
        }
        layoutWidth = w;
        int count = messages.size();
        messageHeights = new int[count];
        totalContentHeight = BUBBLE_MARGIN;
        for (int i = 0; i < count; i++) {
            ChatMessage m = (ChatMessage) messages.elementAt(i);
            int mh = getMessageHeight(m, maxBubbleWidth);
            messageHeights[i] = mh;
            totalContentHeight += mh + MESSAGE_SPACING;
        }
        totalContentHeight += BUBBLE_MARGIN;
        layoutDirty = false;
    }

    /** Maximum characters allowed in the write TextBox. Override per screen. */
    protected int getMaxMessageLength() {
        return 160;
    }

    /** Implement navigation when Back is pressed. */
    protected abstract void onBack();

    /** Implement actual sending of message (channel vs DM). */
    protected abstract void onSendMessage(String msg);

    /** Called when user confirms "Delete history". */
    protected abstract void onClearHistoryConfirmed();

    /** Load one older history batch (from RMS) when user scrolls to the top. */
    protected abstract boolean loadOlderHistoryBatch();

    /** Subtitle for empty state (null = none). Override in DMScreen for contact hint. */
    protected String getEmptyStateSubtitle() {
        return null;
    }
}

