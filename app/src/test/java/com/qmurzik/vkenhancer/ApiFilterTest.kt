package com.qmurzik.vkenhancer

import com.qmurzik.vkenhancer.hook.net.ApiFilter
import com.qmurzik.vkenhancer.hook.net.FilterRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiFilterTest {

    private fun feed(vararg items: String): String {
        val arr = items.joinToString(",")
        return """{"response":{"items":[$arr],"next_from":"x"}}"""
    }

    private fun itemsOf(body: String): List<String> {
        val arr = JSONObject(body).getJSONObject("response").getJSONArray("items")
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("type", arr.getJSONObject(it).toString()) }
    }

    @Test fun methodFromUrl() {
        assertEquals("newsfeed.getFeed", ApiFilter.methodFromUrl("https://api.vk.ru/method/newsfeed.getFeed?v=5.207&x=1"))
        assertEquals("stories.get", ApiFilter.methodFromUrl("https://api.vk.com/method/stories.get"))
        assertNull(ApiFilter.methodFromUrl("https://sun9-1.userapi.com/image.jpg"))
    }

    @Test fun removesAdTypes() {
        val body = feed(
            """{"type":"post","text":"hi"}""",
            """{"type":"ads_post","text":"buy"}""",
            """{"type":"promo_button"}""",
        )
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(adTypes = true))!!
        assertEquals(listOf("post", "promo_button"), itemsOf(out))
    }

    @Test fun removesMarkedAds() {
        val body = feed("""{"type":"post","marked_as_ads":1}""", """{"type":"post","marked_as_ads":0}""")
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(markedAds = true))!!
        assertEquals(1, itemsOf(out).size)
    }

    @Test fun removesByCustomTypeAndPrefix() {
        val body = feed("""{"type":"post"}""", """{"type":"clip"}""", """{"type":"clips_block"}""")
        val rules = FilterRules(feedTypes = setOf("clip"), feedTypePrefixes = listOf("clips_"))
        val out = ApiFilter.filter("newsfeed.getFeed", body, rules)!!
        assertEquals(listOf("post"), itemsOf(out))
    }

    @Test fun removesReposts() {
        val body = feed("""{"type":"post","copy_history":[{"text":"orig"}]}""", """{"type":"post"}""")
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(reposts = true))!!
        assertEquals(1, itemsOf(out).size)
    }

    @Test fun keywordFilterMatchesRepostText() {
        val body = feed(
            """{"type":"post","text":"обычный пост"}""",
            """{"type":"post","text":"","copy_history":[{"text":"Реклама КАЗИНО тут"}]}""",
        )
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(keywords = listOf("казино")))!!
        assertEquals(1, itemsOf(out).size)
    }

    @Test fun nonFeedMethodKeepsTypeFiltersOff() {
        // feedTypes must not touch an arbitrary method's items.
        val body = """{"response":{"items":[{"type":"clip"}]}}"""
        val out = ApiFilter.filter("account.getProfileInfo", body, FilterRules(feedTypes = setOf("clip")))
        assertNull("non-feed methods are not type-filtered", out)
    }

    @Test fun adTypesWorkOnAnyMethod() {
        val body = """{"response":{"items":[{"type":"ads_post"},{"type":"video"}]}}"""
        val out = ApiFilter.filter("video.get", body, FilterRules(adTypes = true))!!
        assertEquals(listOf("video"), itemsOf(out))
    }

    @Test fun disableStoriesEmptiesList() {
        val body = """{"response":{"count":2,"items":[{"id":1},{"id":2}]}}"""
        val out = ApiFilter.filter("stories.get", body, FilterRules(disableStories = true))!!
        assertEquals(0, JSONObject(out).getJSONObject("response").getJSONArray("items").length())
    }

    @Test fun storyAdsRemovesStructuredAdKeys() {
        val body = """{"response":{"items":[{"id":1,"ads_settings":{"interval":5}}]}}"""
        val out = ApiFilter.filter("stories.get", body, FilterRules(storyAds = true))!!
        assertFalse(out.contains("ads_settings"))
    }

    @Test fun returnsNullWhenNothingMatches() {
        val body = feed("""{"type":"post"}""")
        assertNull(ApiFilter.filter("newsfeed.getFeed", body, FilterRules(adTypes = true, markedAds = true)))
    }

    @Test fun ignoresNonJsonBodies() {
        assertNull(ApiFilter.filter("newsfeed.getFeed", "<html>nope</html>", FilterRules(adTypes = true)))
    }

    @Test fun removesModernTypedAdBlock() {
        // New feed format: ad is a block with type "ads" and an ad_data payload, nested in an
        // arbitrarily-named array rather than "items".
        val body = """{"response":{"blocks":[{"type":"post","text":"hi"},{"type":"ads","ad_data":{"mob_sdk":{}}}]}}"""
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(adTypes = true, markedAds = true))!!
        val blocks = JSONObject(out).getJSONObject("response").getJSONArray("blocks")
        assertEquals(1, blocks.length())
        assertEquals("post", blocks.getJSONObject(0).getString("type"))
    }

    @Test fun removesRecommendedPostsFromUnsubscribed() {
        val body = feed(
            """{"type":"post","text":"my sub","source_id":10}""",
            """{"type":"post","text":"reco","source_id":-20,"recommendation_info":{"track_code":"x"}}""",
            """{"type":"post","text":"discover","feed_source":{"type":"from_discover"}}""",
        )
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(recommendedPosts = true))!!
        assertEquals(1, itemsOf(out).size)
    }

    @Test fun keepsNormalPostsWhenRecommendationsOff() {
        val body = feed("""{"type":"post","recommendation_info":{"track_code":"x"}}""")
        assertNull(ApiFilter.filter("newsfeed.getFeed", body, FilterRules(recommendedPosts = false)))
    }

    @Test fun keepsUnrelatedArrays() {
        // profiles/groups arrays must be untouched by the recursive filter.
        val body = """{"response":{"items":[{"type":"ads","ad_data":{}}],"profiles":[{"id":1},{"id":2}],"groups":[{"id":3}]}}"""
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(adTypes = true))!!
        val resp = JSONObject(out).getJSONObject("response")
        assertEquals(0, resp.getJSONArray("items").length())
        assertEquals(2, resp.getJSONArray("profiles").length())
        assertEquals(1, resp.getJSONArray("groups").length())
    }

    @Test fun keepsAdsDisabledSignal() {
        // ads_disabled is a server flag item, not an ad; adTypes must not drop it.
        val body = feed("""{"type":"ads_disabled"}""", """{"type":"ads_post"}""")
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(adTypes = true))!!
        assertTrue(itemsOf(out).contains("ads_disabled"))
    }
}
