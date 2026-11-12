package re.sova.qmods;

import android.app.Activity;
import android.view.View;
import android.view.Window;

/**
 * BUG-0024: while a clip plays, the QVK theme engine painted the system navigation bar with the page colour, which turned it
 * white under a black clip. Clips usually open as a fragment inside VK's main screen (not a separate window), so the clip feed
 * is recognised by its own controls (fullscreen_clip_overlay_like_container). While a clip is visible the bar stays black with
 * light buttons, as in stock VK; other full-screen media windows (video, stories, camera) keep the bars VK set.
 */
public final class Qvk3MediaWindow {
    private static int clipId = -1;

    private Qvk3MediaWindow() {
    }

    public static boolean skip(Activity a) {
        if (a == null || Qvk3Kill.off(a, "media_window")) return false;
        if (media(a.getClass().getName())) return true;
        if (!clipShown(a)) return false;
        darkBars(a.getWindow());
        return true;
    }

    static boolean clipShown(Activity a) {
        try {
            if (clipId == -1) clipId = a.getResources().getIdentifier("fullscreen_clip_overlay_like_container", "id", a.getPackageName());
            if (clipId == 0) return false;
            View v = a.findViewById(clipId);
            return v != null && v.isShown() && v.getGlobalVisibleRect(new android.graphics.Rect());
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    static void darkBars(Window w) {
        if (w.getNavigationBarColor() != 0xFF000000) w.setNavigationBarColor(0xFF000000);
        View d = w.getDecorView();
        int f = d.getSystemUiVisibility();
        if ((f & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0) d.setSystemUiVisibility(f & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (android.os.Build.VERSION.SDK_INT >= 30 && w.getInsetsController() != null)
            w.getInsetsController().setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }

    static boolean media(String n) {
        return n.contains(".clips.") || n.contains("Clip") || n.contains(".libvideo.") || n.contains("VideoPlayer")
                || n.contains(".stories.") || n.contains("Story") || n.contains(".camera.") || n.contains("Camera")
                || n.contains("Fullscreen") || n.contains("ImageViewer");
    }
}
