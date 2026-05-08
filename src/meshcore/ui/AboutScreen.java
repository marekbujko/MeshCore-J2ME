package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;

import meshcore.util.ImageCache;

/**
 * About screen: app icon, name, description, version, platform, author (from JAD/manifest).
 */
public final class AboutScreen extends Form implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;
    private final Command cmdBack;

    public AboutScreen(AppController app, Displayable returnTo) {
        super("About");
        this.app = app;
        this.returnTo = returnTo;

        try {
            Image appIcon = ImageCache.get("/icon.png");
            ImageItem iconItem = new ImageItem(null, appIcon, ImageItem.LAYOUT_LEFT, null);
            append(iconItem);
            append("\n");
        } catch (Exception e) {
        }

        String name = app.getAppProperty("MIDlet-Name");
        if (name == null) name = "MeshCore";
        String description = app.getAppProperty("MIDlet-Description");
        if (description == null || description.length() == 0) {
            description = "MeshCore Companion Radio client for J2ME phones.";
        }
        String version = app.getAppProperty("MIDlet-Version");
        if (version == null) version = "1.0";
        String vendor = app.getAppProperty("MIDlet-Vendor");
        if (vendor == null) vendor = "dobrishinov";

        append(name + "\n");
        append("Version " + version + "\n");
        append("Description: " + description + "\n\n");
        append("Platform: Java ME (MIDP 2.0)\n");
        if (vendor.length() > 0) {
            append("Author: " + vendor + "\n");
        }
        append("Github: https://github.com/dobrishinov/MeshCore-J2ME\n");

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
