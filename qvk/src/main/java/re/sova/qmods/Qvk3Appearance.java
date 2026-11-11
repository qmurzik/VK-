package re.sova.qmods;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Map;

/**
 * Applies {@link Qvk3DesignTokens} to VK 8.100 through QVK's existing appearance engine, and resets it.
 * Before every change the touched keys are backed up, so "Отменить" restores the previous look exactly.
 */
public final class Qvk3Appearance {
    public static final String STYLE = "qvk3_style";
    public static final String TOKENS = "qvk3_tokens";
    /**
     * QVK's existing "system" slot mode: ThemeProfiles picks the day or night palette by Android's night mode,
     * as in QVK 2.x. (alpha2–alpha15 resolved it by VK's internal theme flag instead — patch 0006. That flag changes
     * later than Android's and is flipped by the theme catalog, which mixed the two palettes: BUG-0014/0016.)
     * apply() switches VK's own light/dark theme to follow the system too, so both always agree.
     */
    public static final String MODE_VK = "system";

    static boolean vkDark(Context c) {
        try {
            return NativeTheme.dark(c);
        } catch (Throwable t) {
            return Qvk3Ui.night(c);
        }
    }
    static final String BACKUP = "qvk3_appearance_backup";
    static final String[] THEME_KEYS = {"qmods_theme_palette", "qvk_theme_profile_day", "qvk_theme_profile_night", "qvk_theme_profile_mode"};

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    /** Current tokens: the user's custom set, a chosen preset, or Classic. */
    public static Qvk3DesignTokens current(Context c) {
        SharedPreferences p = prefs(c);
        String id = p.getString(STYLE, "classic");
        Qvk3DesignTokens base = Qvk3DesignTokens.PRESETS.get(id);
        if (base == null) base = Qvk3DesignTokens.PRESETS.get("classic");
        if ("custom".equals(id)) {
            try {
                return Qvk3DesignTokens.fromJson(new JSONObject(p.getString(TOKENS, "{}")), base);
            } catch (Exception e) {
                return base;
            }
        }
        return base;
    }

    public static String currentTitle(Context c) {
        return prefs(c).contains(STYLE) ? current(c).title : "Стандартное (QVK 2.x)";
    }

    private static final String THEME_HASH = "qvk3_style_theme_hash";

    private static String themeHash(SharedPreferences p) {
        return Integer.toHexString((p.getString("qmods_theme_palette", "") + "|" + p.getString(ThemeProfiles.DAY, "") + "|" + p.getString(ThemeProfiles.NIGHT, "")).hashCode());
    }

    /** True when colours come from a theme the user made or installed, not from the last applied QVK 3.0 style. */
    public static boolean hasOwnTheme(Context c) {
        SharedPreferences p = prefs(c);
        boolean any = !p.getString("qmods_theme_palette", "").isEmpty() || !p.getString(ThemeProfiles.DAY, "").isEmpty() || !p.getString(ThemeProfiles.NIGHT, "").isEmpty();
        return any && !themeHash(p).equals(p.getString(THEME_HASH, ""));
    }

    public static boolean weakDevice(Context c) {
        return Qvk3Compat.lowRam(c) || Runtime.getRuntime().availableProcessors() <= 4
                || prefs(c).getBoolean("qvk3_perf_light", false);
    }

    /** Applies a style to the whole app (recreates visible screens). Throws if QVK rejects the theme. */
    public static void apply(Activity a, Qvk3DesignTokens t) throws Exception {
        apply(a, t, true);
    }

    /**
     * colors = false applies only the style's shape and component families (radius, iOS/Material parts) and keeps the
     * user's own QVK theme untouched — styles and themes no longer overwrite each other's colours behind the user's back.
     */
    public static void apply(Activity a, Qvk3DesignTokens t, boolean colors) throws Exception {
        SharedPreferences p = prefs(a);
        backup(p);
        SharedPreferences.Editor e = p.edit();
        for (Map.Entry<String, Boolean> f : t.componentFlags(weakDevice(a)).entrySet()) e.putBoolean(f.getKey(), f.getValue());
        int r = t.radius;
        e.putInt("qvk_ios_radius", Math.max(0, Math.min(28, r)));
        e.putInt("qvk_material3_radius", Math.max(12, Math.min(28, r)));
        e.putString(STYLE, t.id);
        if ("custom".equals(t.id)) e.putString(TOKENS, t.toJson().toString());
        if (!colors) {
            e.commit();
            ThemePalette.refresh(a);
            Qvk3SettingsHistory.record(a, STYLE, "Стиль оформления (без цветов): " + t.title);
            return;
        }
        // Both variants; ThemeProfiles picks one by the system light/dark mode, and VK's theme follows the system.
        String day = t.variant(false).toTheme().toString(), night = t.variant(true).toTheme().toString();
        e.putString(ThemeProfiles.MODE, MODE_VK);
        e.putString(ThemeProfiles.DAY, ThemePalette.validate(day));
        e.putString(ThemeProfiles.NIGHT, ThemePalette.validate(night));
        e.putString("qmods_theme_palette", ThemePalette.validate(Qvk3Ui.night(a) ? night : day));
        e.commit();
        p.edit().putString(THEME_HASH, themeHash(p)).apply();
        try {
            ThemeEditorActivity.setSystemMode(a);   // VK's own light/dark must match the palette slot
        } catch (Throwable x) {
            RuntimeFaults.record("qvk3.appearance.systemMode", x);
        }
        ThemePalette.refresh(a);
        Qvk3SettingsHistory.record(a, STYLE, "Стиль оформления: " + t.title);
    }

    private static void backup(SharedPreferences p) {
        try {
            JSONObject b = new JSONObject();
            for (String k : keys()) {
                Object v = p.getAll().get(k);
                b.put(k, v == null ? JSONObject.NULL : v);
            }
            p.edit().putString(BACKUP, b.toString()).apply();
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.appearance.backup", e);
        }
    }

    static java.util.List<String> keys() {
        java.util.List<String> k = new java.util.ArrayList<>(java.util.Arrays.asList(THEME_KEYS));
        k.addAll(Qvk3DesignTokens.PRESETS.get("classic").componentFlags(false).keySet());
        k.add("qvk_ios_radius");
        k.add("qvk_material3_radius");
        k.add(STYLE);
        k.add(TOKENS);
        return k;
    }

    public static boolean canUndo(Context c) {
        return prefs(c).contains(BACKUP);
    }

    /** Restores the look that was active before the last apply/reset. */
    public static void undo(Activity a) throws Exception {
        SharedPreferences p = prefs(a);
        JSONObject b = new JSONObject(p.getString(BACKUP, "{}"));
        SharedPreferences.Editor e = p.edit();
        java.util.Iterator<String> it = b.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = b.get(k);
            if (v == JSONObject.NULL) e.remove(k);
            else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) e.putInt(k, (Integer) v);
            else if (v instanceof Long) e.putLong(k, (Long) v);
            else e.putString(k, String.valueOf(v));
        }
        e.remove(BACKUP).commit();
        ThemePalette.refresh(a);
    }

    /** "Сбросить всё оформление": theme, component families, QVK 3.0 style. Other settings are untouched. */
    public static void reset(Activity a) {
        SharedPreferences p = prefs(a);
        backup(p);
        SharedPreferences.Editor e = p.edit();
        for (String k : keys()) e.remove(k);
        e.commit();
        try {
            IosAppearance.reset();
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.appearance.reset.ios", t);
        }
        try {
            MaterialAppearance.reset();
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.appearance.reset.material", t);
        }
        try {
            // QVK 2.x «Студия интерфейса» is no longer listed (one customization system): reset is the way back to its defaults
            ModernStudio.save(ModernStudio.defaults());
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.appearance.reset.studio", t);
        }
        Qvk3SettingsHistory.record(a, STYLE, "Оформление сброшено");
        ThemePalette.refresh(a);
    }
}
