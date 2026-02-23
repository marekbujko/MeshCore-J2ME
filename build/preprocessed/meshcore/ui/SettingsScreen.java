package meshcore.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

/**
 * Settings screen: node name, radio params, battery.
 */
public class SettingsScreen extends Form implements CommandListener {

    private final AppController app;
    private final TextField tfNodeName;
    private final TextField tfFreq;
    private final TextField tfBw;
    private final TextField tfSf;
    private final TextField tfCr;
    private final TextField tfTxPwr;
    private final StringItem battInfo;
    private final Command cmdSave;
    private final Command cmdRefresh;
    private final Command cmdStats;
    private final Command cmdTime;
    private final Command cmdAdvert;
    private final Command cmdBack;

    public SettingsScreen(AppController app, String nodeName, String firmwareVer,
            long nodeFreq, long nodeBw, int nodeSf, int nodeCr, int nodeTxPwr) {
        super("Settings");
        this.app = app;
        append(new StringItem("Node:", nodeName + " " + firmwareVer));
        tfNodeName = new TextField("Name:", nodeName, 20, TextField.ANY);
        tfFreq = new TextField("Freq Hz:", String.valueOf(nodeFreq), 12, TextField.NUMERIC);
        tfBw = new TextField("BW Hz:", String.valueOf(nodeBw), 12, TextField.NUMERIC);
        tfSf = new TextField("SF:", String.valueOf(nodeSf), 3, TextField.NUMERIC);
        tfCr = new TextField("CR:", String.valueOf(nodeCr), 3, TextField.NUMERIC);
        tfTxPwr = new TextField("TX dBm:", String.valueOf(nodeTxPwr), 3, TextField.NUMERIC);
        battInfo = new StringItem("Battery:", "Press Refresh");
        append(tfNodeName);
        append(tfFreq);
        append(tfBw);
        append(tfSf);
        append(tfCr);
        append(tfTxPwr);
        append(battInfo);
        cmdSave = new Command("Save", Command.OK, 1);
        cmdRefresh = new Command("Refresh", Command.ITEM, 3);
        cmdStats = new Command("Stats", Command.ITEM, 4);
        cmdTime = new Command("Time", Command.ITEM, 5);
        cmdAdvert = new Command("Advertise", Command.ITEM, 3);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdSave);
        addCommand(cmdRefresh);
        addCommand(cmdStats);
        addCommand(cmdTime);
        addCommand(cmdAdvert);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void setBattInfo(String s) {
        battInfo.setText(s);
    }

    public void showInfo(String title, String text) {
        Alert a = new Alert(title, text, null, null);
        a.setTimeout(Alert.FOREVER);
        app.getDisplay().setCurrent(a, this);
    }

    public String getNodeName() {
        return tfNodeName.getString().trim();
    }

    public String getFreq() {
        return tfFreq.getString();
    }

    public String getBw() {
        return tfBw.getString();
    }

    public String getSf() {
        return tfSf.getString();
    }

    public String getCr() {
        return tfCr.getString();
    }

    public String getTxPwr() {
        return tfTxPwr.getString();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.showMainMenu();
            return;
        }
        if (c == cmdSave) {
            new Thread(new Runnable() {
                public void run() {
                    app.saveSettings();
                }
            }).start();
            return;
        }
        if (c == cmdRefresh) {
            new Thread(new Runnable() {
                public void run() {
                    app.sendGetBattery();
                }
            }).start();
            return;
        }
        if (c == cmdStats) {
            new Thread(new Runnable() {
                public void run() {
                    app.sendGetStats();
                }
            }).start();
            return;
        }
        if (c == cmdTime) {
            new Thread(new Runnable() {
                public void run() {
                    app.sendGetDeviceTime();
                }
            }).start();
            return;
        }
        if (c == cmdAdvert) {
            new Thread(new Runnable() {
                public void run() {
                    app.sendAdvert();
                }
            }).start();
        }
    }
}
