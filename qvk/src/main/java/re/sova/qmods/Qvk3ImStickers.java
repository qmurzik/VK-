package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The user's sticker packs (store.getProducts, active) in a sheet; recent stickers first; a tap sends the sticker. */
final class Qvk3ImStickers {
    static final class Pack {
        final String title;
        final List<Long> ids = new ArrayList<>();
        final List<String> urls = new ArrayList<>();

        Pack(String title) {
            this.title = title;
        }
    }

    private static List<Pack> packs;

    private Qvk3ImStickers() {
    }

    static String url(long id) {
        return "https://vk.com/sticker/1-" + id + "-128";
    }

    /** Parses store.getProducts (both the old stickers_ids form and the newer list of sticker objects). Pure. */
    static List<Pack> parse(JSONObject r) {
        List<Pack> out = new ArrayList<>();
        JSONArray items = r == null ? null : r.optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject p = items.optJSONObject(i);
            if (p == null) continue;
            Pack pack = new Pack(p.optString("title", "Стикеры"));
            Object st = p.opt("stickers");
            if (st instanceof JSONArray) {
                JSONArray a = (JSONArray) st;
                for (int k = 0; k < a.length(); k++) {
                    JSONObject s = a.optJSONObject(k);
                    if (s == null) continue;
                    long id = s.optLong("sticker_id");
                    JSONObject img = Qvk3ImModel.bestSize(s.optJSONArray("images"), 256);
                    pack.ids.add(id);
                    pack.urls.add(img == null ? url(id) : Qvk3ImModel.sizeUrl(img));
                }
            } else if (st instanceof JSONObject) {
                JSONArray ids = ((JSONObject) st).optJSONArray("sticker_ids");
                if (ids == null) ids = ((JSONObject) st).optJSONArray("stickers_ids");
                if (ids != null) for (int k = 0; k < ids.length(); k++) {
                    pack.ids.add(ids.optLong(k));
                    pack.urls.add(url(ids.optLong(k)));
                }
            }
            if (!pack.ids.isEmpty()) out.add(pack);
        }
        return out;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("qvk3_im_drafts", Context.MODE_PRIVATE);
    }

    static List<Long> recent(Context c) {
        List<Long> out = new ArrayList<>();
        for (String s : prefs(c).getString("recent_stickers", "").split(",")) {
            try {
                if (!s.isEmpty()) out.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static void used(Context c, long id) {
        List<Long> r = recent(c);
        r.remove(id);
        r.add(0, id);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(16, r.size()); i++) b.append(i == 0 ? "" : ",").append(r.get(i));
        prefs(c).edit().putString("recent_stickers", b.toString()).apply();
    }

    static void open(Qvk3ImChatView chat) {
        android.app.Activity a = chat.a;
        if (packs != null) {
            show(chat, packs);
            return;
        }
        Qvk3ImStore store = Qvk3ImStore.get(a);
        JSONObject cached = store.readCache("stickers");
        if (cached != null) {
            packs = parse(cached);
            show(chat, packs);
        }
        Qvk3Jobs.io(() -> {
            try {
                JSONObject r = Qvk3ImApi.call("store.getProducts", "type", "stickers", "filters", "active", "extended", "1");
                store.writeCache("stickers", r);
                List<Pack> fresh = parse(r);
                Qvk3Jobs.main(() -> {
                    boolean first = packs == null;
                    packs = fresh;
                    if (first) show(chat, packs);
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> {
                    if (packs == null) Qvk3Ui.toast(a, "Не удалось загрузить стикеры: " + Qvk3Jobs.message(e));
                });
            }
        });
    }

    private static void show(Qvk3ImChatView chat, List<Pack> ps) {
        android.app.Activity a = chat.a;
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Qvk3Ui.dp(a, 12), 0, Qvk3Ui.dp(a, 12), Qvk3Ui.dp(a, 8));
        List<Long> recent = recent(a);
        if (!recent.isEmpty()) {
            Pack r = new Pack("Недавние");
            for (Long id : recent) {
                r.ids.add(id);
                r.urls.add(url(id));
            }
            grid(chat, col, p, r);
        }
        for (Pack pack : ps) grid(chat, col, p, pack);
        if (ps.isEmpty() && recent.isEmpty()) col.addView(Qvk3Sheet.note(a, p, "У вас нет наборов стикеров. Добавить их можно в магазине стикеров ВКонтакте."));
        Qvk3Ui.dialog(a).setTitle("Стикеры").setView(col).setNegativeButton("Закрыть", null).show();
    }

    private static void grid(Qvk3ImChatView chat, LinearLayout col, Qvk3Ui.Palette p, Pack pack) {
        android.app.Activity a = chat.a;
        TextView t = Qvk3Sheet.section(a, p, pack.title);
        col.addView(t);
        int per = 5, size = (a.getResources().getDisplayMetrics().widthPixels - Qvk3Ui.dp(a, 24)) / per;
        LinearLayout row = null;
        for (int i = 0; i < pack.ids.size(); i++) {
            if (i % per == 0) {
                row = new LinearLayout(a);
                row.setGravity(Gravity.START);
                col.addView(row);
            }
            long id = pack.ids.get(i);
            String u = pack.urls.get(i);
            ImageView iv = new ImageView(a);
            iv.setPadding(Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 4));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setContentDescription("Стикер");
            iv.setTag(u);
            Qvk3Images.get(a).load(u, 256, b -> {
                if (b != null && u.equals(iv.getTag())) iv.setImageBitmap(b);
            });
            iv.setOnClickListener(v -> {
                used(a, id);
                android.app.Dialog d = Qvk3Sheet.lastShown;
                if (d != null && d.isShowing()) d.dismiss();
                chat.sendSticker(id, u);
            });
            row.addView(iv, new LinearLayout.LayoutParams(size, size));
        }
    }
}
