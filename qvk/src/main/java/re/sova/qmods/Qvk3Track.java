package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * QVK Music track model. Independent of VK's internal player classes: it is built from the public VK API
 * audio object (audio.get / audio.getById / audio.search) or from a local file.
 *
 * Identity is "owner_id_id" for VK tracks and "local:<uri>" for imported files, so the same track keeps
 * its identity across accounts, playlists and refreshed (expired) stream URLs.
 */
public final class Qvk3Track {
    public final long ownerId;
    public final long id;
    public final String accessKey;
    public final String artist;
    public final String title;
    public final String album;
    public final int duration;
    /** Stream URL as returned by VK (may be HLS .m3u8, may be empty when the track is restricted). */
    public final String url;
    public final String cover;
    public final boolean hasLyrics;
    public final long lyricsId;
    /** Local file/content URI when the track is downloaded or imported, otherwise empty. */
    public final String local;
    /** VK's content_restricted code (0 = playable). */
    public final int restricted;

    public Qvk3Track(long ownerId, long id, String accessKey, String artist, String title, String album, int duration,
                     String url, String cover, boolean hasLyrics, long lyricsId, String local, int restricted) {
        this.ownerId = ownerId;
        this.id = id;
        this.accessKey = accessKey == null ? "" : accessKey;
        this.artist = artist == null ? "" : artist;
        this.title = title == null ? "" : title;
        this.album = album == null ? "" : album;
        this.duration = Math.max(0, duration);
        this.url = url == null ? "" : url;
        this.cover = cover == null ? "" : cover;
        this.hasLyrics = hasLyrics;
        this.lyricsId = lyricsId;
        this.local = local == null ? "" : local;
        this.restricted = restricted;
    }

    public String key() {
        return local.isEmpty() || id != 0 ? ownerId + "_" + id : "local:" + local;
    }

    /** "owner_id_id[_access_key]" as accepted by audio.getById. */
    public String apiId() {
        return accessKey.isEmpty() ? ownerId + "_" + id : ownerId + "_" + id + "_" + accessKey;
    }

    public String label() {
        return artist.isEmpty() ? title : artist + " — " + title;
    }

    public boolean playable() {
        return !local.isEmpty() || (restricted == 0 && !url.isEmpty());
    }

    public Qvk3Track withUrl(String fresh) {
        return new Qvk3Track(ownerId, id, accessKey, artist, title, album, duration, fresh, cover, hasLyrics, lyricsId, local, restricted);
    }

    public Qvk3Track withLocal(String path) {
        return new Qvk3Track(ownerId, id, accessKey, artist, title, album, duration, url, cover, hasLyrics, lyricsId, path, restricted);
    }

    /** Builds a track from the VK API audio object. */
    public static Qvk3Track fromVk(JSONObject o) {
        String cover = "";
        String albumTitle = "";
        JSONObject album = o.optJSONObject("album");
        if (album != null) {
            albumTitle = album.optString("title");
            JSONObject thumb = album.optJSONObject("thumb");
            if (thumb != null) {
                for (String k : new String[]{"photo_600", "photo_300", "photo_270", "photo_135", "photo_68"}) {
                    if (!thumb.optString(k).isEmpty()) {
                        cover = thumb.optString(k);
                        break;
                    }
                }
            }
        }
        boolean lyrics = o.optBoolean("has_lyrics", false) || o.optLong("lyrics_id") > 0;
        return new Qvk3Track(o.optLong("owner_id"), o.optLong("id"), o.optString("access_key"), o.optString("artist"),
                o.optString("title"), albumTitle, o.optInt("duration"), o.optString("url"), cover, lyrics,
                o.optLong("lyrics_id"), "", o.optInt("content_restricted", 0));
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("owner_id", ownerId).put("id", id).put("access_key", accessKey).put("artist", artist)
                    .put("title", title).put("album_title", album).put("duration", duration).put("url", url)
                    .put("cover", cover).put("has_lyrics", hasLyrics).put("lyrics_id", lyricsId)
                    .put("local", local).put("content_restricted", restricted);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return o;
    }

    public static Qvk3Track fromJson(JSONObject o) {
        return new Qvk3Track(o.optLong("owner_id"), o.optLong("id"), o.optString("access_key"), o.optString("artist"),
                o.optString("title"), o.optString("album_title"), o.optInt("duration"), o.optString("url"),
                o.optString("cover"), o.optBoolean("has_lyrics"), o.optLong("lyrics_id"), o.optString("local"),
                o.optInt("content_restricted"));
    }

    public static JSONArray toJson(java.util.List<Qvk3Track> tracks) {
        JSONArray a = new JSONArray();
        for (Qvk3Track t : tracks) a.put(t.toJson());
        return a;
    }

    /** Human readable reason why the independent engine cannot play this track. */
    public String unplayableReason() {
        if (!local.isEmpty()) return "";
        switch (restricted) {
            case 0:
                return url.isEmpty() ? "ВК не выдал адрес аудио для этого трека" : "";
            case 1:
                return "Трек недоступен по решению правообладателя";
            case 2:
                return "Трек недоступен в вашем регионе";
            case 5:
                return "Трек скоро выйдет";
            default:
                return "Трек ограничен ВК (код " + restricted + ")";
        }
    }

    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
