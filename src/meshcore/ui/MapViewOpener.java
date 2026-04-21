package meshcore.ui;

import javax.microedition.lcdui.Displayable;

/**
 * Indirection so {@link MeshCore} does not reference {@link MapViewCanvas} in its bytecode.
 * Some Series 40 / Asha VMs resolve fewer classes at startup; map code loads only when this runs.
 */
public final class MapViewOpener {

    private MapViewOpener() {}

    public static Displayable createMapView(AppController app, Displayable returnTo) {
        return new MapViewCanvas(app, returnTo);
    }

    public static Displayable createMapViewForContact(
            AppController app,
            Displayable returnTo,
            int contactIdx,
            String contactName) {
        return new MapViewCanvas(app, returnTo, contactIdx, contactName);
    }

    public static Displayable createMapViewTracePath(
            AppController app,
            Displayable returnTo,
            byte[] forwardPath,
            byte[] pathSnrs,
            int finalSnr4,
            String destLabel) {
        return new MapViewCanvas(app, returnTo, forwardPath, pathSnrs, finalSnr4, destLabel);
    }
}
