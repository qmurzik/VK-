package re.sova.qmods;

/**
 * Fix for QVK 2.6.x lyrics priority in the classic player overlay (SongLyrics).
 *
 * Root cause: SongLyrics.load() asked VK only when the track carried a legacy {@code lyrics_id}. API 5.2xx
 * audio objects use {@code has_lyrics} and no {@code lyrics_id}, so VK was skipped and LRCLIB was queried
 * first; the external result was then cached and shown even when VK had the official text.
 * A smali patch calls {@link #vk} at the start of SongLyrics.load(): VK is asked by audio_id first and its
 * text replaces any cached external result. Local cache and LRCLIB stay as the next steps, unchanged.
 */
final class Qvk3ClassicLyrics {
    static SongLyrics.Result vk(SongLyrics.Track track, RuntimeSession.Scope scope) {
        if (track == null || track.owner == 0 || track.id == 0) return null;
        try {
            java.util.Map<String, String> args = Qvk3MusicSource.args("audio_id", track.owner + "_" + track.id);
            if (track.lyrics > 0) args.put("lyrics_id", Long.toString(track.lyrics));
            Qvk3Lyrics l = Qvk3Lyrics.fromVk(Qvk3MusicSource.response("audio.getLyrics", args));
            if (l == null) return null;
            String text = l.plain().trim();
            if (text.isEmpty() || text.length() > 20000) return null;
            SongLyrics.Result r = new SongLyrics.Result(text, "ВК", "https://vk.com/audio" + track.owner + "_" + track.id);
            try {
                SongLyrics.cache(track, r, scope);
            } catch (Exception e) {
                RuntimeFaults.record("qvk3.lyrics.classic.cache", e);
            }
            return r;
        } catch (Throwable t) {
            // no VK text (or offline): SongLyrics continues with its local cache and the optional LRCLIB lookup
            RuntimeFaults.record("qvk3.lyrics.classic.vk", t);
            return null;
        }
    }
}
