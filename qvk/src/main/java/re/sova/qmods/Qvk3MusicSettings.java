package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings of QVK Music. Stored in the main QVK preference file (RuntimeBridge.prefs) under "qvk3_music_*"
 * keys so they are covered by QVK's existing backup/export, and old QVK player keys are read as defaults
 * (qvk_music_coverflow, qvk_lyrics_external, qvk_music_cover_radius) — that is the settings migration.
 */
public final class Qvk3MusicSettings {
    public static final String ENABLED = "qvk3_music_enabled";
    public static final String GAPLESS = "qvk3_music_gapless";
    public static final String CROSSFADE = "qvk3_music_crossfade";
    public static final String PAUSE_ON_UNPLUG = "qvk3_music_pause_unplug";
    public static final String RESUME_ON_PLUG = "qvk3_music_resume_plug";
    public static final String LAYOUT = "qvk3_music_layout";
    public static final String COVER_SIZE = "qvk3_music_cover_size";
    public static final String COVER_RADIUS = "qvk3_music_cover_radius";
    public static final String LYRICS_EXTERNAL = "qvk3_music_lyrics_external";
    public static final String LYRICS_SIZE = "qvk3_music_lyrics_size";
    public static final String LYRICS_OVER_COVER = "qvk3_music_lyrics_over_cover";
    public static final String START_TAB = "qvk3_music_start_tab";
    public static final String CACHE_LIMIT_MB = "qvk3_music_cache_mb";
    public static final String HISTORY = "qvk3_music_history";
    public static final String COVERFLOW_SPACING = "qvk3_music_coverflow_spacing";
    public static final String PROGRESS_STYLE = "qvk3_music_progress";

    public static final String[] LAYOUTS = {"modern", "classic", "amoled", "glass", "coverflow"};
    public static final String[] LAYOUT_TITLES = {"QVK Modern", "QVK Classic", "QVK AMOLED", "QVK Glass", "QVK CoverFlow"};

    static SharedPreferences prefs(Context c) {
        SharedPreferences p = RuntimeBridge.prefs;
        return p != null ? p : c.getSharedPreferences(c.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    /** QVK Music replaces the VK music section unless the user turned it off (classic fallback). */
    public static boolean enabled(Context c) {
        return prefs(c).getBoolean(ENABLED, true);
    }

    public static boolean gapless(Context c) {
        return prefs(c).getBoolean(GAPLESS, true);
    }

    public static int crossfadeSeconds(Context c) {
        return prefs(c).getInt(CROSSFADE, 0);
    }

    public static boolean pauseOnUnplug(Context c) {
        return prefs(c).getBoolean(PAUSE_ON_UNPLUG, true);
    }

    public static String layout(Context c) {
        SharedPreferences p = prefs(c);
        String def = p.getBoolean("qvk_music_coverflow", false) ? "coverflow" : "modern";
        String v = p.getString(LAYOUT, def);
        for (String l : LAYOUTS) if (l.equals(v)) return v;
        return "modern";
    }

    /** Cover size as a fraction of the screen width (0.5–1.0). */
    public static float coverSize(Context c) {
        return Math.max(50, Math.min(100, prefs(c).getInt(COVER_SIZE, 86))) / 100f;
    }

    public static int coverRadiusDp(Context c) {
        SharedPreferences p = prefs(c);
        return Math.max(0, Math.min(40, p.getInt(COVER_RADIUS, p.getInt("qvk_music_cover_radius", 16))));
    }

    public static boolean lyricsExternal(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(LYRICS_EXTERNAL, p.getBoolean("qvk_lyrics_external", false));
    }

    public static int lyricsSizeSp(Context c) {
        return Math.max(12, Math.min(32, prefs(c).getInt(LYRICS_SIZE, 18)));
    }

    public static boolean lyricsOverCover(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(LYRICS_OVER_COVER, p.getBoolean("qvk_lyrics_over_cover", false));
    }

    public static boolean history(Context c) {
        return prefs(c).getBoolean(HISTORY, true);
    }

    public static int cacheLimitMb(Context c) {
        return Math.max(50, Math.min(4096, prefs(c).getInt(CACHE_LIMIT_MB, 300)));
    }

    public static int coverflowSpacing(Context c) {
        return Math.max(30, Math.min(90, prefs(c).getInt(COVERFLOW_SPACING, 55)));
    }

    public static String progressStyle(Context c) {
        return prefs(c).getString(PROGRESS_STYLE, "line");
    }

    /** Resets one category of QVK Music settings to defaults (keys with the given prefixes). */
    public static void reset(Context c, String... keys) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (String k : keys) e.remove(k);
        e.apply();
    }
}
