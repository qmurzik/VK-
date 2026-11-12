package re.sova.qmods;

import android.app.Activity;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * IDEA-0009: choose which sections VK's search shows (Игры, Клипы, Товары, Сервисы… can go). VK builds the search
 * tabs from the catalog answer (catalog.getSearch*), so the hidden sections are removed from that answer before VK
 * reads it (patch 0018, NetworkBridge.filterResponse). The titles VK actually sends are remembered, so the settings
 * list shows the real tabs of this account. The first section («Всё») is never removed, and a hidden default
 * section falls back to the first one that stays.
 */
public final class Qvk3SearchTabs {
    static final String SEEN = "qvk3_search_tabs_seen", HIDDEN = "qvk3_search_tabs_hidden";

    private Qvk3SearchTabs() {
    }

    private static SharedPreferences prefs() {
        return RuntimeBridge.prefs;
    }

    public static void filter(Object response, String method) {
        Qvk3CatalogAds.filter(response, method); // IDEA-0001: subscription offers in VK music
        try {
            if (method == null || !method.toLowerCase(java.util.Locale.ROOT).startsWith("catalog.getsearch") || Qvk3Kill.off("search_tabs")) return;
            if (prefs() == null || !(response instanceof JSONObject)) return;
            walk((JSONObject) response, 0);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.search.tabs", t);
        }
    }

    private static void walk(JSONObject o, int depth) throws Exception {
        if (depth > 4) return;
        JSONArray sections = o.optJSONArray("sections");
        if (sections != null && sections.length() > 1 && sections.optJSONObject(0) != null && sections.optJSONObject(0).has("title")) {
            apply(o, sections);
            return;
        }
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            Object v = o.opt(it.next());
            if (v instanceof JSONObject) walk((JSONObject) v, depth + 1);
        }
    }

    private static void apply(JSONObject owner, JSONArray sections) throws Exception {
        Set<String> seen = new LinkedHashSet<>(prefs().getStringSet(SEEN, new LinkedHashSet<>()));
        int before = seen.size();
        for (int i = 1; i < sections.length(); i++) {
            String t = sections.optJSONObject(i) == null ? "" : sections.optJSONObject(i).optString("title").trim();
            if (!t.isEmpty()) seen.add(t);
        }
        if (seen.size() != before && seen.size() < 60) prefs().edit().putStringSet(SEEN, seen).apply();
        Set<String> hidden = prefs().getStringSet(HIDDEN, null);
        if (hidden == null || hidden.isEmpty()) return;
        JSONArray kept = new JSONArray();
        kept.put(sections.get(0));
        for (int i = 1; i < sections.length(); i++) {
            JSONObject s = sections.optJSONObject(i);
            if (s == null || !hidden.contains(s.optString("title").trim())) kept.put(sections.get(i));
        }
        if (kept.length() == sections.length()) return;
        owner.put("sections", kept);
        String def = owner.optString("default_section");
        boolean defKept = def.isEmpty();
        for (int i = 0; i < kept.length() && !defKept; i++) defKept = def.equals(kept.optJSONObject(i) == null ? "" : kept.optJSONObject(i).optString("id"));
        if (!defKept && kept.optJSONObject(0) != null) owner.put("default_section", kept.optJSONObject(0).optString("id"));
    }

    /** Settings: tick the sections to keep. */
    static void settings(Activity a) {
        SharedPreferences p = prefs();
        if (p == null) return;
        List<String> titles = new ArrayList<>(p.getStringSet(SEEN, new LinkedHashSet<>()));
        if (titles.isEmpty()) {
            Qvk3Ui.dialog(a).setTitle("Разделы поиска ВК")
                    .setMessage("Откройте поиск ВК один раз и перейдите по его вкладкам — QVK запомнит, какие разделы есть у вашего аккаунта, и их можно будет скрыть здесь.")
                    .setPositiveButton("Понятно", null).show();
            return;
        }
        java.util.Collections.sort(titles);
        Set<String> hidden = new LinkedHashSet<>(p.getStringSet(HIDDEN, new LinkedHashSet<>()));
        boolean[] keep = new boolean[titles.size()];
        for (int i = 0; i < keep.length; i++) keep[i] = !hidden.contains(titles.get(i));
        Qvk3Ui.dialog(a).setTitle("Какие разделы показывать в поиске")
                .setMultiChoiceItems(titles.toArray(new String[0]), keep, (d, w, on) -> keep[w] = on)
                .setPositiveButton("Сохранить", (d, w) -> {
                    Set<String> h = new LinkedHashSet<>();
                    for (int i = 0; i < keep.length; i++) if (!keep[i]) h.add(titles.get(i));
                    Qvk3SettingsHistory.before(a, HIDDEN, "Разделы поиска: скрыто " + h.size());
                    p.edit().putStringSet(HIDDEN, h).apply();
                    Qvk3Ui.toast(a, h.isEmpty() ? "Показываются все разделы" : "Скрыто разделов: " + h.size() + ". Откройте поиск заново");
                })
                .setNeutralButton("Показать все", (d, w) -> p.edit().remove(HIDDEN).apply())
                .setNegativeButton("Отмена", null).show();
    }
}
