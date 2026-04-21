package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

/**
 * Manual trace path selector.
 * Builds a path by selecting repeaters; then starts TRACE (forward only).
 */
public final class TracePathSelectScreen extends List implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;

    private final PathEditorModel pathModel = new PathEditorModel();

    private final Command cmdBack;
    private final Command cmdAddHop;
    private final Command cmdClear;
    private final Command cmdRemoveLast;
    private final Command cmdAutoReturnPath;
    private final Command cmdEditHex;
    private final Command cmdSamePath;

    public TracePathSelectScreen(
            AppController app,
            Displayable returnTo
    ) {
        super("Trace Path", List.IMPLICIT);
        this.app = app;
        this.returnTo = returnTo;

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdSamePath = new Command("Run Trace", Command.SCREEN, 1);
        cmdAddHop = new Command("Add Repeater", Command.SCREEN, 2);
        cmdRemoveLast = new Command("Remove Repeater", Command.SCREEN, 3);
        cmdAutoReturnPath = new Command("Auto Return Path", Command.SCREEN, 4);
        cmdEditHex = new Command("Enter Path Manually", Command.SCREEN, 5);
        cmdClear = new Command("Reset Path", Command.SCREEN, 6);

        addCommand(cmdSamePath);

        addCommand(cmdBack);
        addCommand(cmdAddHop);
        addCommand(cmdRemoveLast);
        addCommand(cmdAutoReturnPath);
        addCommand(cmdEditHex);
        addCommand(cmdClear);

        setCommandListener(this);
        refreshList();
    }

    private void refreshList() {
        deleteAll();

        byte[] pathBytes = getSelectedPathBytes();
        int hops = (pathBytes != null) ? pathBytes.length : 0;
        String csv = PathHexCodec.formatBytesCsv(pathBytes);

        String summary = "Trace Path: " + (hops == 0 ? "(none)" : csv);
        append(summary, null);

        if (pathModel.size() == 0) {
            append("(no repeaters selected)", null);
        } else {
            for (int i = 0; i < pathModel.size(); i++) {
                byte b = pathModel.get(i);
                String name = app.getRepeaterNameForPathByte(b);
                if (name == null || name.length() == 0) {
                    name = "Repeater " + (i + 1);
                }
                append((i + 1) + ". " + name, null);
            }
        }
        setTitle("Trace Path (" + hops + " hop" + (hops == 1 ? "" : "s") + ")");
    }

    private byte[] getSelectedPathBytes() {
        return pathModel.toByteArray();
    }

    /** Called by RepeaterPickerScreen when user selects a repeater to add. */
    public void addHop(byte pathByte) {
        pathModel.addHop(pathByte);
        refreshList();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdAddHop) {
            app.getDisplay().setCurrent(new RepeaterPickerScreen(
                    app,
                    new RepeaterPickerScreen.Listener() {
                        public void onRepeaterPicked(byte pathByte) {
                            addHop(pathByte);
                        }
                    },
                    this,
                    "Select Repeater"
            ));
            return;
        }
        if (c == cmdClear) {
            pathModel.clear();
            refreshList();
            return;
        }
        if (c == cmdRemoveLast) {
            int sel = getSelectedIndex() - 1; // summary row is first
            if (!pathModel.removeAt(sel)) {
                pathModel.removeLast();
            } else {
            }
            refreshList();
            return;
        }
        if (c == cmdAutoReturnPath) {
            int n = pathModel.size();
            if (n <= 1) {
                Alerts.warning(app.getDisplay(), this, "Trace Path",
                        "Need at least 2 repeaters for auto return.", 2000);
                return;
            }
            // Append reverse path excluding latest hop:
            // [A, B, C] -> [A, B, C, B, A]
            for (int i = n - 2; i >= 0; i--) {
                pathModel.addHop(pathModel.get(i));
            }
            refreshList();
            return;
        }

        if (c == List.SELECT_COMMAND) {
            int sel = getSelectedIndex();
            if (sel == 0) {
                openHexEditorWithHint();
            }
            return;
        }
        if (c == cmdEditHex) {
            openHexEditorWithHint();
            return;
        }

        if (c == cmdSamePath) {
            byte[] fwd = getSelectedPathBytes();
            if (fwd == null || fwd.length == 0) {
                Alerts.warning(app.getDisplay(), this, "Trace Path", "Select at least one repeater.", 2500);
                return;
            }
            // Only show one reply: run forward TRACE without a separate back stage.
            app.tracePathManual(fwd, this);
            return;
        }
    }

    private void openHexEditorWithHint() {
        final TracePathSelectScreen self = this;
        javax.microedition.lcdui.Alert a = Alerts.confirm(
                "Enter Path Manually",
                "Enter first 2 characters from the start of each repeater's public key, for each hop.\nExample: 64, 6F, A1"
        );
        a.addCommand(new Command("OK", Command.OK, 1));
        a.addCommand(new Command("Cancel", Command.BACK, 2));
        a.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    openHexEditor();
                } else {
                    app.getDisplay().setCurrent(self);
                }
            }
        });
        app.getDisplay().setCurrent(a, this);
    }

    private void openHexEditor() {
        final TracePathSelectScreen self = this;
        final TextBox tb = new TextBox("Enter Path Manually",
                PathHexCodec.formatBytesCsv(getSelectedPathBytes()),
                128,
                TextField.ANY);
        Command cmdOk = new Command("Save", Command.OK, 1);
        Command cmdCancel = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(cmdOk);
        tb.addCommand(cmdCancel);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable disp) {
                if (cmd.getCommandType() == Command.OK) {
                    byte[] parsed = PathHexCodec.parseHexList(tb.getString());
                    if (parsed == null) {
                        Alerts.warning(app.getDisplay(), tb, "Invalid bytes",
                                "Use hex like 64, 6F (comma separated).", 2000);
                        return;
                    }
                    pathModel.setBytes(parsed);
                    refreshList();
                }
                app.getDisplay().setCurrent(self);
            }
        });
        app.getDisplay().setCurrent(tb);
    }
}

