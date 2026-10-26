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
}
