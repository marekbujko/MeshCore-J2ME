package meshcore.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.media.MediaException;
import javax.microedition.media.control.VideoControl;

import dk.onlinecity.qrr.client.CameraCanvas;
import dk.onlinecity.qrr.client.CameraControl;
import dk.onlinecity.qrr.client.DecodeCanvas;
import dk.onlinecity.qrr.client.QrScanListener;
import meshcore.util.TextUtils;

/**
 * Scans a QR code and adds a channel if the decoded text is a valid
 * meshcore://channel/add?name=...&secret=... link (32 hex secret).
 */
public final class AddChannelFromQrScanScreen implements QrScanListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Display display;
    private boolean capturing;
    private boolean active;

    public AddChannelFromQrScanScreen(AppController app, Displayable returnTo) {
        this.app = app;
        this.returnTo = returnTo;
        this.display = app.getDisplay();
        this.capturing = false;
        this.active = true;
        display.setCurrent(CameraCanvas.getInstance(this, display));
    }

    public void showCamera() {
        if (!active) return;
        try {
            CameraControl camera = CameraControl.getInstance();
            VideoControl video = camera.getVideoControl(CameraCanvas.getInstance(this, display));
            video.setDisplayFullScreen(true);
            video.setDisplayLocation(0, 0);
            camera.getPlayer().start();
            video.setVisible(true);
            display.setCurrent(CameraCanvas.getInstance(this, display));
        } catch (MediaException e) {
            Alerts.info(display, returnTo, "Error", "Cannot start camera.");
        } catch (Exception e) {
            Alerts.info(display, returnTo, "Error", "Cannot start camera.");
        }
    }

    public void takeSnapshot() {
        if (!active || capturing) return;
        capturing = true;
        display.setCurrent(new QrProcessingCanvas());
        new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] raw = CameraControl.getInstance().getSnapshot();
                    try {
                        CameraControl.getInstance().getPlayer().stop();
                    } catch (Exception e) {
                    }
                    if (active) {
                        display.setCurrent(new DecodeCanvas(AddChannelFromQrScanScreen.this, raw));
                    }
                } catch (Exception e) {
                    Alerts.info(display, returnTo, "Error", "Could not capture image.");
                } finally {
                    capturing = false;
                }
            }
        }).start();
    }

    public void showResult(String text) {
        if (!active) return;
        active = false;
        if (parseAndAdd(text)) {
            Alerts.info(display, returnTo, "Add Channel", "Channel added.");
        } else {
            Alerts.info(display, returnTo, "Error", "Not a valid MeshCore channel QR code.");
        }
    }

    public void exit() {
        active = false;
        display.setCurrent(returnTo);
    }

    /** Parse meshcore://channel/add?name=...&secret=... (secret = 32 hex chars). */
    private boolean parseAndAdd(String link) {
        if (link == null || !link.startsWith("meshcore://channel/add")) return false;
        int q = link.indexOf('?');
        if (q < 0 || q + 1 >= link.length()) return false;
        String query = link.substring(q + 1);
        String name = null;
        String secret = null;
        int pos = 0;
        while (pos < query.length()) {
            int amp = query.indexOf('&', pos);
            if (amp < 0) amp = query.length();
            String pair = query.substring(pos, amp);
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq);
                String val = pair.substring(eq + 1);
                if ("name".equals(key)) {
                    name = TextUtils.urlDecode(val);
                } else if ("secret".equals(key)) {
                    secret = val.trim().toLowerCase();
                }
            }
            pos = amp + 1;
        }
        if (name == null || secret == null || secret.length() != 32) return false;
        for (int i = 0; i < 32; i++) {
            char c = secret.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) continue;
            return false;
        }
        app.addPrivateChannel(name, secret);
        return true;
    }

    private static final class QrProcessingCanvas extends Canvas {
        QrProcessingCanvas() {
            setFullScreenMode(true);
        }
        protected void paint(Graphics g) {
            g.setColor(0x000000);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0xffffff);
            g.setFont(Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
            g.drawString("Please Wait...", getWidth() >> 1, getHeight() >> 1,
                    Graphics.HCENTER | Graphics.BASELINE);
        }
    }
}
