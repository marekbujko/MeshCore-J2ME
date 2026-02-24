package meshcore.ui;

import meshcore.util.ConnectStorage;
import meshcore.util.ParseUtils;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;

/**
 * Connect screen: IP and port entry.
 */
public class ConnectScreen extends Form implements CommandListener {

    private final AppController app;
    private final TextField tfHost;
    private final TextField tfPort;
    private final Command cmdConnect;

    public ConnectScreen(AppController app) {
        super("MeshCore");
        this.app = app;
        tfHost = new TextField("ESP32 IP:", ConnectStorage.loadHost(), 40, TextField.ANY);
        tfPort = new TextField("Port:", ConnectStorage.loadPort(), 6, TextField.NUMERIC);
        append(tfHost);
        append(tfPort);
        cmdConnect = new Command("Connect", Command.OK, 1);
        addCommand(cmdConnect);
        setCommandListener(this);
    }

    public void setTitle(String title) {
        super.setTitle(title);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdConnect) {
            final String host = tfHost.getString().trim();
            final int port = ParseUtils.parseInt(tfPort.getString().trim(), 5000);
            new Thread(new Runnable() {
                public void run() {
                    app.connect(host, port);
                }
            }).start();
        }
    }
}
