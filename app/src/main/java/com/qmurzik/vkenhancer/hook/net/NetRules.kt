package com.qmurzik.vkenhancer.hook.net

import java.util.Locale

/** Decides which outgoing requests are cancelled before they reach the network. */
data class BlockRules(
    val adHosts: Boolean = false,
    val trackerHosts: Boolean = false,
    val analyticsMethods: Boolean = false,
    val noRead: Boolean = false,
    val noTyping: Boolean = false,
    val offline: Boolean = false,
    val storiesIncognito: Boolean = false,
    val customMethods: Set<String> = emptySet(),
    val customHosts: List<String> = emptyList(),
    /** Method-name prefixes from the feature catalog (lower-case), matched with startsWith. */
    val methodPrefixes: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !adHosts && !trackerHosts && !analyticsMethods && !noRead && !noTyping && !offline &&
            !storiesIncognito && customMethods.isEmpty() && customHosts.isEmpty() && methodPrefixes.isEmpty()
}

object NetRules {
    /** Advertising SDK endpoints bundled into VK (myTarget, Yandex, mediation partners). */
    val AD_HOSTS = listOf(
        "ad.mail.ru", "ads.vk.com", "ads.vk.ru", "target.my.com", "r.mradx.net", "rs.mail.ru",
        "adsdk.yandex.ru", "an.yandex.ru", "yandexadexchange.net", "adfox.ru", "adv.rustore.ru",
        "googleadservices.com", "googlesyndication.com", "doubleclick.net", "admob.com",
        "fundingchoicesmessages.google.com", "applovin.com", "applvn.com", "inner-active.mobi",
        "unityads.unity3d.com", "mediation.unity3d.com", "unity3dusercontent.com", "vungle.com",
        "supersonicads.com", "ironsrc.mobi", "ironsrc.com", "sonicsads.com", "adsbigo.com", "maxesads.com",
        "mtgglobals.com", "rayjump.com", "mintegral.com", "ad-host-backup", "pangle", "bytegsdk.com",
        "mytarget",
    )

    /** Analytics and crash-reporting endpoints that only report on the user. */
    val TRACKER_HOSTS = listOf(
        "vk-analytics.ru", "tracker-api.my.com", "mytracker", "trk.mail.ru", "appmetrica.yandex", "appmetrica.io",
        "report.appmetrica", "startup.mobile.yandex.net", "stats-dg.rustore.ru", "crashlytics.com",
        "firebase-settings.crashlytics.com", "app-measurement.com", "sentry.io", "stat.vk-portal.net",
    )

    /** VK API methods that only exist to collect statistics or ad events. */
    val ANALYTICS_METHODS = listOf(
        "stats.", "adsint.", "ads.conversionhit", "ads.retargetinghit", "account.tracktoggles",
        "apps.gettrackbridgecallhandlers",
    )

    private val READ_METHODS = setOf("messages.markasread", "messages.markasanswered", "messages.markreactionsasread")
    private val TYPING_METHODS = setOf("messages.setactivity")
    private val ONLINE_METHODS = setOf("account.setonline")
    private val STORY_SEEN_METHODS = setOf("stories.markseen", "stories.seenreplies", "stories.markquestionsseen")

    fun hostOf(url: String): String {
        var start = url.indexOf("://")
        start = if (start < 0) 0 else start + 3
        var end = start
        while (end < url.length && url[end] != '/' && url[end] != ':' && url[end] != '?') end++
        return url.substring(start, end).lowercase(Locale.ROOT)
    }

    /** Returns a short reason when [url] must be blocked, or null to let it through. */
    fun blockReason(url: String, rules: BlockRules): String? {
        if (rules.isEmpty) return null
        val host = hostOf(url)
        if (host.isEmpty()) return null
        if (rules.adHosts && matchesHost(host, url, AD_HOSTS)) return "ad-host"
        if (rules.trackerHosts && matchesHost(host, url, TRACKER_HOSTS)) return "tracker-host"
        if (rules.customHosts.isNotEmpty() && matchesHost(host, url, rules.customHosts)) return "custom-host"

        val method = ApiFilter.methodFromUrl(url)?.lowercase(Locale.ROOT) ?: return null
        if (rules.analyticsMethods && ANALYTICS_METHODS.any { method.startsWith(it) }) return "analytics"
        if (rules.noRead && method in READ_METHODS) return "read-receipt"
        if (rules.noTyping && method in TYPING_METHODS) return "typing"
        if (rules.offline && method in ONLINE_METHODS) return "online"
        if (rules.storiesIncognito && method in STORY_SEEN_METHODS) return "story-seen"
        if (method in rules.customMethods) return "custom-method"
        if (rules.methodPrefixes.any { method.startsWith(it) }) return "catalog-method"
        return null
    }

    /**
     * Host patterns match whole domains (`vungle.com` matches `adx.ads.vungle.com`); patterns that are
     * not domains (`ad-host-backup`, `mytarget`) match anywhere in the host.
     */
    private fun matchesHost(host: String, url: String, patterns: List<String>): Boolean {
        for (p in patterns) {
            if (p.isEmpty()) continue
            if (p.contains('.')) {
                if (host == p || host.endsWith(".$p")) return true
                // Path-qualified patterns such as "example.com/ads".
                if (p.contains('/') && url.contains(p)) return true
            } else if (host.contains(p)) {
                return true
            }
        }
        return false
    }

    /** Parses a user-entered list: commas, spaces and new lines all separate entries. */
    fun parseList(raw: String?): List<String> =
        raw.orEmpty().split(',', '\n', ' ', ';', '\t')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
}
