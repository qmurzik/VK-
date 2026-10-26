package com.vkmod.net;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Filters one VK API response body according to [FilterRules]. Pure logic, unit-tested on the JVM. */
public final class ApiFilter {
    private ApiFilter() {}

    private static final int MAX_DEPTH = 12;
    private static final String[] STORY_AD_KEYS = {"ads_settings", "ads", "ads_block", "promo_data", "ad_config"};
    private static final String[] VIDEO_AD_KEYS = {"ads_info", "ads"};

    /** `https://api.vk.ru/method/newsfeed.getFeed?v=5.x` -> `newsfeed.getFeed`. */
    public static String methodFromUrl(String url) {
        int idx = url.indexOf("/method/");
        if (idx < 0) return null;
        int start = idx + 8, end = start;
        while (end < url.length()) {
            char c = url.charAt(end);
            if (c == '?' || c == '/' || c == '#' || c == '&') break;
            end++;
        }
        return end > start ? url.substring(start, end) : null;
    }

    /** @return the rewritten body, or null if nothing was removed (original bytes stay untouched). */
    public static String filter(String method, String body, FilterRules rules) {
        if (rules == null || rules.isEmpty()) return null;
        int s = 0;
        while (s < body.length() && Character.isWhitespace(body.charAt(s))) s++;
        if (s >= body.length() || body.charAt(s) != '{') return null;
        if (!mightMatch(method, body, rules)) return null;
        JSONObject root;
        try {
            Object v = new JSONTokener(body).nextValue();
            if (!(v instanceof JSONObject)) return null;
            root = (JSONObject) v;
        } catch (Exception e) {
            return null;
        }
        Ctx ctx = new Ctx(method.toLowerCase(Locale.ROOT), rules);
        Object response = root.opt("response");
        if (response == null) return null;
        walk(response, ctx, 0);
        return ctx.changed ? root.toString() : null;
    }

    private static boolean isFeedMethod(String m) {
        return m.startsWith("newsfeed.") || m.startsWith("execute") || m.startsWith("wall.get") || m.contains("feed");
    }

    private static boolean mightMatch(String method, String body, FilterRules r) {
        String m = method.toLowerCase(Locale.ROOT);
        if (r.disableStories && m.startsWith("stories.")) return true;
        if (isFeedMethod(m) && (!r.feedTypes.isEmpty() || !r.feedTypePrefixes.isEmpty() || r.reposts
                || !r.keywords.isEmpty() || r.recommendedPosts)) return true;
        if (r.adTypes && (body.contains("\"ads_") || body.contains("\"ads\"") || body.contains("\"ad_data\""))) return true;
        if (r.markedAds && (body.contains("\"marked_as_ads\"") || body.contains("\"ad_data\""))) return true;
        if (r.storyAds && (m.startsWith("stories.") || m.startsWith("execute")) && body.contains("\"ads")) return true;
        if (r.promoChats && body.contains("\"is_promo\"")) return true;
        if (r.videoAds && body.contains("\"ads")) return true;
        return false;
    }

    private static final class Ctx {
        final String method; final FilterRules rules; boolean changed;
        final boolean feed, stories;
        Ctx(String method, FilterRules rules) {
            this.method = method; this.rules = rules;
            feed = isFeedMethod(method);
            stories = method.startsWith("stories.") || method.startsWith("execute");
        }
    }

    private static void walk(Object node, Ctx ctx, int depth) {
        if (depth > MAX_DEPTH) return;
        if (node instanceof JSONObject) walkObject((JSONObject) node, ctx, depth);
        else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) walk(a.opt(i), ctx, depth + 1);
        }
    }

    private static void walkObject(JSONObject obj, Ctx ctx, int depth) {
        FilterRules rules = ctx.rules;
        if (rules.storyAds && ctx.stories) removeKeys(obj, STORY_AD_KEYS, ctx);
        if (rules.videoAds && obj.has("ads_info")) removeKeys(obj, VIDEO_AD_KEYS, ctx);

        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) keys.add(it.next());
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                if (rules.disableStories && ctx.method.startsWith("stories.get")
                        && (key.equals("items") || key.equals("stories")) && arr.length() > 0) {
                    try { obj.put(key, new JSONArray()); ctx.changed = true; } catch (Exception ignored) { }
                    continue;
                }
                JSONArray filtered = filterArray(arr, ctx);
                JSONArray use = filtered != null ? filtered : arr;
                if (filtered != null) {
                    try { obj.put(key, filtered); ctx.changed = true; } catch (Exception ignored) { }
                }
                for (int i = 0; i < use.length(); i++) walk(use.opt(i), ctx, depth + 1);
            } else {
                walk(value, ctx, depth + 1);
            }
        }
    }

    private static void removeKeys(JSONObject obj, String[] keys, Ctx ctx) {
        for (String k : keys) {
            Object v = obj.opt(k);
            if (v instanceof JSONObject || v instanceof JSONArray) {
                obj.remove(k);
                ctx.changed = true;
            }
        }
    }

    private static JSONArray filterArray(JSONArray array, Ctx ctx) {
        JSONArray out = null;
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            boolean drop = item instanceof JSONObject && shouldDrop((JSONObject) item, ctx);
            if (drop && out == null) {
                out = new JSONArray();
                for (int j = 0; j < i; j++) out.put(array.opt(j));
            }
            if (!drop && out != null) out.put(item);
        }
        return out;
    }

    static boolean shouldDrop(JSONObject item, Ctx c) {
        FilterRules rules = c.rules;
        String type = item.optString("type", "");
        if (rules.adTypes && (type.equals("ads") || (type.startsWith("ads_") && !type.equals("ads_disabled")))) return true;
        if (rules.markedAds && (isTruthy(item.opt("marked_as_ads")) || item.has("ad_data"))) return true;
        if (rules.promoChats) {
            JSONObject conv = item.optJSONObject("conversation");
            if (conv != null && isTruthy(conv.opt("is_promo"))) return true;
        }
        if (!c.feed) return false;
        if (!type.isEmpty()) {
            if (rules.feedTypes.contains(type)) return true;
            for (String p : rules.feedTypePrefixes) if (type.startsWith(p)) return true;
        }
        boolean isPost = type.isEmpty() || type.equals("post") || type.equals("wall_photo");
        if (rules.recommendedPosts && isPost && (item.has("recommendation_info") || containsFromDiscover(item))) return true;
        if (rules.reposts && isPost) {
            JSONArray h = item.optJSONArray("copy_history");
            if (h != null && h.length() > 0) return true;
        }
        if (!rules.keywords.isEmpty() && isPost && containsKeyword(item, rules.keywords)) return true;
        return false;
    }

    /** Test hook. */
    public static boolean dropFor(String method, JSONObject item, FilterRules rules) {
        return shouldDrop(item, new Ctx(method.toLowerCase(Locale.ROOT), rules));
    }

    private static boolean containsKeyword(JSONObject post, List<String> keywords) {
        StringBuilder text = new StringBuilder(post.optString("text", ""));
        JSONArray h = post.optJSONArray("copy_history");
        if (h != null) for (int i = 0; i < h.length(); i++) {
            JSONObject o = h.optJSONObject(i);
            if (o != null) text.append('\n').append(o.optString("text", ""));
        }
        if (text.length() == 0) return false;
        String lower = text.toString().toLowerCase(Locale.ROOT);
        for (String k : keywords) if (!k.isEmpty() && lower.contains(k)) return true;
        return false;
    }

    private static boolean containsFromDiscover(JSONObject item) {
        String[] keys = {"feed_source", "caption", "header", "type_feed_source"};
        for (String key : keys) {
            Object v = item.opt(key);
            if (v instanceof JSONObject && "from_discover".equals(((JSONObject) v).optString("type"))) return true;
        }
        return "from_discover".equals(item.optString("feed_source")) || "from_discover".equals(item.optString("type_feed_source"));
    }

    private static boolean isTruthy(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) return v.equals("1") || ((String) v).equalsIgnoreCase("true");
        return false;
    }
}
