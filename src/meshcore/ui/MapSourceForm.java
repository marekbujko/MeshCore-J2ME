package meshcore.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

import meshcore.util.MapViewStore;

public final class MapSourceForm extends Form implements CommandListener {

    private final AppController app;
    private final MapViewCanvas mapCanvas;
    private final TextField tfUrl;

    private final Command cmdOk;
    private final Command cmdCancel;
    private final Command cmdExampleSd;
    private final Command cmdExampleKade;
    private final Command cmdExampleGoogleM;
    private final Command cmdExampleGoogleS;
    private final Command cmdExampleGoogleY;
    private final Command cmdExampleGoogleP;
    private final Command cmdLightweightNoTiles;

    /** Google Maps vt base; lyrs: m roadmap, s satellite, y hybrid, p terrain, h roads, r roadmap alt, t terrain only. */
    private static final String GOOGLE_VT = "http://{s}.google.com/vt/lyrs=";

    public MapSourceForm(AppController app, MapViewCanvas mapCanvas) {
        super("Map Source");
        this.app = app;
        this.mapCanvas = mapCanvas;

        tfUrl = new TextField("Tiles Source Url Template", MapViewStore.loadTemplate(), 512, TextField.ANY);
        append(tfUrl);
        append(new StringItem(null,
                "Placeholders: #Z# #X# #Y# or {z}{x}{y} and {s}. Menu: Lightweight, External, Google."));

        cmdOk = new Command("OK", Command.OK, 1);
        cmdCancel = new Command("Cancel", Command.BACK, 2);
        cmdLightweightNoTiles = new Command("Lightweight - No Tiles", Command.ITEM, 3);
        cmdExampleSd = new Command("External: SD Card", Command.ITEM, 4);
        cmdExampleKade = new Command("External: BGMountains", Command.ITEM, 5);
        cmdExampleGoogleM = new Command("Google: Roadmap", Command.ITEM, 6);
        cmdExampleGoogleS = new Command("Google: Satellite", Command.ITEM, 7);
        cmdExampleGoogleY = new Command("Google: Hybrid", Command.ITEM, 8);
        cmdExampleGoogleP = new Command("Google: Terrain", Command.ITEM, 9);
        addCommand(cmdOk);
        addCommand(cmdCancel);
        addCommand(cmdLightweightNoTiles);
        addCommand(cmdExampleSd);
        addCommand(cmdExampleKade);
        addCommand(cmdExampleGoogleM);
        addCommand(cmdExampleGoogleS);
        addCommand(cmdExampleGoogleY);
        addCommand(cmdExampleGoogleP);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdCancel) {
            app.getDisplay().setCurrent(mapCanvas);
            return;
        }
        if (c == cmdExampleSd) {
            tfUrl.setString("file:///E:/maps/#Z#/#X#/#Y#.png");
            return;
        }
        if (c == cmdExampleKade) {
            tfUrl.setString("http://bgmtile.kade.si/#Z#/#X#/#Y#.png");
            return;
        }
        if (c == cmdExampleGoogleM) {
            tfUrl.setString(GOOGLE_VT + "m&x={x}&y={y}&z={z}");
            return;
        }
        if (c == cmdExampleGoogleS) {
            tfUrl.setString(GOOGLE_VT + "s&x={x}&y={y}&z={z}");
            return;
        }
        if (c == cmdExampleGoogleY) {
            tfUrl.setString(GOOGLE_VT + "y&x={x}&y={y}&z={z}");
            return;
        }
        if (c == cmdExampleGoogleP) {
            tfUrl.setString(GOOGLE_VT + "p&x={x}&y={y}&z={z}");
            return;
        }
        if (c == cmdLightweightNoTiles) {
            mapCanvas.setTemplate("");
            app.getDisplay().setCurrent(mapCanvas);
            return;
        }
        if (c == cmdOk) {
            String t = tfUrl.getString();
            if (t != null) {
                t = t.trim();
            } else {
                t = "";
            }
            mapCanvas.setTemplate(t);
            app.getDisplay().setCurrent(mapCanvas);
        }
    }
}
