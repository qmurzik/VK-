package com.vkmod.net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What to cut out of VK API responses. Expressed in public API terms (item type, marked_as_ads, ...). */
public final class FilterRules {
    public final Set<String> feedTypes = new HashSet<>();
    public final List<String> feedTypePrefixes = new ArrayList<>();
    public boolean adTypes, markedAds, reposts, storyAds, disableStories, promoChats, videoAds, recommendedPosts;
    public final List<String> keywords = new ArrayList<>();
    /** Strip unread/new-count style badges from counters-bearing responses. */
    public boolean badges;

    public boolean isEmpty() {
        return feedTypes.isEmpty() && feedTypePrefixes.isEmpty() && !adTypes && !markedAds && !reposts
                && keywords.isEmpty() && !storyAds && !disableStories && !promoChats && !videoAds
                && !recommendedPosts && !badges;
    }
}
