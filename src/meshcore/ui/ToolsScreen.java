package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;

/**
 * Tools submenu (placeholder for future tools).
 */
public final class ToolsScreen extends Form implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;

    public ToolsScreen(AppController app, Displayable returnTo) {
        super("Tools");
        this.app = app;
        this.returnTo = returnTo;
        append("No tools yet.");
        cmdBack = new Command("Back", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
        }
    }
}
