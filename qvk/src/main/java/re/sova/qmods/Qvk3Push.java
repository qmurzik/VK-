package re.sova.qmods;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Push diagnostics centre (QVK 3.0). Complements QVK 2.x's PushSupport report with checks of the device:
 * which Google services implementation is installed (Google Play services or microG), background limits,
 * standby bucket, Data Saver and notification permission. It never claims a server-side delivery test:
 * VK's servers can only be verified by a real incoming message.
 */
public final class Qvk3Push {
    public static final class Line {
        public final String title, value;
        /** 1 = ok, 0 = neutral/info, -1 = problem. */
        public final int level;

        Line(String title, String value, int level) {
            this.title = title;
            this.value = value;
            this.level = level;
        }
    }

    /** What provides com.google.android.gms on this device. */
    public static String gmsProvider(Context c) {
        PackageManager pm = c.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo("com.google.android.gms", 0);
            boolean microg = false;
            try {
                pm.getPackageInfo("org.microg.gms.droidguard", 0);
                microg = true;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            CharSequence label = pm.getApplicationLabel(pi.applicationInfo);
            if (!microg && label != null && label.toString().toLowerCase(java.util.Locale.ROOT).contains("microg")) microg = true;
            if (!microg) {
                try {
                    // microG ships its own settings activity inside the GmsCore package
                    microg = pm.getActivityInfo(new android.content.ComponentName("com.google.android.gms", "org.microg.gms.ui.SettingsActivity"), 0) != null;
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            return (microg ? "microG GmsCore " : "Google Play services ") + pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static List<Line> diagnostics(Context c) {
        List<Line> out = new ArrayList<>();
        String gms = gmsProvider(c);
        out.add(new Line("Сервисы Google", gms.isEmpty() ? "не установлены — FCM недоступен, нужен Long Poll/фоновая доставка QVK" : gms,
                gms.isEmpty() ? -1 : 0));
        if (gms.startsWith("microG")) {
            Boolean cm = Qvk3MicroG.cloudMessagingEnabled(c);
            String note = "FCM обслуживает microG, а не Google. Включите в microG «Cloud Messaging» и выполните "
                    + "регистрацию устройства; подмена подписи QVK не нужна — microG регистрирует токен под пакетом "
                    + "и сертификатом сборки. Кнопка «Проверить microG» ниже запрашивает реальный токен.";
            out.add(new Line("microG", (cm != null ? (cm ? "Cloud Messaging включён. " : "Cloud Messaging выключен — включите его. ") : "") + note,
                    cm != null && !cm ? -1 : 0));
        }
        String mode;
        try {
            mode = PushModes.mode();
        } catch (Throwable t) {
            mode = "?";
        }
        out.add(new Line("Выбранный транспорт QVK", mode + (PushModes.direct() ? " (без Firebase)" : ""), 0));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        boolean allowed = nm != null && nm.areNotificationsEnabled();
        out.add(new Line("Разрешение на уведомления", allowed ? "выдано" : "отключено в Android", allowed ? 1 : -1));
        PowerManager pm = c.getSystemService(PowerManager.class);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        out.add(new Line("Оптимизация батареи", ignoring ? "отключена для QVK" : "включена — фоновая доставка без FCM может задерживаться в режиме Doze", ignoring ? 1 : 0));
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am != null && Build.VERSION.SDK_INT >= 28) {
            boolean restricted = am.isBackgroundRestricted();
            out.add(new Line("Фоновые ограничения", restricted ? "QVK ограничен в фоне пользователем/системой" : "нет", restricted ? -1 : 1));
        }
        UsageStatsManager us = c.getSystemService(UsageStatsManager.class);
        if (us != null) {
            int b = us.getAppStandbyBucket();
            String name = b <= UsageStatsManager.STANDBY_BUCKET_ACTIVE ? "активное" : b <= UsageStatsManager.STANDBY_BUCKET_WORKING_SET ? "рабочий набор"
                    : b <= UsageStatsManager.STANDBY_BUCKET_FREQUENT ? "частое" : b <= UsageStatsManager.STANDBY_BUCKET_RARE ? "редкое" : "ограниченное";
            out.add(new Line("Группа ожидания приложения", name, b > UsageStatsManager.STANDBY_BUCKET_FREQUENT ? -1 : 0));
        }
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        if (cm != null) {
            int s = cm.getRestrictBackgroundStatus();
            out.add(new Line("Экономия трафика", s == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ? "включена — фоновые соединения ограничены" : "не ограничивает QVK",
                    s == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ? -1 : 1));
        }
        try {
            out.add(new Line("Отчёт доставки QVK", PushSupport.report(c), 0));
        } catch (Throwable t) {
            out.add(new Line("Отчёт доставки QVK", "недоступен: " + Qvk3Jobs.message(t), -1));
        }
        out.add(new Line("Что не проверяется", "Сервер ВК нельзя проверить без настоящего входящего сообщения. Проверка ниже показывает только, что Android разрешает уведомления QVK.", 0));
        return out;
    }
}
