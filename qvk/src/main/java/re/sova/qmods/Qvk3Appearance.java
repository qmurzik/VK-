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
        return prefs(c).contains(STYLE) ? current(c).title : "Оформление QVK 2.x (без стиля 3.0)";
    }

    public static boolean weakDevice(Context c) {
        return Qvk3Compat.lowRam(c) || Runtime.getRuntime().availableProcessors() <= 4
                || prefs(c).getBoolean("qvk3_perf_light", false);
    }

    /** Applies a style to the whole app (recreates visible screens). Throws if QVK rejects the theme. */
    public static void apply(Activity a, Qvk3DesignTokens t) throws Exception {
        SharedPreferences p = prefs(a);
        backup(p);
        SharedPreferences.Editor e = p.edit();
        for (Map.Entry<String, Boolean> f : t.componentFlags(weakDevice(a)).entrySet()) e.putBoolean(f.getKey(), f.getValue());
        int r = t.radius;
        e.putInt("qvk_ios_radius", Math.max(0, Math.min(28, r)));
        e.putInt("qvk_material3_radius", Math.max(12, Math.min(28, r)));
        e.putString(STYLE, t.id);
        if ("custom".equals(t.id)) e.putString(TOKENS, t.toJson().toString());
        e.commit();
        ThemePalette.apply(a, t.toTheme().toString());
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
        Qvk3SettingsHistory.record(a, STYLE, "Оформление сброшено");
        ThemePalette.refresh(a);
    }
}
