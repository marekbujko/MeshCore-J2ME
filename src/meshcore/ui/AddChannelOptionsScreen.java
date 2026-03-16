package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

/**
 * Add Channel menu: Create Private Channel, Join Private Channel, Join Hashtag Channel, Scan QR code.
 */
public final class AddChannelOptionsScreen extends List implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;

    private static final int IDX_CREATE_PRIVATE = 0;
    private static final int IDX_JOIN_PRIVATE = 1;
    private static final int IDX_JOIN_HASHTAG = 2;
    private static final int IDX_SCAN_QR = 3;

    private final Command cmdBack;

    public AddChannelOptionsScreen(AppController app, Displayable returnTo) {
        super("Add Channel", List.IMPLICIT);
        this.app = app;
        this.returnTo = returnTo;

        append("Create Private Channel", null);
        append("Join Private Channel", null);
        append("Join Hashtag Channel", null);
        append("Scan QR code", null);

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
            if (sel == IDX_CREATE_PRIVATE) {
                app.getDisplay().setCurrent(new CreatePrivateChannelScreen(app, this));
            } else if (sel == IDX_JOIN_PRIVATE) {
                showJoinPrivateChannel();
            } else if (sel == IDX_JOIN_HASHTAG) {
                showJoinHashtagChannel();
            } else if (sel == IDX_SCAN_QR) {
                new AddChannelFromQrScanScreen(app, this);
            }
        }
    }

    private static String trimAll(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != ' ' && ch != '\t') sb.append(ch);
        }
        return sb.toString();
    }

    private void showJoinHashtagChannel() {
        final Displayable backTo = this;
        final TextBox tb = new TextBox("Join Hashtag Channel", "", 32, 0);
        tb.addCommand(new Command("OK", Command.OK, 1));
        tb.addCommand(new Command("Cancel", Command.CANCEL, 2));
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable disp) {
                if (cmd.getCommandType() == Command.OK) {
                    String name = tb.getString().trim();
                    if (name.length() > 0) {
                        if (!name.startsWith("#")) name = "#" + name;
                        app.addChannel(name);
                    }
                }
                app.getDisplay().setCurrent(backTo);
            }
            });
        app.getDisplay().setCurrent(tb);
    }

    private void showJoinPrivateChannel() {
        final Displayable backTo = this;
        final Form form = new Form("Join Private Channel");
        final TextField tfName = new TextField("Channel name", "", 32, TextField.ANY);
        final TextField tfSecret = new TextField("Secret Key (32 hex)", "", 32, TextField.ANY);
        form.append(tfName);
        form.append(tfSecret);
        form.addCommand(new Command("OK", Command.OK, 1));
        form.addCommand(new Command("Cancel", Command.CANCEL, 2));
        form.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable disp) {
                if (cmd.getCommandType() == Command.OK) {
                    String name = tfName.getString().trim();
                    String secret = trimAll(tfSecret.getString().trim());
                    if (name.length() > 0 && secret.length() == 32) {
                        app.addPrivateChannel(name, secret);
                        app.getDisplay().setCurrent(backTo);
                    } else if (secret.length() != 32) {
                        Alerts.warning(app.getDisplay(), form,
                                "Secret", "Secret must be 32 hex chars", 2500);
                        return;
                    } else {
                        app.getDisplay().setCurrent(backTo);
                    }
                } else {
                    app.getDisplay().setCurrent(backTo);
                }
            }
        });
        app.getDisplay().setCurrent(form);
    }
}
