package com.vkmod;

import static org.junit.Assert.*;

import com.vkmod.core.Prefs;
import com.vkmod.core.Registry;
import com.vkmod.net.Msgpack;
import com.vkmod.net.NetHook;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.m;
import okhttp3.p;
import okhttp3.u;
import okhttp3.v;

/** Drives the real NetHook through an OkHttp-shaped fake (same member shapes as VK's obfuscated OkHttp). */
public class NetHookTest {
    private Interceptor interceptor;

    @Before public void setUp() {
        Prefs.attach(new FakeStore());
        Registry.ensureLoaded();
        Registry.setOn("no_read", true);
        Registry.setOn("offline", true);
        Registry.setOn("hide_reposts", true);
        List<Object> list = new ArrayList<>();
        Interceptor existing = chain -> chain.a(chain.request());
        list.add(existing);
        @SuppressWarnings("unchecked") List<Object> wrapped = NetHook.wrap(list);
        assertEquals(2, wrapped.size());
        assertTrue("our interceptor must run first", wrapped.get(0) != existing);
        interceptor = (Interceptor) wrapped.get(0);
        assertSame("wrapping twice must not duplicate", wrapped.get(0), ((Interceptor) NetHook.wrap(wrapped).get(0)));
    }

    private static Interceptor.a chain(final String url, final String body, final String contentType, final boolean[] proceeded) {
        return new Interceptor.a() {
            final p req = new p(url, "GET");
            public u a(p pVar) { proceeded[0] = true; return new u(pVar, okhttp3.Protocol.HTTP_1_1, "OK", 200, v.create(body, new m(contentType))); }
            public p request() { return req; }
            public okhttp3.d call() { return null; }
            public int d() { return 0; }
            public Interceptor.a c(int i) { return this; }
        };
    }

    private static final String FEED = "{\"response\":{\"items\":[{\"type\":\"ads\",\"ad_data\":\"x\"},{\"type\":\"post\",\"text\":\"keep\"},"
            + "{\"type\":\"post\",\"text\":\"repost\",\"copy_history\":[{}]}]}}";

    @Test public void filtersJsonFeed() throws IOException {
        boolean[] pr = {false};
        u r = interceptor.intercept(chain("https://api.vk.ru/method/newsfeed.getFeed?v=5", FEED, "application/json; charset=utf-8", pr));
        assertTrue(pr[0]);
        JSONObject o = new JSONObject(r.g.string());
        assertEquals(1, o.getJSONObject("response").getJSONArray("items").length());
        assertEquals("application/json; charset=utf-8", r.g.contentType().toString());
    }

    @Test public void filtersMsgpackFeedAndKeepsContentType() throws IOException {
        byte[] packed = Msgpack.fromJson(FEED);
        // body is passed as latin1 string through the fake; build the msgpack body directly instead
        boolean[] pr = {false};
        Interceptor.a c = new Interceptor.a() {
            final p req = new p("https://api.vk.ru/method/newsfeed.getFeed?v=5", "GET");
            public u a(p pVar) { return new u(pVar, okhttp3.Protocol.HTTP_1_1, "OK", 200, v.create(packed, new m("application/x-msgpack"))); }
            public p request() { return req; }
            public okhttp3.d call() { return null; }
            public int d() { return 0; }
            public Interceptor.a c(int i) { return this; }
        };
        u r = interceptor.intercept(c);
        assertEquals("application/x-msgpack", r.g.contentType().toString());
        JSONObject o = new JSONObject(Msgpack.toJson(r.g.bytes()));
        assertEquals(1, o.getJSONObject("response").getJSONArray("items").length());
    }

    @Test public void swallowsReadReceiptWithFakeSuccess() throws IOException {
        boolean[] pr = {false};
        u r = interceptor.intercept(chain("https://api.vk.ru/method/messages.markAsRead?peer_id=1", "{}", "application/json", pr));
        assertFalse("request must not reach the network", pr[0]);
        assertEquals(200, r.d);
        assertEquals("{\"response\":1}", r.g.string());
    }

    @Test public void swallowsOnlineAndAnalytics() throws IOException {
        for (String m : new String[]{"account.setOnline", "stats.trackEvents"}) {
            boolean[] pr = {false};
            u r = interceptor.intercept(chain("https://api.vk.ru/method/" + m, "{}", "application/json", pr));
            assertFalse(m, pr[0]);
            assertEquals("{\"response\":1}", r.g.string());
        }
    }

    @Test public void dropsTrackerHostWithIoException() {
        boolean[] pr = {false};
        try {
            interceptor.intercept(chain("https://tracker-api.my.com/x", "{}", "application/json", pr));
            fail("tracker request must fail");
        } catch (IOException expected) {
            assertFalse(pr[0]);
        }
    }

    @Test public void untouchedTrafficPassesThrough() throws IOException {
        boolean[] pr = {false};
        u r = interceptor.intercept(chain("https://api.vk.ru/method/messages.send?peer_id=1", "{\"response\":5}", "application/json", pr));
        assertTrue(pr[0]);
        assertEquals("{\"response\":5}", r.g.string());
        boolean[] pr2 = {false};
        u r2 = interceptor.intercept(chain("https://sun1.userapi.com/c1/photo.jpg", "BINARY", "image/jpeg", pr2));
        assertTrue(pr2[0]);
        assertEquals("BINARY", r2.g.string());
    }

    @Test public void disabledFeaturesStopFiltering() throws IOException {
        Registry.setOn("ads_feed", false);
        Registry.setOn("hide_reposts", false);
        Registry.setOn("promo_communities", false);
        Registry.setOn("ads_stories", false);
        Registry.setOn("ads_clips", false);
        Registry.setOn("banners", false);
        boolean[] pr = {false};
        u r = interceptor.intercept(chain("https://api.vk.ru/method/newsfeed.getFeed", FEED, "application/json", pr));
        assertEquals(3, new JSONObject(r.g.string()).getJSONObject("response").getJSONArray("items").length());
    }

    @Test public void realAdTypesOfVk8200AreRemoved() throws IOException {
        String body = "{\"response\":{\"items\":[{\"type\":\"ads_post\"},{\"type\":\"ads_app_slider\"},{\"type\":\"promo_button\"},"
                + "{\"type\":\"videos_promo\"},{\"type\":\"clips_ozon\"},{\"type\":\"ads_disabled\"},{\"type\":\"post\",\"text\":\"ok\"}]}}";
        boolean[] pr = {false};
        u r = interceptor.intercept(chain("https://api.vk.ru/method/newsfeed.getFeed", body, "application/json", pr));
        org.json.JSONArray it = new JSONObject(r.g.string()).getJSONObject("response").getJSONArray("items");
        assertEquals(2, it.length());          // ads_disabled is a settings marker, not an ad
        assertEquals("ads_disabled", it.getJSONObject(0).getString("type"));
    }

    @Test public void optionalFeedBlocksAreOffByDefaultAndWorkWhenOn() throws IOException {
        String body = "{\"response\":{\"items\":[{\"type\":\"clips_block\"},{\"type\":\"games_carousel\"},{\"type\":\"dzen_block\"},{\"type\":\"digest\"},{\"type\":\"post\",\"text\":\"ok\"}]}}";
        boolean[] pr = {false};
        u r = interceptor.intercept(chain("https://api.vk.ru/method/newsfeed.getFeed", body, "application/json", pr));
        assertEquals(5, new JSONObject(r.g.string()).getJSONObject("response").getJSONArray("items").length());
        for (String f : new String[]{"feed_clips", "feed_games", "feed_dzen", "feed_misc"}) Registry.setOn(f, true);
        u r2 = interceptor.intercept(chain("https://api.vk.ru/method/newsfeed.getFeed", body, "application/json", new boolean[]{false}));
        assertEquals(1, new JSONObject(r2.g.string()).getJSONObject("response").getJSONArray("items").length());
    }

    @Test public void hideStoriesRemovesFeedBlockToo() throws IOException {
        Registry.setOn("hide_stories", true);
        String body = "{\"response\":{\"items\":[{\"type\":\"stories\"},{\"type\":\"post\",\"text\":\"ok\"}]}}";
        u r = interceptor.intercept(chain("https://api.vk.ru/method/newsfeed.getFeed", body, "application/json", new boolean[]{false}));
        assertEquals(1, new JSONObject(r.g.string()).getJSONObject("response").getJSONArray("items").length());
    }

    @Test public void customBlocksSplitMethodsAndHosts() throws IOException {
        Registry.setOn("custom_blocks", true);
        com.vkmod.core.Prefs.putString("v.custom_blocks", "stats.fooBar, ads.example.com");
        boolean[] pr = {false};
        u r = interceptor.intercept(chain("https://api.vk.ru/method/stats.fooBar?x=1", "{}", "application/json", pr));
        assertFalse(pr[0]);
        assertEquals("{\"response\":1}", r.g.string());
        try { interceptor.intercept(chain("https://ads.example.com/x", "{}", "application/json", new boolean[]{false})); fail(); }
        catch (IOException expected) { }
    }

    @Test public void videoAdDescriptorsRemovedWhenEnabled() throws IOException {
        String body = "{\"response\":{\"items\":[{\"id\":1,\"ads_info\":{\"preroll\":1},\"title\":\"t\"}]}}";
        u r = interceptor.intercept(chain("https://api.vk.ru/method/video.get", body, "application/json", new boolean[]{false}));
        JSONObject item = new JSONObject(r.g.string()).getJSONObject("response").getJSONArray("items").getJSONObject(0);
        assertFalse(item.has("ads_info"));
        assertEquals("t", item.getString("title"));
    }

    private static final String MIXED = "{\"response\":{\"items\":[{\"type\":\"post\",\"source_id\":-5,\"text\":\"group\"},"
            + "{\"type\":\"post\",\"source_id\":7,\"text\":\"person\"},"
            + "{\"type\":\"post\",\"source_id\":8,\"text\":\"poll\",\"attachments\":[{\"type\":\"poll\"}]},"
            + "{\"type\":\"friend\"},{\"type\":\"photo_tag\"},{\"type\":\"my_type\"}]}}";

    private String texts(u r) throws IOException {
        org.json.JSONArray it = new JSONObject(r.g.string()).getJSONObject("response").getJSONArray("items");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < it.length(); i++) sb.append(it.getJSONObject(i).optString("text", it.getJSONObject(i).optString("type"))).append('|');
        return sb.toString();
    }

    private u feed(String method) throws IOException {
        return interceptor.intercept(chain("https://api.vk.ru/method/" + method, MIXED, "application/json", new boolean[]{false}));
    }

    @Test public void sourceFiltersSplitPeopleAndCommunities() throws IOException {
        assertEquals("group|person|poll|friend|photo_tag|my_type|", texts(feed("newsfeed.get")));
        Registry.setOn("feed_only_friends", true);
        assertEquals("person|poll|friend|photo_tag|my_type|", texts(feed("newsfeed.get")));
        assertEquals("group|person|poll|friend|photo_tag|my_type|", texts(feed("wall.get")).replace("friend|photo_tag|my_type|", "friend|photo_tag|my_type|"));
        Registry.setOn("feed_only_friends", false);
        Registry.setOn("feed_only_groups", true);
        assertEquals("group|friend|photo_tag|my_type|", texts(feed("newsfeed.get")));
    }

    @Test public void pollsFriendEventsPhotoTagsAndCustomTypes() throws IOException {
        Registry.setOn("feed_poll_posts", true);
        Registry.setOn("feed_friend_events", true);
        Registry.setOn("feed_photo_tags", true);
        Registry.setOn("custom_feed_types", true);
        com.vkmod.core.Prefs.putString("v.custom_feed_types", "my_type");
        assertEquals("group|person|", texts(feed("newsfeed.get")));
    }

    @Test public void listenBroadcastAndVoiceListenedAreSwallowed() throws IOException {
        Registry.setOn("no_listen_broadcast", true);
        Registry.setOn("no_voice_listened", true);
        for (String m : new String[]{"audio.setBroadcast", "messages.markAsListened"}) {
            boolean[] pr = {false};
            u r = interceptor.intercept(chain("https://api.vk.ru/method/" + m + "?x=1", "{}", "application/json", pr));
            assertFalse(m, pr[0]);
            assertEquals("{\"response\":1}", r.g.string());
        }
    }

    @Test public void fetchStyleMethodsAreDroppedNotFaked() {
        Registry.setOn("no_easter_eggs", true);
        try { interceptor.intercept(chain("https://api.vk.ru/method/specials.getEasterEggs", "{}", "application/json", new boolean[]{false})); fail(); }
        catch (IOException expected) { }
        try { interceptor.intercept(chain("https://api.vk.ru/method/splashscreens.get", "{}", "application/json", new boolean[]{false})); fail(); }
        catch (IOException expected) { }
    }
}
