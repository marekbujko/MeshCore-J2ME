package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

/**
 * Channel chat screen for a specific channel.
 */
public class ChannelScreen extends Form implements CommandListener {

    private final AppController app;
    private final int channelIndex;
    private final StringBuffer channelBuf;
    private final StringItem channelLog;
    private final TextField tfChannelMsg;
    private final Command cmdSend;
    private final Command cmdBack;

    public ChannelScreen(AppController app, int channelIndex, String channelName,
            StringBuffer channelBuf) {
        super(channelName);
        this.app = app;
        this.channelIndex = channelIndex;
        this.channelBuf = channelBuf;
        channelLog = new StringItem("", channelBuf.toString());
        tfChannelMsg = new TextField("Message:", "", 160, TextField.ANY);
        append(channelLog);
        append(tfChannelMsg);
        cmdSend = new Command("Send", Command.OK, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdSend);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public int getChannelIndex() {
        return channelIndex;
    }

    public void refreshLog() {
        channelLog.setText(channelBuf.toString());
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showChannelListScreen();
            return;
        }
        if (c == cmdSend) {
            final String msg = tfChannelMsg.getString().trim();
            if (msg.length() > 0) {
                tfChannelMsg.setString("");
                new Thread(new Runnable() {
                    public void run() {
                        app.sendChannelMessage(channelIndex, msg);
                    }
                }).start();
            }
        }
    }
}
