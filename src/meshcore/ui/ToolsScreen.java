package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * Tools menu: trace path, noise floor, activity log, etc.
 */
public final class ToolsScreen extends List implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;

    private static final int IDX_TRACE = 0;
    private static final int IDX_TRACE_MAP = 1;
    private static final int IDX_NOISE = 2;
    private static final int IDX_ACTIVITY_LOG = 3;

    public ToolsScreen(AppController app, Displayable returnTo) {
        super("Tools", List.IMPLICIT);
        this.app = app;
        this.returnTo = returnTo;

        append("Trace Path \u2022 Manual", null);
        append("Trace Path \u2022 Using Map", null);
        append("Noise Floor", null);
        append("Activity Log", null);

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
                app.getDisplay().setCurrent(new TracePathSelectScreen(app, this));
            } else if (sel == IDX_TRACE_MAP) {
                app.getDisplay().setCurrent(new MapViewCanvas(app, this, true));
            } else if (sel == IDX_NOISE) {
                app.showNoiseFloorScreen(this);
            } else if (sel == IDX_ACTIVITY_LOG) {
                app.showActivityLogScreen(this);
            }
        }
    }
}

