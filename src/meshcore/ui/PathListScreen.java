package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import meshcore.util.FrameUtils;

/**
 * Reusable screen that shows a contact's path as a list of repeaters.
 * Can be opened from DM chat or (in future) from the contact list.
 * Offers Back, Add hop, Remove last, Save, Reset Path.
 */
public class PathListScreen extends List implements CommandListener {

    private final AppController app;
    private final int contactIdx;
    private final Displayable returnTo;

    private final PathEditorModel pathModel = new PathEditorModel();
    private final byte[] originalPathBytes;
    private final int initialHops;
    private final Command cmdBack;
    private final Command cmdAddHop;
    private final Command cmdRemoveLast;
    private final Command cmdSave;
    private final Command cmdResetPath;
    private final Command cmdEditHex;

    public PathListScreen(AppController app, int contactIdx, String contactName, Displayable returnTo) {
        super("Path", List.IMPLICIT);
        this.app = app;
        this.contactIdx = contactIdx;
        this.returnTo = returnTo;
        this.initialHops = app.getContactPathHops(contactIdx);

        byte[] pathBytes = app.getContactPathBytes(contactIdx);
        if (pathBytes != null) {
            originalPathBytes = new byte[pathBytes.length];
            System.arraycopy(pathBytes, 0, originalPathBytes, 0, pathBytes.length);
            pathModel.setBytes(pathBytes);
        } else {
            originalPathBytes = new byte[0];
            pathModel.setBytes(new byte[0]);
        }

        cmdBack = new Command("Back", Command.BACK, 1);
        cmdAddHop = new Command("Add hop", Command.SCREEN, 2);
        // Remove the currently selected hop rather than always removing the last one.
        cmdRemoveLast = new Command("Remove hop", Command.SCREEN, 3);
        cmdEditHex = new Command("Enter Path Manually", Command.SCREEN, 4);
        cmdSave = new Command("Save", Command.OK, 5);
        cmdResetPath = new Command("Reset Path", Command.SCREEN, 6);
        addCommand(cmdBack);
        addCommand(cmdAddHop);
        addCommand(cmdRemoveLast);
        addCommand(cmdEditHex);
        addCommand(cmdSave);
        addCommand(cmdResetPath);
        setCommandListener(this);
        refreshList();
    }

    /** Called by RepeaterPickerScreen when user selects a repeater to add. */
    public void addHop(byte pathByte) {
        pathModel.addHop(pathByte);
        refreshList();
    }

    private void refreshList() {
        deleteAll();
        // First row: hex representation of path bytes for quick visual reference.
        String hex = PathHexCodec.formatBytesCsv(pathModel.toByteArray());
        if (hex.length() == 0) {
            append("Path: (none)", null);
        } else {
            append("Path: " + hex, null);
        }
        if (pathModel.size() == 0) {
            if (initialHops < 0) {
                setTitle("Path: Flood");
                append("Flood (no path yet)", null);
            } else {
                setTitle("Path: Direct");
                append("Direct (no repeaters)", null);
            }
        } else {
            setTitle(buildTitle(pathModel.size()));
            for (int i = 0; i < pathModel.size(); i++) {
                byte b = pathModel.get(i);
                String name = app.getRepeaterNameForPathByte(b);
                String label = (name != null && name.length() > 0)
                        ? ((i + 1) + ". " + name)
                        : ("Repeater " + (i + 1) + " (0x" + FrameUtils.bytesToHex(new byte[]{b}, 0, 1) + ")");
                append(label, null);
            }
        }
    }

    private static String buildTitle(int hops) {
        if (hops <= 0) {
            return "Path: Direct";
        }
        return "Path: " + hops + " hop" + (hops == 1 ? "" : "s");
    }

    private byte[] getEditablePathBytes() {
        return pathModel.toByteArray();
    }

    /** True if current editable path differs from original path bytes. */
    private boolean isModified() {
        if (pathModel.size() != originalPathBytes.length) return true;
        for (int i = 0; i < originalPathBytes.length; i++) {
            if (pathModel.get(i) != originalPathBytes[i]) return true;
        }
        return false;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            handleBack();
        } else if (c == cmdAddHop) {
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
        } else if (c == List.SELECT_COMMAND) {
            // Tapping the first row ("Path: ...") opens the hex editor with hint.
            int sel = getSelectedIndex();
            if (sel == 0) {
                openHexEditorWithHint();
            }
        } else if (c == cmdRemoveLast) {
            int sel = getSelectedIndex() - 1; // subtract header row
            if (sel >= 0 && sel < pathModel.size()) {
                pathModel.removeAt(sel);
                refreshList();
            }
        } else if (c == cmdEditHex) {
            openHexEditorWithHint();
        } else if (c == cmdSave) {
            app.updateContactPath(contactIdx, getEditablePathBytes());
            app.getDisplay().setCurrent(returnTo);
        } else if (c == cmdResetPath) {
            openResetPathConfirm();
        }
    }

    private void handleBack() {
        if (!isModified()) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        final PathListScreen self = this;
        javax.microedition.lcdui.Alert a = Alerts.confirm("Save Path",
                "Save changes to this Path before leaving?");
        a.addCommand(new Command("Yes", Command.OK, 1));
        a.addCommand(new Command("No", Command.BACK, 2));
        a.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    app.updateContactPath(contactIdx, getEditablePathBytes());
                }
                app.getDisplay().setCurrent(returnTo);
            }
        });
        app.getDisplay().setCurrent(a, self);
    }

    private void openHexEditorWithHint() {
        final PathListScreen self = this;
        javax.microedition.lcdui.Alert a =
                Alerts.confirm("Enter Path Manually",
                        "Enter first 2 characters from the start of each repeater's public key, for each hop.\nExample: 64, 6F, A1");
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
        final PathListScreen self = this;
        final TextBox tb = new TextBox("Enter Path Manually",
                PathHexCodec.formatBytesCsv(pathModel.toByteArray()),
                128, TextField.ANY);
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

    private void openResetPathConfirm() {
        javax.microedition.lcdui.Alert a = Alerts.confirm("Reset Path",
                "Reset routing path for this contact? Messages will use flood until a new path is learned.");
        a.addCommand(new Command("Yes", Command.OK, 1));
        a.addCommand(new Command("No", Command.BACK, 2));
        a.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    app.resetPath(contactIdx);
                }
                app.getDisplay().setCurrent(returnTo);
            }
        });
        app.getDisplay().setCurrent(a, returnTo);
    }
}
