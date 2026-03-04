package meshcore.ui;

import javax.microedition.lcdui.Display;

import meshcore.util.TextUtils;

public final class SettingsPresenter {

    private SettingsPresenter() {}

    public static String handleBatteryUpdate(SettingsScreen screen, String info) {
        if (screen == null) return TextUtils.formatBatteryStatus(info);
        String status = TextUtils.formatBatteryStatus(info);
        screen.setBattInfo(info);
        return status;
    }

    public static void showStats(SettingsScreen screen, String title, String content) {
        if (screen == null) return;
        screen.showInfo(title, content);
    }

    public static void showDeviceTime(SettingsScreen screen, String content) {
        if (screen == null) return;
        screen.showInfo("Device Time", content);
    }
}

