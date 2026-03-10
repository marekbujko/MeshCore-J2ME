package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import meshcore.protocol.ProtocolConstants;

/**
 * Share contact screen: choose between copying raw public key, share link, or QR code.
 */
public final class ShareContactScreen extends List implements CommandListener {

    private final AppController app;
    private final int contactIdx;
    private final int contactType;
    private final String contactName;
    private final Displayable returnTo;

    private final Command cmdBack;

    public ShareContactScreen(AppController app, int contactIdx, String name, int contactType, Displayable returnTo) {
        super("Share", List.IMPLICIT);
        this.app = app;
        this.contactIdx = contactIdx;
        this.contactType = contactType;
        this.contactName = name;
        this.returnTo = returnTo;

        append("Copy Public Key", null);   // 0
        append("Show QR Code", null);      // 1

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int sel = getSelectedIndex();
            if (sel == 0) {
                showPublicKey();
            } else if (sel == 1) {
                showQrCode();
            }
        }
    }

    private void showPublicKey() {
        String hex = app.getContactPublicKeyHex(contactIdx);
        if (hex == null) hex = "";
        final TextBox tb = new TextBox("Public Key", hex, Math.max(64, hex.length()), TextField.ANY);
        Command cmdBack = new Command("Back", Command.BACK, 1);
        tb.addCommand(cmdBack);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                app.getDisplay().setCurrent(ShareContactScreen.this);
            }
        });
        app.getDisplay().setCurrent(tb);
    }

    private String buildShareUrl() {
        String pubHex = app.getContactPublicKeyHex(contactIdx);
        if (pubHex == null) pubHex = "";
        String encodedName = urlEncode(contactName != null ? contactName : "");

        // Map internal ADV_TYPE_* to docs type values
        int typeParam;
        if (contactType == ProtocolConstants.ADV_TYPE_REPEATER) {
            typeParam = 2; // Repeater
        } else if (contactType == ProtocolConstants.ADV_TYPE_ROOM) {
            typeParam = 3; // Room Server
        } else {
            typeParam = 1; // Companion / normal contact
        }

        StringBuffer sb = new StringBuffer();
        sb.append("meshcore://contact/add?name=");
        sb.append(encodedName);
        sb.append("&public_key=");
        sb.append(pubHex);
        sb.append("&type=");
        sb.append(typeParam);
        return sb.toString();
    }

    private static String urlEncode(String s) {
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else if (c == ' ') {
                out.append('+');
            } else {
                int b = c;
                if (b < 0) b += 256;
                int hi = (b >> 4) & 0xF;
                int lo = b & 0xF;
                out.append('%');
                out.append(intToHex(hi));
                out.append(intToHex(lo));
            }
        }
        return out.toString();
    }

    private static char intToHex(int v) {
        return (char) (v < 10 ? ('0' + v) : ('A' + (v - 10)));
    }

    private void showQrCode() {
        String link = buildShareUrl();
        app.getDisplay().setCurrent(new ShareQrScreen(app, link, this));
    }
}

