package com.vkmod.net;

import com.vkmod.core.Registry;

import java.util.Arrays;

/** Builds the live rule objects from the feature registry; cached until a setting changes. */
public final class NetConfig {
    private NetConfig() {}

    private static volatile int builtFor = -1;
    private static volatile NetRules rules = new NetRules();
    private static volatile FilterRules filter = new FilterRules();

    // Every value below was checked against the 8.200 NewsfeedNewsfeedItemTypeDto enum (see docs/INTERVENTION_MAP.md).
    static final String[] AD_TYPES = {"promo_button", "videos_promo", "clips_ozon", "clips_shops"}; // besides every ads_* type
    static final String[] RECO_GROUPS = {"recommended_groups", "recommended_channels", "recommended_chats", "liked_by_friends_groups",
            "authors_rec", "user_rec", "recommended_narratives"};
    static final String[] RECO_CONTENT = {"recommended_audios", "recommended_playlists", "recommended_artists", "audios_for_you_block",
            "playlists_for_you_block", "videos_for_you", "videos_for_you_block", "recommended_game", "games_carousel",
            "recommended_games_carousel", "mini_apps_carousel", "recommended_mini_app", "market_carousel", "market_groups_block",
            "youla_carousel", "youla_groups_block", "tags_suggestions", "discover_media_block"};
    static final String[] FRIENDS = {"friends_recomm", "friends_entrypoints", "holiday_friends"};
    static final String[] BANNERS = {"info_block", "animated_block", "generic_webview_block", "uxpolls_block", "uxpoll_block",
            "feedback_poll", "signals"};
    static final String[] STORIES = {"stories", "stories_interesting_block"};
    static final String[] CLIPS = {"clip", "clips_block", "clips_autoplay", "clips_challenges", "clips_retention"};
    static final String[] GAMES = {"achievement_game", "bookmark_game", "games_carousel", "recommended_game", "recommended_games_carousel",
            "mini_apps_carousel", "recommended_mini_app", "app_widget"};
    static final String[] MARKET = {"market_carousel", "market_groups_block", "youla_carousel", "youla_groups_block", "market"};
    static final String[] DZEN = {"dzen_block", "dzen_story_news", "dzen_top_stories"};
    static final String[] MUSIC = {"audios_for_you_block", "playlists_for_you_block", "recommended_audios", "recommended_playlists",
            "recommended_artists"};
    static final String[] VIDEO = {"videos_for_you", "videos_for_you_block", "video_postcard", "discover_media_block"};
    static final String[] MISC = {"digest", "expert_card", "textlive", "interests", "tags_suggestions"};

    private static void sync() {
        int v = com.vkmod.core.Prefs.version();
        if (v == builtFor) return;
        synchronized (NetConfig.class) {
            if (v == builtFor) return;
            NetRules r = new NetRules();
            FilterRules f = new FilterRules();

            boolean ads = Registry.isOn("ads_feed");
            f.adTypes = ads; f.markedAds = ads;
            if (ads) f.feedTypes.addAll(Arrays.asList(AD_TYPES));
            if (Registry.isOn("feed_clips")) f.feedTypes.addAll(Arrays.asList(CLIPS));
            if (Registry.isOn("feed_games")) f.feedTypes.addAll(Arrays.asList(GAMES));
            if (Registry.isOn("feed_market")) f.feedTypes.addAll(Arrays.asList(MARKET));
            if (Registry.isOn("feed_dzen")) { f.feedTypes.addAll(Arrays.asList(DZEN)); f.feedTypePrefixes.add("dzen_"); }
            if (Registry.isOn("feed_music")) f.feedTypes.addAll(Arrays.asList(MUSIC));
            if (Registry.isOn("feed_video")) f.feedTypes.addAll(Arrays.asList(VIDEO));
            if (Registry.isOn("feed_misc")) f.feedTypes.addAll(Arrays.asList(MISC));
            if (Registry.isOn("promo_communities")) f.feedTypes.addAll(Arrays.asList(RECO_GROUPS));
            if (Registry.isOn("feed_reco")) { f.recommendedPosts = true; f.feedTypes.addAll(Arrays.asList(RECO_CONTENT)); }
            if (Registry.isOn("friends_suggest")) f.feedTypes.addAll(Arrays.asList(FRIENDS));
            if (Registry.isOn("banners")) {
                f.feedTypes.addAll(Arrays.asList(BANNERS));
                f.feedTypePrefixes.add("donut_"); // Donut teasers; prefix kept for newer builds
                r.dropPrefixes.add("splashscreens.get");
            }
            f.storyAds = Registry.isOn("ads_stories");
            f.videoAds = Registry.isOn("ads_clips");
            if (Registry.isOn("ads_clips")) { f.promoChats = false; }
            f.disableStories = Registry.isOn("hide_stories");
            if (f.disableStories) f.feedTypes.addAll(Arrays.asList(STORIES));
            f.reposts = Registry.isOn("hide_reposts");
            if (Registry.isOn("keywords")) f.keywords.addAll(NetRules.parseList(Registry.str("keywords")));
            f.promoChats = Registry.isOn("promo_communities");

            r.adHosts = ads || Registry.isOn("ads_stories") || Registry.isOn("ads_clips");
            r.trackerHosts = Registry.isOn("block_trackers");
            r.analyticsMethods = Registry.isOn("block_trackers");
            r.noRead = Registry.isOn("no_read");
            r.noTyping = Registry.isOn("no_typing");
            r.offline = Registry.isOn("offline");
            if (Registry.isOn("no_listen_broadcast")) r.methodPrefixes.add("audio.setbroadcast");
            if (Registry.isOn("no_voice_listened")) r.methodPrefixes.add("messages.markaslistened");
            if (Registry.isOn("no_easter_eggs")) r.dropPrefixes.add("specials.geteastereggs");
            f.hideCommunityPosts = Registry.isOn("feed_only_friends");
            f.hideUserPosts = Registry.isOn("feed_only_groups");
            f.hidePollPosts = Registry.isOn("feed_poll_posts");
            if (Registry.isOn("feed_friend_events")) f.feedTypes.add("friend");
            if (Registry.isOn("feed_photo_tags")) f.feedTypes.add("photo_tag");
            if (Registry.isOn("custom_feed_types")) for (String t : NetRules.parseList(Registry.str("custom_feed_types"))) f.feedTypes.add(t);
            if (Registry.isOn("custom_blocks")) parseCustom(Registry.str("custom_blocks"), r);

            rules = r; filter = f; builtFor = v;
        }
    }

    /** "stats.trackEvents" (one dot, API method) vs "ads.example.com" (host); both lower-cased. */
    static void parseCustom(String raw, NetRules r) {
        for (String t : NetRules.parseList(raw)) {
            int dots = t.length() - t.replace(".", "").length();
            if (dots == 1 && t.indexOf('/') < 0 && Character.isLetter(t.charAt(0)) && t.matches("[a-z0-9_]+\\.[a-z0-9_]+") && !t.matches(".*\\.(com|ru|net|org|io|su|me|xyz|info)$")) r.customMethods.add(t);
            else r.customHosts.add(t);
        }
    }

    public static NetRules rules() { sync(); return rules; }
    public static FilterRules filter() { sync(); return filter; }
}
