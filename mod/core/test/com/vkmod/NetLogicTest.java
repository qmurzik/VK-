package com.vkmod;

import static org.junit.Assert.*;

import com.vkmod.net.ApiFilter;
import com.vkmod.net.FilterRules;
import com.vkmod.net.Msgpack;
import com.vkmod.net.NetRules;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NetLogicTest {
    private static final String FEED = "{\"response\":{\"page_size\":10,\"feed_type\":\"feed\",\"profiles\":[{\"id\":1}],\"groups\":[{\"id\":2}],"
            + "\"items\":["
            + "{\"advertiser_info_url\":\"x\",\"ads\":[{\"button\":\"Install\",\"ad_data\":\"qR1D\"}],\"type\":\"ads\",\"ads_title\":\"Ad\",\"date\":1},"
            + "{\"type\":\"post\",\"text\":\"normal post\",\"source_id\":5},"
            + "{\"type\":\"post\",\"text\":\"marked\",\"marked_as_ads\":1},"
            + "{\"type\":\"post\",\"text\":\"repost me\",\"copy_history\":[{\"text\":\"orig\"}]},"
            + "{\"type\":\"post\",\"text\":\"BUY crypto now\"},"
            + "{\"type\":\"recommended_groups\",\"items\":[]},"
            + "{\"type\":\"friends_recomm\"},"
            + "{\"type\":\"post\",\"text\":\"rec\",\"recommendation_info\":{\"x\":1}}"
            + "],\"next_from\":\"nf\"}}";

    private static JSONArray items(String json) { return new JSONObject(json).getJSONObject("response").getJSONArray("items"); }

    @Test public void removesTypedAndMarkedAds() {
        FilterRules r = new FilterRules(); r.adTypes = true; r.markedAds = true;
        String out = ApiFilter.filter("newsfeed.getFeed", FEED, r);
        assertNotNull(out);
        JSONArray it = items(out);
        for (int i = 0; i < it.length(); i++) {
            assertNotEquals("ads", it.getJSONObject(i).optString("type"));
            assertFalse(it.getJSONObject(i).has("marked_as_ads"));
        }
        assertEquals(6, it.length());
    }

    @Test public void returnsNullWhenNothingMatches() {
        FilterRules r = new FilterRules(); r.keywords.add("zzz-not-present");
        assertNull(ApiFilter.filter("newsfeed.getFeed", FEED, r));
    }

    @Test public void filtersRepostsKeywordsAndRecommendations() {
        FilterRules r = new FilterRules(); r.reposts = true; r.recommendedPosts = true; r.keywords.add("crypto");
        r.feedTypes.add("recommended_groups"); r.feedTypes.add("friends_recomm");
        String out = ApiFilter.filter("newsfeed.getFeed", FEED, r);
        assertNotNull(out);
        JSONArray it = items(out);
        StringBuilder texts = new StringBuilder();
        for (int i = 0; i < it.length(); i++) texts.append(it.getJSONObject(i).optString("text")).append('|');
        String s = texts.toString();
        assertFalse(s.contains("repost me")); assertFalse(s.contains("BUY")); assertFalse(s.contains("rec|"));
        assertTrue(s.contains("normal post"));
        for (int i = 0; i < it.length(); i++) assertNotEquals("friends_recomm", it.getJSONObject(i).optString("type"));
    }

    @Test public void feedRulesDoNotTouchNonFeedMethods() {
        FilterRules r = new FilterRules(); r.reposts = true;
        String wall = "{\"response\":{\"items\":[{\"type\":\"post\",\"copy_history\":[{}]}]}}";
        assertNull(ApiFilter.filter("users.get", wall, r));
    }

    @Test public void storiesDisabledAndAdsStripped() {
        FilterRules r = new FilterRules(); r.disableStories = true;
        String body = "{\"response\":{\"items\":[{\"stories\":[{\"id\":1}]}]}}";
        String out = ApiFilter.filter("stories.get", body, r);
        assertNotNull(out);
        assertEquals(0, new JSONObject(out).getJSONObject("response").getJSONArray("items").length());
        FilterRules r2 = new FilterRules(); r2.storyAds = true;
        String ads = "{\"response\":{\"items\":[],\"ads_settings\":{\"x\":1},\"ok\":1}}";
        String out2 = ApiFilter.filter("stories.get", ads, r2);
        assertNotNull(out2);
        assertFalse(new JSONObject(out2).getJSONObject("response").has("ads_settings"));
    }

    @Test public void promoChatsRemoved() {
        FilterRules r = new FilterRules(); r.promoChats = true;
        String body = "{\"response\":{\"items\":[{\"conversation\":{\"is_promo\":true}},{\"conversation\":{\"id\":2}}]}}";
        String out = ApiFilter.filter("messages.getConversations", body, r);
        assertNotNull(out);
        assertEquals(1, items(out).length());
    }

    @Test public void methodParsing() {
        assertEquals("newsfeed.getFeed", ApiFilter.methodFromUrl("https://api.vk.ru/method/newsfeed.getFeed?v=5.2"));
        assertNull(ApiFilter.methodFromUrl("https://sun1.userapi.com/img.jpg"));
    }

    @Test public void blocksReadTypingOnlineAndTrackers() {
        NetRules r = new NetRules();
        r.noRead = true; r.noTyping = true; r.offline = true; r.trackerHosts = true; r.analyticsMethods = true; r.adHosts = true;
        assertEquals("read-receipt", r.blockReason("https://api.vk.ru/method/messages.markAsRead?v=5"));
        assertEquals("typing", r.blockReason("https://api.vk.ru/method/messages.setActivity"));
        assertEquals("online", r.blockReason("https://api.vk.ru/method/account.setOnline"));
        assertEquals("analytics", r.blockReason("https://api.vk.ru/method/stats.trackEvents"));
        assertEquals("tracker-host", r.blockReason("https://tracker-api.my.com/x"));
        assertEquals("ad-host", r.blockReason("https://adx.ads.vungle.com/x"));
        assertNull(r.blockReason("https://api.vk.ru/method/messages.send"));
        assertNull(r.blockReason("https://sun1.userapi.com/photo.jpg"));
        assertTrue(NetRules.wantsFakeSuccess("read-receipt"));
        assertFalse(NetRules.wantsFakeSuccess("tracker-host"));
    }

    @Test public void emptyRulesBlockNothing() {
        assertNull(new NetRules().blockReason("https://api.vk.ru/method/messages.markAsRead"));
    }

    @Test public void msgpackRoundTrip() {
        String json = "{\"response\":{\"a\":1,\"b\":-5,\"c\":300,\"d\":70000,\"e\":5000000000,\"f\":\"привет\",\"g\":[true,false,null],\"h\":1.5,\"i\":{\"j\":[]}}}";
        byte[] packed = Msgpack.fromJson(json);
        assertNotNull(packed);
        String back = Msgpack.toJson(packed);
        assertNotNull(back);
        JSONObject r = new JSONObject(back).getJSONObject("response");
        assertEquals(1, r.getInt("a")); assertEquals(-5, r.getInt("b")); assertEquals(300, r.getInt("c"));
        assertEquals(70000, r.getInt("d")); assertEquals(5000000000L, r.getLong("e")); assertEquals("привет", r.getString("f"));
        assertEquals(1.5, r.getDouble("h"), 1e-9); assertEquals(3, r.getJSONArray("g").length());
    }

    @Test public void msgpackRejectsGarbageAndBinary() {
        assertNull(Msgpack.toJson(new byte[]{(byte) 0xc4, 0x01, 0x00})); // bin8 is unsupported -> leave bytes alone
        assertNull(Msgpack.toJson(new byte[]{(byte) 0x81}));             // truncated map
    }

    @Test public void filterThroughMsgpack() {
        byte[] packed = Msgpack.fromJson(FEED);
        String json = Msgpack.toJson(packed);
        FilterRules r = new FilterRules(); r.adTypes = true;
        String out = ApiFilter.filter("newsfeed.getFeed", json, r);
        assertNotNull(out);
        assertNotNull(Msgpack.fromJson(out));
    }
}
