package re.sova.qmods;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Battery numbers for «Фоновая работа и батарея»: how often Android started QVK without a screen (push, jobs, music) and how
 * much processor time the QVK process used in the background and on screen, per day (last 7 days).
 *
 * Measured from inside the process with Process.getElapsedCpuTime() at foreground/background switches and on trim-memory
 * callbacks — no timers, no wakeups of its own. When Android kills QVK in the background the time since the last switch is
 * lost, so the background figure is a lower bound.
 */
public final class Qvk3Energy implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
    static final String KEY = "qvk3_energy";
    private static Qvk3Energy instance;
    private final Context app;
    private int started;
    private boolean everForeground;
    private long markCpu = android.os.Process.getElapsedCpuTime(), markWall = SystemClock.elapsedRealtime();

    private Qvk3Energy(Context app) {
        this.app = app;
    }

    static synchronized void install(Application a) {
        if (instance != null) return;
        instance = new Qvk3Energy(a);
        if (visible()) {
            // installed after the first screen was already shown (start-up is posted): count it
            instance.started = 1;
            instance.everForeground = true;
        }
        a.registerActivityLifecycleCallbacks(instance);
        a.registerComponentCallbacks(instance);
        // a process start with no screen within 5 s = Android woke QVK in the background
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!instance.everForeground && !visible()) instance.add("bgStarts", 1);
        }, 5000);
    }

    static boolean visible() {
        android.app.ActivityManager.RunningAppProcessInfo info = new android.app.ActivityManager.RunningAppProcessInfo();
        android.app.ActivityManager.getMyMemoryState(info);
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }

    static String day(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t));
    }

    private synchronized void add(String field, long v) {
        try {
            SharedPreferences p = Qvk3MusicSettings.prefs(app);
            JSONObject all = new JSONObject(p.getString(KEY, "{}"));
            String d = day(System.currentTimeMillis());
            JSONObject today = all.optJSONObject(d);
            if (today == null) today = new JSONObject();
            today.put(field, today.optLong(field) + v);
            all.put(d, today);
            // keep a week
            java.util.List<String> days = new java.util.ArrayList<>();
            java.util.Iterator<String> it = all.keys();
            while (it.hasNext()) days.add(it.next());
            java.util.Collections.sort(days);
            for (int i = 0; i < days.size() - 7; i++) all.remove(days.get(i));
            p.edit().putString(KEY, all.toString()).apply();
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.energy", e);
        }
    }

    /** Books the CPU and wall time since the last mark to the background or the foreground. */
    private void flush(boolean foreground) {
        long cpu = android.os.Process.getElapsedCpuTime(), wall = SystemClock.elapsedRealtime();
        long dCpu = Math.max(0, cpu - markCpu), dWall = Math.max(0, wall - markWall);
        markCpu = cpu;
        markWall = wall;
        if (dCpu > 0) add(foreground ? "fgCpuMs" : "bgCpuMs", dCpu);
        if (!foreground && dWall > 0) add("bgAliveMs", dWall);
    }

    @Override
    public void onActivityStarted(Activity a) {
        if (started++ == 0) {
            flush(false); // the time until now was background
            everForeground = true;
        }
    }

    @Override
    public void onActivityStopped(Activity a) {
        if (--started <= 0) {
            started = 0;
            flush(true);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        if (started == 0 && level >= TRIM_MEMORY_BACKGROUND) flush(false);
    }

    /** Human summary for today and the last 7 days. */
    static String summary(Context c) {
        try {
            if (instance != null) instance.flush(instance.started > 0);
            JSONObject all = new JSONObject(Qvk3MusicSettings.prefs(c).getString(KEY, "{}"));
            JSONObject t = all.optJSONObject(day(System.currentTimeMillis()));
            long starts = 0, bg = 0, fg = 0, alive = 0;
            java.util.Iterator<String> it = all.keys();
            int days = 0;
            while (it.hasNext()) {
                JSONObject d = all.optJSONObject(it.next());
                if (d == null) continue;
                days++;
                starts += d.optLong("bgStarts");
                bg += d.optLong("bgCpuMs");
                fg += d.optLong("fgCpuMs");
                alive += d.optLong("bgAliveMs");
            }
            StringBuilder s = new StringBuilder();
            if (t != null) {
                s.append("Сегодня: запусков в фоне — ").append(t.optLong("bgStarts")).append(", процессор в фоне — ").append(sec(t.optLong("bgCpuMs")))
                        .append(" (QVK жил в фоне ").append(min(t.optLong("bgAliveMs"))).append("), на экране — ").append(sec(t.optLong("fgCpuMs"))).append(".\n");
            } else s.append("Сегодня данных ещё нет.\n");
            if (days > 1) s.append("За ").append(days).append(" дн.: запусков в фоне — ").append(starts).append(", процессор в фоне — ").append(sec(bg))
                    .append(", в фоне ").append(min(alive)).append(", на экране — ").append(sec(fg)).append(".\n");
            s.append("Процессор в фоне — главное, что тратит батарею без экрана. Несколько секунд за день — норма; минуты — повод проверить доставку уведомлений и музыку. Оценка снизу: если Android закрыл QVK в фоне, последний отрезок не учтён.");
            return s.toString();
        } catch (Exception e) {
            return "Статистика недоступна: " + Qvk3Jobs.message(e);
        }
    }

    static String sec(long ms) {
        return ms < 60_000 ? String.format(Locale.US, "%.1f с", ms / 1000f) : (ms / 60_000) + " мин " + (ms / 1000 % 60) + " с";
    }

    static String min(long ms) {
        return ms < 3_600_000 ? (ms / 60_000) + " мин" : (ms / 3_600_000) + " ч " + (ms / 60_000 % 60) + " мин";
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
    }

    @Override
    public void onLowMemory() {
    }

    @Override
    public void onActivityCreated(Activity a, Bundle b) {
    }

    @Override
    public void onActivityResumed(Activity a) {
    }

    @Override
    public void onActivityPaused(Activity a) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity a, Bundle b) {
    }

    @Override
    public void onActivityDestroyed(Activity a) {
    }
}
