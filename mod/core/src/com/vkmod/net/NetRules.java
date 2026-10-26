package com.vkmod.net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Decides which outgoing requests are cancelled before they reach the network. */
public final class NetRules {
    public boolean adHosts, trackerHosts, analyticsMethods, noRead, noTyping, offline, storiesIncognito;
    public final Set<String> customMethods = new HashSet<>();
    public final List<String> customHosts = new ArrayList<>();
    public final List<String> methodPrefixes = new ArrayList<>();

    public boolean isEmpty() {
        return !adHosts && !trackerHosts && !analyticsMethods && !noRead && !noTyping && !offline
                && !storiesIncognito && customMethods.isEmpty() && customHosts.isEmpty() && methodPrefixes.isEmpty();
    }

    public static final String[] AD_HOSTS = {
            "ad.mail.ru", "ads.vk.com", "ads.vk.ru", "target.my.com", "r.mradx.net", "rs.mail.ru",
            "adsdk.yandex.ru", "an.yandex.ru", "yandexadexchange.net", "adfox.ru", "adv.rustore.ru",
            "googleadservices.com", "googlesyndication.com", "doubleclick.net", "admob.com",
            "fundingchoicesmessages.google.com", "applovin.com", "applvn.com", "inner-active.mobi",
            "unityads.unity3d.com", "mediation.unity3d.com", "unity3dusercontent.com", "vungle.com",
            "supersonicads.com", "ironsrc.mobi", "ironsrc.com", "sonicsads.com", "adsbigo.com", "maxesads.com",
            "mtgglobals.com", "rayjump.com", "mintegral.com", "ad-host-backup", "pangle", "bytegsdk.com", "mytarget"};

    public static final String[] TRACKER_HOSTS = {
            "vk-analytics.ru", "tracker-api.my.com", "mytracker", "trk.mail.ru", "appmetrica.yandex", "appmetrica.io",
            "report.appmetrica", "startup.mobile.yandex.net", "stats-dg.rustore.ru", "crashlytics.com",
            "firebase-settings.crashlytics.com", "app-measurement.com", "sentry.io", "stat.vk-portal.net"};

    public static final String[] ANALYTICS_METHODS = {
            "stats.", "adsint.", "ads.conversionhit", "ads.retargetinghit", "account.tracktoggles",
            "apps.gettrackbridgecallhandlers"};

    private static final Set<String> READ = set("messages.markasread", "messages.markasanswered", "messages.markreactionsasread");
    private static final Set<String> TYPING = set("messages.setactivity");
    private static final Set<String> ONLINE = set("account.setonline");
    private static final Set<String> STORY_SEEN = set("stories.markseen", "stories.seenreplies", "stories.markquestionsseen");

    private static Set<String> set(String... a) { Set<String> s = new HashSet<>(); for (String x : a) s.add(x); return s; }

    public static String hostOf(String url) {
        int start = url.indexOf("://");
        start = start < 0 ? 0 : start + 3;
        int end = start;
        while (end < url.length()) {
            char c = url.charAt(end);
            if (c == '/' || c == ':' || c == '?') break;
            end++;
        }
        return url.substring(start, end).toLowerCase(Locale.ROOT);
    }

    /** Short reason when the request must be blocked; null to let it through. */
    public String blockReason(String url) {
        if (isEmpty()) return null;
        String host = hostOf(url);
        if (host.isEmpty()) return null;
        if (adHosts && matchesHost(host, url, AD_HOSTS)) return "ad-host";
        if (trackerHosts && matchesHost(host, url, TRACKER_HOSTS)) return "tracker-host";
        if (!customHosts.isEmpty() && matchesHost(host, url, customHosts.toArray(new String[0]))) return "custom-host";
        String method = ApiFilter.methodFromUrl(url);
        if (method == null) return null;
        method = method.toLowerCase(Locale.ROOT);
        if (analyticsMethods) for (String p : ANALYTICS_METHODS) if (method.startsWith(p)) return "analytics";
        if (noRead && READ.contains(method)) return "read-receipt";
        if (noTyping && TYPING.contains(method)) return "typing";
        if (offline && ONLINE.contains(method)) return "online";
        if (storiesIncognito && STORY_SEEN.contains(method)) return "story-seen";
        if (customMethods.contains(method)) return "custom-method";
        for (String p : methodPrefixes) if (method.startsWith(p)) return "catalog-method";
        return null;
    }

    /** True for blocked VK API calls that should get a fake `{"response":1}` instead of a network error. */
    public static boolean wantsFakeSuccess(String reason) {
        return "read-receipt".equals(reason) || "typing".equals(reason) || "online".equals(reason)
                || "story-seen".equals(reason) || "analytics".equals(reason) || "catalog-method".equals(reason)
                || "custom-method".equals(reason);
    }

    private static boolean matchesHost(String host, String url, String[] patterns) {
        for (String p : patterns) {
            if (p.isEmpty()) continue;
            if (p.indexOf('.') >= 0) {
                if (host.equals(p) || host.endsWith("." + p)) return true;
                if (p.indexOf('/') >= 0 && url.contains(p)) return true;
            } else if (host.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> parseList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split("[,\\n ;\\t]")) {
            s = s.trim().toLowerCase(Locale.ROOT);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
