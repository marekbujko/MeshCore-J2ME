package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import meshcore.util.AppConstants;
import meshcore.util.HistoryStore;

/**
 * Direct message screen with a contact, rendered as bubbles on a Canvas.
 * Title rotates between contact name and hop count (like main menu).
 */
public class DMScreen extends AbstractChatCanvas {

    private final int contactIdx;
    private final String contactName;
    private final Displayable returnTo;
    private Command cmdResetPath;
    private Command cmdViewPath;
    private Thread titleRotationThread;
    private volatile boolean titleRotationStop;

    public DMScreen(AppController app, int contactIdx, String contactName, StringBuffer dmBuf, Displayable returnTo) {
        super(app, contactName, dmBuf);
        this.contactIdx = contactIdx;
        this.contactName = contactName;
        this.returnTo = returnTo;
        cmdViewPath = new Command("Set Path", Command.SCREEN, 4);
        cmdResetPath = new Command(buildResetPathLabel(app.getContactPathHops(contactIdx)), Command.SCREEN, 5);
        addCommand(cmdViewPath);
        addCommand(cmdResetPath);
    }

    protected String getEmptyStateSubtitle() {
        return "To send this contact a message, they must also add you as a contact.";
    }

    private static String buildHopLabel(int hops) {
        if (hops < 0) {
            return "Path: Flood";
        }
        return (hops == 0) ? "Path: Direct" : ("Path: " + hops + " hops");
    }

    private static String buildResetPathLabel(int hops) {
        String prefix;
        if (hops < 0) {
            prefix = "(Flood) ";
        } else if (hops == 0) {
            prefix = "(Direct) ";
        } else {
            prefix = "(" + hops + " hops) ";
        }
        return prefix + "Reset Path";
    }

    protected void showNotify() {
        try {
            super.showNotify();
            refreshTitle();
            startTitleRotation();
        } catch (Throwable t) {
            // Nokia S40 can throw InterruptedException when opening options menu
        }
    }

    protected void hideNotify() {
        try {
            stopTitleRotation();
            super.hideNotify();
        } catch (Throwable t) {
            // Nokia S40 can throw InterruptedException when opening options
        }
    }

    private void startTitleRotation() {
        stopTitleRotation();
        titleRotationStop = false;
        titleRotationThread = new Thread(new Runnable() {
            public void run() {
                int index = 0;
                while (!titleRotationStop) {
                    try {
                        Thread.sleep(AppConstants.TITLE_ROTATION_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (titleRotationStop) break;
                    index++;
                    final int idx = index;
                    try {
                        app.getDisplay().callSerially(new Runnable() {
                            public void run() {
                                if (titleRotationStop) return;
                                try {
                                    int hops = app.getContactPathHops(contactIdx);
                                    setTitle((idx % 2 == 0) ? contactName : buildHopLabel(hops));
                                } catch (Throwable ignore) {}
                            }
                        });
                    } catch (Throwable ignore) {}
                }
            }
        });
        titleRotationThread.start();
    }

    private void stopTitleRotation() {
        titleRotationStop = true;
        titleRotationThread = null;
    }

    /** Refresh title and Reset Path command (e.g. when contacts sync completes). */
    public void refreshTitle() {
        try {
            stopTitleRotation();
            setTitle(contactName);
            int hops = app.getContactPathHops(contactIdx);
            removeCommand(cmdResetPath);
            cmdResetPath = new Command(buildResetPathLabel(hops), Command.SCREEN, 4);
            addCommand(cmdResetPath);
            startTitleRotation();
        } catch (Throwable t) {
            // Nokia S40 can throw InterruptedException when opening options menu
        }
    }

    public int getContactIdx() {
        return contactIdx;
    }

    protected void onBack() {
        if (returnTo != null) {
            app.getDisplay().setCurrent(returnTo);
        } else {
            app.showContactsScreen();
        }
    }

    protected int getMaxMessageLength() {
        return AppConstants.DM_MSG_MAX_BYTES;
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
        if (c == cmdViewPath && d == this) {
            showHopsList();
            return;
        }
        super.commandAction(c, d);
    }

    private void showHopsList() {
        app.getDisplay().setCurrent(new PathListScreen(app, contactIdx, contactName, this));
    }

    private void openResetPathConfirm() {
        javax.microedition.lcdui.Alert a =
                Alerts.confirm("Reset Path", "Reset routing path for this contact? Messages will use flood until a new path is learned.");
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

