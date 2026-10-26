package com.vkmod.feat;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.vkmod.Mod;
import com.vkmod.core.ModLog;
import com.vkmod.core.Registry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collects direct media links (photos at maximum size, video files, voice messages, stories, clips) that VK
 * itself received from the server, so they can be downloaded from the mod screen. Extraction works on the public
 * API object shapes (photo.sizes, video.files, audio_message.link_*), not on app code.
 */
public final class MediaLog {
    private MediaLog() {}

    public static final class Item {
        public final String kind, url, ref;
        public final long time;
        Item(String kind, String url, String ref, long time) { this.kind = kind; this.url = url; this.ref = ref; this.time = time; }
    }

    private static final int MAX = 300;
    private static final LinkedHashMap<String, Item> ITEMS = new LinkedHashMap<>();
    private static boolean loaded;

    public static boolean enabled() {
        return Registry.isOn("dl_photo") || Registry.isOn("dl_video") || Registry.isOn("dl_clips")
                || Registry.isOn("dl_stories") || Registry.isOn("dl_voice");
    }

    /** Extracts media from one API response body. Pure with respect to Android except for persistence. */
    public static List<Item> extract(String method, String json) {
        List<Item> out = new ArrayList<>();
        try {
            Object root = new JSONTokener(json).nextValue();
            String m = method == null ? "" : method.toLowerCase(Locale.ROOT);
            boolean stories = m.startsWith("stories.");
            boolean clips = m.startsWith("shortvideo.") || m.startsWith("clips.");
            walk(root, stories, clips, out, 0, true, true, true, true, true);
        } catch (Throwable t) {
            ModLog.w("media extract failed", t);
        }
        return out;
    }

    private static void walk(Object node, boolean stories, boolean clips, List<Item> out, int depth,
                             boolean photo, boolean video, boolean voice, boolean st, boolean cl) {
        if (depth > 14 || node == null) return;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) walk(a.opt(i), stories, clips, out, depth + 1, photo, video, voice, st, cl);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        long now = System.currentTimeMillis();
        String ref = o.has("owner_id") && o.has("id") ? o.optString("owner_id") + "_" + o.optString("id") : "";

        JSONArray sizes = o.optJSONArray("sizes");
        if (sizes != null && sizes.length() > 0) {
            String best = bestSize(sizes);
            if (best != null) out.add(new Item(stories ? "story_photo" : "photo", best, ref, now));
        } else if (o.has("orig_photo")) {
            JSONObject op = o.optJSONObject("orig_photo");
            if (op != null && !op.optString("url").isEmpty()) out.add(new Item(stories ? "story_photo" : "photo", op.optString("url"), ref, now));
        }
        JSONObject files = o.optJSONObject("files");
        if (files != null) {
            String best = bestVideo(files);
            if (best != null) {
                String type = o.optString("type", "");
                String kind = stories ? "story_video" : (clips || type.equals("short_video") || o.has("is_clip")) ? "clip" : "video";
                out.add(new Item(kind, best, ref, now));
            }
        }
        String voiceUrl = o.optString("link_mp3", "");
        if (voiceUrl.isEmpty()) voiceUrl = o.optString("link_ogg", "");
        if (!voiceUrl.isEmpty()) out.add(new Item("voice", voiceUrl, ref, now));

        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            if (v instanceof JSONObject || v instanceof JSONArray) {
                if (k.equals("sizes") || k.equals("files")) continue;
                walk(v, stories, clips, out, depth + 1, photo, video, voice, st, cl);
            }
        }
    }

    static String bestSize(JSONArray sizes) {
        String best = null; long bestArea = -1;
        for (int i = 0; i < sizes.length(); i++) {
            JSONObject s = sizes.optJSONObject(i);
            if (s == null) continue;
            String url = s.optString("url", s.optString("src", ""));
            if (url.isEmpty()) continue;
            long area = (long) s.optInt("width") * s.optInt("height");
            if (area >= bestArea) { bestArea = area; best = url; }
        }
        return best;
    }

    static String bestVideo(JSONObject files) {
        String best = null; int bestQ = -1;
        Iterator<String> it = files.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!k.startsWith("mp4_")) continue;
            int q;
            try { q = Integer.parseInt(k.substring(4)); } catch (NumberFormatException e) { continue; }
            String u = files.optString(k, "");
            if (q > bestQ && !u.isEmpty()) { bestQ = q; best = u; }
        }
        if (best == null) { String h = files.optString("hls", ""); if (!h.isEmpty()) return null; }
        return best;
    }

    private static boolean allowed(String kind) {
        switch (kind) {
            case "photo": return Registry.isOn("dl_photo");
            case "video": return Registry.isOn("dl_video");
            case "clip": return Registry.isOn("dl_clips");
            case "story_photo": case "story_video": return Registry.isOn("dl_stories");
            case "voice": return Registry.isOn("dl_voice");
            default: return false;
        }
    }

    /** Called from the network hook for every buffered API response. */
    public static void scan(String method, String json) {
        if (json.indexOf("http") < 0) return;
        List<Item> found = extract(method, json);
        if (found.isEmpty()) return;
        boolean changed = false;
        synchronized (ITEMS) {
            ensureLoaded();
            for (Item it : found) {
                if (!allowed(it.kind) || ITEMS.containsKey(it.url)) continue;
                ITEMS.put(it.url, it);
                changed = true;
                while (ITEMS.size() > MAX) { String first = ITEMS.keySet().iterator().next(); ITEMS.remove(first); }
            }
        }
        if (changed) save();
    }

    public static List<Item> snapshot() {
        synchronized (ITEMS) { ensureLoaded(); List<Item> l = new ArrayList<>(ITEMS.values()); java.util.Collections.reverse(l); return l; }
    }

    public static void clear() { synchronized (ITEMS) { ITEMS.clear(); } save(); }

    private static File file() { return new File(Mod.app().getFilesDir(), "media_log.json"); }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            File f = file();
            if (!f.exists()) return;
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } }
            JSONArray a = new JSONArray(new String(b, "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                ITEMS.put(o.getString("url"), new Item(o.getString("kind"), o.getString("url"), o.optString("ref"), o.optLong("time")));
            }
        } catch (Throwable t) { ModLog.w("media log load failed", t); }
    }

    private static void save() {
        try {
            JSONArray a = new JSONArray();
            synchronized (ITEMS) { for (Item it : ITEMS.values()) { JSONObject o = new JSONObject(); o.put("kind", it.kind); o.put("url", it.url); o.put("ref", it.ref); o.put("time", it.time); a.put(o); } }
            try (FileWriter w = new FileWriter(file())) { w.write(a.toString()); }
        } catch (Throwable t) { ModLog.w("media log save failed", t); }
    }

    public static String extensionFor(Item it) {
        String u = it.url.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?'); if (q > 0) u = u.substring(0, q);
        if (it.kind.equals("voice")) return u.endsWith(".ogg") ? "ogg" : "mp3";
        if (it.kind.contains("video") || it.kind.equals("clip")) return "mp4";
        return u.endsWith(".png") ? "png" : u.endsWith(".webp") ? "webp" : "jpg";
    }

    /** Enqueues a download into the public Downloads folder. @return null on success, otherwise an error text. */
    public static String download(Context c, Item it) {
        try {
            DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            String name = "VK_" + it.kind + "_" + (it.ref.isEmpty() ? Long.toString(it.time) : it.ref) + "." + extensionFor(it);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(it.url));
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VK/" + name);
            dm.enqueue(r);
            return null;
        } catch (Throwable t) {
            ModLog.w("download failed", t);
            return t.getMessage() == null ? t.toString() : t.getMessage();
        }
    }
}
