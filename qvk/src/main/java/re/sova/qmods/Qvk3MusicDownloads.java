package re.sova.qmods;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sequential local downloads of QVK Music.
 *
 * Only tracks VK serves to this account (content_restricted == 0 and a stream URL present) are saved —
 * this does not touch VK's subscription "offline" feature, so it never opens VK's purchase screen and never
 * substitutes paid access. Each track: fresh URL → HLS conversion (QVK's existing HlsMp3, same as the
 * classic export) or a resumable HTTP download → ID3 tags + cover → MediaStore "Music/QVK" (Android 10+)
 * or the app's own music folder on Android 9. Network errors are retried with back-off; cancel stops after
 * the current chunk.
 */
public final class Qvk3MusicDownloads {
    public interface Listener {
        void onProgress(String key, int percent, String status);
    }

    private static final AtomicBoolean running = new AtomicBoolean();
    private static final AtomicBoolean cancel = new AtomicBoolean();
    private static volatile Listener listener;
    private static volatile String status = "";
    private static final int NOTIFICATION = 0x51c4;

    public static void setListener(Listener l) {
        listener = l;
    }

    public static String status() {
        return status;
    }

    public static boolean running() {
        return running.get();
    }

    /** Queues tracks (skipping unavailable ones and duplicates) and starts the worker. Returns a summary. */
    public static String enqueue(Context c, List<Qvk3Track> tracks) {
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(c);
        int added = 0, skipped = 0, dup = 0;
        for (Qvk3Track t : tracks) {
            if (t.id == 0 || t.restricted != 0) {
                skipped++;
                continue;
            }
            if (lib.enqueueDownload(t)) added++;
            else dup++;
        }
        start(c);
        StringBuilder b = new StringBuilder("В очереди: " + added);
        if (dup > 0) b.append(" · уже загружены/в очереди: ").append(dup);
        if (skipped > 0) b.append(" · недоступны: ").append(skipped);
        return b.toString();
    }

    public static void cancel() {
        cancel.set(true);
    }

    public static void start(Context c) {
        if (!running.compareAndSet(false, true)) return;
        cancel.set(false);
        Context app = c.getApplicationContext();
        Qvk3Jobs.download(() -> {
            try {
                loop(app);
            } finally {
                running.set(false);
                notify(app, null, -1);
            }
        });
    }

    private static void loop(Context c) {
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(c);
        Qvk3Track t;
        while (!cancel.get() && (t = lib.nextQueuedDownload()) != null) {
            lib.downloadState(t.key(), Qvk3MusicLibrary.DL_RUNNING, null, -1, null);
            String error = null;
            for (int attempt = 0; attempt < 3 && !cancel.get(); attempt++) {
                try {
                    String location = one(c, t);
                    long size = size(c, location);
                    lib.downloadState(t.key(), Qvk3MusicLibrary.DL_DONE, location, size, null);
                    error = null;
                    break;
                } catch (InterruptedIOException e) {
                    error = "Отменено";
                    break;
                } catch (Exception e) {
                    error = Qvk3Jobs.message(e);
                    if (!(e instanceof IOException) || e.getMessage() != null && e.getMessage().startsWith("Трек")) break;
                    sleep(2000L << attempt); // 2, 4, 8 s back-off on network errors
                }
            }
            if (error != null) {
                lib.downloadState(t.key(), cancel.get() ? Qvk3MusicLibrary.DL_QUEUED : Qvk3MusicLibrary.DL_FAILED, null, -1, error);
                report(t.key(), -1, "Ошибка: " + error);
            } else {
                report(t.key(), 100, "Сохранено: " + t.label());
            }
        }
        status = cancel.get() ? "Загрузки приостановлены" : "Все загрузки завершены";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** IDEA-0018: progress of the track being downloaded (key → 0..99); removed when it is saved or failed. */
    public static final java.util.concurrent.ConcurrentHashMap<String, Integer> PROGRESS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Keys saved during this run (the library knows them too; this avoids a lookup per list row). */
    public static final java.util.Set<String> SAVED = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final java.util.concurrent.CopyOnWriteArrayList<Listener> extra = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addListener(Listener l) {
        if (l != null && !extra.contains(l)) extra.add(l);
    }

    public static void removeListener(Listener l) {
        extra.remove(l);
    }

    private static void report(String key, int percent, String s) {
        status = s;
        if (percent >= 0 && percent < 100) PROGRESS.put(key, percent);
        else {
            PROGRESS.remove(key);
            if (percent >= 100) SAVED.add(key);
        }
        Listener l = listener;
        if (l != null || !extra.isEmpty()) Qvk3Jobs.main(() -> {
            if (l != null) l.onProgress(key, percent, s);
            for (Listener x : extra) x.onProgress(key, percent, s);
        });
    }

    private static String one(Context c, Qvk3Track t) throws Exception {
        Qvk3Track fresh = Qvk3MusicSource.refresh(t);
        if (!fresh.playable()) throw new IOException("Трек недоступен: " + fresh.unplayableReason());
        notify(c, fresh, 0);
        File tmp;
        String ext;
        if (fresh.url.contains(".m3u8")) {
            HlsMp3.Guard guard = new HlsMp3.Guard() {
                @Override
                public void check() throws IOException {
                    if (cancel.get()) throw new InterruptedIOException("cancel");
                }

                @Override
                public void progress(long percent) {
                    int p = (int) Math.max(0, Math.min(99, percent));
                    report(t.key(), p, "Загрузка: " + t.label() + " · " + p + "%");
                }
            };
            tmp = HlsMp3.convert(c, fresh.url, guard);
            ext = tmp.getName().endsWith(".ogg") ? ".ogg" : ".mp3";
        } else {
            tmp = new File(c.getCacheDir(), "qvk3-dl-" + t.key() + ".part");
            http(fresh.url, tmp, t.key(), t.label());
            ext = ".mp3";
        }
        try {
            byte[] cover = null;
            if (!fresh.cover.isEmpty()) {
                try {
                    cover = bytes(fresh.cover, 4 << 20);
                } catch (Exception ignored) {
                    // cover is optional
                }
            }
            if (ext.equals(".mp3")) tmp = Qvk3Id3.tag(tmp, fresh, cover);
            return publish(c, tmp, fresh, ext);
        } finally {
            tmp.delete();
        }
    }

    /** Resumable download: keeps the .part file and continues with an HTTP Range request. */
    private static void http(String url, File part, String key, String label) throws Exception {
        long have = part.isFile() ? part.length() : 0;
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);
        if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
        int code = con.getResponseCode();
        if (code == 416) {
            con.disconnect();
            return; // already complete
        }
        if (code != 200 && code != 206) throw new IOException("HTTP " + code);
        if (code == 200) have = 0; // server ignored Range: restart
        long total = have + Math.max(0, con.getContentLengthLong());
        try (InputStream in = con.getInputStream(); RandomAccessFile out = new RandomAccessFile(part, "rw")) {
            out.setLength(have);
            out.seek(have);
            byte[] buf = new byte[65536];
            int r, last = -1;
            while ((r = in.read(buf)) != -1) {
                if (cancel.get()) throw new InterruptedIOException("cancel");
                out.write(buf, 0, r);
                have += r;
                int p = total > 0 ? (int) (have * 100 / total) : 0;
                if (p != last) {
                    last = p;
                    report(key, p, "Загрузка: " + label + " · " + p + "%");
                }
            }
        } finally {
            con.disconnect();
        }
    }

    static byte[] bytes(String url, int limit) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) != -1) {
                if (b.size() + r > limit) throw new IOException("too large");
                b.write(buf, 0, r);
            }
            return b.toByteArray();
        } finally {
            con.disconnect();
        }
    }

    static String fileName(Qvk3Track t, String ext) {
        String base = (t.artist.isEmpty() ? t.title : t.artist + " - " + t.title).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (base.length() > 120) base = base.substring(0, 120).trim();
        if (base.isEmpty()) base = "track_" + t.ownerId + "_" + t.id;
        return base + ext;
    }

    /** Moves the finished file to its final, user-visible place and returns a playable location. */
    private static String publish(Context c, File src, Qvk3Track t, String ext) throws IOException {
        String mime = ext.equals(".ogg") ? "audio/ogg" : "audio/mpeg";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName(t, ext));
            v.put(MediaStore.Audio.Media.MIME_TYPE, mime);
            v.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/QVK");
            v.put(MediaStore.Audio.Media.TITLE, t.title);
            v.put(MediaStore.Audio.Media.ARTIST, t.artist);
            if (!t.album.isEmpty()) v.put(MediaStore.Audio.Media.ALBUM, t.album);
            v.put(MediaStore.Audio.Media.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (uri == null) throw new IOException("Не удалось создать файл в «Музыке»");
            try (InputStream in = new FileInputStream(src); OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) throw new IOException("Хранилище недоступно");
                copy(in, out);
            } catch (IOException e) {
                cr.delete(uri, null, null);
                throw e;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Audio.Media.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return uri.toString();
        }
        File dir = new File(c.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "QVK");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Нет доступа к папке музыки");
        File dst = new File(dir, fileName(t, ext));
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
        }
        return dst.getPath();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
    }

    static long size(Context c, String location) {
        if (location.startsWith("content:")) {
            try (Cursor cur = c.getContentResolver().query(Uri.parse(location), new String[]{MediaStore.MediaColumns.SIZE}, null, null, null)) {
                return cur != null && cur.moveToFirst() ? cur.getLong(0) : 0;
            } catch (Exception e) {
                return 0;
            }
        }
        return new File(location).length();
    }

    /** Deletes a downloaded file and its index entry. */
    public static void delete(Context c, Qvk3MusicLibrary.Download d) {
        if (d.location != null) {
            try {
                if (d.location.startsWith("content:")) c.getContentResolver().delete(Uri.parse(d.location), null, null);
                else new File(d.location).delete();
            } catch (Exception e) {
                RuntimeFaults.record("qvk3.music.delete", e);
            }
        }
        Qvk3MusicLibrary.get(c).removeDownload(d.track.key());
    }

    /**
     * Migration: indexes audio previously exported by QVK 2.x (Downloads/&lt;QVK folder&gt; and Music/QVK) as
     * local tracks. Files are only referenced, never moved or deleted.
     */
    public static int importExisting(Context c) {
        if (Build.VERSION.SDK_INT < 29) return 0;
        String folder = MediaFiles.folder(RuntimeBridge.prefs.getString("qmods_download_folder", "QMods"));
        List<Qvk3Track> found = new ArrayList<>();
        String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION};
        String sel = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? OR " + MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?";
        String[] args = {Environment.DIRECTORY_DOWNLOADS + "/" + folder + "%", Environment.DIRECTORY_MUSIC + "/QVK%"};
        Uri base = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        try (Cursor cur = c.getContentResolver().query(base, proj, sel, args, MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            while (cur != null && cur.moveToNext()) {
                Uri u = android.content.ContentUris.withAppendedId(base, cur.getLong(0));
                found.add(new Qvk3Track(0, 0, "", cur.getString(2), cur.getString(1), cur.getString(3),
                        (int) (cur.getLong(4) / 1000), "", "", false, 0, u.toString(), 0));
            }
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.import", e);
        }
        Qvk3MusicLibrary.get(c).storePage("local", 0, found, found.size());
        return found.size();
    }

    /** Builds a track for a file picked through the system document picker (SAF). */
    public static Qvk3Track fromDocument(Context c, Uri uri) {
        String title = "", artist = "", album = "";
        int dur = 0;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            dur = d == null ? 0 : (int) (Long.parseLong(d) / 1000);
        } catch (Exception ignored) {
            // unknown metadata: use the file name below
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
        if (title == null || title.isEmpty()) {
            String seg = uri.getLastPathSegment();
            title = seg == null ? "Файл" : seg.replaceAll(".*/", "");
        }
        return new Qvk3Track(0, 0, "", artist, title, album, dur, "", "", false, 0, uri.toString(), 0);
    }

    private static void notify(Context c, Qvk3Track t, int percent) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (t == null) {
            nm.cancel(NOTIFICATION);
            return;
        }
        nm.createNotificationChannel(new NotificationChannel("qvk3_music_downloads", "QVK Music · загрузки", NotificationManager.IMPORTANCE_LOW));
        nm.notify(NOTIFICATION, new Notification.Builder(c, "qvk3_music_downloads")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("QVK Music · загрузка")
                .setContentText(t.label())
                .setProgress(100, Math.max(0, percent), percent < 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build());
    }
}
