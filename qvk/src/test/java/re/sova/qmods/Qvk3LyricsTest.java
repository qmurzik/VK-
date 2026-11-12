package re.sova.qmods;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class Qvk3LyricsTest {
    @Test public void parsesLrcWithMultipleStampsAndOffset() {
        Qvk3Lyrics l = Qvk3Lyrics.parse("[ar:X]\n[offset:+500]\n[00:10.50][00:30.5]Chorus\n[00:20.123]Verse", "t");
        assertTrue(l.synced);
        assertEquals(3, l.lines.size());
        assertEquals(10000, l.lines.get(0).time);
        assertEquals("Chorus", l.lines.get(0).text);
        assertEquals(19623, l.lines.get(1).time);
        assertEquals(30000, l.lines.get(2).time);
        assertEquals(-1, l.lineAt(5000));
        assertEquals(0, l.lineAt(10000));
        assertEquals(1, l.lineAt(25000));
        assertEquals(2, l.lineAt(999999));
    }

    @Test public void plainTextIsNotSynced() {
        Qvk3Lyrics l = Qvk3Lyrics.parse("line one\nline two", "t");
        assertFalse(l.synced);
        assertEquals("line one\nline two", l.plain());
        assertEquals(-1, l.lineAt(1000));
    }

    @Test public void vkModernTimestampFormat() throws Exception {
        JSONObject r = new JSONObject("{\"lyrics\":{\"timestamps\":[{\"begin\":2000,\"end\":4000,\"line\":\"b\"},{\"begin\":1000,\"line\":\"a\"}],\"text\":[\"a\",\"b\"]}}");
        Qvk3Lyrics l = Qvk3Lyrics.fromVk(r);
        assertTrue(l.synced);
        assertEquals("a", l.lines.get(0).text);
        assertEquals(Qvk3Lyrics.SOURCE_VK, l.source);
    }

    @Test public void vkTextArrayAndLegacyString() throws Exception {
        assertEquals("a\nb", Qvk3Lyrics.fromVk(new JSONObject("{\"lyrics\":{\"text\":[\"a\",\"b\"]}}")).plain());
        assertEquals("x\ny", Qvk3Lyrics.fromVk(new JSONObject("{\"text\":\"x\\ny\"}")).plain());
        assertNull(Qvk3Lyrics.fromVk(new JSONObject("{}")));
    }

    @Test public void externalMatchRequiresSameTrack() {
        assertTrue(Qvk3Lyrics.matches("Кино", "Группа крови", 285, "КИНО", "Группа Крови", 287));
        assertTrue(Qvk3Lyrics.matches("Daft Punk", "Get Lucky (feat. Pharrell Williams)", 248, "Daft Punk & Pharrell Williams", "Get Lucky", 248));
        assertFalse("similar title only", Qvk3Lyrics.matches("Кино", "Группа крови", 285, "Кино", "Группа крови (live)", 285));
        assertFalse("other artist", Qvk3Lyrics.matches("Кино", "Звезда", 200, "Alla", "Звезда", 200));
        assertFalse("duration differs", Qvk3Lyrics.matches("A", "B", 200, "A", "B", 260));
    }

    @Test public void lrclibResultIsRejectedWhenNotMatching() throws Exception {
        JSONObject o = new JSONObject("{\"artistName\":\"Other\",\"trackName\":\"Song\",\"duration\":200,\"plainLyrics\":\"text\"}");
        assertNull(Qvk3Lyrics.fromLrclib(o, "Artist", "Song", 200));
        o.put("artistName", "Artist");
        assertEquals(Qvk3Lyrics.SOURCE_LRCLIB, Qvk3Lyrics.fromLrclib(o, "Artist", "Song", 201).source);
    }

    @Test public void lrcRoundTrip() {
        Qvk3Lyrics l = Qvk3Lyrics.parse("[01:02.30]a\n[01:05.00]b", "t");
        Qvk3Lyrics r = Qvk3Lyrics.parse(l.toLrc(), "t");
        assertEquals(62300, r.lines.get(0).time);
        assertEquals("b", r.lines.get(1).text);
    }

    @Test public void cleanTitleDropsReleaseDecorations() {
        assertEquals(Qvk3Lyrics.cleanTitle("Yellow"), Qvk3Lyrics.cleanTitle("Yellow - Remastered 2011"));
        assertEquals(Qvk3Lyrics.cleanTitle("Группа крови"), Qvk3Lyrics.cleanTitle("Группа крови (Radio Edit)"));
        assertTrue(Qvk3Lyrics.matchesLoose("Кино", "Группа крови", 284, "Кино", "Группа крови (Remastered)", 287));
        assertFalse(Qvk3Lyrics.matchesLoose("Кино", "Группа крови", 284, "Кино", "Кукушка", 284));
    }

    @Test public void translationsAreRejectedByScript() {
        assertFalse(Qvk3Lyrics.scriptMatches("Группа крови", "It's such a warm place, but there on the street"));
        assertTrue(Qvk3Lyrics.scriptMatches("Группа крови", "Тёплое место, но улицы ждут отпечатков наших ног"));
        assertTrue(Qvk3Lyrics.scriptMatches("Yellow", "Look at the stars, look how they shine for you"));
        assertFalse(Qvk3Lyrics.scriptMatches("Yellow", "Посмотри на звёзды, как они сияют для тебя"));
    }

    @Test public void lrclibSearchPicksOriginalSyncedClosestDuration() throws Exception {
        org.json.JSONArray a = new org.json.JSONArray()
                .put(new org.json.JSONObject().put("artistName", "Группа КИНО").put("trackName", "Группа крови").put("duration", 236.0)
                        .put("plainLyrics", "It's such a warm place,\nBut there on the street"))
                .put(new org.json.JSONObject().put("artistName", "Кино").put("trackName", "Группа крови").put("duration", 237.0)
                        .put("syncedLyrics", "[00:45.91]Тёплое место\n[00:54.80]Звёздная пыль"))
                .put(new org.json.JSONObject().put("artistName", "Кино").put("trackName", "Группа крови").put("duration", 284.0)
                        .put("syncedLyrics", "[00:47.00]Тёплое место\n[00:56.00]Звёздная пыль"));
        Qvk3Lyrics l = Qvk3Lyrics.fromLrclibSearch(a, "Кино", "Группа крови", 285);
        assertNotNull(l);
        assertTrue(l.synced);
        assertEquals(47000, l.lines.get(0).time);
    }

    @Test public void neteaseCreditsAreRemoved() throws Exception {
        org.json.JSONObject search = new org.json.JSONObject("{\"result\":{\"songs\":[{\"id\":1,\"name\":\"Yellow\",\"duration\":300000,\"artists\":[{\"name\":\"Coldplay\"}]},"
                + "{\"id\":17177324,\"name\":\"Yellow\",\"duration\":266773,\"artists\":[{\"name\":\"Coldplay\"}]}]}}");
        assertEquals(17177324, Qvk3Lyrics.neteaseSongId(search, "Coldplay", "Yellow", 267));
        org.json.JSONObject lyr = new org.json.JSONObject().put("lrc", new org.json.JSONObject().put("lyric",
                "[00:00.000] 作词 : Chris Martin\n[00:01.000] 作曲 : Chris Martin\n[00:33.10]Look at the stars\n[00:38.20]Look how they shine for you\n[00:43.00]And everything you do"));
        Qvk3Lyrics l = Qvk3Lyrics.fromNetease(lyr, "Yellow");
        assertNotNull(l);
        assertTrue(l.synced);
        assertEquals("Look at the stars", l.lines.get(0).text);
        assertEquals(3, l.lines.size());
    }

    @Test public void ovhPlainText() throws Exception {
        Qvk3Lyrics l = Qvk3Lyrics.fromOvh(new org.json.JSONObject().put("lyrics", "Paroles de la chanson Yellow par Coldplay\r\nLook at the stars\nLook how they shine for you"), "Yellow");
        assertNotNull(l);
        assertFalse(l.synced);
        assertEquals("Look at the stars", l.lines.get(0).text);
    }
}
