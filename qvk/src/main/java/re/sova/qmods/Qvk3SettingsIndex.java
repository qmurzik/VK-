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
import java.util.Set;

/**
 * Index of every QVK setting for the new settings centre (pure Java, unit-tested).
 *
 * Sources: the QVK feature catalog (assets/qmods_features.json — the same entries QVK 2.x shows, so values
 * and keys are untouched and no migration of stored values is needed), the screens of QVK's SettingsHub and
 * QVK 3.0 items. Everything is assigned to one of the 15 sections of the new centre.
 */
public final class Qvk3SettingsIndex {
    public static final String[] SECTIONS = {
            "Оформление", "Навигация", "Сообщения", "Приватность и защита", "Музыка",
            "Лента и медиа", "Уведомления", "Система", "Обновления и о QVK"};
    public static final String[] SECTION_HINTS = {
            "Темы, цвета, фон чатов, значок, анимации",
            "Боковое меню, сервисы, стартовый экран",
            "Нечиталка, голосовые, стикеры, скрытые чаты",
            "Онлайн, статистика, PIN и блокировка",
            "QVK Music, плеер, офлайн, тексты песен",
            "Лента, истории, реклама, видео и фото",
            "Режим доставки, звук, диагностика Push",
            "Загрузки, место, сеть, батарея, копии, отладка",
            "Обновления, версия, лицензии"};
    /** Icon and tile colour of each section (index-aligned with SECTIONS). */
    public static final String[] SECTION_ICONS = {"palette", "nav", "chat", "lock", "music", "image", "bell", "sliders", "update"};
    public static final int[] SECTION_COLORS = {0xFFAF52DE, 0xFF5856D6, 0xFF34C759, 0xFF3A3A3C, 0xFFFF2D55, 0xFFFF9500, 0xFFFF3B30, 0xFF8E8E93, 0xFF007AFF};

    public static final int SWITCH = 0, ACTION = 1, SCREEN = 2;

    public static final class Item {
        public final String id;      // unique: feature key or "screen:<target>"
        public final String key;     // preference key for switches
        public final String title;
        public final String summary;
        public final int section;
        /** Card title inside the section ("Нечиталка", "Голосовые", …). */
        public final String group;
        public final int type;
        public final String target;  // QModsActivity action / SettingsHub route / qvk3:* route
        public final boolean def;
        final String haystack;

        Item(String id, String key, String title, String summary, int section, String group, int type, String target, boolean def) {
            this.id = id;
            this.group = group;
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

    /** Section "Система" (7) holds every screen that is still a QVK 2.x screen, so no other section links to old UI. */
    static final int CLASSIC_SECTION = 7;

    /** Targets edited natively in the QVK 3.0 centre (see Qvk3NativeEditors). */
    public static final Set<String> NATIVE = new java.util.HashSet<>(java.util.Arrays.asList("pushModes", "lock", "timeout", "folder", "deletedMark", "reset", "host"));

    static boolean oldScreen(int type, String target) {
        if (type == SWITCH || target == null) return false;
        if (target.startsWith("qvk3:")) return false;
        return !NATIVE.contains(target);
    }

    /**
     * QVK 2.x appearance screens replaced by QVK 3.0 «Стили и конструктор» (which writes the same iOS/Material/theme
     * keys) and by «Светлая и тёмная тема». They are no longer listed or searchable: one customization system.
     */
    static final Set<String> LEGACY_LOOK = new java.util.HashSet<>(java.util.Arrays.asList(
            "qvk_ios_style", "qvk_material3_style", "themes", "qvk_monet_options",
            "ios", "material3", "modern", "original:themes", "monet"));

    static boolean legacyLook(Item i) {
        return LEGACY_LOOK.contains(i.id) || (i.target != null && LEGACY_LOOK.contains(i.target))
                || (i.id.startsWith("screen:") && LEGACY_LOOK.contains(i.id.substring(7)));
    }

    void add(Item i) {
        if (byId.containsKey(i.id)) return;
        if (legacyLook(i)) return;
        // Older QVK 2.x screens stay in their real section: with patch 0010 (Qvk3Look) they share this centre's look.
        items.add(i);
        byId.put(i.id, i);
    }

    // ------------------------------------------------------------------ section mapping

    /** key → "section|card title". Keys not listed fall back to their catalog group (see placeCatalog). */
    static final Map<String, String> PLACE = new HashMap<>();

    static void place(int section, String group, String... keys) {
        for (String k : keys) PLACE.put(k, section + "|" + group);
    }

    static {
        place(0, "Темы и цвета", "qvk_theme_catalog", "qvk_monet_colors");
        place(0, "Фон и пузыри чатов", "qvk_personalization");
        place(0, "Анимации и запуск", "qvk_motion_options", "qvk_smooth_motion", "qvk_motion_blur_options", "qvk_splash_options");
        place(0, "Значки и украшения", "badges", "sova_badges_enabled", "qvk_ornament_catalog", "qvk_badge_application", "disable_nft_avatars", "disable_emoji_status", "core_christmas_garland");
        place(0, "Иконка, язык и эмодзи", "changeIcon", "appLanguage", "qvk_system_emoji");
        place(1, "Нижняя панель", "editDock", "hide_bottom_navigation", "transparentNavbar", "bottomDescription", "qvk_floating_navigation");
        place(1, "Боковое меню", "forceShowDrawer", "drawer_ignore_navigation_level", "hide_drawer_dashboard", "editDrawerItems", "qvk_drawer_zone", "qvk_drawer_cards_options");
        place(1, "Сервисы", "editSAItems", "editServiceTiles", "qvk_services_layout", "qvk_services_icon_style", "disableServicesShowcase", "disableSuperAppForYou", "disableSuperPromo", "qmods_lite_services", "qvk_hide_widgets_settings");
        place(1, "Запуск", "launchPage");
        place(2, "Нечиталка и «печатает»", "dnrGlobal", "dnrPM", "dnrGroups", "dnrConvs", "dntGlobal", "dntPM", "dntGroups", "dntConvs", "qvk_chat_privacy", "screenshot_dnr");
        place(2, "Голосовые и звонки", "voiceDownload", "voice_platform_codec", "voiceEnhance", "disableBTHeadsetRecording", "hide_vmsg_button", "disableVoipButtons");
        place(2, "Удалённые и изменённые", "undelete", "choose_undeleted_text_mark", "unedit");
        place(2, "Стикеры, эмодзи и реакции", "tgstickers", "emojiHints", "replace_vk_reactions", "disable_reactions", "vas_new_stickers_suggestion", "qvk_gif_names", "swap_attachments_with_emoji");
        place(2, "Чаты и диалоги", "fastReplies", "qvk_hidden_dialogs", "qvk_community_inbox", "qvk_text_tools_settings", "qvk_index59", "qvk_folders59",
                "qvk_hide_dialog_greeting", "rounded_writebar", "qvk_compact_dialogs", "use_old_read_indicator", "disable_message_tooltip", "qvk_account_names", "fullTime");
        place(3, "Приватность", "online_privacy", "online_privacy_bypass", "offline", "device_mask", "qvk_client_platform_settings", "qvk_online_status_viewer", "multiaccount75", "qvk_hide_vpn_detection");
        place(3, "Статистика и аналитика", "telemetry", "news_telemetry", "music_telemetry");
        place(3, "Блокировка приложения", "enablePin", "setPin", "fingerUnlock", "vibrateOnTap", "timeout", "lock", "qvk_pattern_setup");
        place(4, "Классический раздел ВК", "autoCache", "qmods_offline_music", "shuffle_hint", "downloadAudio", "cache_my_music_mp3", "audio_service",
                "use_legacy_shuffle", "remove_catalog_banners", "classic_my_music", "show_my_music", "qmods_music_compat", "qvk_disable_audio_ads",
                "qvk_music_playlists", "qvk_music_export_quality", "qvk_music_explorer", "qvk_music_lab59", "audio_saving_player_state", "sova_music_download_folder", "clearAudioCache");
        place(5, "Лента", "changeFeedSettings", "__dbg_no_ads", "disableAdsInGroups", "disableRecommendedPosts", "disableClipsBlock", "disableSourcePosts", "for_you_as_feed",
                "poll_preview", "rightLikes", "feed_post_footer_redesign_v3", "feed_redesign_islands", "feed_repost_headers_redesign", "feed_redesign_text_position",
                "resetFeedStyle", "hidePostPromotion", "qvk_feed_rules59", "feed_double_tap_like", "con_posting_redesign", "qvk_post_coauthors");
        place(5, "Истории", "disableStories", "storyDNR", "storyAdblock", "qvk_story_downloads", "qvk_story_download_action");
        place(5, "Видео, клипы и фото", "disableClipsAds", "disableVideoAds", "qvk_video_links", "qvk_wall_media", "qvk_image_search", "core_new_photo_editor_collage");
        place(5, "Сообщества и друзья", "qmods_groups_compat", "qvk_community_layout", "qvk_community_recommendations", "qvk_hide_possible_friends",
                "qvk_hide_friends_contacts_prompt", "qvk_hide_friends_suggest_prompt");
        place(6, "Доставка уведомлений", "qvk_push_modes", "qvk_background_delivery", "disable_push_warning");
        place(6, "Звук и вид уведомлений", "qvk_notification_sound", "qvk_notification_tools");
        place(7, "Резервные копии", "export_server_auto", "export_server", "import_server", "export", "import", "qvk_settings_catalog");
        place(7, "Сеть и адреса", "sslPinning", "apiHost", "oauthHost", "vkUiHostUri", "spaUri", "awayPhpDomain", "vkDesktopDomain", "resetDomains", "disableAway");
        place(7, "Инструменты", "qvk_workbench", "qvk_tools59", "disable_contacts_warning", "short_share_timeout");
        place(8, "Объявления и обновления", "qmods_announcements", "qmods_updates");
    }

    /** Placement of a catalog entry: explicit table first, then its catalog group. */
    static String[] placeCatalog(String group, String key) {
        String v = PLACE.get(key);
        if (v != null) return v.split("\\|", 2);
        switch (group) {
            case "Стили интерфейса":
                return new String[]{"0", "Стиль интерфейса"};
            case "Сообщения":
                return new String[]{"2", "Чаты и диалоги"};
            case "Приватность":
                return new String[]{"3", "Приватность"};
            case "Блокировка":
                return new String[]{"3", "Блокировка приложения"};
            case "Музыка":
                return new String[]{"4", "Классический раздел ВК"};
            case "Лента":
                return new String[]{"5", "Лента"};
            case "Интерфейс":
                return new String[]{"0", "Интерфейс"};
            case "Сеть":
                return new String[]{"7", "Сеть и адреса"};
            default:
                return new String[]{"7", "Прочее"};
        }
    }

    /** SettingsHub screens. */
    static String[] placeHub(String group, String target) {
        switch (target) {
            case "original:themes":
            case "themeCatalog":
                return new String[]{"0", "Темы и цвета"};
            case "personal":
                return new String[]{"0", "Фон и пузыри чатов"};
            case "original:badges":
                return new String[]{"0", "Значки и украшения"};
            case "comfort":
                return new String[]{"0", "Удобство и чтение"};
            case "icons":
            case "languagesCustom":
                return new String[]{"0", "Иконка, язык и эмодзи"};
            case "dock":
            case "all:appearance":
                return new String[]{"1", "Нижняя панель"};
            case "original:pushes":
            case "pushModes":
                return new String[]{"6", "Доставка уведомлений"};
            case "notificationSound":
                return new String[]{"6", "Звук и вид уведомлений"};
            case "original:tools":
            case "original:other":
                return new String[]{"7", "Резервные копии"};
            case "appDiagnostics":
            case "original:debug":
            case "appearanceRecovery":
                return new String[]{"7", "Диагностика"};
            case "qmodsInbox":
            case "original:about":
            case "builtinLicenses":
                return new String[]{"8", "О QVK"};
            case "videoLinks":
            case "storyDownloads":
                return new String[]{"5", "Видео, клипы и фото"};
            case "original:locker":
                return new String[]{"3", "Блокировка приложения"};
            case "all:privacy":
            case "anonymous":
            case "clientPlatform":
                return new String[]{"3", "Приватность"};
            case "settingsHome:stickers":
                return new String[]{"2", "Стикеры, эмодзи и реакции"};
            case "all:network":
                return new String[]{"7", "Сеть и адреса"};
            default:
                break;
        }
        switch (group) {
            case "appearance":
                return new String[]{"0", "Интерфейс"};
            case "messages":
                return new String[]{"2", "Чаты и диалоги"};
            case "media":
                return new String[]{"4", "Классический раздел ВК"};
            case "privacy":
                return new String[]{"3", "Приватность"};
            case "network":
                return new String[]{"6", "Доставка уведомлений"};
            case "about":
                return new String[]{"8", "О QVK"};
            case "feed":
                return new String[]{"5", "Лента"};
            default:
                return new String[]{"7", "Инструменты"};
        }
    }

    // ------------------------------------------------------------------ build

    /** Adds the QVK feature catalog (array of {name, items[]}). Unavailable entries are skipped. */
    /**
     * QVK 2.x catalog entries that duplicate a QVK 3.0 feature and only lead back into the old QVK 2.x screens —
     * hidden so the settings centre is not cluttered with two of everything. The QVK 3.0 equivalents:
     * iOS/Material/Monet styles → the «Светлая и тёмная тема» theme system; «Оформление и темы»/«Каталог настроек» →
     * this very centre; the classic «моя музыка» toggles → QVK Music.
     */
    static final java.util.Set<String> HIDE = new java.util.HashSet<>(java.util.Arrays.asList(
            "qvk_ios_style", "qvk_material3_style", "qvk_monet_colors", "qvk_monet_options", "themes", "qvk_settings_catalog",
            "classic_my_music", "show_my_music", "qmods_music_compat", "use_legacy_shuffle"));

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
                if (HIDE.contains(key)) continue;
                boolean sw = o.optBoolean("switch") || key.equals("enablePin");
                String summary = o.optString("summary", o.optString("detail"));
                if ("partial".equals(o.optString("status"))) summary = (summary.isEmpty() ? "" : summary + " ") + "(работает частично)";
                String[] pl = placeCatalog(name, key);
                add(new Item(key, sw ? key : null, o.optString("title"), summary, Integer.parseInt(pl[0]), pl[1],
                        sw ? SWITCH : ACTION, sw ? null : o.optString("action", key), o.optBoolean("default")));
            }
        }
    }

    /** Adds a screen (SettingsHub route or a QVK 3.0 route). */
    public void addScreen(String target, String title, String summary, int section, String group) {
        add(new Item("screen:" + target, null, title, summary, section, group, SCREEN, target, false));
    }

    /** A QVK 3.0 switch stored directly in QVK preferences (keys start with "qvk3_"). */
    public void addSwitch(String key, String title, String summary, int section, String group, boolean def) {
        add(new Item(key, key, title, summary, section, group, SWITCH, null, def));
    }

    public void addHubScreen(String group, String title, String summary, String target) {
        // "all:"/"basic:" routes only re-list catalog switches that the centre already shows; a screen whose
        // target or title duplicates a catalog entry would appear twice.
        if (target.startsWith("all:") || target.startsWith("basic:")) return;
        String nt = normal(title);
        for (Item i : items) {
            if (target.equals(i.target) || normal(i.title).equals(nt)) return;
            if (target.startsWith("tools59:") && target.equals(i.target)) return;
        }
        String[] pl = placeHub(group, target);
        addScreen(target, title, summary, Integer.parseInt(pl[0]), pl[1]);
    }

    /** Card titles of a section in display order (order of first appearance, QVK 3.0 cards first). */
    public List<String> groups(int section) {
        List<String> out = new ArrayList<>();
        for (Item i : items) if (i.section == section && !out.contains(i.group)) out.add(i.group);
        return out;
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
