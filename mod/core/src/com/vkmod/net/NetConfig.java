package com.vkmod.net;

import com.vkmod.core.Registry;

import java.util.Arrays;

/** Builds the live rule objects from the feature registry; cached until a setting changes. */
public final class NetConfig {
    private NetConfig() {}

    private static volatile int builtFor = -1;
    private static volatile NetRules rules = new NetRules();
    private static volatile FilterRules filter = new FilterRules();

    static final String[] RECO_GROUPS = {"recommended_groups", "recommended_channels", "recommended_chats", "liked_by_friends_groups",
            "authors_rec", "user_rec", "recommended_narratives"};
    static final String[] RECO_CONTENT = {"recommended_audios", "recommended_playlists", "recommended_artists", "audios_for_you_block",
            "playlists_for_you_block", "videos_for_you", "videos_for_you_block", "music_tracks_carousel", "recommended_live_videos",
            "live_recommended", "recommended_game", "games_carousel", "recommended_games_carousel", "mini_apps_carousel",
            "recommended_mini_app", "mini_app_recommended_v2", "market_carousel", "market_groups_block", "product_carousel",
            "youla_carousel", "youla_groups_block", "tags_suggestions", "tags_suggestions_info", "stickers_suggestions"};
    static final String[] FRIENDS = {"friends_recomm", "friends_entrypoints", "holiday_friends", "friends_tags_confirmation"};
    static final String[] BANNERS = {"info_block", "animated_block", "generic_webview_block", "donut_post_placeholder",
            "donut_post_teaser", "uxpolls_block", "uxpoll_block", "feedback_poll", "feedback_block", "signals"};

    private static void sync() {
        int v = com.vkmod.core.Prefs.version();
        if (v == builtFor) return;
        synchronized (NetConfig.class) {
            if (v == builtFor) return;
            NetRules r = new NetRules();
            FilterRules f = new FilterRules();

            boolean ads = Registry.isOn("ads_feed");
            f.adTypes = ads; f.markedAds = ads;
            if (Registry.isOn("promo_communities")) f.feedTypes.addAll(Arrays.asList(RECO_GROUPS));
            if (Registry.isOn("feed_reco")) { f.recommendedPosts = true; f.feedTypes.addAll(Arrays.asList(RECO_CONTENT)); }
            if (Registry.isOn("friends_suggest")) f.feedTypes.addAll(Arrays.asList(FRIENDS));
            if (Registry.isOn("banners")) {
                f.feedTypes.addAll(Arrays.asList(BANNERS));
                f.feedTypePrefixes.add("donut_");
                r.methodPrefixes.add("splashscreens.get");
                r.methodPrefixes.add("apps.getadvertisementconfig");
            }
            f.storyAds = Registry.isOn("ads_stories");
            f.videoAds = Registry.isOn("ads_clips");
            if (Registry.isOn("ads_clips")) { f.promoChats = false; }
            f.disableStories = Registry.isOn("hide_stories");
            f.reposts = Registry.isOn("hide_reposts");
            if (Registry.isOn("keywords")) f.keywords.addAll(NetRules.parseList(Registry.str("keywords")));
            f.promoChats = Registry.isOn("promo_communities");

            r.adHosts = ads || Registry.isOn("ads_stories") || Registry.isOn("ads_clips");
            r.trackerHosts = Registry.isOn("block_trackers");
            r.analyticsMethods = Registry.isOn("block_trackers");
            r.noRead = Registry.isOn("no_read");
            r.noTyping = Registry.isOn("no_typing");
            r.offline = Registry.isOn("offline");

            rules = r; filter = f; builtFor = v;
        }
    }

    public static NetRules rules() { sync(); return rules; }
    public static FilterRules filter() { sync(); return filter; }
}
