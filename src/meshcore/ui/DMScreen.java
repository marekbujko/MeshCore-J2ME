package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import meshcore.util.HistoryStore;

/**
 * Direct message screen with a contact, rendered as bubbles on a Canvas.
 */
public class DMScreen extends AbstractChatCanvas {

    private final int contactIdx;
    private final String contactName;
    private final Command cmdResetPath;

    public DMScreen(AppController app, int contactIdx, String contactName, StringBuffer dmBuf) {
        super(app, buildTitle(contactName, app.getContactPathHops(contactIdx)), dmBuf);
        this.contactIdx = contactIdx;
        this.contactName = contactName;
        cmdResetPath = new Command("Reset path", Command.SCREEN, 4);
        addCommand(cmdResetPath);
    }

    private static String buildTitle(String name, int hops) {
        String suffix = (hops < 0 || hops == 0) ? " (direct)" : " (" + hops + " hops)";
        return "DM: " + name + suffix;
    }

    protected void showNotify() {
        try {
            super.showNotify();
            refreshTitle();
        } catch (Throwable t) {
            // Nokia S40 can throw InterruptedException when opening options menu
        }
    }

    protected void hideNotify() {
        try {
            super.hideNotify();
        } catch (Throwable t) {
            // Nokia S40 can throw InterruptedException when opening options
        }
    }

    /** Refresh title with current path (e.g. when contacts sync completes). */
    public void refreshTitle() {
        try {
            setTitle(buildTitle(contactName, app.getContactPathHops(contactIdx)));
        } catch (Throwable t) {
            // Defensive: avoid crash on Nokia when opening options
        }
    }

    public int getContactIdx() {
        return contactIdx;
    }

    protected void onBack() {
        app.showContactsScreen();
    }

    protected void onSendMessage(final String msg) {
        if (contactIdx < 0) return;
        new Thread(new Runnable() {
            public void run() {
                app.sendDirectMessage(contactIdx, msg);
            }
        }).start();
    }

    protected void onClearHistoryConfirmed() {
        app.clearDmHistory(contactIdx);
    }

    protected boolean loadOlderHistoryBatch() {
        return HistoryStore.loadOlderDmIntoBuffer(contactIdx, buf);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdResetPath && d == this) {
            openResetPathConfirm();
            return;
        }
        super.commandAction(c, d);
    }

    private void openResetPathConfirm() {
        javax.microedition.lcdui.Alert a =
                Alerts.confirm("Reset path", "Reset routing path for this contact? Messages will use flood until a new path is learned.");
        a.addCommand(new Command("Yes", Command.OK, 1));
        a.addCommand(new Command("No", Command.BACK, 2));
        a.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    app.resetPath(contactIdx);
                }
                app.getDisplay().setCurrent(DMScreen.this);
            }
        });
        app.getDisplay().setCurrent(a, this);
    }
}

