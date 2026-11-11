package re.sova.qmods;

import android.app.Activity;

/**
 * BUG-0024: full-screen media screens of VK (clips, video, stories, camera) run on their own, light-based activity theme.
 * The QVK theme engine painted the navigation bar of such windows with that theme's page colour — white under a black
 * clip. These windows keep the bars VK itself set for the player.
 */
public final class Qvk3MediaWindow {
    private Qvk3MediaWindow() {
    }

    public static boolean skip(Activity a) {
        return a != null && media(a.getClass().getName());
    }

    static boolean media(String n) {
        return n.contains(".clips.") || n.contains("Clip") || n.contains(".libvideo.") || n.contains("VideoPlayer")
                || n.contains(".stories.") || n.contains("Story") || n.contains(".camera.") || n.contains("Camera")
                || n.contains("Fullscreen") || n.contains("ImageViewer");
    }
}
