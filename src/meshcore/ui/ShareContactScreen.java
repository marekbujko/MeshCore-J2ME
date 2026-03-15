package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import meshcore.protocol.ProtocolConstants;
import meshcore.util.TextUtils;

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
        super((name != null && name.length() > 0) ? ("Share " + name) : "Share", List.IMPLICIT);
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
        String hex;
        if (contactIdx >= 0) {
            hex = app.getContactPublicKeyHex(contactIdx);
        } else {
            // Self contact: use node's public key, exposed via contact index -1 handler in MeshCore.getContactPublicKeyHex
            hex = app.getContactPublicKeyHex(contactIdx);
        }
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
        String encodedName = TextUtils.urlEncode(contactName != null ? contactName : "");

        // Map internal ADV_TYPE_* to docs type values (1=Companion, 2=Repeater, 3=Room Server, 4=Sensor)
        int typeParam;
        if (contactType == ProtocolConstants.ADV_TYPE_REPEATER) {
            typeParam = 2;
        } else if (contactType == ProtocolConstants.ADV_TYPE_ROOM) {
            typeParam = 3;
        } else if (contactType == ProtocolConstants.ADV_TYPE_SENSOR) {
            typeParam = 4;
        } else {
            typeParam = 1; // Companion
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

    private void showQrCode() {
        String link = buildShareUrl();
        String title = (contactName != null && contactName.length() > 0) ? contactName : "QR Code";
        app.getDisplay().setCurrent(new ShareQrScreen(app, link, title, this));
    }
}

