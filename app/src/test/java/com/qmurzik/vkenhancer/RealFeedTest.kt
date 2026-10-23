package com.qmurzik.vkenhancer

import com.qmurzik.vkenhancer.hook.net.ApiFilter
import com.qmurzik.vkenhancer.hook.net.FilterRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Regression test built from a real newsfeed.getFeed body captured in logs (type:"ads" item). */
class RealFeedTest {
    private val body = """
        {"response":{"page_size":10,"feed_type":"feed","profiles":[{"id":1}],"groups":[{"id":2}],
        "items":[
          {"advertiser_info_url":"x","ads":[{"button":"Установить","ad_data":"qR1D"}],"is_async":false,
           "source_id":-1,"type":"ads","ads_title":"Реклама","hide_reasons":[],"ad_source":"mt",
           "ads_id1":"a","date":1,"track_code":"t","ads_id2":"b","header":{}},
          {"type":"post","text":"normal","source_id":5}
        ],"next_from":"nf"}}
    """.trimIndent()

    @Test fun removesRealTypedAdsItem() {
        val out = ApiFilter.filter("newsfeed.getFeed", body, FilterRules(adTypes = true, markedAds = true))
        assertNotNull("type:ads item must be removed", out)
        val items = JSONObject(out!!).getJSONObject("response").getJSONArray("items")
        assertEquals(1, items.length())
        assertEquals("post", items.getJSONObject(0).getString("type"))
    }
}
