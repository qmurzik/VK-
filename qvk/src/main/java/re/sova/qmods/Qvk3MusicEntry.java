package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Compatibility bridge between QVK's navigation and QVK Music.
 *
 * QVK routes every "open music" action (bottom bar, side menu, start page, settings shortcut, deep links
 * handled by RuntimeBridge.navigateChecked) through NativeRoutes.music(); a two-instruction smali patch
 * calls {@link #open} first. When QVK Music is enabled the VK music catalog is not created at all, which
 * also removes the QVK 2.6.x "Мои треки" redirect that sent users back to the home screen.
 */
public final class Qvk3MusicEntry {
    private static long lastOpen;

    /** Returns true when QVK Music handled the navigation. Never throws into VK code. */
    public static boolean open(Activity a) {
        try {
            if (a == null || !Qvk3MusicSettings.enabled(a)) return false;
            long now = SystemClock.elapsedRealtime();
            if (now - lastOpen < 700) return true; // swallow double taps / re-entrant routing
            lastOpen = now;
            if (a instanceof Qvk3MusicActivity) return true;
            a.startActivity(new Intent(a, Qvk3MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.music.entry", t);
            return false; // fall back to the classic VK route
        }
    }
}
