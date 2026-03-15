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
import meshcore.protocol.ProtocolConstants;
import meshcore.util.TextUtils;

/**
 * Screen that scans a QR code via camera and adds the contact if the
 * decoded text is a valid meshcore://contact/add link.
 * Implements QrScanListener for the dk.onlinecity.qrr QR scanner.
 */
public final class AddFromQrScanScreen implements QrScanListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Display display;
    private boolean capturing;
    private boolean active;

    public AddFromQrScanScreen(AppController app, Displayable returnTo) {
        this.app = app;
        this.returnTo = returnTo;
        this.display = app.getDisplay();
        this.capturing = false;
        this.active = true;
        display.setCurrent(CameraCanvas.getInstance(this, display));
    }

    public void showCamera() {
        if (!active) {
            return;
        }
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
        if (!active) {
            return;
        }
        if (capturing) {
            // Ignore extra taps/keys while a capture is already in progress
            return;
        }
        capturing = true;
        // Immediately switch away from live preview to a simple processing screen
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
                        display.setCurrent(new DecodeCanvas(AddFromQrScanScreen.this, raw));
                    }
                } catch (Exception e) {
                    Alerts.info(display, returnTo, "Error", "Could not capture image.");
                } finally {
                    // Allow another capture if the scanner returns to the camera view
                    capturing = false;
                }
            }
        }).start();
    }

    public void showResult(String text) {
        if (!active) {
            return;
        }
        active = false;
        if (parseAndAdd(text)) {
            Alerts.info(display, returnTo, "Add Contact", "Successfully added!");
        } else {
            Alerts.info(display, returnTo, "Error", "Not a valid MeshCore contact QR code.");
        }
    }

    public void exit() {
        active = false;
        display.setCurrent(returnTo);
    }

    private boolean parseAndAdd(String link) {
        if (link == null || !link.startsWith("meshcore://")) return false;
        int q = link.indexOf('?');
        if (q < 0 || q + 1 >= link.length()) return false;
        String query = link.substring(q + 1);
        String name = null;
        String pubKey = null;
        int typeParam = 1;
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
                } else if ("public_key".equals(key)) {
                    pubKey = val;
                } else if ("type".equals(key)) {
                    try {
                        typeParam = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                    }
                }
            }
            pos = amp + 1;
        }
        if (pubKey == null || pubKey.length() != 64 || name == null) {
            return false;
        }
        int advType;
        switch (typeParam) {
            case 1: advType = ProtocolConstants.ADV_TYPE_CHAT; break;    // Companion
            case 2: advType = ProtocolConstants.ADV_TYPE_REPEATER; break;
            case 3: advType = ProtocolConstants.ADV_TYPE_ROOM; break;    // Room Server
            case 4: advType = ProtocolConstants.ADV_TYPE_SENSOR; break;
            default: advType = ProtocolConstants.ADV_TYPE_CHAT; break;
        }
        app.addManualContact(name, pubKey, advType);
        return true;
    }

    /**
     * Simple full-screen canvas shown between capture and DecodeCanvas,
     * so the user does not keep seeing the live camera preview while
     * the snapshot/decoding work is starting.
     */
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
