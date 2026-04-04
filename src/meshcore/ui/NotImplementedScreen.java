package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Placeholder screen for features not built yet.
 */
public final class NotImplementedScreen extends Canvas implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;
    private final String heading;
    private final String body;

    private Font titleFont;
    private Font bodyFont;

    public NotImplementedScreen(AppController app, String screenTitle, Displayable returnTo) {
        this(app, screenTitle, returnTo, "This feature is not implemented yet.");
    }

    public NotImplementedScreen(AppController app, String screenTitle, Displayable returnTo, String bodyText) {
        this.app = app;
        this.returnTo = returnTo;
        this.heading = (screenTitle != null && screenTitle.length() > 0) ? screenTitle : "Coming soon";
        this.body = (bodyText != null) ? bodyText : "Not implemented yet.";
        setFullScreenMode(false);
        setTitle(heading);

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        int pad = 12;

        if (titleFont == null) {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        g.setColor(UiTheme.BG_WHITE);
        g.fillRect(0, 0, w, h);

        g.setFont(titleFont);
        g.setColor(UiTheme.TEXT_DARK);
        int y = pad;
        y += UiCanvasUtil.drawWrappedCentered(g, heading, pad, y, w - pad * 2);
        y += titleFont.getHeight();

        g.setFont(bodyFont);
        g.setColor(UiTheme.TEXT_GRAY);
        UiCanvasUtil.drawWrappedCentered(g, body, pad, y, w - pad * 2);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack && returnTo != null) {
            app.getDisplay().setCurrent(returnTo);
        }
    }
}
