package com.qmurzik.vkenhancer

import com.qmurzik.vkenhancer.hook.net.BlockRules
import com.qmurzik.vkenhancer.hook.net.NetRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetRulesTest {

    @Test fun hostParsing() {
        assertEquals("api.vk.com", NetRules.hostOf("https://api.vk.com/method/x?y=1"))
        assertEquals("ad.mail.ru", NetRules.hostOf("https://ad.mail.ru:443/path"))
        assertEquals("r.mradx.net", NetRules.hostOf("http://r.mradx.net"))
    }

    @Test fun blocksAdHostsBySuffix() {
        val rules = BlockRules(adHosts = true)
        assertEquals("ad-host", NetRules.blockReason("https://adx.ads.vungle.com/x", rules))
        assertEquals("ad-host", NetRules.blockReason("https://ad.mail.ru/banner", rules))
        assertNull(NetRules.blockReason("https://api.vk.com/method/users.get", rules))
    }

    @Test fun doesNotBlockLegitHostSharingSuffixText() {
        // "myvk.com" must not match the bare token rule for "vk.com".
        val rules = BlockRules(adHosts = true, trackerHosts = true)
        assertNull(NetRules.blockReason("https://static.vk.com/img.png", rules))
    }

    @Test fun analyticsMethodsBlocked() {
        val rules = BlockRules(analyticsMethods = true)
        assertEquals("analytics", NetRules.blockReason("https://api.vk.com/method/stats.trackEvents", rules))
        assertEquals("analytics", NetRules.blockReason("https://api.vk.com/method/adsint.registerAdEvents", rules))
        assertNull(NetRules.blockReason("https://api.vk.com/method/messages.send", rules))
    }

    @Test fun privacyMethods() {
        assertEquals("read-receipt", NetRules.blockReason("https://api.vk.com/method/messages.markAsRead?x=1", BlockRules(noRead = true)))
        assertEquals("typing", NetRules.blockReason("https://api.vk.com/method/messages.setActivity", BlockRules(noTyping = true)))
        assertEquals("online", NetRules.blockReason("https://api.vk.com/method/account.setOnline", BlockRules(offline = true)))
        assertEquals("story-seen", NetRules.blockReason("https://api.vk.com/method/stories.markSeen", BlockRules(storiesIncognito = true)))
    }

    @Test fun customHostAndMethod() {
        val rules = BlockRules(customHosts = listOf("evil.example"), customMethods = setOf("friends.add"))
        assertEquals("custom-host", NetRules.blockReason("https://evil.example/x", rules))
        assertEquals("custom-method", NetRules.blockReason("https://api.vk.com/method/friends.add", rules))
    }

    @Test fun emptyRulesBlockNothing() {
        assertNull(NetRules.blockReason("https://ad.mail.ru/x", BlockRules()))
    }

    @Test fun parseListSplitsOnCommonSeparators() {
        assertEquals(listOf("a", "b", "c", "d"), NetRules.parseList("a, b\nc;d"))
        assertEquals(emptyList<String>(), NetRules.parseList(null))
    }
}
