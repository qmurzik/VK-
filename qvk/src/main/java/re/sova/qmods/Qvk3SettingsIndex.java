package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Index of every QVK setting for the new settings centre (pure Java, unit-tested).
 *
 * Sources: the QVK feature catalog (assets/qmods_features.json — the same entries QVK 2.x shows, so values
 * and keys are untouched and no migration of stored values is needed), the screens of QVK's SettingsHub and
 * QVK 3.0 items. Everything is assigned to one of the 15 sections of the new centre.
 */
public final class Qvk3SettingsIndex {
    public static final String[] SECTIONS = {
            "Внешний вид", "Темы и персонализация", "Навигация и жесты", "Сообщения и чаты", "Конфиденциальность",
            "Музыка и плеер", "Видео и медиа", "Уведомления и Push", "Загрузки и хранилище", "Производительность и батарея",
            "Дополнительные функции", "Резервные копии и синхронизация", "Обновления QVK", "Диагностика", "О приложении"};
    public static final String[] SECTION_HINTS = {
            "Студия интерфейса, Material, iOS, шрифты, значок",
            "Стили QVK 3.0, конструктор, темы, фоны, пузыри",
            "Нижняя панель, боковое меню, стартовый экран, жесты",
            "Нечиталка, быстрые ответы, скрытые диалоги, PIN чатов",
            "Онлайн, статистика, блокировка приложения",
            "QVK Music, плеер, офлайн, тексты песен",
            "Видео, клипы, истории, GIF, фото",
            "Режим доставки, звук, диагностика Push",
            "Папки загрузок, кэш, место на устройстве",
            "Анимации, эффекты, фоновая работа",
            "Инструменты, стикеры Telegram, значки",
            "Экспорт и импорт настроек, серверная копия",
            "Сервер обновлений, зеркала, история версий",
            "Отчёты об ошибках, проверка функций",
            "Версия, лицензии, сообщество"};

    public static final int SWITCH = 0, ACTION = 1, SCREEN = 2;

    public static final class Item {
        public final String id;      // unique: feature key or "screen:<target>"
        public final String key;     // preference key for switches
        public final String title;
        public final String summary;
        public final int section;
        public final int type;
        public final String target;  // QModsActivity action / SettingsHub route / qvk3:* route
        public final boolean def;
        final String haystack;

        Item(String id, String key, String title, String summary, int section, int type, String target, boolean def) {
            this.id = id;
            this.key = key;
            this.title = title;
            this.summary = summary == null ? "" : summary;
            this.section = section;
            this.type = type;
            this.target = target;
            this.def = def;
            this.haystack = normal(title + " " + this.summary + " " + SECTIONS[section] + " " + (key == null ? "" : key));
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Map<String, Item> byId = new HashMap<>();

    public List<Item> all() {
        return Collections.unmodifiableList(items);
    }

    public Item get(String id) {
        return byId.get(id);
    }

    public List<Item> section(int s) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (i.section == s) out.add(i);
        return out;
    }

    void add(Item i) {
        if (byId.containsKey(i.id)) return;
        items.add(i);
        byId.put(i.id, i);
    }

    // ------------------------------------------------------------------ section mapping

    static final Map<String, Integer> KEY_SECTION = new HashMap<>();

    static {
        String[][] map = {
                {"0", "qvk_ios_style", "qvk_material3_style", "bottomDescription", "fullTime", "disable_nft_avatars", "disable_emoji_status", "changeIcon", "appLanguage", "qvk_system_emoji", "qvk_compact_dialogs", "qvk_compact_community_tabs", "qvk_compact_community_info", "core_christmas_garland", "use_old_read_indicator", "disable_reactions", "rounded_writebar"},
                {"1", "qvk_personalization", "qvk_monet_colors", "qvk_monet_options", "themes", "qvk_theme_catalog", "qvk_splash_options", "badges", "sova_badges_enabled", "qvk_ornament_catalog", "qvk_badge_application"},
                {"2", "launchPage", "transparentNavbar", "editDock", "forceShowDrawer", "drawer_ignore_navigation_level", "hide_bottom_navigation", "hide_drawer_dashboard", "editDrawerItems", "editSAItems", "editServiceTiles", "qvk_services_layout", "qvk_services_icon_style", "qvk_floating_navigation", "qvk_drawer_zone", "qvk_drawer_cards_options", "feed_double_tap_like", "qmods_lite_services", "qvk_hide_widgets_settings", "disableServicesShowcase", "disableSuperAppForYou", "disableSuperPromo"},
                {"4", "online_privacy", "online_privacy_bypass", "offline", "telemetry", "news_telemetry", "music_telemetry", "device_mask", "qvk_client_platform_settings", "enablePin", "setPin", "fingerUnlock", "vibrateOnTap", "timeout", "lock", "qvk_pattern_setup", "qvk_hide_vpn_detection", "screenshot_dnr", "multiaccount75", "qvk_online_status_viewer"},
                {"5", "qvk_disable_audio_ads"},
                {"6", "disableClipsAds", "disableVideoAds", "qvk_story_downloads", "qvk_story_download_action", "qvk_gif_names", "qvk_wall_media", "qvk_image_search", "qvk_video_links", "core_new_photo_editor_collage", "storyDNR", "disableStories", "storyAdblock"},
                {"7", "qvk_push_modes", "qvk_notification_sound", "qvk_notification_tools", "qvk_background_delivery", "disable_push_warning"},
                {"8", "sova_music_download_folder", "clearAudioCache"},
                {"9", "qvk_smooth_motion", "qvk_motion_options", "qvk_motion_blur_options", "short_share_timeout"},
                {"11", "export_server_auto", "export_server", "import_server", "export", "import", "qvk_settings_catalog"},
                {"12", "qmods_updates"},
                {"14", "qmods_announcements"},
                {"3", "voiceDownload", "fastReplies", "qvk_hidden_dialogs", "qvk_community_inbox", "qvk_chat_privacy", "qvk_text_tools_settings", "qvk_index59", "qvk_folders59", "tgstickers", "qvk_account_names"},
        };
        for (String[] row : map) {
            int s = Integer.parseInt(row[0]);
            for (int i = 1; i < row.length; i++) KEY_SECTION.put(row[i], s);
        }
    }

    /** Section for a catalog entry: explicit map first, then the catalog group. */
    static int sectionFor(String group, String key) {
        Integer s = KEY_SECTION.get(key);
        if (s != null) return s;
        switch (group) {
            case "Стили интерфейса":
                return 0;
            case "Сообщения":
                return 3;
            case "Приватность":
            case "Блокировка":
                return 4;
            case "Музыка":
                return 5;
            case "Лента":
                return 0;
            case "Интерфейс":
                return 0;
            case "Сеть":
                return 10;
            default:
                return 10;
        }
    }

    /** SettingsHub group → section, for screens that are not catalog entries. */
    static int sectionForHub(String group, String target) {
        switch (target) {
            case "original:themes":
            case "themeCatalog":
            case "personal":
            case "original:badges":
                return 1;
            case "dock":
            case "all:appearance":
                return 2;
            case "original:pushes":
            case "pushModes":
            case "notificationSound":
                return 7;
            case "original:tools":
            case "original:other":
                return 11;
            case "appDiagnostics":
            case "original:debug":
            case "appearanceRecovery":
                return 13;
            case "qmodsInbox":
                return 12;
            case "original:about":
            case "builtinLicenses":
                return 14;
            case "videoLinks":
            case "storyDownloads":
                return 6;
            case "original:locker":
            case "all:privacy":
            case "anonymous":
            case "clientPlatform":
                return 4;
            default:
                break;
        }
        switch (group) {
            case "appearance":
                return 0;
            case "messages":
                return 3;
            case "media":
                return 5;
            case "privacy":
                return 4;
            case "network":
                return 7;
            case "about":
                return 14;
            case "feed":
                return 0;
            default:
                return 10;
        }
    }

    // ------------------------------------------------------------------ build

    /** Adds the QVK feature catalog (array of {name, items[]}). Unavailable entries are skipped. */
    public void addCatalog(JSONArray catalog) {
        for (int g = 0; g < catalog.length(); g++) {
            JSONObject group = catalog.optJSONObject(g);
            if (group == null) continue;
            String name = group.optString("name");
            JSONArray its = group.optJSONArray("items");
            if (its == null) continue;
            for (int i = 0; i < its.length(); i++) {
                JSONObject o = its.optJSONObject(i);
                if (o == null || "unavailable".equals(o.optString("status"))) continue;
                String key = o.optString("key");
                boolean sw = o.optBoolean("switch") || key.equals("enablePin");
                String summary = o.optString("summary", o.optString("detail"));
                if ("partial".equals(o.optString("status"))) summary = (summary.isEmpty() ? "" : summary + " ") + "(работает частично)";
                add(new Item(key, sw ? key : null, o.optString("title"), summary, sectionFor(name, key),
                        sw ? SWITCH : ACTION, sw ? null : o.optString("action", key), o.optBoolean("default")));
            }
        }
    }

    /** Adds a screen (SettingsHub route or a QVK 3.0 route). */
    public void addScreen(String target, String title, String summary, int section) {
        add(new Item("screen:" + target, null, title, summary, section, SCREEN, target, false));
    }

    public void addHubScreen(String group, String title, String summary, String target) {
        addScreen(target, title, summary, sectionForHub(group, target));
    }

    // ------------------------------------------------------------------ search

    public static String normal(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('ё', 'е');
        return n.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /**
     * Ranked search. Every query word must match (prefix of a word in title/summary/section/key);
     * title matches rank higher, so "нечит" finds the "Нечиталка" switches before related screens.
     */
    public List<Item> search(String query, int limit) {
        String q = normal(query);
        if (q.isEmpty()) return new ArrayList<>();
        String[] words = q.split(" ");
        LinkedHashMap<Item, Integer> scored = new LinkedHashMap<>();
        for (Item it : items) {
            int score = 0;
            boolean all = true;
            String title = normal(it.title);
            for (String w : words) {
                int s = wordScore(title, w) * 3;
                if (s == 0) s = wordScore(it.haystack, w);
                if (s == 0) {
                    all = false;
                    break;
                }
                score += s;
            }
            if (all) scored.put(it, score + (title.startsWith(q) ? 10 : 0));
        }
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(scored.entrySet());
        Collections.sort(list, (a, b) -> b.getValue() - a.getValue());
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < list.size() && i < limit; i++) out.add(list.get(i).getKey());
        return out;
    }

    private static int wordScore(String text, String w) {
        if (text.isEmpty()) return 0;
        if ((" " + text + " ").contains(" " + w + " ")) return 3;
        if ((" " + text).contains(" " + w)) return 2;
        return w.length() >= 3 && text.contains(w) ? 1 : 0;
    }
}
