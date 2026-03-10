package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import com.google.zxing.TextToQRcodeImage;

/**
 * Canvas that renders a QR code for a given share link using ZXing J2ME library.
 */
public final class ShareQrScreen extends Canvas implements CommandListener {

    private final AppController app;
    private final String link;
    private final Displayable returnTo;
    private final Image qrImage;

    private final Command cmdBack;

    public ShareQrScreen(AppController app, String link, Displayable returnTo) {
        this.app = app;
        this.link = link;
        this.returnTo = returnTo;
        this.qrImage = TextToQRcodeImage.encode(link);
        this.cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);

        if (qrImage == null) {
            // Nothing to draw
            return;
        }

        int iw = qrImage.getWidth();
        int ih = qrImage.getHeight();
        int x = (w - iw) / 2;
        int y = (h - ih) / 2;
        g.drawImage(qrImage, x, y, Graphics.TOP | Graphics.LEFT);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
        }
    }
}

