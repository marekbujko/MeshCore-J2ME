package meshcore.ui;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import meshcore.util.FrameUtils;

/**
 * Manual trace path selector.
 * Builds a path by selecting repeaters; then starts TRACE (forward only).
 */
public final class TracePathSelectScreen extends List implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;

    private final Vector selectedPathBytes = new Vector(); // Byte

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
        cmdSamePath = new Command("Run Trace", Command.OK, 1);
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
        String csv = formatBytesCsv(pathBytes);

        String summary = "Trace Path: " + (hops == 0 ? "(none)" : csv);
        append(summary, null);

        if (selectedPathBytes.size() == 0) {
            append("(no repeaters selected)", null);
        } else {
            for (int i = 0; i < selectedPathBytes.size(); i++) {
                byte b = ((Byte) selectedPathBytes.elementAt(i)).byteValue();
                String name = app.getRepeaterNameForPathByte(b);
                if (name == null || name.length() == 0) {
                    name = "Repeater " + (i + 1);
                }
                append((i + 1) + ". " + name, null);
            }
        }
        setTitle("Trace Path (" + hops + " hop" + (hops == 1 ? "" : "s") + ")");
    }

    private static String formatBytesCsv(byte[] b) {
        if (b == null || b.length == 0) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(", ");
            int v = b[i] & 0xFF;
            String h = Integer.toHexString(v).toUpperCase();
            if (h.length() == 1) h = "0" + h;
            sb.append(h);
        }
        return sb.toString();
    }

    private byte[] getSelectedPathBytes() {
        int n = selectedPathBytes.size();
        if (n == 0) return new byte[0];
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = ((Byte) selectedPathBytes.elementAt(i)).byteValue();
        }
        return b;
    }

    /** Called by TraceRepeaterPickerScreen when user selects a repeater to add. */
    public void addHop(byte pathByte) {
        selectedPathBytes.addElement(new Byte(pathByte));
        refreshList();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdAddHop) {
            app.getDisplay().setCurrent(new TraceRepeaterPickerScreen(app, this));
            return;
        }
        if (c == cmdClear) {
            selectedPathBytes.removeAllElements();
            refreshList();
            return;
        }
        if (c == cmdRemoveLast) {
            int sel = getSelectedIndex() - 1; // summary row is first
            if (sel >= 0 && sel < selectedPathBytes.size()) {
                selectedPathBytes.removeElementAt(sel);
            } else {
                int n = selectedPathBytes.size();
                if (n > 0) selectedPathBytes.removeElementAt(n - 1);
            }
            refreshList();
            return;
        }
        if (c == cmdAutoReturnPath) {
            int n = selectedPathBytes.size();
            if (n <= 1) {
                Alerts.warning(app.getDisplay(), this, "Trace Path",
                        "Need at least 2 repeaters for auto return.", 2000);
                return;
            }
            // Append reverse path excluding latest hop:
            // [A, B, C] -> [A, B, C, B, A]
            for (int i = n - 2; i >= 0; i--) {
                selectedPathBytes.addElement(selectedPathBytes.elementAt(i));
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

    private String buildHexSummary() {
        byte[] b = getSelectedPathBytes();
        if (b == null || b.length == 0) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(", ");
            int v = b[i] & 0xFF;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase());
        }
        return sb.toString();
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
        final TextBox tb = new TextBox("Enter Path Manually", buildHexSummary(), 128, TextField.ANY);
        Command cmdOk = new Command("Save", Command.OK, 1);
        Command cmdCancel = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(cmdOk);
        tb.addCommand(cmdCancel);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable disp) {
                if (cmd.getCommandType() == Command.OK) {
                    byte[] parsed = parseHexList(tb.getString());
                    if (parsed == null) {
                        Alerts.warning(app.getDisplay(), tb, "Invalid bytes",
                                "Use hex like 64, 6F (comma separated).", 2000);
                        return;
                    }
                    selectedPathBytes.removeAllElements();
                    for (int i = 0; i < parsed.length; i++) {
                        selectedPathBytes.addElement(new Byte(parsed[i]));
                    }
                    refreshList();
                }
                app.getDisplay().setCurrent(self);
            }
        });
        app.getDisplay().setCurrent(tb);
    }

    private byte[] parseHexList(String text) {
        Vector out = new Vector();
        StringBuffer tok = new StringBuffer();
        int len = text != null ? text.length() : 0;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == ',' || c == ' ' || c == ';') {
                if (!processHexToken(tok, out)) return null;
                tok.setLength(0);
            } else {
                tok.append(c);
            }
        }
        if (!processHexToken(tok, out)) return null;
        int n = out.size();
        if (n > 64) n = 64;
        byte[] result = new byte[n];
        for (int i = 0; i < n; i++) {
            result[i] = ((Byte) out.elementAt(i)).byteValue();
        }
        return result;
    }

    private boolean processHexToken(StringBuffer tokBuf, Vector out) {
        if (tokBuf == null) return true;
        String tok = tokBuf.toString().trim();
        if (tok.length() == 0) return true;
        if (tok.startsWith("0x") || tok.startsWith("0X")) tok = tok.substring(2);
        if (tok.length() == 0 || tok.length() > 2) return false;
        for (int i = 0; i < tok.length(); i++) {
            char ch = tok.charAt(i);
            boolean hex = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
            if (!hex) return false;
        }
        int v;
        try {
            v = Integer.parseInt(tok, 16);
        } catch (NumberFormatException e) {
            return false;
        }
        out.addElement(new Byte((byte) v));
        return true;
    }
}

