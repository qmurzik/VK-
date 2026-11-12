package re.sova.qmods;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Lyrics loading with the fixed priority VK → local → external (see {@link Qvk3Lyrics}).
 *
 * Local text: a user .lrc next to an imported file is not reachable through SAF, so "local" means the
 * per-track file in QVK's own storage (filesDir/qvk3_lyrics), which the user can also fill by importing an
 * .lrc from the player. VK results are cached there too, labelled with their real source.
 */
public final class Qvk3LyricsManager {
    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "qvk3_lyrics");
        d.mkdirs();
        return d;
    }

    private static File file(Context c, Qvk3Track t) {
        return new File(dir(c), t.key().replaceAll("[^A-Za-z0-9_-]", "_") + ".lrc");
    }

    /**
     * Blocking; call off the main thread. Returns null when no trustworthy text exists.
     * Order: VK → saved → .lrc next to the audio file → (opt-in) LRCLIB, LRCLIB search, NetEase, lyrics.ovh.
     * Synced text wins: a plain VK text is kept only until an external source finds a synced one.
     */
    public static Qvk3Lyrics load(Context c, Qvk3Track t) throws Exception {
        Qvk3Lyrics plain = null;
        // 1. VK
        if (t.id != 0 && t.hasLyrics) {
            try {
                Qvk3Lyrics vk = Qvk3MusicSource.lyrics(t);
                if (vk != null && !vk.lines.isEmpty()) {
                    if (vk.synced || !Qvk3MusicSettings.lyricsExternal(c)) {
                        save(c, t, vk);
                        return vk;
                    }
                    plain = vk;
                }
            } catch (Exception e) {
                RuntimeFaults.record("qvk3.lyrics.vk", e);
            }
        }
        // 2. locally saved (user import, user choice or an earlier result)
        Qvk3Lyrics local = cached(c, t);
        if (local != null) return local;
        // 3. a .lrc / .txt next to a downloaded or imported audio file
        Qvk3Lyrics side = sidecar(t);
        if (side != null) {
            save(c, t, side);
            return side;
        }
        // 4. external, opt-in, strict match only; stop at the first synced text
        if (Qvk3MusicSettings.lyricsExternal(c) && !t.artist.isEmpty() && !t.title.isEmpty()) {
            for (int src = 0; src < SOURCES; src++) {
                Qvk3Lyrics l = external(src, t);
                if (l == null) continue;
                if (l.synced) {
                    save(c, t, l);
                    return l;
                }
                if (plain == null) plain = l;
            }
        }
        if (plain != null) save(c, t, plain);
        return plain;
    }

    static final int SOURCES = 4;

    static Qvk3Lyrics external(int src, Qvk3Track t) {
        try {
            switch (src) {
                case 0:
                    return lrclib(t);
                case 1:
                    return lrclibSearch(t);
                case 2:
                    return netease(t);
                default:
                    return ovh(t);
            }
        } catch (Exception e) {
            android.util.Log.i("QVK3Lyrics", "source " + src + " failed: " + Qvk3Jobs.message(e));
            return null;
        }
    }

    /** Everything every source has for this track (for «Другой текст…»); runs the sources in parallel. */
    public static java.util.List<Qvk3Lyrics> searchAll(Context c, Qvk3Track t) {
        java.util.List<Qvk3Lyrics> out = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.List<Thread> ts = new java.util.ArrayList<>();
        if (t.id != 0 && t.hasLyrics) ts.add(new Thread(() -> {
            try {
                Qvk3Lyrics vk = Qvk3MusicSource.lyrics(t);
                if (vk != null && !vk.lines.isEmpty()) out.add(vk);
            } catch (Exception ignored) {
            }
        }));
        ts.add(new Thread(() -> {
            Qvk3Lyrics s = sidecar(t);
            if (s != null) out.add(s);
        }));
        if (!t.artist.isEmpty() && !t.title.isEmpty()) for (int i = 0; i < SOURCES; i++) {
            final int src = i;
            ts.add(new Thread(() -> {
                Qvk3Lyrics l = external(src, t);
                if (l != null) out.add(l);
            }));
        }
        for (Thread th : ts) th.start();
        for (Thread th : ts) {
            try {
                th.join(15000);
            } catch (InterruptedException ignored) {
            }
        }
        java.util.List<Qvk3Lyrics> res = new java.util.ArrayList<>(out);
        // synced first, then by source name; drop duplicates with the same text
        res.sort((x, y) -> x.synced != y.synced ? (x.synced ? -1 : 1) : x.source.compareTo(y.source));
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<Qvk3Lyrics> uniq = new java.util.ArrayList<>();
        for (Qvk3Lyrics l : res) if (seen.add((l.synced ? "s" : "p") + Qvk3Lyrics.normal(l.plain()).hashCode())) uniq.add(l);
        return uniq;
    }

    /** User picked a text: it replaces the saved one for this track. */
    public static void choose(Context c, Qvk3Track t, Qvk3Lyrics l) {
        save(c, t, l);
    }

    static Qvk3Lyrics sidecar(Qvk3Track t) {
        if (t.local == null || t.local.isEmpty() || t.local.startsWith("content:")) return null;
        File audio = new File(t.local);
        String base = audio.getName().replaceFirst("\\.[^.]+$", "");
        for (String ext : new String[]{".lrc", ".LRC", ".txt"}) {
            File f = new File(audio.getParentFile(), base + ext);
            if (!f.isFile() || f.length() > 512 * 1024) continue;
            try {
                Qvk3Lyrics l = Qvk3Lyrics.parse(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Qvk3Lyrics.SOURCE_LOCAL);
                if (l != null) return l;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static String http(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(10000);
        con.setRequestProperty("User-Agent", url.contains("163.com") ? "Mozilla/5.0 (Linux; Android 14) QVK" : "QVK Music (https://qmods.ru)");
        try {
            int code = con.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new java.io.IOException(new URL(url).getHost() + ": HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                byte[] data = new byte[1 << 20];
                int n = Qvk3Id3.readFully(in, data);
                return new String(data, 0, n, StandardCharsets.UTF_8);
            }
        } finally {
            con.disconnect();
        }
    }

    static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    /** LRCLIB fuzzy search (title + artist), original language and synced text preferred. */
    static Qvk3Lyrics lrclibSearch(Qvk3Track t) throws Exception {
        String body = http("https://lrclib.net/api/search?track_name=" + enc(t.title) + "&artist_name=" + enc(t.artist));
        return body == null ? null : Qvk3Lyrics.fromLrclibSearch(new org.json.JSONArray(body), t.artist, t.title, t.duration);
    }

    /** NetEase Cloud Music: a large catalogue of synced LRC, including Russian songs. Only artist and title are sent. */
    static Qvk3Lyrics netease(Qvk3Track t) throws Exception {
        String s = http("https://music.163.com/api/search/get?type=1&limit=10&s=" + enc(t.artist + " " + t.title));
        if (s == null) return null;
        long id = Qvk3Lyrics.neteaseSongId(new JSONObject(s), t.artist, t.title, t.duration);
        if (id == 0) return null;
        String l = http("https://music.163.com/api/song/lyric?lv=1&id=" + id);
        return l == null ? null : Qvk3Lyrics.fromNetease(new JSONObject(l), t.title);
    }

    /** lyrics.ovh: plain text only, as a last resort. */
    static Qvk3Lyrics ovh(Qvk3Track t) throws Exception {
        String body = http("https://api.lyrics.ovh/v1/" + enc(t.artist).replace("+", "%20") + "/" + enc(t.title).replace("+", "%20"));
        return body == null ? null : Qvk3Lyrics.fromOvh(new JSONObject(body), t.title);
    }

    static Qvk3Lyrics cached(Context c, Qvk3Track t) {
        File f = file(c, t);
        if (!f.isFile() || f.length() > 512 * 1024) return null;
        try {
            String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String source = Qvk3Lyrics.SOURCE_CACHE;
            if (text.startsWith("#source:")) {
                int nl = text.indexOf('\n');
                source = text.substring(8, nl < 0 ? text.length() : nl).trim() + " · сохранено";
                text = nl < 0 ? "" : text.substring(nl + 1);
            }
            return Qvk3Lyrics.parse(text, source);
        } catch (Exception e) {
            return null;
        }
    }

    static void save(Context c, Qvk3Track t, Qvk3Lyrics l) {
        try (FileOutputStream out = new FileOutputStream(file(c, t))) {
            out.write(("#source:" + l.source + "\n" + l.toLrc()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.lyrics.save", e);
        }
    }

    /** Imports a user .lrc/.txt for this track (picked through the system file picker). */
    public static Qvk3Lyrics importFile(Context c, Qvk3Track t, Uri uri) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("Файл недоступен");
            byte[] data = new byte[512 * 1024];
            int n = Qvk3Id3.readFully(in, data);
            Qvk3Lyrics l = Qvk3Lyrics.parse(new String(data, 0, n, StandardCharsets.UTF_8), Qvk3Lyrics.SOURCE_LOCAL);
            if (l == null) throw new java.io.IOException("Файл пустой");
            save(c, t, l);
            return l;
        }
    }

    public static void forget(Context c, Qvk3Track t) {
        file(c, t).delete();
    }

    /** LRCLIB exact lookup; only title, artist, album and duration are sent. */
    static Qvk3Lyrics lrclib(Qvk3Track t) throws Exception {
        String body = http("https://lrclib.net/api/get?track_name=" + enc(t.title) + "&artist_name=" + enc(t.artist)
                + (t.album.isEmpty() ? "" : "&album_name=" + enc(t.album)) + (t.duration > 0 ? "&duration=" + t.duration : ""));
        return body == null ? null : Qvk3Lyrics.fromLrclib(new JSONObject(body), t.artist, t.title, t.duration);
    }
}
