package meshcore.ui;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * Repeater picker for Trace Path manual screen.
 * Select one repeater hop at a time, then return to TracePathSelectScreen.
 */
public final class TraceRepeaterPickerScreen extends List implements CommandListener {

    private final AppController app;
    private final TracePathSelectScreen traceScreen;
    private final Vector repeaterIndices = new Vector();
    private final Command cmdBack = new Command("Back", Command.BACK, 1);

    public TraceRepeaterPickerScreen(AppController app, TracePathSelectScreen traceScreen) {
        super("Select Repeater", List.IMPLICIT);
        this.app = app;
        this.traceScreen = traceScreen;

        Vector indices = app.getRepeaterIndices();
        for (int i = 0; i < indices.size(); i++) {
            int idx = ((Integer) indices.elementAt(i)).intValue();
            repeaterIndices.addElement(new Integer(idx));
            String name = app.getRepeaterNameForPathByte(app.getRepeaterPathByte(idx));
            if (name == null || name.length() == 0) {
                name = "Repeater";
            }
            String keyHex = app.getRepeaterPublicKeyHex(idx);
            if (keyHex != null && keyHex.length() > 0) {
                append(name + " " + keyHex, null);
            } else {
                append(name, null);
            }
        }
        if (repeaterIndices.size() == 0) {
            append("(no repeaters)", null);
        }

        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(traceScreen);
            return;
        }
        if (c == List.SELECT_COMMAND && repeaterIndices.size() > 0) {
            int sel = getSelectedIndex();
            if (sel >= 0 && sel < repeaterIndices.size()) {
                int idx = ((Integer) repeaterIndices.elementAt(sel)).intValue();
                byte pathByte = app.getRepeaterPathByte(idx);
                traceScreen.addHop(pathByte);
            }
            app.getDisplay().setCurrent(traceScreen);
        }
    }
}
