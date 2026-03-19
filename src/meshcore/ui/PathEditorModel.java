package meshcore.ui;

import java.util.Vector;

/**
 * Lightweight mutable path model (Vector<Byte>) for J2ME screens.
 */
public final class PathEditorModel {

    private final Vector bytes = new Vector();

    public PathEditorModel() {}

    public PathEditorModel(byte[] initial) {
        setBytes(initial);
    }

    public void setBytes(byte[] data) {
        bytes.removeAllElements();
        if (data == null) return;
        for (int i = 0; i < data.length; i++) {
            bytes.addElement(new Byte(data[i]));
        }
    }

    public void clear() {
        bytes.removeAllElements();
    }

    public int size() {
        return bytes.size();
    }

    public byte get(int idx) {
        return ((Byte) bytes.elementAt(idx)).byteValue();
    }

    public Vector asVector() {
        return bytes;
    }

    public void addHop(byte pathByte) {
        bytes.addElement(new Byte(pathByte));
    }

    public boolean removeAt(int idx) {
        if (idx < 0 || idx >= bytes.size()) return false;
        bytes.removeElementAt(idx);
        return true;
    }

    public boolean removeLast() {
        int n = bytes.size();
        if (n <= 0) return false;
        bytes.removeElementAt(n - 1);
        return true;
    }

    public byte[] toByteArray() {
        int n = bytes.size();
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = ((Byte) bytes.elementAt(i)).byteValue();
        }
        return out;
    }
}
