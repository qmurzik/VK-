package re.sova.qmods;

import android.app.Activity;
import android.content.Context;
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

    /**
     * Called (patch 0007) at the start of VK's MusicFragmentNavigator.a(Context) — the single launcher used by
     * VK's own entries (services, profile "Музыка", side menu, links). Only the user's own music library is
     * redirected; podcasts/audiobooks, searches, specific catalog links and other people's music stay in VK.
     */
    public static boolean fromNavigator(Object navigator, Context ctx) {
        try {
            if (navigator == null || ctx == null || !Qvk3MusicSettings.enabled(ctx)) return false;
            if (navigator.getClass().getField("b").getBoolean(navigator)) return false; // podcasts / audiobooks
            Object builder = navigator.getClass().getField("a").get(navigator);
            android.os.Bundle args = (android.os.Bundle) builder.getClass().getField("W3").get(builder);
            if (args != null) {
                if (args.containsKey("query") || args.containsKey("key_url")) return false;
                Object owner = args.get("owner_id");
                if (owner != null) {
                    long id = ((Number) owner.getClass().getMethod("getValue").invoke(owner)).longValue();
                    if (id != RuntimeBridge.account()) return false;
                }
            }
            if (ctx instanceof Activity) return open((Activity) ctx);
            long now = SystemClock.elapsedRealtime();
            if (now - lastOpen < 700) return true;
            lastOpen = now;
            ctx.startActivity(new Intent(ctx, Qvk3MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.music.navigator", t);
            return false;
        }
    }

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
