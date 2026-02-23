package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

/**
 * Direct message screen with a contact.
 */
public class DMScreen extends Form implements CommandListener {

    private final AppController app;
    private final int contactIdx;
    private final StringBuffer dmBuf;
    private final StringItem dmLog;
    private final TextField tfDmMsg;
    private final Command cmdSend;
    private final Command cmdBack;

    public DMScreen(AppController app, int contactIdx, String contactName, StringBuffer dmBuf) {
        super("DM: " + contactName);
        this.app = app;
        this.contactIdx = contactIdx;
        this.dmBuf = dmBuf;
        dmLog = new StringItem("", dmBuf.toString());
        tfDmMsg = new TextField("Message:", "", 160, TextField.ANY);
        append(dmLog);
        append(tfDmMsg);
        cmdSend = new Command("Send", Command.OK, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdSend);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void refreshLog() {
        dmLog.setText(dmBuf.toString());
    }

    public int getContactIdx() {
        return contactIdx;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showContactsScreen();
            return;
        }
        if (c == cmdSend) {
            final String msg = tfDmMsg.getString().trim();
            if (msg.length() > 0 && contactIdx >= 0) {
                tfDmMsg.setString("");
                new Thread(new Runnable() {
                    public void run() {
                        app.sendDirectMessage(contactIdx, msg);
                    }
                }).start();
            }
        }
    }
}
