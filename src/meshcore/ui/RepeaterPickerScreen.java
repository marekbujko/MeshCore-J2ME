package meshcore.ui;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * Screen to pick a repeater to add as a path hop.
 * On select, adds that repeater's path byte to the path and returns to PathListScreen.
 */
public class RepeaterPickerScreen extends List implements CommandListener {

    public interface Listener {
        void onRepeaterPicked(byte pathByte);
    }

    private final AppController app;
    private final Listener listener;
    private final Displayable returnTo;
    private final Vector repeaterIndices = new Vector();
    private final Command cmdBack = new Command("Back", Command.BACK, 1);

    public RepeaterPickerScreen(AppController app, Listener listener, Displayable returnTo, String title) {
        super(title != null ? title : "Select Repeater", List.IMPLICIT);
        this.app = app;
        this.listener = listener;
        this.returnTo = returnTo;
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
                // keyHex is already formatted like "<b5919f3d...efb7de3a>"
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
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == List.SELECT_COMMAND && repeaterIndices.size() > 0) {
            int sel = getSelectedIndex();
            if (sel >= 0 && sel < repeaterIndices.size()) {
                int idx = ((Integer) repeaterIndices.elementAt(sel)).intValue();
                byte pathByte = app.getRepeaterPathByte(idx);
                if (listener != null) listener.onRepeaterPicked(pathByte);
            }
            app.getDisplay().setCurrent(returnTo);
        }
    }
}
