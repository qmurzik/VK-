package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote switch-off of misbehaving features («аварийное отключение»). The team lists a feature and the affected builds in
 * the signed update manifest; on the next update check QVK stores the list, and the feature's entry point asks
 * {@link #off(String)} before doing anything. The list comes only from a manifest whose signature was verified, it can
 * only turn features off (never on, never run code), and a later manifest without the entry turns the feature back on.
 */
final class Qvk3Kill {
    static final String PREF = "qvk3_kill_list";

    /** Features that can be switched off remotely and how they are called in the app. */
    static final String[][] FEATURES = {
            {"send_retry", "Автоповтор неотправленных сообщений"},
            {"splash_weather", "Погода на экране запуска"},
            {"catalog_ads", "Фильтр рекламы подписки в музыке ВК"},
            {"search_tabs", "Разделы поиска ВК"},
            {"feed_now", "Кнопка музыки в шапках ВК"},
            {"mini_on_vk", "Мини-плеер на экранах ВК"},
            {"music_takeover", "Треки из ленты и сообщений в QVK Music"},
            {"skin", "Правила интерфейса и плавающая панель"},
            {"shake_report", "Отчёт встряхиванием"},
            {"media_window", "Чёрные панели в клипах"},
            {"dock_quick", "Быстрый редактор нижней панели"},
            {"post_tint", "Перекраска значков постов"},
            {"settings_motion", "Анимации настроек"},
            {"shell", "Оболочка «Чаты» QVK"},
            {"update_prompt", "Окно «Доступно обновление»"},
            {"messenger", "Мессенджер QVK"},
    };

    private static volatile Map<String, String> cache;

    private Qvk3Kill() {
    }

    /** True when the team switched this feature off for the installed build. Cheap: an in-memory map after first use. */
    static boolean off(String feature) {
        Map<String, String> m = cache;
        if (m == null) {
            Context c = RuntimeBridge.context;
            if (c == null) return false;
            m = load(c);
        }
        return m.containsKey(feature);
    }

    static boolean off(Context c, String feature) {
        Map<String, String> m = cache;
        if (m == null) m = load(c);
        return m.containsKey(feature);
    }

    /** Active switch-offs: feature → reason. */
    static Map<String, String> active(Context c) {
        return Collections.unmodifiableMap(load(c));
    }

    static String title(String feature) {
        for (String[] f : FEATURES) if (f[0].equals(feature)) return f[1];
        return feature;
    }

    private static synchronized Map<String, String> load(Context c) {
        if (cache != null) return cache;
        Map<String, String> m = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(prefs(c).getString(PREF, "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                m.put(k, o.optString(k));
            }
        } catch (Exception ignored) {
        }
        cache = m;
        return m;
    }

    /** Stores the switch-offs of a verified manifest for this build (an empty list turns everything back on). */
    static void save(Context c, List<Qvk3UpdateManifest.Kill> kills) {
        JSONObject o = new JSONObject();
        Map<String, String> m = new LinkedHashMap<>();
        try {
            for (Qvk3UpdateManifest.Kill k : kills) {
                o.put(k.feature, k.reason);
                m.put(k.feature, k.reason);
            }
        } catch (Exception ignored) {
        }
        String before = prefs(c).getString(PREF, "{}");
        prefs(c).edit().putString(PREF, o.toString()).apply();
        cache = m;
        if (!before.equals(o.toString())) android.util.Log.w("QVK3Kill", "remote switch-offs for this build: " + (m.isEmpty() ? "none" : m.keySet()));
    }

    /** Features newly switched off since the last notice, as one line for a toast; null when nothing new. */
    static String takeNotice(Context c) {
        Map<String, String> m = load(c);
        java.util.Set<String> seen = new java.util.HashSet<>(java.util.Arrays.asList(prefs(c).getString(PREF + "_seen", "").split(",")));
        StringBuilder b = new StringBuilder();
        for (String k : m.keySet()) if (!seen.contains(k)) b.append(b.length() == 0 ? "" : ", ").append(title(k));
        prefs(c).edit().putString(PREF + "_seen", String.join(",", m.keySet())).apply();
        return b.length() == 0 ? null : "Команда QVK временно отключила: " + b + ". Подробности: Настройки QVK → О QVK 3.0";
    }

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    /** Text for diagnostics / «О QVK». */
    static String describe(Context c) {
        Map<String, String> m = load(c);
        if (m.isEmpty()) return "Аварийно отключённых функций нет.";
        StringBuilder b = new StringBuilder("Команда временно отключила в этой сборке:");
        for (Map.Entry<String, String> e : m.entrySet()) {
            b.append("\n• ").append(title(e.getKey()));
            if (!e.getValue().isEmpty()) b.append(" — ").append(e.getValue());
        }
        b.append("\n\nФункции вернутся сами, когда команда снимет отключение или выйдет исправленная сборка.");
        return b.toString();
    }

    static JSONArray featuresJson() {
        JSONArray a = new JSONArray();
        for (String[] f : FEATURES) a.put(f[0]);
        return a;
    }
}
