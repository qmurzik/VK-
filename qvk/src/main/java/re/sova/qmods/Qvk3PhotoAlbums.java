package re.sova.qmods;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IDEA-0025: download a whole VK photo album. Pick one of your albums (or paste a link to any album you can see),
 * choose the quality, and the photos go to «Pictures/QVK/&lt;название альбома&gt;» — the gallery shows it as its own
 * album. One notification shows «Скачано 64 из 92» and then «Альбом … скачан».
 */
final class Qvk3PhotoAlbums {
    private static final String CHANNEL = "qvk3_photo_downloads";
    private static final int NOTIFICATION = 0x51c7;
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final AtomicBoolean CANCEL = new AtomicBoolean();

    static final class Album {
        final long ownerId, id;
        final String title;
        final int size;

        Album(long ownerId, long id, String title, int size) {
            this.ownerId = ownerId;
            this.id = id;
            this.title = title;
            this.size = size;
        }
    }

    private Qvk3PhotoAlbums() {
    }

    static void open(Activity a) {
        if (BUSY.get()) {
            Qvk3Ui.dialog(a).setTitle("Фотоальбом скачивается").setMessage("Прогресс — в уведомлении. Остановить загрузку?")
                    .setPositiveButton("Остановить", (d, w) -> CANCEL.set(true)).setNegativeButton("Пусть качается", null).show();
            return;
        }
        Qvk3Ui.toast(a, "Загружаем ваши альбомы…");
        Qvk3Jobs.io(() -> {
            List<Album> albums = new ArrayList<>();
            String err = null;
            try {
                albums = myAlbums();
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            final List<Album> list = albums;
            final String fErr = err;
            Qvk3Jobs.main(() -> pick(a, list, fErr));
        });
    }

    private static void pick(Activity a, List<Album> list, String err) {
        List<String> labels = new ArrayList<>();
        labels.add("Вставить ссылку на альбом…");
        for (Album al : list) labels.add(al.title + " · " + al.size + " фото");
        Qvk3Ui.dialog(a).setTitle(err == null ? "Какой альбом скачать?" : "Альбомы недоступны: " + err)
                .setItems(labels.toArray(new String[0]), (d, i) -> {
                    if (i == 0) askLink(a);
                    else quality(a, list.get(i - 1));
                }).setNegativeButton("Отмена", null).show();
    }

    private static void askLink(Activity a) {
        EditText e = new EditText(a);
        e.setHint("vk.com/album-123456_789");
        e.setSingleLine(true);
        int p = Qvk3Ui.dp(a, 20);
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        box.setPadding(p, Qvk3Ui.dp(a, 8), p, 0);
        box.addView(e);
        Qvk3Ui.dialog(a).setTitle("Ссылка на альбом").setView(box).setPositiveButton("Дальше", (d, w) -> {
            Matcher m = Pattern.compile("album(-?\\d+)_(\\d+)").matcher(e.getText().toString());
            if (!m.find()) {
                Qvk3Ui.toast(a, "Не похоже на ссылку на альбом: нужна вида vk.com/album-123_456");
                return;
            }
            long owner = Long.parseLong(m.group(1));
            long id = Long.parseLong(m.group(2));
            // VK links use 0/00/000 for the profile, wall and saved photos; the API calls them -6/-7/-15
            if (id == 0) id = -6;
            if ("00".equals(m.group(2))) id = -7;
            if ("000".equals(m.group(2))) id = -15;
            final long fid = id;
            Qvk3Jobs.io(() -> {
                Album al;
                try {
                    al = albumInfo(owner, fid);
                } catch (Exception ex) {
                    al = new Album(owner, fid, "Альбом " + owner + "_" + fid, 0);
                }
                final Album fal = al;
                Qvk3Jobs.main(() -> quality(a, fal));
            });
        }).setNegativeButton("Отмена", null).show();
    }

    private static void quality(Activity a, Album al) {
        String[] q = {"Оригинал — самый большой размер", "Поменьше — до 1280 px, экономит место"};
        Qvk3Ui.dialog(a).setTitle(al.title + (al.size > 0 ? " · " + al.size + " фото" : ""))
                .setItems(q, (d, w) -> start(a, al, w == 0)).setNegativeButton("Отмена", null).show();
    }

    private static void start(Activity a, Album al, boolean original) {
        if (!BUSY.compareAndSet(false, true)) return;
        CANCEL.set(false);
        Context c = a.getApplicationContext();
        Qvk3Ui.toast(a, "Скачиваем «" + al.title + "» — прогресс в уведомлении");
        Qvk3Jobs.download(() -> {
            int done = 0, failed = 0, total = 0;
            String folder = Environment.DIRECTORY_PICTURES + "/QVK/" + safe(al.title);
            try {
                List<String> urls = photoUrls(al, original);
                total = urls.size();
                notify(c, al, 0, total, false);
                for (int i = 0; i < urls.size(); i++) {
                    if (CANCEL.get()) break;
                    try {
                        save(c, urls.get(i), folder, safe(al.title) + "_" + String.format(java.util.Locale.ROOT, "%04d", i + 1) + ".jpg");
                        done++;
                    } catch (Exception e) {
                        failed++;
                        RuntimeFaults.record("qvk3.photoAlbum.photo", e);
                    }
                    notify(c, al, done + failed, total, false);
                }
                finish(c, al, done, failed, total, CANCEL.get() ? "остановлено" : null);
            } catch (Exception e) {
                finish(c, al, done, failed, total, Qvk3Jobs.message(e));
            } finally {
                BUSY.set(false);
            }
        });
    }

    // ------------------------------------------------------------------ VK API

    private static JSONObject call(String method, String... kv) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null) m.put(kv[i], kv[i + 1]);
        JSONObject r;
        try {
            r = Qvk3MusicSource.api.call(method, m);
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(), t);
        }
        if (r == null) throw new IOException("Пустой ответ ВК");
        JSONObject err = r.optJSONObject("error");
        if (err != null) {
            int code = err.optInt("error_code");
            if (code == 5) throw new IOException("Сессия ВК истекла: войдите заново");
            if (code == 15 || code == 200 || code == 30) throw new IOException("Нет доступа к этому альбому");
            throw new IOException("ВК вернул ошибку " + code + ": " + err.optString("error_msg"));
        }
        Object resp = r.opt("response");
        if (resp instanceof JSONObject) return (JSONObject) resp;
        if (resp instanceof JSONArray) return new JSONObject().put("items", resp);
        return r;
    }

    private static List<Album> myAlbums() throws Exception {
        JSONArray items = call("photos.getAlbums", "need_system", "1", "count", "200").optJSONArray("items");
        List<Album> out = new ArrayList<>();
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null || o.optInt("size") <= 0) continue;
            out.add(new Album(o.optLong("owner_id"), o.optLong("id"), o.optString("title", "Альбом"), o.optInt("size")));
        }
        return out;
    }

    private static Album albumInfo(long owner, long id) throws Exception {
        JSONArray items = call("photos.getAlbums", "owner_id", Long.toString(owner), "album_ids", Long.toString(id), "need_system", "1")
                .optJSONArray("items");
        JSONObject o = items == null ? null : items.optJSONObject(0);
        if (o == null) return new Album(owner, id, "Альбом " + owner + "_" + id, 0);
        return new Album(owner, id, o.optString("title", "Альбом"), o.optInt("size"));
    }

    private static String albumParam(long id) {
        return id == -6 ? "profile" : id == -7 ? "wall" : id == -15 ? "saved" : Long.toString(id);
    }

    /** All photo links of the album: the largest size, or the largest that fits 1280 px. */
    private static List<String> photoUrls(Album al, boolean original) throws Exception {
        List<String> out = new ArrayList<>();
        for (int offset = 0; offset < 10000; ) {
            JSONObject r = call("photos.get", "owner_id", Long.toString(al.ownerId), "album_id", albumParam(al.id),
                    "photo_sizes", "1", "count", "1000", "offset", Integer.toString(offset), "rev", "0");
            JSONArray items = r.optJSONArray("items");
            if (items == null || items.length() == 0) break;
            for (int i = 0; i < items.length(); i++) {
                String u = best(items.optJSONObject(i), original);
                if (u != null) out.add(u);
            }
            offset += items.length();
            if (offset >= r.optInt("count", offset)) break;
        }
        if (out.isEmpty()) throw new IOException("В альбоме нет доступных фото");
        return out;
    }

    private static String best(JSONObject photo, boolean original) {
        if (photo == null) return null;
        JSONArray sizes = photo.optJSONArray("sizes");
        if (sizes == null) return null;
        String bestUrl = null, fitUrl = null;
        long bestArea = -1, fitArea = -1;
        for (int i = 0; i < sizes.length(); i++) {
            JSONObject s = sizes.optJSONObject(i);
            if (s == null || s.optString("url").isEmpty()) continue;
            int w = s.optInt("width"), h = s.optInt("height");
            long area = (long) w * h;
            if (area > bestArea) {
                bestArea = area;
                bestUrl = s.optString("url");
            }
            if (Math.max(w, h) <= 1280 && area > fitArea) {
                fitArea = area;
                fitUrl = s.optString("url");
            }
        }
        return original || fitUrl == null ? bestUrl : fitUrl;
    }

    // ------------------------------------------------------------------ files

    private static String safe(String s) {
        String t = s.replaceAll("[\\\\/:*?\"<>|\\n\\r]", "_").trim();
        if (t.isEmpty()) t = "Альбом";
        return t.length() > 60 ? t.substring(0, 60) : t;
    }

    private static void save(Context c, String url, String folder, String name) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            // Android 9: no RELATIVE_PATH — write the file into Pictures/QVK/<album> and let the media scanner index it
            java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    folder.substring(folder.indexOf('/') + 1));
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Нет доступа к папке «Pictures»");
            java.io.File f = new java.io.File(dir, name);
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            try (InputStream in = con.getInputStream(); OutputStream out = new java.io.FileOutputStream(f)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            } finally {
                con.disconnect();
            }
            android.media.MediaScannerConnection.scanFile(c, new String[]{f.getAbsolutePath()}, new String[]{"image/jpeg"}, null);
            return;
        }
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, folder);
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("Не удалось создать файл в галерее");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        try (InputStream in = con.getInputStream(); OutputStream out = cr.openOutputStream(uri)) {
            if (out == null) throw new IOException("Нет доступа к файлу");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } catch (Exception e) {
            cr.delete(uri, null, null);
            throw e;
        } finally {
            con.disconnect();
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, done, null, null);
    }

    // ------------------------------------------------------------------ notification

    private static void notify(Context c, Album al, int done, int total, boolean finished) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "QVK · фотоальбомы", NotificationManager.IMPORTANCE_LOW));
        nm.notify(NOTIFICATION, new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Скачиваем «" + al.title + "»")
                .setContentText("Скачано " + done + " из " + total)
                .setProgress(Math.max(1, total), done, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build());
    }

    private static void finish(Context c, Album al, int done, int failed, int total, String problem) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "QVK · фотоальбомы", NotificationManager.IMPORTANCE_LOW));
        String text = problem == null
                ? "Сохранено " + done + " фото в «Pictures/QVK/" + safe(al.title) + "»" + (failed > 0 ? ", не удалось: " + failed : "")
                : "Сохранено " + done + " из " + total + " · " + problem;
        nm.notify(NOTIFICATION, new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(problem == null ? "Альбом «" + al.title + "» скачан" : "Альбом «" + al.title + "»")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build());
    }
}
