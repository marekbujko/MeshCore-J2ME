package meshcore.ui;

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
    private final StringItem nodeInfoItem;
    private final StringItem keyItem;
    private final StringItem battInfo;
    private final Command cmdSave;
    private final Command cmdRefresh;
    private final Command cmdStats;
    private final Command cmdTime;
    private final Command cmdMsgSettings;
    private final Command cmdBack;

    public SettingsScreen(AppController app, String nodeName, String firmwareVer,
            String nodePublicKeyHex, long nodeFreq, long nodeBw, int nodeSf, int nodeCr, int nodeTxPwr) {
        super("Settings");
        this.app = app;
        String nn = (nodeName != null ? nodeName : "");
        String fw = (firmwareVer != null ? firmwareVer : "");
        String nameInit = meshcore.util.TextUtils.sanitizeLabel(nn, 32);
        if (nameInit.length() == 0) nameInit = "Node0";
        nodeInfoItem = new StringItem("Node:", nn + " " + fw);
        keyItem = new StringItem("Public key:", formatPublicKey(nodePublicKeyHex));
        battInfo = new StringItem("Battery:", "Loading...");
        append(nodeInfoItem);
        append(keyItem);
        append(battInfo);

        // Expose message settings via menu command to keep layout consistent.
        cmdMsgSettings = new Command("Message settings", Command.SCREEN, 6);
        addCommand(cmdMsgSettings);

        tfNodeName = new TextField("Name:", nameInit, 32, TextField.ANY);
        append(tfNodeName);
        tfFreq = new TextField("Freq MHz:", formatFreqBw(nodeFreq), 12, TextField.ANY);
        append(tfFreq);
        tfBw = new TextField("BW kHz:", formatFreqBw(nodeBw), 12, TextField.ANY);
        append(tfBw);
        tfSf = new TextField("SF:", String.valueOf(nodeSf), 3, TextField.ANY);
        append(tfSf);
        tfCr = new TextField("CR:", String.valueOf(nodeCr), 3, TextField.ANY);
        append(tfCr);
        tfTxPwr = new TextField("TX dBm:", String.valueOf(nodeTxPwr), 3, TextField.ANY);
        append(tfTxPwr);
        cmdSave = new Command("Save", Command.OK, 1);
        cmdRefresh = new Command("Refresh", Command.SCREEN, 3);
        cmdStats = new Command("Stats", Command.SCREEN, 4);
        cmdTime = new Command("Time", Command.SCREEN, 5);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdSave);
        addCommand(cmdRefresh);
        addCommand(cmdStats);
        addCommand(cmdTime);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    private static String formatPublicKey(String hex) {
        String s = meshcore.util.TextUtils.formatPublicKeyShort(hex);
        return (s.length() > 0) ? s : "n/a";
    }

    private static String formatFreqBw(long raw) {
        if (raw < 0) raw = 0;
        long whole = raw / 1000;
        int frac = (int) (raw % 1000);
        String f = (frac < 10) ? "00" : (frac < 100) ? "0" : "";
        f += frac;
        while (f.length() > 1 && f.charAt(f.length() - 1) == '0') {
            f = f.substring(0, f.length() - 1);
        }
        String s = whole + "." + f;
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

    public void setNodeInfo(String nodeName, String firmwareVer) {
        String nn = (nodeName != null ? nodeName : "");
        String fw = (firmwareVer != null ? firmwareVer : "");
        nodeInfoItem.setText(nn + " " + fw);
    }

    public void setPublicKey(String hex) {
        keyItem.setText(formatPublicKey(hex));
    }

    public void setBattInfo(String s) {
        battInfo.setText(s);
    }

    public void showInfo(String title, String text) {
        Alerts.info(app.getDisplay(), this, title, text);
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
                    app.sendRefreshSettings();
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
        if (c == cmdMsgSettings) {
            app.showMessageSettingsScreen();
            return;
        }
    }
}
