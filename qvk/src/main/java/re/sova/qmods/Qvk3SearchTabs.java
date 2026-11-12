package re.sova.qmods;

import android.app.Activity;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * IDEA-0009 / BUG-0048: choose which tabs VK's search shows. VK 8.100 builds the tab row itself from the enum
 * DiscoverSearchTabs.SearchTabName (each tab then calls its own catalog.get*Search method), once per process, in
 * DiscoverSearchTabs#k. Patch 0025 passes that list through {@link #tabs}, which drops the tabs switched off here.
 * «Все» always stays. The list is built when the search screen is first opened, so a change applies after a restart.
 */
public final class Qvk3SearchTabs {
    static final String OFF = "qvk3_search_tabs_off";
    /** Legacy key of the alpha-era filter (catalog section titles) — cleared, it never matched VK 8.100's tabs. */
    static final String LEGACY = "qvk3_search_tabs_hidden", LEGACY_SEEN = "qvk3_search_tabs_seen";
    static final String[][] TABS = {
            {"SearchPeople", "Люди"},
            {"SearchGroups", "Сообщества"},
            {"SearchMusic", "Музыка"},
            {"SearchVideo", "Видео"},
            {"SearchClips", "Клипы"},
            {"SearchNews", "Новости"},
            {"SearchMiniApps", "Сервисы"},
            {"SearchGames", "Игры"},
            {"SearchGoods", "Товары"},
    };

    private Qvk3SearchTabs() {
    }

    private static SharedPreferences prefs() {
        SharedPreferences p = RuntimeBridge.prefs;
        if (p == null && RuntimeBridge.context != null) p = Qvk3MusicSettings.prefs(RuntimeBridge.context);
        return p;
    }

    /** NetworkBridge response hook (patch 0018): only the music subscription-offer filter remains here. */
    public static void filter(Object response, String method) {
        Qvk3CatalogAds.filter(response, method); // IDEA-0001: subscription offers in VK music
    }

    /** VK's search tab list (enum constants); returns it without the tabs switched off. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List tabs(List in) {
        try {
            if (in == null || Qvk3Kill.off("search_tabs")) return in;
            SharedPreferences p = prefs();
            Set<String> off = p == null ? null : p.getStringSet(OFF, null);
            if (off == null || off.isEmpty()) return in;
            List out = new ArrayList();
            for (Object t : in) {
                String n = t instanceof Enum ? ((Enum) t).name() : String.valueOf(t);
                if ("SearchAll".equals(n) || !off.contains(n)) out.add(t);
            }
            if (out.isEmpty()) return in;
            android.util.Log.i("QVK3", "search tabs: " + out.size() + " of " + in.size());
            return out;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.search.tabs", t);
            return in;
        }
    }

    static String title(String name) {
        for (String[] t : TABS) if (t[0].equals(name)) return t[1];
        return name;
    }

    /** Settings: switch the tabs on and off. */
    static void settings(Activity a) {
        SharedPreferences p = prefs();
        if (p == null) return;
        if (p.contains(LEGACY) || p.contains(LEGACY_SEEN)) p.edit().remove(LEGACY).remove(LEGACY_SEEN).apply();
        Set<String> off = new LinkedHashSet<>(p.getStringSet(OFF, new LinkedHashSet<>()));
        Qvk3Ui.Palette pal = Qvk3Sheet.palette(a);
        android.widget.LinearLayout col = new android.widget.LinearLayout(a);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 24);
        col.setPadding(pad, 0, pad, Qvk3Ui.dp(a, 8));
        android.widget.LinearLayout g = Qvk3Sheet.group(a, pal);
        for (String[] t : TABS) {
            g.addView(Qvk3Sheet.switchRow(a, pal, t[1], null, !off.contains(t[0]), on -> {
                Set<String> now = new LinkedHashSet<>(p.getStringSet(OFF, new LinkedHashSet<>()));
                if (on) now.remove(t[0]);
                else now.add(t[0]);
                Qvk3SettingsHistory.before(a, OFF, "Разделы поиска: скрыто " + now.size());
                p.edit().putStringSet(OFF, now).apply();
            }));
        }
        col.addView(g);
        col.addView(Qvk3Sheet.note(a, pal, "«Все» остаётся всегда. Изменения применятся после перезапуска ВК."));
        Qvk3Ui.dialog(a).setTitle("Разделы поиска").setView(col).setPositiveButton("Готово", null).show();
    }
}
