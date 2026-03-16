package meshcore.ui;

import java.util.Random;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;

import meshcore.util.FrameUtils;
import meshcore.util.SHA256;

/**
 * Create a new private channel: user enters name, app generates secret and shows it for sharing.
 * MeshCore private channel secrets are 16 bytes (32 hex chars). They should be generated with
 * cryptographically secure random; J2ME CLDC has no SecureRandom, so we derive 16 bytes from
 * SHA-256(name + timestamp + random bytes) to mix in entropy. For high-security use, generate
 * the key externally (e.g. secure keygen) and share it via "Join Private Channel".
 */
public final class CreatePrivateChannelScreen extends Form implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;

    private final TextField tfName;
    private final Command cmdCreate;
    private final Command cmdBack;

    public CreatePrivateChannelScreen(AppController app, Displayable returnTo) {
        super("Create Private Channel");
        this.app = app;
        this.returnTo = returnTo;

        tfName = new TextField("Channel name", "", 32, TextField.ANY);
        append(tfName);
        append("A secret will be generated. Share it with others to let them join.");

        cmdCreate = new Command("Create", Command.OK, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdCreate);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    /**
     * Generate 16-byte channel secret. MeshCore expects 16 bytes (32 hex).
     * Derive from SHA-256(name + timestamp + random) for better entropy than raw Random in J2ME.
     */
    private static byte[] generateChannelSecret(String channelName) {
        Random r = new Random();
        long t = System.currentTimeMillis();
        byte[] random = new byte[16];
        for (int i = 0; i < 16; i++) {
            random[i] = (byte) r.nextInt(256);
        }
        byte[] nameBytes;
        try {
            nameBytes = channelName.getBytes("UTF-8");
        } catch (Exception e) {
            nameBytes = channelName.getBytes();
        }
        int len = nameBytes.length + 8 + 16;
        byte[] input = new byte[len];
        int off = 0;
        System.arraycopy(nameBytes, 0, input, off, nameBytes.length);
        off += nameBytes.length;
        for (int i = 7; i >= 0; i--) {
            input[off++] = (byte) (t >>> (i * 8));
        }
        System.arraycopy(random, 0, input, off, 16);
        byte[] hash = SHA256.digest(input);
        byte[] secret = new byte[16];
        System.arraycopy(hash, 0, secret, 0, 16);
        return secret;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdCreate) {
            String name = tfName.getString().trim();
            if (name.length() == 0) {
                Alerts.info(app.getDisplay(), this, "Error", "Enter a channel name.");
                return;
            }
            byte[] secret = generateChannelSecret(name);
            String secretHex = FrameUtils.bytesToHex(secret, 0, 16);
            app.addPrivateChannel(name, secretHex);
            app.getDisplay().setCurrent(new PrivateChannelSecretScreen(app, returnTo, name, secretHex));
        }
    }
}
