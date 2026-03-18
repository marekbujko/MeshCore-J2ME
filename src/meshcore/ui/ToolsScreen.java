package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * Tools menu: entry point to various tools (e.g. trace path, noise floor).
 */
public final class ToolsScreen extends List implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;

    private static final int IDX_TRACE = 0;
    private static final int IDX_NOISE = 1;

    public ToolsScreen(AppController app, Displayable returnTo) {
        super("Tools", List.IMPLICIT);
        this.app = app;
        this.returnTo = returnTo;

        append("Trace Path \u2022 Manual", null);
        append("Noise Floor", null);

        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == List.SELECT_COMMAND) {
            int sel = getSelectedIndex();
            if (sel == IDX_TRACE) {
                Alerts.info(app.getDisplay(), this, "Trace Path", "Manual trace path is not implemented yet.");
            } else if (sel == IDX_NOISE) {
                app.showNoiseFloorScreen(this);
            }
        }
    }
}

