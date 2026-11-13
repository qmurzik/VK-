package re.sova.qmods;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Attachments of the QVK messenger: photos and files picked on the phone are uploaded the official way
 * (getMessagesUploadServer → POST the file → save) and appear above the composer; «Отправить» sends them.
 */
final class Qvk3ImAttach {
    static final long MAX = 200L << 20;

    private Qvk3ImAttach() {
    }

    static void pick(Qvk3ImChatView chat) {
        Activity a = chat.a;
        Qvk3Ui.dialog(a).setTitle("Прикрепить").setItems(new String[]{
                "Камера\nСнять фото и отправить",
                "Фотографии\nДо 10 из галереи",
                "Видео\nИз галереи, до 100 МБ",
                "Файл\nЛюбой документ до 200 МБ",
                "Голосовое сообщение\nУдерживайте кнопку записи"}, (d, w) -> {
            if (w == 0) Qvk3ImPickActivity.pick(a, Qvk3ImPickActivity.CAMERA, uris -> upload(chat, uris, true));
            else if (w == 1) Qvk3ImPickActivity.pick(a, "image/*", uris -> upload(chat, uris, true));
            else if (w == 2) Qvk3ImPickActivity.pick(a, "video/*", uris -> uploadVideo(chat, uris));
            else if (w == 3) Qvk3ImPickActivity.pick(a, "*/*", uris -> upload(chat, uris, false));
            else Qvk3ImRecorder.open(chat);
        }).show();
    }

    static void upload(Qvk3ImChatView chat, List<Uri> uris, boolean photo) {
        Activity a = chat.a;
        Qvk3Ui.toast(a, "Загружаем: " + uris.size());
        for (Uri u : uris) Qvk3Jobs.io(() -> {
            try {
                String name = name(a, u);
                byte[] data = read(a, u);
                Qvk3ImModel.Attachment preview = photo ? photo(chat.peer, name, data) : doc(chat.peer, name, data, "doc");
                Qvk3Jobs.main(() -> chat.attachUploaded(preview.ref(), preview));
            } catch (Exception e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Не загрузилось: " + Qvk3Jobs.message(e)));
            }
        });
    }

    /** Video for a message: video.save (private, not on the wall) → upload the file → "video{owner}_{id}". */
    static void uploadVideo(Qvk3ImChatView chat, List<Uri> uris) {
        Activity a = chat.a;
        Qvk3Ui.toast(a, "Загружаем видео: " + uris.size() + " — это может занять время");
        for (Uri u : uris) Qvk3Jobs.io(() -> {
            try {
                String name = name(a, u);
                byte[] data = read(a, u);
                if (data.length > (100L << 20)) throw new java.io.IOException("Видео больше 100 МБ — отправьте его файлом");
                JSONObject srv = Qvk3ImApi.videoSave(name);
                JSONObject up = new JSONObject(post(srv.getString("upload_url"), "video_file", name, data));
                Qvk3ImModel.Attachment v = new Qvk3ImModel.Attachment("video");
                v.ownerId = up.optLong("owner_id", srv.optLong("owner_id"));
                v.id = up.optLong("video_id", srv.optLong("video_id"));
                v.accessKey = srv.optString("access_key", "");
                v.title = name;
                if (v.id == 0) throw new java.io.IOException("ВК не сохранил видео");
                Qvk3Jobs.main(() -> chat.attachUploaded(v.ref(), v));
            } catch (Throwable e) {
                Qvk3Jobs.main(() -> Qvk3Ui.toast(a, "Видео не загрузилось: " + Qvk3Jobs.message(e)));
            }
        });
    }

    static Qvk3ImModel.Attachment photo(long peer, String name, byte[] data) throws Exception {
        JSONObject srv = Qvk3ImApi.call("photos.getMessagesUploadServer", "peer_id", Long.toString(peer));
        JSONObject up = new JSONObject(post(srv.getString("upload_url"), "photo", name.toLowerCase().matches(".*\\.(jpe?g|png|gif|webp|heic)$") ? name : name + ".jpg", data));
        JSONObject saved = Qvk3ImApi.call("photos.saveMessagesPhoto", "photo", up.optString("photo"), "server", up.optString("server"), "hash", up.optString("hash"));
        JSONArray items = saved.optJSONArray("items");
        JSONObject p = items == null ? null : items.optJSONObject(0);
        if (p == null) throw new java.io.IOException("ВК не сохранил фото");
        return Qvk3ImModel.attachment(new JSONObject().put("type", "photo").put("photo", p));
    }

    /** type "doc" for files, "audio_message" for voice. */
    static Qvk3ImModel.Attachment doc(long peer, String name, byte[] data, String type) throws Exception {
        JSONObject srv = Qvk3ImApi.call("docs.getMessagesUploadServer", "peer_id", Long.toString(peer), "type", type);
        JSONObject up = new JSONObject(post(srv.getString("upload_url"), "file", name, data));
        if (up.has("error")) throw new java.io.IOException(up.optString("error_descr", up.optString("error")));
        JSONObject saved = Qvk3ImApi.call("docs.save", "file", up.optString("file"), "title", name);
        String t = saved.optString("type", type);
        JSONObject o = saved.optJSONObject(t);
        if (o == null) throw new java.io.IOException("ВК не сохранил файл");
        return Qvk3ImModel.attachment(new JSONObject().put("type", t).put(t, o));
    }

    static String name(Activity a, Uri u) {
        try (Cursor c = a.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignored) {
        }
        String last = u.getLastPathSegment();
        return last == null ? "file" : last;
    }

    static byte[] read(Activity a, Uri u) throws Exception {
        try (InputStream in = a.getContentResolver().openInputStream(u)) {
            if (in == null) throw new java.io.IOException("Файл недоступен");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
                if (out.size() > MAX) throw new java.io.IOException("Файл больше 200 МБ");
            }
            return out.toByteArray();
        }
    }

    /** multipart/form-data upload to a VK upload server. */
    static String post(String url, String field, String filename, byte[] data) throws Exception {
        String boundary = "qvk" + System.nanoTime();
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setDoOutput(true);
        con.setConnectTimeout(15_000);
        con.setReadTimeout(120_000);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        String safe = filename.replace("\"", "'").replace("\r", "").replace("\n", "");
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + safe
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        con.setFixedLengthStreamingMode((long) head.length + data.length + tail.length);
        try (OutputStream o = con.getOutputStream()) {
            o.write(head);
            o.write(data);
            o.write(tail);
        }
        try (InputStream in = con.getResponseCode() < 400 ? con.getInputStream() : con.getErrorStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while (in != null && (n = in.read(b)) > 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }
}
