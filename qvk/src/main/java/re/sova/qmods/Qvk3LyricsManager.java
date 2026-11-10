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

    /** Blocking; call off the main thread. Returns null when no trustworthy text exists. */
    public static Qvk3Lyrics load(Context c, Qvk3Track t) throws Exception {
        // 1. VK
        if (t.id != 0 && t.hasLyrics) {
            try {
                Qvk3Lyrics vk = Qvk3MusicSource.lyrics(t);
                if (vk != null && !vk.lines.isEmpty()) {
                    save(c, t, vk);
                    return vk;
                }
            } catch (Exception e) {
                RuntimeFaults.record("qvk3.lyrics.vk", e);
            }
        }
        // 2. locally saved (user import or an earlier result)
        Qvk3Lyrics local = cached(c, t);
        if (local != null) return local;
        // 3. external, opt-in, strict match only
        if (Qvk3MusicSettings.lyricsExternal(c) && !t.artist.isEmpty() && !t.title.isEmpty()) {
            Qvk3Lyrics ext = lrclib(t);
            if (ext != null) {
                save(c, t, ext);
                return ext;
            }
        }
        return null;
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
        String url = "https://lrclib.net/api/get?track_name=" + URLEncoder.encode(t.title, "UTF-8")
                + "&artist_name=" + URLEncoder.encode(t.artist, "UTF-8")
                + (t.album.isEmpty() ? "" : "&album_name=" + URLEncoder.encode(t.album, "UTF-8"))
                + (t.duration > 0 ? "&duration=" + t.duration : "");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(10000);
        con.setRequestProperty("User-Agent", "QVK Music (https://qmods.ru)");
        try {
            int code = con.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new java.io.IOException("LRCLIB: HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                byte[] data = new byte[1 << 20];
                int n = Qvk3Id3.readFully(in, data);
                return Qvk3Lyrics.fromLrclib(new JSONObject(new String(data, 0, n, StandardCharsets.UTF_8)), t.artist, t.title, t.duration);
            }
        } finally {
            con.disconnect();
        }
    }
}
