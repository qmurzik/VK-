package com.vkmod.feat;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowManager;

import com.vkmod.core.Guard;
import com.vkmod.core.ModLog;
import com.vkmod.core.Registry;

import java.lang.ref.WeakReference;

/** Process-wide activity callbacks: window flags, text scale/density, app lock, bottom-bar tweaks. */
public final class Lifecycle implements Application.ActivityLifecycleCallbacks {
    private static int started;
    private static long backgroundAt;
    private static volatile boolean locked = true; // cold start requires unlocking
    private static WeakReference<Activity> top = new WeakReference<>(null);

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Lifecycle());
        app.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override public void onConfigurationChanged(Configuration c) {
                Activity a = top.get();
                if (a != null) applyConfig(a);
            }
            @Override public void onLowMemory() { }
            @Override public void onTrimMemory(int level) { }
        });
    }

    public static void unlock() { locked = false; }
    public static boolean isLocked() { return locked; }

    // ---- callbacks ----

    @Override public void onActivityPreCreated(Activity a, Bundle b) { Guard.run("text_scale", () -> applyConfig(a)); }

    @Override public void onActivityCreated(Activity a, Bundle b) {
        if (android.os.Build.VERSION.SDK_INT < 29) applyConfig(a);
        applyWindow(a);
    }

    @Override public void onActivityStarted(Activity a) {
        if (started++ == 0 && backgroundAt > 0) {
            long grace = Registry.num("app_lock_grace") * 1000L;
            if (SystemClock.elapsedRealtime() - backgroundAt > grace) locked = true;
        }
        checkLock(a);
    }

    @Override public void onActivityResumed(Activity a) {
        top = new WeakReference<>(a);
        applyWindow(a);
        checkLock(a);
        NavTweaks.schedule(a);
    }

    @Override public void onActivityPaused(Activity a) { }

    @Override public void onActivityStopped(Activity a) {
        if (--started <= 0) { started = 0; backgroundAt = SystemClock.elapsedRealtime(); }
    }

    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }

    // ---- features ----

    private static boolean animWarned;

    /** Animation speed: ValueAnimator's duration scale (hidden API, so strictly best-effort) and window animations. */
    private static void applyAnimations(Activity a) {
        if (!Registry.isOn("anim_speed")) return;
        int pct = Registry.num("anim_speed");
        try {
            java.lang.reflect.Method m = android.animation.ValueAnimator.class.getMethod("setDurationScale", float.class);
            m.invoke(null, pct / 100f);
        } catch (Throwable t) {
            if (!animWarned) { animWarned = true; ModLog.w("ValueAnimator.setDurationScale unavailable on this Android version", t); }
        }
        if (pct == 0) a.getWindow().setWindowAnimations(0);
    }

    private static void applyWindow(Activity a) {
        if (a instanceof LockActivity) return;
        Guard.run("anim_speed", () -> applyAnimations(a));
        Guard.run("allow_screenshots", () -> {
            if (Registry.isOn("allow_screenshots")) a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        });
        Guard.run("keep_screen_on", () -> {
            if (Registry.isOn("keep_screen_on")) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    private static boolean applied;

    /** Font scale and UI density, independent of the system settings. */
    private static void applyConfig(Activity a) {
        boolean scale = Registry.isOn("text_scale");
        boolean dens = Registry.isOn("ui_density");
        if (!scale && !dens && !applied) return;
        Resources r = a.getResources();
        Configuration sys = Resources.getSystem().getConfiguration();
        Configuration c = new Configuration(r.getConfiguration());
        float fs = scale ? Registry.num("text_scale") / 100f : sys.fontScale;
        int dpi = dens ? Math.round(sys.densityDpi * Registry.num("ui_density") / 100f) : sys.densityDpi;
        if (c.fontScale == fs && c.densityDpi == dpi) return;
        c.fontScale = fs;
        c.densityDpi = dpi;
        r.updateConfiguration(c, r.getDisplayMetrics());
        applied = scale || dens;
    }

    private static void checkLock(Activity a) {
        if (a instanceof LockActivity || !locked) return;
        if (!Guard.call("app_lock", false, () -> Registry.isOn("app_lock") && AppLock.hasPin())) { locked = false; return; }
        try {
            Intent i = new Intent(a, LockActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            a.startActivity(i);
        } catch (Throwable t) {
            Guard.fail("app_lock", t);
            ModLog.w("lock screen failed to start; lock disabled");
            locked = false;
        }
    }
}
