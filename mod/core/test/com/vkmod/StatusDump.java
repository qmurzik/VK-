package com.vkmod;

import com.vkmod.core.Feature;
import com.vkmod.core.Prefs;
import com.vkmod.core.Registry;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Prints the status table (markdown). Usage: StatusDump patch_report.json
 * A feature is ✅ only when its hook was applied AND the catalog does not mark it partial; ⚠ when partial; ❌ when a
 * required hook point was not found in this VK version.
 */
public class StatusDump {
    static final Map<String, String> MECH = new HashMap<>();
    static final Map<String, String> TESTED = new HashMap<>();

    static void m(String patch, String... ids) { for (String id : ids) MECH.put(id, patch); }

    public static void main(String[] a) throws Exception {
        Prefs.attach(new FakeStore());
        Registry.ensureLoaded();
        m("color_resolver", "theme_engine", "theme_amoled", "material_you", "theme_preset", "bars_alpha", "chat_bg", "bubble_style");
        m("okhttp", "ads_feed", "promo_communities", "ads_stories", "ads_clips", "banners", "feed_reco", "friends_suggest", "hide_stories",
                "hide_reposts", "keywords", "custom_blocks", "no_read", "no_typing", "offline", "block_trackers",
                "feed_clips", "feed_games", "feed_market", "feed_dzen", "feed_music", "feed_video", "feed_misc",
                "feed_friend_events", "feed_photo_tags", "feed_only_friends", "feed_only_groups", "feed_poll_posts", "custom_feed_types",
                "no_voice_listened", "no_listen_broadcast", "no_easter_eggs",
                "dl_photo", "dl_video", "dl_clips", "dl_stories", "dl_voice");
        m("inflater", "hide_ad_labels", "hide_view_ids", "no_haptics", "app_font", "select_text", "avatar_shape");
        m("exact_date", "exact_dates");
        String[] tested = {"feed_friend_events", "feed_photo_tags", "feed_only_friends", "feed_only_groups", "feed_poll_posts", "custom_feed_types",
                "no_voice_listened", "no_listen_broadcast", "no_easter_eggs", "theme_engine", "theme_amoled", "theme_preset", "theme_io", "bars_alpha", "chat_bg", "bubble_style",
                "ads_feed", "promo_communities", "ads_stories", "ads_clips", "banners", "feed_reco", "friends_suggest", "hide_stories",
                "hide_reposts", "keywords", "custom_blocks", "no_read", "no_typing", "offline", "block_trackers",
                "feed_clips", "feed_games", "feed_market", "feed_dzen", "feed_music", "feed_video", "feed_misc",
                "dl_photo", "dl_video", "dl_clips", "dl_stories", "dl_voice", "exact_dates", "backup", "reset_all"};
        Set<String> testedSet = new HashSet<>(java.util.Arrays.asList(tested));
        String json = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
        org.json.JSONArray arr = new org.json.JSONArray(json);
        Set<String> found = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) { org.json.JSONObject o = arr.getJSONObject(i); if (o.getString("status").equals("applied")) found.add(o.getString("id")); }
        StringBuilder sb = new StringBuilder();
        sb.append("| № | Функция | Категория | Статус | Механизм | Проверено | Примечание |\n|---|---|---|---|---|---|---|\n");
        int ok = 0, okStar = 0, part = 0, bad = 0;
        for (Feature f : Registry.all()) {
            String mech = MECH.get(f.id);
            String via = mech == null ? "ядро (жизненный цикл/экраны)" : "хук `" + mech + "`";
            String st; String note = "";
            String proof = testedSet.contains(f.id) ? "JVM-тесты + хук" : mech != null ? "хук + сборка" : "сборка";
            if (mech != null && !found.contains(mech)) { st = "❌"; note = "точка хука не найдена в этой версии VK"; if (!f.aux) bad++; }
            else if (f.status == Feature.Status.PARTIAL) { st = "⚠️"; note = f.partialNote; if (!f.aux) part++; }
            else if (testedSet.contains(f.id)) { st = "✅"; if (!f.aux) ok++; }
            else { st = "✅*"; if (!f.aux) okStar++; }
            if (f.aux) { st = "·"; note = "часть функции выше (в счёт не идёт)"; }
            sb.append("| ").append(f.number == 0 ? "доп." : String.valueOf(f.number)).append(" | ").append(f.title).append(" | ")
              .append(f.category.title).append(" | ").append(st).append(" | ").append(via).append(" | ").append(proof).append(" | ").append(note.replace("|", "/")).append(" |\n");
        }
        System.out.println("Итого: ✅ " + ok + " (подтверждены тестами) + ✅* " + okStar + " (хук найден, ядро вызывается, нужна проверка на устройстве) = "
                + (ok + okStar) + "; ⚠️ " + part + "; ❌ " + bad + "; всего функций: " + (ok + okStar + part + bad) + "\n");
        System.out.print(sb);
    }
}
