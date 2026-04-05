package meshcore.ui;

import javax.microedition.lcdui.Display;

import meshcore.util.TextUtils;

public final class SettingsPresenter {

    private SettingsPresenter() {}

    public static String handleBatteryUpdate(DeviceInfoScreen deviceInfo, String info) {
        String status = TextUtils.formatBatteryStatus(info);
        if (deviceInfo == null) {
            return status;
        }
        String[] parts = TextUtils.splitBatteryStorage(info);
        String bat = (parts[0] != null && parts[0].length() > 0) ? parts[0] : "n/a";
        String sto = (parts[1] != null && parts[1].length() > 0) ? parts[1] : "n/a";
        deviceInfo.setBatteryAndStorage(bat, sto);
        return status;
    }

}

