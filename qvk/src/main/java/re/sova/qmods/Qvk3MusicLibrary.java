package re.sova.qmods;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Indexed offline library of QVK Music (SQLite, per account).
 *
 *  - lists:      server lists cached page by page ("my", "pl:<owner>_<id>", "local") so they open offline
 *                instantly and are never re-scanned or loaded into memory as a whole (LIMIT/OFFSET paging);
 *  - downloads:  user downloads, kept strictly apart from temporary caches; unique per track key, so the
 *                same song is never downloaded twice;
 *  - history / favorites: local, never sent anywhere.
 */
public final class Qvk3MusicLibrary extends SQLiteOpenHelper {
    private static volatile Qvk3MusicLibrary instance;

    public static Qvk3MusicLibrary get(Context c) {
        if (instance == null) {
            synchronized (Qvk3MusicLibrary.class) {
                if (instance == null) instance = new Qvk3MusicLibrary(c.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context app;

    private Qvk3MusicLibrary(Context c) {
        super(c, "qvk3_music.db", null, 1);
        app = c;
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE list_items(account INTEGER, list TEXT, pos INTEGER, key TEXT, json TEXT, search TEXT, artist TEXT, album TEXT, cover TEXT, PRIMARY KEY(account, list, pos))");
        db.execSQL("CREATE INDEX list_items_album ON list_items(account, list, album)");
        db.execSQL("CREATE TABLE lists(account INTEGER, list TEXT, title TEXT, total INTEGER, synced INTEGER, json TEXT, PRIMARY KEY(account, list))");
        db.execSQL("CREATE TABLE downloads(key TEXT PRIMARY KEY, account INTEGER, json TEXT, location TEXT, size INTEGER, state INTEGER, error TEXT, added INTEGER, search TEXT)");
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER, key TEXT, json TEXT, played INTEGER)");
        db.execSQL("CREATE INDEX history_by_time ON history(account, played)");
        db.execSQL("CREATE TABLE favorites(account INTEGER, key TEXT, json TEXT, added INTEGER, PRIMARY KEY(account, key))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 is the first schema; future migrations go here and must keep downloads/history.
    }

    static long account() {
        try {
            return Math.max(0, RuntimeBridge.account());
        } catch (Throwable t) {
            return 0;
        }
    }

    static String search(Qvk3Track t) {
        return Qvk3Lyrics.normal(t.artist + " " + t.title + " " + t.album);
    }

    // ------------------------------------------------------------------ lists

    /** Stores one page of a server list at {@code offset}; when it is the first page, the stale tail is dropped later. */
    public void storePage(String list, int offset, List<Qvk3Track> page, int total) {
        long acc = account();
        if (acc <= 0) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < page.size(); i++) {
                Qvk3Track t = page.get(i);
                ContentValues v = new ContentValues();
                v.put("account", acc);
                v.put("list", list);
                v.put("pos", offset + i);
                v.put("key", t.key());
                v.put("json", t.toJson().toString());
                v.put("search", search(t));
                v.put("artist", t.artist);
                v.put("album", t.album);
                v.put("cover", t.cover);
                db.insertWithOnConflict("list_items", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            // Items past the server total were removed on the server: drop them so the list matches VK.
            db.delete("list_items", "account=? AND list=? AND pos>=?", new String[]{Long.toString(acc), list, Integer.toString(total)});
            ContentValues l = new ContentValues();
            l.put("account", acc);
            l.put("list", list);
            l.put("total", total);
            l.put("synced", System.currentTimeMillis());
            db.insertWithOnConflict("lists", null, l, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Qvk3Track> page(String list, int offset, int count) {
        List<Qvk3Track> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT json FROM list_items WHERE account=? AND list=? ORDER BY pos LIMIT ? OFFSET ?",
                new String[]{Long.toString(account()), list, Integer.toString(count), Integer.toString(offset)})) {
            while (c.moveToNext()) out.add(withDownload(Qvk3Track.fromJson(new JSONObject(c.getString(0)))));
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.db.page", e);
        }
        return out;
    }

    public static final class Group {
        public final String name;
        public final String artist;
        public final String cover;
        public final int count;

        Group(String name, String artist, String cover, int count) {
            this.name = name;
            this.artist = artist;
            this.cover = cover;
            this.count = count;
        }
    }

    /** Albums of a cached list (album title + main artist), for the album grid and CoverFlow. */
    public List<Group> albums(String list) {
        List<Group> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT album, MIN(artist), MAX(cover), COUNT(*) FROM list_items WHERE account=? AND list=? AND album<>'' "
                        + "GROUP BY album ORDER BY MIN(pos)", new String[]{Long.toString(account()), list})) {
            while (c.moveToNext()) out.add(new Group(c.getString(0), c.getString(1), c.getString(2), c.getInt(3)));
        }
        return out;
    }

    public List<Group> artists(String list) {
        List<Group> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT artist, artist, MAX(cover), COUNT(*) FROM list_items WHERE account=? AND list=? AND artist<>'' "
                        + "GROUP BY artist ORDER BY COUNT(*) DESC, artist", new String[]{Long.toString(account()), list})) {
            while (c.moveToNext()) out.add(new Group(c.getString(0), c.getString(1), c.getString(2), c.getInt(3)));
        }
        return out;
    }

    /** Tracks of one album or artist inside a cached list, in list order. */
    public List<Qvk3Track> group(String list, String column, String value) {
        if (!column.equals("album") && !column.equals("artist")) throw new IllegalArgumentException(column);
        List<Qvk3Track> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM list_items WHERE account=? AND list=? AND "
                + column + "=? ORDER BY pos", new String[]{Long.toString(account()), list, value})) {
            while (c.moveToNext()) out.add(withDownload(Qvk3Track.fromJson(new JSONObject(c.getString(0)))));
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.db.group", e);
        }
        return out;
    }

    public int cachedCount(String list) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM list_items WHERE account=? AND list=?",
                new String[]{Long.toString(account()), list})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int cachedTotal(String list) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT total FROM lists WHERE account=? AND list=?",
                new String[]{Long.toString(account()), list})) {
            return c.moveToFirst() ? c.getInt(0) : -1;
        }
    }

    /** Offline search over every cached list and downloads of the current account. */
    public List<Qvk3Track> searchLocal(String query, int limit) {
        String q = "%" + Qvk3Lyrics.normal(query) + "%";
        List<Qvk3Track> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        String acc = Long.toString(account());
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT key, json FROM downloads WHERE account=? AND state=2 AND search LIKE ? LIMIT ?",
                new String[]{acc, q, Integer.toString(limit)})) {
            while (c.moveToNext()) if (seen.add(c.getString(0))) out.add(Qvk3Track.fromJson(new JSONObject(c.getString(1))));
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.db.search", e);
        }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT key, json FROM list_items WHERE account=? AND search LIKE ? ORDER BY list, pos LIMIT ?",
                new String[]{acc, q, Integer.toString(limit)})) {
            while (c.moveToNext() && out.size() < limit) {
                if (seen.add(c.getString(0))) out.add(withDownload(Qvk3Track.fromJson(new JSONObject(c.getString(1)))));
            }
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.db.search", e);
        }
        return out;
    }

    // ------------------------------------------------------------------ downloads

    public static final int DL_QUEUED = 0, DL_RUNNING = 1, DL_DONE = 2, DL_FAILED = 3;

    /** Queues a download; returns false when the track is already downloaded or queued (duplicate protection). */
    public boolean enqueueDownload(Qvk3Track t) {
        ContentValues v = new ContentValues();
        v.put("key", t.key());
        v.put("account", account());
        v.put("json", t.toJson().toString());
        v.put("state", DL_QUEUED);
        v.put("added", System.currentTimeMillis());
        v.put("search", search(t));
        return getWritableDatabase().insertWithOnConflict("downloads", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public void retryDownload(String key) {
        ContentValues v = new ContentValues();
        v.put("state", DL_QUEUED);
        v.putNull("error");
        getWritableDatabase().update("downloads", v, "key=? AND state=?", new String[]{key, Integer.toString(DL_FAILED)});
    }

    public void downloadState(String key, int state, String location, long size, String error) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        if (location != null) v.put("location", location);
        if (size >= 0) v.put("size", size);
        v.put("error", error);
        getWritableDatabase().update("downloads", v, "key=?", new String[]{key});
    }

    public Qvk3Track nextQueuedDownload() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM downloads WHERE state IN (0,1) ORDER BY added LIMIT 1", null)) {
            return c.moveToFirst() ? Qvk3Track.fromJson(new JSONObject(c.getString(0))) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static final class Download {
        public final Qvk3Track track;
        public final int state;
        public final String location;
        public final long size;
        public final String error;

        Download(Qvk3Track track, int state, String location, long size, String error) {
            this.track = track;
            this.state = state;
            this.location = location;
            this.size = size;
            this.error = error;
        }
    }

    public List<Download> downloads(int offset, int count) {
        List<Download> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT json, state, location, size, error FROM downloads ORDER BY state=2, added DESC LIMIT ? OFFSET ?",
                new String[]{Integer.toString(count), Integer.toString(offset)})) {
            while (c.moveToNext()) {
                Qvk3Track t = Qvk3Track.fromJson(new JSONObject(c.getString(0)));
                String loc = c.getString(2);
                if (c.getInt(1) == DL_DONE && loc != null) t = t.withLocal(loc);
                out.add(new Download(t, c.getInt(1), loc, c.getLong(3), c.getString(4)));
            }
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.db.downloads", e);
        }
        return out;
    }

    public long downloadedBytes() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(size),0), COUNT(*) FROM downloads WHERE state=2", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public String downloadedLocation(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT location FROM downloads WHERE key=? AND state=2", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public void removeDownload(String key) {
        getWritableDatabase().delete("downloads", "key=?", new String[]{key});
    }

    /** Adds the local location to a track when it was downloaded, so playback works offline. */
    public Qvk3Track withDownload(Qvk3Track t) {
        if (!t.local.isEmpty()) return t;
        String loc = downloadedLocation(t.key());
        return loc == null ? t : t.withLocal(loc);
    }

    // ------------------------------------------------------------------ history & favorites

    public void recordPlay(Qvk3Track t) {
        if (t == null || !Qvk3MusicSettings.history(app)) return;
        Qvk3Jobs.io(() -> {
            ContentValues v = new ContentValues();
            v.put("account", account());
            v.put("key", t.key());
            v.put("json", t.toJson().toString());
            v.put("played", System.currentTimeMillis());
            SQLiteDatabase db = getWritableDatabase();
            db.insert("history", null, v);
            // keep the 2000 most recent plays
            db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY played DESC LIMIT 2000)");
        });
    }

    /** Distinct recently played tracks, newest first. */
    public List<Qvk3Track> recent(int limit) {
        List<Qvk3Track> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT json FROM history WHERE account=? GROUP BY key ORDER BY MAX(played) DESC LIMIT ?",
                new String[]{Long.toString(account()), Integer.toString(limit)})) {
            while (c.moveToNext()) out.add(withDownload(Qvk3Track.fromJson(new JSONObject(c.getString(0)))));
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.db.history", e);
        }
        return out;
    }

    public void clearHistory() {
        getWritableDatabase().delete("history", "account=?", new String[]{Long.toString(account())});
    }

    public boolean favorite(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM favorites WHERE account=? AND key=?",
                new String[]{Long.toString(account()), key})) {
            return c.moveToFirst();
        }
    }

    /** Local favourites (a QVK-only list; it does not change the VK library). */
    public void setFavorite(Qvk3Track t, boolean on) {
        if (on) {
            ContentValues v = new ContentValues();
            v.put("account", account());
            v.put("key", t.key());
            v.put("json", t.toJson().toString());
            v.put("added", System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            getWritableDatabase().delete("favorites", "account=? AND key=?", new String[]{Long.toString(account()), t.key()});
        }
    }

    public List<Qvk3Track> favorites(int offset, int count) {
        List<Qvk3Track> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT json FROM favorites WHERE account=? ORDER BY added DESC LIMIT ? OFFSET ?",
                new String[]{Long.toString(account()), Integer.toString(count), Integer.toString(offset)})) {
            while (c.moveToNext()) out.add(withDownload(Qvk3Track.fromJson(new JSONObject(c.getString(0)))));
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.db.favorites", e);
        }
        return out;
    }

    /** Saves the given tracks as a local playlist (used by "save queue as playlist"). */
    public void saveLocalPlaylist(String title, List<Qvk3Track> tracks) {
        String list = "local:" + System.currentTimeMillis();
        storePage(list, 0, tracks, tracks.size());
        ContentValues v = new ContentValues();
        v.put("title", title);
        getWritableDatabase().update("lists", v, "account=? AND list=?", new String[]{Long.toString(account()), list});
    }

    public static final class LocalList {
        public final String list;
        public final String title;
        public final int total;

        LocalList(String list, String title, int total) {
            this.list = list;
            this.title = title;
            this.total = total;
        }
    }

    public List<LocalList> localPlaylists() {
        List<LocalList> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT list, title, total FROM lists WHERE account=? AND list LIKE 'local:%' ORDER BY synced DESC",
                new String[]{Long.toString(account())})) {
            while (c.moveToNext()) out.add(new LocalList(c.getString(0), c.getString(1), c.getInt(2)));
        }
        return out;
    }

    public void deleteList(String list) {
        String[] a = {Long.toString(account()), list};
        getWritableDatabase().delete("list_items", "account=? AND list=?", a);
        getWritableDatabase().delete("lists", "account=? AND list=?", a);
    }

    /** Diagnostics line for the QVK Music settings screen. */
    public String stats() {
        String acc = Long.toString(account());
        long items = 0, dls = 0, hist = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM list_items WHERE account=?", new String[]{acc})) {
            if (c.moveToFirst()) items = c.getLong(0);
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM downloads WHERE state=2", null)) {
            if (c.moveToFirst()) dls = c.getLong(0);
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM history WHERE account=?", new String[]{acc})) {
            if (c.moveToFirst()) hist = c.getLong(0);
        }
        return "В индексе: " + items + " · загружено: " + dls + " (" + (downloadedBytes() >> 20) + " МБ) · история: " + hist;
    }
}
