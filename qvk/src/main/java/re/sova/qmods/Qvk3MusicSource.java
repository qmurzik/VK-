package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VK as a *data source* for QVK Music.
 *
 * Every call goes through {@link NetworkBridge#apiVersioned}, i.e. through VK 8.100's own authorised API
 * client: QVK Music never sees or stores an access token, and requests carry the same account binding
 * (a request started for one account fails with "Аккаунт сменился" if the user switches accounts).
 * Nothing here touches VK's player, fragments or view models.
 */
public final class Qvk3MusicSource {
    static final String V = "5.243";
    public static final int PAGE = 100;

    public static final class Page<T> {
        public final List<T> items;
        public final int total;

        Page(List<T> items, int total) {
            this.items = items;
            this.total = total;
        }
    }

    public static final class Playlist {
        public final long ownerId;
        public final long id;
        public final String accessKey;
        public final String title;
        public final String subtitle;
        public final int count;
        public final String cover;

        Playlist(JSONObject o) {
            ownerId = o.optLong("owner_id");
            id = o.optLong("id");
            accessKey = o.optString("access_key");
            title = o.optString("title");
            int year = o.optInt("year");
            String main = "";
            JSONArray artists = o.optJSONArray("main_artists");
            if (artists != null && artists.length() > 0 && artists.optJSONObject(0) != null)
                main = artists.optJSONObject(0).optString("name");
            subtitle = main.isEmpty() ? (year > 0 ? String.valueOf(year) : "") : (year > 0 ? main + " · " + year : main);
            count = o.optInt("count");
            String c = "";
            JSONObject photo = o.optJSONObject("photo");
            JSONArray thumbs = o.optJSONArray("thumbs");
            if (photo == null && thumbs != null && thumbs.length() > 0) photo = thumbs.optJSONObject(0);
            if (photo != null) {
                for (String k : new String[]{"photo_600", "photo_300", "photo_270", "photo_135"}) {
                    if (!photo.optString(k).isEmpty()) {
                        c = photo.optString(k);
                        break;
                    }
                }
            }
            cover = c;
        }

        public String key() {
            return ownerId + "_" + id;
        }
    }

    /** Test seam: the default implementation calls VK through NetworkBridge. */
    interface Api {
        JSONObject call(String method, Map<String, String> args) throws Throwable;
    }

    static Api api = (method, args) -> NetworkBridge.apiVersioned(method, args, V);

    static JSONObject response(String method, Map<String, String> args) throws Exception {
        JSONObject r;
        try {
            r = api.call(method, args);
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(), t);
        }
        if (r == null) throw new IOException("Пустой ответ ВК");
        JSONObject err = r.optJSONObject("error");
        if (err != null) {
            int code = err.optInt("error_code");
            String msg = err.optString("error_msg");
            if (code == 5) throw new IOException("Сессия ВК истекла: войдите в аккаунт заново");
            if (code == 15 || code == 201 || code == 203) throw new IOException("Нет доступа к аудио: " + msg);
            throw new IOException("ВК вернул ошибку " + code + (msg.isEmpty() ? "" : ": " + msg));
        }
        Object resp = r.opt("response");
        if (resp instanceof JSONObject) return (JSONObject) resp;
        if (resp instanceof JSONArray) return new JSONObject().put("items", resp).put("count", ((JSONArray) resp).length());
        return r;
    }

    static Map<String, String> args(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null && !kv[i + 1].isEmpty()) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static Page<Qvk3Track> tracks(JSONObject r) {
        JSONArray items = r.optJSONArray("items");
        List<Qvk3Track> out = new ArrayList<>();
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o != null && o.optLong("id") != 0) out.add(Qvk3Track.fromVk(o));
        }
        return new Page<>(out, r.optInt("count", out.size()));
    }

    public static long account() {
        return RuntimeBridge.account();
    }

    /** "Моя музыка": tracks of the signed-in account, paginated. */
    public static Page<Qvk3Track> myTracks(int offset) throws Exception {
        long me = account();
        if (me <= 0) throw new IOException("Войдите в аккаунт ВК");
        return tracks(response("audio.get", args("owner_id", Long.toString(me), "count", Integer.toString(PAGE),
                "offset", Integer.toString(offset))));
    }

    public static Page<Playlist> playlists(int offset) throws Exception {
        long me = account();
        if (me <= 0) throw new IOException("Войдите в аккаунт ВК");
        JSONObject r = response("audio.getPlaylists", args("owner_id", Long.toString(me), "count", "50",
                "offset", Integer.toString(offset)));
        JSONArray items = r.optJSONArray("items");
        List<Playlist> out = new ArrayList<>();
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o != null) out.add(new Playlist(o));
        }
        return new Page<>(out, r.optInt("count", out.size()));
    }

    public static Page<Qvk3Track> playlistTracks(Playlist p, int offset) throws Exception {
        return tracks(response("audio.get", args("owner_id", Long.toString(p.ownerId), "playlist_id", Long.toString(p.id),
                "access_key", p.accessKey, "count", Integer.toString(PAGE), "offset", Integer.toString(offset))));
    }

    public static Page<Qvk3Track> search(String q, boolean artistOnly, int offset) throws Exception {
        return tracks(response("audio.search", args("q", q, "count", Integer.toString(PAGE), "offset",
                Integer.toString(offset), "auto_complete", "1", "performer_only", artistOnly ? "1" : "0")));
    }

    /** Re-resolves stream URLs (they expire); returns tracks in the requested order, missing ones skipped. */
    public static List<Qvk3Track> byIds(List<Qvk3Track> tracks) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (Qvk3Track t : tracks) {
            if (t.id == 0) continue;
            if (ids.length() > 0) ids.append(',');
            ids.append(t.apiId());
        }
        if (ids.length() == 0) return new ArrayList<>();
        JSONObject r = response("audio.getById", args("audios", ids.toString()));
        return tracks(r).items;
    }

    public static Qvk3Track refresh(Qvk3Track t) throws Exception {
        List<Qvk3Track> one = new ArrayList<>();
        one.add(t);
        List<Qvk3Track> fresh = byIds(one);
        for (Qvk3Track f : fresh) if (f.ownerId == t.ownerId && f.id == t.id) return t.local.isEmpty() ? f : f.withLocal(t.local);
        throw new IOException("ВК не вернул трек " + t.label());
    }

    public static Qvk3Lyrics lyrics(Qvk3Track t) throws Exception {
        Map<String, String> a = args("audio_id", t.ownerId + "_" + t.id);
        if (t.lyricsId > 0) a.put("lyrics_id", Long.toString(t.lyricsId));
        return Qvk3Lyrics.fromVk(response("audio.getLyrics", a));
    }
}
