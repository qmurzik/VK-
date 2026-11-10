package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics model and source policy for QVK Music (pure Java, unit-tested).
 *
 * Source priority (fixes the QVK 2.6.x order where LRCLIB was queried before VK):
 *   1. text supplied by VK (audio.getLyrics) — tried whenever the track has has_lyrics or lyrics_id;
 *   2. locally saved text (user .lrc/.txt next to the file, or a previous VK result);
 *   3. external source (LRCLIB), only if enabled by the user AND the result matches the track
 *      (normalised artist + title equal, duration within tolerance). A similar title alone is never accepted.
 */
public final class Qvk3Lyrics {
    public static final String SOURCE_VK = "ВК";
    public static final String SOURCE_LOCAL = "Локальный файл";
    public static final String SOURCE_CACHE = "Сохранённый текст";
    public static final String SOURCE_LRCLIB = "LRCLIB";
    /** Max duration difference, in seconds, for accepting an external match. */
    public static final int DURATION_TOLERANCE = 3;

    public static final class Line {
        /** Start time in ms, or -1 for unsynchronised text. */
        public final long time;
        public final String text;

        Line(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    public final List<Line> lines;
    public final boolean synced;
    public final String source;

    public Qvk3Lyrics(List<Line> lines, boolean synced, String source) {
        this.lines = Collections.unmodifiableList(lines);
        this.synced = synced;
        this.source = source;
    }

    public String plain() {
        StringBuilder b = new StringBuilder();
        for (Line l : lines) {
            if (b.length() > 0) b.append('\n');
            b.append(l.text);
        }
        return b.toString();
    }

    /** Index of the line active at {@code positionMs}, -1 before the first line or for unsynced text. */
    public int lineAt(long positionMs) {
        if (!synced || lines.isEmpty()) return -1;
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).time <= positionMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern LRC_OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);

    /** Parses LRC (with multiple timestamps per line and [offset:]) or falls back to plain text. */
    public static Qvk3Lyrics parse(String text, String source) {
        if (text == null) return null;
        String body = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (body.isEmpty()) return null;
        List<Line> timed = new ArrayList<>();
        long offset = 0;
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            Matcher off = LRC_OFFSET.matcher(line);
            if (off.find()) {
                offset = Long.parseLong(off.group(1));
                continue;
            }
            Matcher m = LRC_TIME.matcher(line);
            int end = 0;
            List<Long> times = new ArrayList<>();
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                String frac = m.group(3);
                long ms = 0;
                if (frac != null) {
                    ms = Long.parseLong(frac);
                    if (frac.length() == 1) ms *= 100;
                    else if (frac.length() == 2) ms *= 10;
                }
                times.add(min * 60_000 + sec * 1000 + ms);
                end = m.end();
            }
            if (!times.isEmpty()) {
                String content = line.substring(end).trim();
                for (long t : times) timed.add(new Line(t, content));
            }
        }
        if (timed.size() >= 2) {
            final long o = offset;
            List<Line> shifted = new ArrayList<>();
            for (Line l : timed) shifted.add(new Line(Math.max(0, l.time - o), l.text));
            Collections.sort(shifted, (a, b) -> Long.compare(a.time, b.time));
            return new Qvk3Lyrics(shifted, true, source);
        }
        List<Line> plain = new ArrayList<>();
        for (String raw : body.split("\n")) {
            String line = raw.replaceAll("^\\[[a-zA-Z]+:[^]]*]$", "").trim();
            plain.add(new Line(-1, line));
        }
        return new Qvk3Lyrics(plain, false, source);
    }

    /**
     * Parses the response of VK audio.getLyrics. Handles both the modern shape
     * {lyrics:{timestamps:[{begin,line}], text:[...]}} and the legacy {text:"..."}.
     */
    public static Qvk3Lyrics fromVk(JSONObject response) {
        if (response == null) return null;
        JSONObject lyr = response.optJSONObject("lyrics");
        JSONObject src = lyr != null ? lyr : response;
        JSONArray stamps = src.optJSONArray("timestamps");
        if (stamps != null && stamps.length() > 0) {
            List<Line> lines = new ArrayList<>();
            for (int i = 0; i < stamps.length(); i++) {
                JSONObject s = stamps.optJSONObject(i);
                if (s == null) continue;
                String line = s.optString("line", s.optString("text"));
                long begin = s.optLong("begin", -1);
                if (begin >= 0) lines.add(new Line(begin, line.trim()));
            }
            if (lines.size() >= 2) {
                Collections.sort(lines, (a, b) -> Long.compare(a.time, b.time));
                return new Qvk3Lyrics(lines, true, SOURCE_VK);
            }
        }
        Object text = src.opt("text");
        if (text instanceof JSONArray) {
            JSONArray arr = (JSONArray) text;
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) b.append('\n');
                b.append(arr.optString(i));
            }
            return parse(b.toString(), SOURCE_VK);
        }
        if (text instanceof String) return parse((String) text, SOURCE_VK);
        return null;
    }

    /** Lower-case, accents stripped, punctuation and "(feat. …)"/"[remix]" decorations removed. */
    public static String normal(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        n = n.toLowerCase(Locale.ROOT).replace('ё', 'е');
        n = n.replaceAll("\\s*[(\\[](feat|ft|prod)\\.?[^)\\]]*[)\\]]", "");
        n = n.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return n;
    }

    /**
     * True when an external result really describes this track. The artist must be equal after
     * normalisation (or one must contain the other for "A & B" credits), the title equal, and the
     * duration within {@link #DURATION_TOLERANCE} seconds when both durations are known.
     */
    public static boolean matches(String artist, String title, int duration, String rArtist, String rTitle, int rDuration) {
        String a = normal(artist), t = normal(title), ra = normal(rArtist), rt = normal(rTitle);
        if (a.isEmpty() || t.isEmpty() || ra.isEmpty() || rt.isEmpty()) return false;
        if (!t.equals(rt)) return false;
        boolean artistOk = a.equals(ra) || (a.length() >= 3 && ra.length() >= 3 && (a.contains(ra) || ra.contains(a)));
        if (!artistOk) return false;
        return duration <= 0 || rDuration <= 0 || Math.abs(duration - rDuration) <= DURATION_TOLERANCE;
    }

    /** Parses one LRCLIB /api/get object, returning null unless it matches the track. */
    public static Qvk3Lyrics fromLrclib(JSONObject o, String artist, String title, int duration) {
        if (o == null) return null;
        if (!matches(artist, title, duration, o.optString("artistName"), o.optString("trackName"),
                (int) Math.round(o.optDouble("duration", 0)))) return null;
        String synced = o.optString("syncedLyrics", "");
        if (synced != null && !synced.isEmpty() && !"null".equals(synced)) {
            Qvk3Lyrics l = parse(synced, SOURCE_LRCLIB);
            if (l != null && l.synced) return l;
        }
        String plain = o.optString("plainLyrics", "");
        return plain == null || plain.isEmpty() || "null".equals(plain) ? null : parse(plain, SOURCE_LRCLIB);
    }

    /** Serialised form for the local cache: LRC when synced, plain text otherwise. */
    public String toLrc() {
        if (!synced) return plain();
        StringBuilder b = new StringBuilder();
        for (Line l : lines) {
            long t = l.time;
            b.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]%s\n", t / 60000, (t / 1000) % 60, (t % 1000) / 10, l.text));
        }
        return b.toString();
    }
}
