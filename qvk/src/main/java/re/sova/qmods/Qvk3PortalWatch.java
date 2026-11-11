package re.sova.qmods;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * Beta-portal glue that runs while VK screens are in front — nothing in the background:
 * - after a crash: «QVK закрылся с ошибкой. Отправить отчёт?» on the next VK screen, with the crash log attached;
 * - shake the phone on a VK screen → «Сообщить о проблеме» with a screenshot of that screen (accelerometer only while a VK
 *   screen is in front, unregistered on pause; can be turned off);
 * - portal notifications (status changes, team answers) as Android notifications, checked when VK comes to the front,
 *   at most once in 20 minutes and only when an account is linked (the portal has no push).
 */
public final class Qvk3PortalWatch implements Application.ActivityLifecycleCallbacks, SensorEventListener {
    public static final String SHAKE = "qvk3_shake_report";
    static final String CRASH_SEEN = "qvk3_crash_seen_at", POLL_AT = "qvk3_portal_poll_at", SHOWN = "qvk3_portal_shown";
    static final String CHANNEL = "qvk3_portal";
    static final long POLL_EVERY = 20 * 60_000L;

    private static Qvk3PortalWatch instance;
    private WeakReference<Activity> resumed = new WeakReference<>(null);
    private SensorManager sensors;
    private long lastShake, firstJolt;
    private boolean polling, crashAsked;

    static synchronized void install(Application app) {
        if (instance != null) return;
        instance = new Qvk3PortalWatch();
        app.registerActivityLifecycleCallbacks(instance);
    }

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    @Override
    public void onActivityResumed(Activity a) {
        if (!Qvk3MiniBar.vkScreen(a)) return;
        resumed = new WeakReference<>(a);
        SharedPreferences p = prefs(a);
        if (p.getBoolean(SHAKE, true)) {
            try {
                sensors = (SensorManager) a.getSystemService(Context.SENSOR_SERVICE);
                Sensor acc = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (acc != null) sensors.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.portal.shake", t);
            }
        }
        if (!crashAsked) {
            crashAsked = true;
            File crash = latestCrash(a);
            if (crash != null && crash.lastModified() > p.getLong(CRASH_SEEN, 0)
                    && System.currentTimeMillis() - crash.lastModified() < 3 * 86_400_000L) {
                p.edit().putLong(CRASH_SEEN, crash.lastModified()).apply();
                a.getWindow().getDecorView().postDelayed(() -> askCrash(a, crash), 1500);
            }
        }
        if (Qvk3Portal.linked(a) && !polling && System.currentTimeMillis() - p.getLong(POLL_AT, 0) > POLL_EVERY) {
            polling = true;
            p.edit().putLong(POLL_AT, System.currentTimeMillis()).apply();
            Context app = a.getApplicationContext();
            Qvk3Jobs.io(() -> {
                try {
                    notify(app, Qvk3Portal.notifications(app, true));
                } catch (Exception e) {
                    // offline or not linked any more: try again next time
                } finally {
                    polling = false;
                }
            });
        }
    }

    @Override
    public void onActivityPaused(Activity a) {
        if (resumed.get() != a) return;
        resumed = new WeakReference<>(null);
        if (sensors != null) {
            try {
                sensors.unregisterListener(this);
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ crash

    static File latestCrash(Context c) {
        File[] f = Qvk3Logs.dir(c).listFiles((d, n) -> n.startsWith("crash-"));
        if (f == null || f.length == 0) return null;
        java.util.Arrays.sort(f);
        return f[f.length - 1];
    }

    static void askCrash(Activity a, File crash) {
        if (a.isFinishing() || a.isDestroyed()) return;
        String text;
        try {
            text = new String(java.nio.file.Files.readAllBytes(crash.toPath()), "UTF-8");
        } catch (Exception e) {
            text = "";
        }
        final String t = text.length() > 4000 ? text.substring(0, 4000) : text;
        Qvk3Ui.dialog(a).setTitle("QVK закрылся с ошибкой")
                .setMessage("Отправьте отчёт команде — журнал с ошибкой уже приложен, допишите, что вы делали. Токены и пароли в журнале скрыты.")
                .setPositiveButton("Отправить отчёт", (d, w) -> Qvk3Report.open(a, null, t))
                .setNegativeButton("Не сейчас", null).show();
    }

    // ------------------------------------------------------------------ shake

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0] / SensorManager.GRAVITY_EARTH, y = e.values[1] / SensorManager.GRAVITY_EARTH, z = e.values[2] / SensorManager.GRAVITY_EARTH;
        double g = Math.sqrt(x * x + y * y + z * z);
        if (g < 2.6) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastShake < 4000) return;
        if (firstJolt == 0 || now - firstJolt > 900) {
            firstJolt = now; // two strong jolts within a second = a deliberate shake, not a bump
            return;
        }
        firstJolt = 0;
        lastShake = now;
        Activity a = resumed.get();
        if (a == null || a.isFinishing()) return;
        Bitmap shot = Qvk3Report.capture(a);
        a.getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        Qvk3Ui.dialog(a).setTitle("Сообщить о проблеме?")
                .setMessage("Снимок этого экрана и журнал приложения можно приложить к обращению на портале бета-теста.")
                .setPositiveButton("Да", (d, w) -> Qvk3Report.open(a, shot, null))
                .setNeutralButton("Не встряхивать", (d, w) -> {
                    prefs(a).edit().putBoolean(SHAKE, false).apply();
                    onActivityPaused(a);
                    Qvk3Ui.toast(a, "Включить снова: Настройки QVK → Диагностика");
                })
                .setNegativeButton("Нет", null).show();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ------------------------------------------------------------------ notifications

    static void notify(Context c, JSONArray items) {
        if (items.length() == 0) return;
        SharedPreferences p = prefs(c);
        Set<String> shown = new HashSet<>(p.getStringSet(SHOWN, new HashSet<>()));
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Портал бета-теста QVK", NotificationManager.IMPORTANCE_DEFAULT));
        for (int i = 0; i < items.length() && i < 10; i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            String id = String.valueOf(o.opt("id"));
            if (shown.contains(id)) continue;
            shown.add(id);
            String code = o.optString("ticket");
            Intent open = new Intent(c, Qvk3SettingsActivity.class).putExtra(Qvk3SettingsActivity.EXTRA_ROUTE, code.isEmpty() ? "myTickets" : "ticket:" + code)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(code.isEmpty() ? "Портал бета-теста QVK" : code).setContentText(o.optString("text"))
                    .setStyle(new Notification.BigTextStyle().bigText(o.optString("text"))).setAutoCancel(true).setContentIntent(pi).build();
            try {
                nm.notify("qvk3_portal", id.hashCode(), n);
            } catch (SecurityException ignored) {
                // notifications not allowed on Android 13+: the list is still in «Мои обращения»
            }
        }
        if (shown.size() > 200) shown.clear();
        p.edit().putStringSet(SHOWN, shown).apply();
    }

    @Override
    public void onActivityCreated(Activity a, Bundle b) {
    }

    @Override
    public void onActivityStarted(Activity a) {
    }

    @Override
    public void onActivityStopped(Activity a) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity a, Bundle b) {
    }

    @Override
    public void onActivityDestroyed(Activity a) {
    }
}
