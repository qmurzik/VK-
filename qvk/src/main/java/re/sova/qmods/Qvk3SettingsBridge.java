package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;

/**
 * Glue between QVK 2.x settings code and the QVK 3.0 settings centre.
 *  - RuntimeBridge.open() (drawer, menus) calls {@link #openHub}: the new centre opens unless the user chose
 *    the classic screen ("qvk3_settings_classic").
 *  - Catalog actions belong to QModsActivity.action(); the centre starts QModsActivity with
 *    EXTRA_ACTION and a patched onCreate() calls {@link #afterClassicCreate}, which runs the action once
 *    the classic screen is laid out. Back from the opened screen returns to the classic list, then to the
 *    centre.
 */
public final class Qvk3SettingsBridge {
    static final String EXTRA_ACTION = "qvk3_action";
    static final String CLASSIC = "qvk3_settings_classic";

    public static boolean openHub(Activity a) {
        try {
            // IDEA-0033: the «open classic settings» switch is gone; an old «on» must not trap users in QVK 2's screen
            if (a == null) return false;
            a.startActivity(new Intent(a, Qvk3SettingsActivity.class));
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.settings.openHub", t);
            return false;
        }
    }

    static void runClassicAction(Activity from, String action) {
        from.startActivity(new Intent(from, QModsActivity.class).putExtra(EXTRA_ACTION, action));
    }

    public static void afterClassicCreate(QModsActivity a) {
        try {
            Intent i = a.getIntent();
            String action = i == null ? null : i.getStringExtra(EXTRA_ACTION);
            if (action == null || action.isEmpty()) return;
            i.removeExtra(EXTRA_ACTION); // run once, not again after rotation/recreation
            a.getWindow().getDecorView().post(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                try {
                    // QModsActivity.action(String) is private in QVK 2.6.18; reflection on the app's own class is allowed.
                    java.lang.reflect.Method m = QModsActivity.class.getDeclaredMethod("action", String.class);
                    m.setAccessible(true);
                    m.invoke(a, action);
                } catch (Throwable t) {
                    RuntimeFaults.record("qvk3.settings.classicAction", t);
                    Qvk3Ui.toast(a, "Действие недоступно: " + Qvk3Jobs.message(t));
                }
            });
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.settings.afterClassicCreate", t);
        }
    }
}
