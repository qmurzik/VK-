package re.sova.qmods;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Behaviour profiles ("сценарии"). A scenario is a named set of preference values (QVK 2.x feature keys and
 * QVK 3.0 keys) plus an optional visual style. Applying one first snapshots the affected keys, so
 * "Вернуть как было" restores the exact previous state. Built-in scenarios never touch notification
 * settings or anything security related; the user can create, edit and delete their own.
 */
public final class Qvk3Scenarios {
    static final String KEY = "qvk3_scenarios";
    static final String ACTIVE = "qvk3_scenario_active";
    static final String UNDO = "qvk3_scenario_undo";

    public static final class Scenario {
        public final String id, title, description, style;
        public final Map<String, Object> values;
        public final boolean builtin;

        Scenario(String id, String title, String description, String style, Map<String, Object> values, boolean builtin) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.style = style;
            this.values = values;
            this.builtin = builtin;
        }
    }

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    public static List<Scenario> builtins() {
        List<Scenario> l = new ArrayList<>();
        l.add(new Scenario("normal", "Обычный", "Стандартный интерфейс QVK, лента и анимации как обычно", "classic",
                m("disableStories", false, "disableRecommendedPosts", false, "disableClipsBlock", false, "qvk_compact_dialogs", false,
                        "qvk_smooth_motion", true, Qvk3MusicSettings.LAYOUT, "modern"), true));
        l.add(new Scenario("work", "Работа", "Компактный интерфейс, без лишних анимаций и рекомендаций", "compact",
                m("qvk_compact_dialogs", true, "qvk_smooth_motion", false, "disableRecommendedPosts", true, "disableClipsBlock", true,
                        "disableSuperPromo", true), true));
        l.add(new Scenario("night", "Ночь", "AMOLED, мягкие цвета, тёмный плеер", "amoled",
                m(Qvk3MusicSettings.LAYOUT, "amoled"), true));
        l.add(new Scenario("music", "Музыка", "QVK Music с CoverFlow и полноэкранным плеером", "modern",
                m(Qvk3MusicSettings.LAYOUT, "coverflow", Qvk3MusicSettings.ENABLED, true, Qvk3MusicSettings.START_TAB, 1), true));
        l.add(new Scenario("focus", "Фокус", "Скрыты истории, клипы, рекомендации и промо — минимум шума", "modern",
                m("disableStories", true, "disableClipsBlock", true, "disableRecommendedPosts", true, "disableServicesShowcase", true,
                        "disableSuperPromo", true, "disableSuperAppForYou", true, "qvk_smooth_motion", false), true));
        return l;
    }

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    public static List<Scenario> all(Context c) {
        List<Scenario> l = builtins();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Map<String, Object> v = new LinkedHashMap<>();
                JSONObject vals = o.optJSONObject("values");
                if (vals != null) {
                    Iterator<String> it = vals.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        v.put(k, vals.get(k));
                    }
                }
                l.add(new Scenario(o.getString("id"), o.getString("title"), o.optString("description"), o.optString("style", ""), v, false));
            }
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.scenarios.read", e);
        }
        return l;
    }

    public static String active(Context c) {
        return prefs(c).getString(ACTIVE, "");
    }

    public static Scenario find(Context c, String id) {
        for (Scenario s : all(c)) if (s.id.equals(id)) return s;
        return null;
    }

    /** Applies a scenario: snapshot → values → style (the style recreates screens). */
    public static void apply(Activity a, Scenario s) throws Exception {
        SharedPreferences p = prefs(a);
        JSONObject undo = new JSONObject();
        Map<String, ?> all = p.getAll();
        for (String k : s.values.keySet()) undo.put(k, all.containsKey(k) ? all.get(k) : JSONObject.NULL);
        undo.put("__active", p.getString(ACTIVE, ""));
        SharedPreferences.Editor e = p.edit();
        for (Map.Entry<String, Object> en : s.values.entrySet()) put(e, en.getKey(), en.getValue());
        e.putString(UNDO, undo.toString()).putString(ACTIVE, s.id).commit();
        Qvk3SettingsHistory.record(a, ACTIVE, "Сценарий: " + s.title);
        if (!s.style.isEmpty() && Qvk3DesignTokens.PRESETS.containsKey(s.style)) {
            Qvk3Appearance.apply(a, Qvk3DesignTokens.PRESETS.get(s.style));
        } else {
            ThemePalette.refresh(a);
        }
    }

    public static boolean canUndo(Context c) {
        return prefs(c).contains(UNDO);
    }

    public static void undo(Activity a) throws Exception {
        SharedPreferences p = prefs(a);
        JSONObject u = new JSONObject(p.getString(UNDO, "{}"));
        SharedPreferences.Editor e = p.edit();
        Iterator<String> it = u.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.equals("__active")) continue;
            Object v = u.get(k);
            if (v == JSONObject.NULL) e.remove(k);
            else put(e, k, v);
        }
        e.putString(ACTIVE, u.optString("__active")).remove(UNDO).commit();
        if (Qvk3Appearance.canUndo(a)) Qvk3Appearance.undo(a);
        else ThemePalette.refresh(a);
    }

    static void put(SharedPreferences.Editor e, String k, Object v) {
        if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) e.putInt(k, (Integer) v);
        else if (v instanceof Long) e.putLong(k, (Long) v);
        else if (v instanceof Float || v instanceof Double) e.putFloat(k, ((Number) v).floatValue());
        else e.putString(k, String.valueOf(v));
    }

    /** Saves the current state of the given switch keys + current style as a user scenario. */
    public static void saveCurrent(Context c, String title, List<String> keys) throws Exception {
        SharedPreferences p = prefs(c);
        JSONObject vals = new JSONObject();
        Map<String, ?> all = p.getAll();
        for (String k : keys) if (all.containsKey(k)) vals.put(k, all.get(k));
        JSONObject o = new JSONObject().put("id", "user_" + System.currentTimeMillis()).put("title", title)
                .put("description", "Свой сценарий · " + vals.length() + " параметров")
                .put("style", p.getString(Qvk3Appearance.STYLE, "")).put("values", vals);
        JSONArray a = new JSONArray(p.getString(KEY, "[]"));
        a.put(o);
        p.edit().putString(KEY, a.toString()).apply();
    }

    public static void delete(Context c, String id) throws Exception {
        SharedPreferences p = prefs(c);
        JSONArray a = new JSONArray(p.getString(KEY, "[]")), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!id.equals(a.getJSONObject(i).optString("id"))) out.put(a.get(i));
        p.edit().putString(KEY, out.toString()).apply();
    }
}
