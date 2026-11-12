package re.sova.qmods;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

/**
 * Starts the QVK shell on top of VK's main screen when the app is launched from the launcher.
 * VK's activity is created underneath as usual, so sign-in, pushes and VK's own initialisation are untouched;
 * when nobody is signed in the shell is not shown and VK's login appears normally.
 */
final class Qvk3Shell implements Application.ActivityLifecycleCallbacks {
    static volatile boolean skipOnce;
    private static boolean installed;

    static synchronized void install(Application app) {
        if (installed || Qvk3Kill.off(app, "shell")) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Qvk3Shell());
    }

    static boolean enabled(Activity a) {
        return Qvk3MusicSettings.prefs(a).getBoolean(Qvk3ShellActivity.ENABLED, false);
    }

    @Override
    public void onActivityCreated(Activity a, Bundle state) {
        try {
            Intent i = a.getIntent();
            if (state != null || i == null || !Intent.ACTION_MAIN.equals(i.getAction()) || !i.hasCategory(Intent.CATEGORY_LAUNCHER)) return;
            if (a.getClass().getName().startsWith("re.sova.qmods.")) return;
            if (skipOnce) {
                skipOnce = false;
                return;
            }
            if (!enabled(a) || RuntimeBridge.account() <= 0 || !RuntimeBridge.launchUnlocked()) return;
            a.startActivity(new Intent(a, Qvk3ShellActivity.class));
            a.overridePendingTransition(0, 0);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.shell.start", t);
        }
    }

    @Override
    public void onActivityStarted(Activity a) {
    }

    @Override
    public void onActivityResumed(Activity a) {
    }

    @Override
    public void onActivityPaused(Activity a) {
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
