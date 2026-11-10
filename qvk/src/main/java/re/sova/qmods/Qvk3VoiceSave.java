package re.sova.qmods;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Voice message saving (fix for "голосовые не скачиваются").
 *
 * QVK 2.6.x handed the cached linkOgg to DownloadManager. Those links are signed and expire, so for older
 * messages DownloadManager failed with HTTP 403/410 and the user only saw a generic failure; on Android 10+
 * a SAF folder was also required in some setups. Now:
 *   1. if VK already has the voice file locally (AttachAudioMsg.localFileUri) it is copied — works offline;
 *   2. otherwise the MP3 link, then the OGG link, are downloaded directly with explicit status handling;
 *   3. the file is written through MediaStore (Download/&lt;QVK folder&gt;) or into the user's SAF folder.
 * The message itself is never modified.
 */
public final class Qvk3VoiceSave {
    private static volatile WeakReference<Object> last = new WeakReference<>(null);

    /** Called (via patch) at the start of VoiceSave.url(): remembers the attachment being saved. */
    public static void remember(Object attach) {
        if (attach != null) last = new WeakReference<>(attach);
    }

    static String field(Object o, String name) {
        try {
            Object v = o.getClass().getField(name).get(o);
            return v instanceof String ? (String) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Replacement for NativeMedia.request(...) in the voice menu; same signature. */
    public static void request(Context c, String url, String title, String kind) {
        Context app = c.getApplicationContext();
        Object attach = last.get();
        String ogg = attach == null ? null : field(attach, "g");
        String mp3 = attach == null ? null : field(attach, "h");
        String local = attach == null ? null : field(attach, "i");
        if (attach != null && url != null && !url.equals(ogg) && !url.equals(mp3)) {
            local = null; // remembered attachment is a different message
            ogg = url;
            mp3 = null;
        }
        if (attach == null) ogg = url;
        final String fOgg = ogg, fMp3 = mp3, fLocal = local;
        Qvk3Ui.toast(app, "Сохраняем голосовое…");
        Qvk3Jobs.io(() -> {
            try {
                String where = save(app, fLocal, fMp3, fOgg, title == null ? "Голосовое" : title);
                Qvk3Ui.toast(app, "Голосовое сохранено: " + where);
            } catch (Exception e) {
                RuntimeFaults.record("qvk3.voice.save", e);
                Qvk3Ui.toast(app, Qvk3Jobs.message(e));
            }
        });
    }

    static String save(Context c, String local, String mp3, String ogg, String title) throws Exception {
        File tmp = File.createTempFile("qvk3-voice-", ".part", c.getCacheDir());
        String ext = null;
        try {
            File src = localFile(local);
            if (src != null) {
                copy(new FileInputStream(src), new FileOutputStream(tmp));
                ext = src.getName().toLowerCase(Locale.ROOT).endsWith(".mp3") ? ".mp3" : ".ogg";
            } else {
                int lastStatus = 0;
                for (String u : new String[]{mp3, ogg}) {
                    if (u == null || !u.startsWith("https://")) continue;
                    lastStatus = download(u, tmp);
                    if (lastStatus == 200) {
                        ext = u == mp3 ? ".mp3" : ".ogg";
                        break;
                    }
                }
                if (ext == null) {
                    if (lastStatus == 403 || lastStatus == 404 || lastStatus == 410) {
                        throw new IOException("Ссылка ВК на это голосовое устарела. Прослушайте его, чтобы ВК загрузил файл, и сохраните снова");
                    }
                    if (lastStatus == 0) throw new IOException("В этом сообщении нет ссылки на аудио");
                    throw new IOException("Сервер ВК ответил HTTP " + lastStatus);
                }
            }
            if (tmp.length() == 0) throw new IOException("Получен пустой файл");
            String name = clean(title) + "_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date()) + ext;
            return publish(c, tmp, name, ext.equals(".mp3") ? "audio/mpeg" : "audio/ogg");
        } finally {
            tmp.delete();
        }
    }

    static File localFile(String local) {
        if (local == null || local.isEmpty()) return null;
        try {
            String path = local.startsWith("file:") ? Uri.parse(local).getPath() : local;
            File f = new File(path);
            return f.isFile() && f.canRead() && f.length() > 0 ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    static int download(String url, File out) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);
        try {
            int code = con.getResponseCode();
            if (code != 200) return code;
            copy(con.getInputStream(), new FileOutputStream(out));
            return 200;
        } finally {
            con.disconnect();
        }
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        try (InputStream i = in; OutputStream o = out) {
            byte[] b = new byte[65536];
            int n;
            long total = 0;
            while ((n = i.read(b)) != -1) {
                total += n;
                if (total > 64L << 20) throw new IOException("Файл слишком большой");
                o.write(b, 0, n);
            }
        }
    }

    static String clean(String s) {
        String t = s.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        return t.isEmpty() ? "Голосовое" : t.length() > 80 ? t.substring(0, 80) : t;
    }

    static String publish(Context c, File src, String name, String mime) throws IOException {
        ContentResolver cr = c.getContentResolver();
        Uri tree = null;
        try {
            tree = DownloadDest.tree(c);
        } catch (Throwable ignored) {
            // no SAF folder configured
        }
        if (tree != null) {
            Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            Uri doc = DocumentsContract.createDocument(cr, dir, mime, name);
            if (doc == null) throw new IOException("Выбранная папка недоступна — выберите её заново в настройках загрузок");
            copy(new FileInputStream(src), cr.openOutputStream(doc));
            return "выбранная папка/" + name;
        }
        String folder = MediaFiles.folder(RuntimeBridge.prefs.getString("qmods_download_folder", "QMods"));
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder);
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (uri == null) throw new IOException("Не удалось создать файл в «Загрузках»");
            try {
                copy(new FileInputStream(src), cr.openOutputStream(uri));
            } catch (IOException e) {
                cr.delete(uri, null, null);
                throw e;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return "Загрузки/" + folder + "/" + name;
        }
        if (c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw new IOException("Нужно разрешение на запись в память — выдайте его QVK или выберите папку загрузок в настройках");
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Нет доступа к папке «Загрузки»");
        File dst = new File(dir, name);
        copy(new FileInputStream(src), new FileOutputStream(dst));
        return "Загрузки/" + folder + "/" + name;
    }
}
