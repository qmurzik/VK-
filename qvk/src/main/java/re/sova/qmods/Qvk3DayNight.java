package re.sova.qmods;

import android.app.Activity;
import android.content.SharedPreferences;

/**
 * Gives QVK 2.x settings that have a single value separate values for the light and the dark VK theme (BUG-0011:
 * colours of the like/repost/comment buttons were shared between themes). The 2.x code keeps reading its one key; this
 * class swaps that key's value whenever the theme changes, so the 2.x code needs no change.
 *
 * The first time the theme changes both themes start with the same value, so nothing visibly changes until the user
 * edits one of them. Each theme keeps its own edit from then on.
 */
final class Qvk3DayNight {
    /** Settings that get a value per theme. */
    static final String[] KEYS = {"qvk_post_button_colors"};
    private static final String ACTIVE = "qvk3_dn_active";

    private Qvk3DayNight() {
    }

    static void sync(Activity a) {
        try {
            SharedPreferences p = RuntimeBridge.prefs;
            if (p == null) return;
            // same choice as the palette itself (ThemeProfiles slot), else Android's night mode
            String slot = ThemeProfiles.slot(a);
            String mode = ThemeProfiles.NIGHT.equals(slot) ? "night" : ThemeProfiles.DAY.equals(slot) ? "day" : (Qvk3Ui.night(a) ? "night" : "day");
            String active = p.getString(ACTIVE, "");
            if (mode.equals(active)) return;
            SharedPreferences.Editor e = p.edit();
            if (!active.isEmpty()) {
                for (String k : KEYS) {
                    // keep what the user has set for the theme that is going away
                    if (p.contains(k)) e.putString("qvk3_dn_" + active + ":" + k, p.getString(k, "{}"));
                    else e.remove("qvk3_dn_" + active + ":" + k);
                }
                for (String k : KEYS) {
                    String own = "qvk3_dn_" + mode + ":" + k;
                    if (p.contains(own)) e.putString(k, p.getString(own, "{}"));
                }
            }
            e.putString(ACTIVE, mode).apply();
            // the style chosen for this theme brings its own shape (components, radius) — BUG-0016
            if (!active.isEmpty()) Qvk3Appearance.syncShape(p, "night".equals(mode));
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.daynight.sync", t);
        }
    }
}
