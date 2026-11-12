package re.sova.qmods;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * VK's message reactions: ids and their pictures come from messages.getReactionsAssets (the same assets the VK app
 * draws), so QVK never guesses which emoji an id means. Cached in memory and on disk.
 */
final class Qvk3ImReactions {
    private static final Map<Integer, String> urls = new TreeMap<>();
    private static boolean loading;
    static volatile android.app.Dialog lastDialog;

    private Qvk3ImReactions() {
    }

    static String url(int id) {
        String u = urls.get(id);
        return u == null ? "" : u;
    }

    static List<Integer> ids() {
        return new ArrayList<>(urls.keySet());
    }

    static void load(Context c, Runnable changed) {
        if (!urls.isEmpty() || loading) return;
        Qvk3ImStore store = Qvk3ImStore.get(c);
        JSONObject cached = store.readCache("reactions");
        if (cached != null) apply(cached);
        if (!urls.isEmpty()) return;
        loading = true;
        Qvk3Jobs.io(() -> {
            try {
                JSONObject r = Qvk3ImApi.call("messages.getReactionsAssets");
                store.writeCache("reactions", r);
                Qvk3Jobs.main(() -> {
                    apply(r);
                    loading = false;
                    if (changed != null) changed.run();
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> loading = false);
            }
        });
    }

    private static void apply(JSONObject r) {
        JSONArray assets = r.optJSONArray("assets");
        if (assets == null) return;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            JSONObject links = a == null ? null : a.optJSONObject("links");
            if (links == null) continue;
            String u = links.optString("static", "");
            if (!u.isEmpty()) urls.put(a.optInt("reaction_id"), u);
        }
    }
}
