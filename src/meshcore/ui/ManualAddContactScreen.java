package meshcore.ui;

import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;

import meshcore.protocol.ProtocolConstants;

/**
 * Form to manually add a contact or repeater by name and public key.
 */
public final class ManualAddContactScreen extends Form implements CommandListener {

    private final AppController app;
    private final Displayable returnTo;

    private final TextField nameField;
    private final TextField keyField;
    private final ChoiceGroup typeGroup;

    private final Command cmdOk;
    private final Command cmdBack;

    public ManualAddContactScreen(AppController app, Displayable returnTo) {
        super("Add Contact");
        this.app = app;
        this.returnTo = returnTo;

        nameField = new TextField("Name", "", 64, TextField.ANY);
        keyField = new TextField("Public key (64 hex)", "", 64, TextField.ANY);
        String[] types = new String[]{
            "Contact (Companion)", // type=1
            "Repeater",            // type=2
            "Room Server",         // type=3
            "Sensor"               // type=4
        };
        typeGroup = new ChoiceGroup("Type", Choice.EXCLUSIVE, types, null);
        append(nameField);
        append(keyField);
        append(typeGroup);

        cmdOk = new Command("OK", Command.OK, 1);
        cmdBack = new Command("Back", Command.BACK, 2);
        addCommand(cmdOk);
        addCommand(cmdBack);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            app.getDisplay().setCurrent(returnTo);
            return;
        }
        if (c == cmdOk) {
            String name = nameField.getString().trim();
            String keyHex = keyField.getString().trim();
            if (name.length() == 0 || keyHex.length() == 0) {
                Alerts.info(app.getDisplay(), this, "Error", "Name and public key are required.");
                return;
            }
            if (keyHex.length() != 64) {
                Alerts.info(app.getDisplay(), this, "Error", "Public key must be 64 hex characters.");
                return;
            }
            int typeIdx = typeGroup.getSelectedIndex();
            int advType;
            switch (typeIdx) {
                case 1: advType = ProtocolConstants.ADV_TYPE_REPEATER; break;
                case 2: advType = ProtocolConstants.ADV_TYPE_ROOM; break;
                case 3: advType = ProtocolConstants.ADV_TYPE_SENSOR; break;
                default: advType = ProtocolConstants.ADV_TYPE_CHAT; break;
            }
            app.addManualContact(name, keyHex, advType);
            Alerts.info(app.getDisplay(), returnTo, "Add Contact", "Successfully added!");
        }
    }
}

