package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * IDEA-0001: VK music (the «Музыка ВК» tab, IDEA-0008) shows subscription offers as catalog blocks — «Целый месяц — 0 ₽
 * … Подключить». They come in the API answer of catalog.* methods, so they are dropped there, before VK draws them:
 * offer layouts (small_banner_offer, synthetic_offline_music_subscription_*) and catalog_banners whose banners are
 * subscription offers. Content banners (playlists, curators) stay. Pure JSON logic: unit-tested on the JVM.
 */
public final class Qvk3CatalogAds {
    public static final String PREF = "qvk3_hide_subscription_promo";

    private Qvk3CatalogAds() {
    }

    public static void filter(Object response, String method) {
        try {
            if (Qvk3Kill.off("catalog_ads")) return;
            String m = method == null ? "" : method.toLowerCase(Locale.ROOT);
            if (response instanceof JSONObject && (m.startsWith("superapp.") || m.contains("showcase"))) {
                if (RuntimeBridge.prefs == null || RuntimeBridge.prefs.getBoolean(PREF, true)) dropStoreNags((JSONObject) response, 0);
                return;
            }
            if (method == null || !m.startsWith("catalog.")) return;
            if (!(response instanceof JSONObject)) return;
            if (RuntimeBridge.prefs != null && !RuntimeBridge.prefs.getBoolean(PREF, true)) return;
            strip((JSONObject) response);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.catalog.ads", t);
        }
    }

    /** Removes subscription-offer blocks anywhere in a catalog answer; returns how many were removed. */
    public static int strip(JSONObject root) throws Exception {
        Set<String> promoBanners = new HashSet<>();
        collectBanners(root, promoBanners, 0);
        return walk(root, promoBanners, 0);
    }

    static void collectBanners(JSONObject o, Set<String> out, int depth) {
        if (depth > 6) return;
        JSONArray b = o.optJSONArray("catalog_banners");
        if (b != null) for (int i = 0; i < b.length(); i++) {
            JSONObject x = b.optJSONObject(i);
            if (x != null && offer(x.toString())) out.add(String.valueOf(x.opt("id")));
        }
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            Object v = o.opt(it.next());
            if (v instanceof JSONObject) collectBanners((JSONObject) v, out, depth + 1);
        }
    }

    static boolean offer(String json) {
        String s = json.toLowerCase(Locale.ROOT);
        return s.contains("subscription") || s.contains("подписк") || s.contains("buy_") || s.contains("combo")
                || s.contains("trial") || s.contains("0 ₽") || s.contains("0₽") || s.contains("подключить");
    }

    static boolean promoBlock(JSONObject block, Set<String> promoBanners) {
        String type = block.optString("data_type").toLowerCase(Locale.ROOT);
        JSONObject layout = block.optJSONObject("layout");
        String name = layout == null ? "" : layout.optString("name").toLowerCase(Locale.ROOT);
        if (name.contains("banner_offer") || name.contains("music_subscription") || type.contains("subscription_offer")
                || type.contains("available_offer")) return true;
        if (!type.equals("catalog_banners")) return false;
        JSONArray ids = block.optJSONArray("catalog_banner_ids");
        if (ids == null || ids.length() == 0) return false;
        for (int i = 0; i < ids.length(); i++) if (!promoBanners.contains(String.valueOf(ids.opt(i)))) return false;
        return true;
    }

    static int walk(JSONObject o, Set<String> promoBanners, int depth) throws Exception {
        if (depth > 6) return 0;
        int removed = 0;
        JSONArray blocks = o.optJSONArray("blocks");
        if (blocks != null) {
            JSONArray kept = new JSONArray();
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject b = blocks.optJSONObject(i);
                if (b != null && promoBlock(b, promoBanners)) removed++;
                else kept.put(blocks.get(i));
            }
            if (removed > 0) o.put("blocks", kept);
        }
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            if (v instanceof JSONObject) removed += walk((JSONObject) v, promoBanners, depth + 1);
            else if (v instanceof JSONArray && !k.equals("blocks")) {
                JSONArray a = (JSONArray) v;
                for (int i = 0; i < a.length() && i < 50; i++) if (a.opt(i) instanceof JSONObject) removed += walk(a.getJSONObject(i), promoBanners, depth + 1);
            }
        }
        return removed;
    }

    /**
     * BUG-0026: «Установите VK из RuStore — это нужно, чтобы получать обновления» in «Сервисы → Обзор» (superApp.getShowcase).
     * QVK is updated by QVK, so any showcase item that sends to RuStore is dropped. Returns how many were removed.
     */
    public static int dropStoreNags(JSONObject o, int depth) throws Exception {
        if (depth > 7) return 0;
        int removed = 0;
        java.util.Iterator<String> it = o.keys();
        java.util.List<String> keys = new java.util.ArrayList<>();
        while (it.hasNext()) keys.add(it.next());
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof JSONArray) {
                JSONArray a = (JSONArray) v, kept = new JSONArray();
                int before = removed;
                for (int i = 0; i < a.length(); i++) {
                    Object e = a.opt(i);
                    if (e instanceof JSONObject && storeNag(e.toString())) {
                        removed++;
                        continue;
                    }
                    if (e instanceof JSONObject) removed += dropStoreNags((JSONObject) e, depth + 1);
                    kept.put(e);
                }
                if (removed > before) o.put(k, kept);
            } else if (v instanceof JSONObject) {
                removed += dropStoreNags((JSONObject) v, depth + 1);
            }
        }
        return removed;
    }

    static boolean storeNag(String json) {
        String s = json.toLowerCase(Locale.ROOT);
        // small objects only: a whole section that merely mentions RuStore somewhere deep inside is not removed
        return s.length() < 6000 && (s.contains("rustore") || s.contains("ru_store")) && (s.contains("установите") || s.contains("обновлен")
                || s.contains("install") || s.contains("update") || s.contains("banner"));
    }
}
