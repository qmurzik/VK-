package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Client of the QVK Beta Portal app API (/api/app/v1, docs/APP_API.md of the portal).
 *
 * The personal token (qat_…) is obtained by pairing (code confirmed in Telegram or on the site) and stored encrypted with an
 * Android Keystore AES-GCM key — never in plain preferences, never in logs. All calls are blocking: run them on Qvk3Jobs.io.
 */
public final class Qvk3Portal {
    public static final String BASE = "https://qvk-moderator.qmurzik7.workers.dev/api/app/v1";
    static final String TOKEN = "qvk3_portal_token", USER = "qvk3_portal_user", KEY_ALIAS = "qvk3_portal";
    public static final long MAX_FILE = 5L << 20;

    private Qvk3Portal() {
    }

    /** API error with the portal's own Russian message (safe to show) and code. */
    public static final class ApiException extends IOException {
        public final int http;
        public final String code;
        public final int retryAfter;

        ApiException(int http, String code, String message, int retryAfter) {
            super(message);
            this.http = http;
            this.code = code;
            this.retryAfter = retryAfter;
        }

        public boolean relink() {
            return http == 401;
        }
    }

    /** A file to upload. */
    public static final class Part {
        public final String name, mime;
        public final byte[] data;

        public Part(String name, String mime, byte[] data) {
            this.name = name;
            this.mime = mime;
            this.data = data;
        }
    }

    // ------------------------------------------------------------------ token

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    public static boolean linked(Context c) {
        return prefs(c).contains(TOKEN);
    }

    public static String userName(Context c) {
        return prefs(c).getString(USER, "");
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return g.generateKey();
    }

    static void saveToken(Context c, String token, String user) throws Exception {
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = ci.getIV(), ct = ci.doFinal(token.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        prefs(c).edit().putString(TOKEN, iv.length + ":" + Base64.encodeToString(all, Base64.NO_WRAP)).putString(USER, user).apply();
    }

    static String token(Context c) {
        String s = prefs(c).getString(TOKEN, null);
        if (s == null) return null;
        try {
            int colon = s.indexOf(':');
            int ivLen = Integer.parseInt(s.substring(0, colon));
            byte[] all = Base64.decode(s.substring(colon + 1), Base64.NO_WRAP);
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, ivLen));
            return new String(ci.doFinal(all, ivLen, all.length - ivLen), StandardCharsets.UTF_8);
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.portal.token", e);
            forget(c); // key lost (backup restore, new device): pair again
            return null;
        }
    }

    public static void forget(Context c) {
        prefs(c).edit().remove(TOKEN).remove(USER).apply();
    }

    // ------------------------------------------------------------------ HTTP

    static JSONObject call(Context c, String method, String path, JSONObject body, boolean auth) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(BASE + path).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(30000);
        con.setRequestMethod(method);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "QVK/" + Qvk3Updates.installedName(c));
        if (auth) {
            String t = token(c);
            if (t == null) throw new ApiException(401, "unauthorized", "Подключите аккаунт портала бета-теста", 0);
            con.setRequestProperty("Authorization", "Bearer " + t);
        }
        try {
            if (body != null) {
                byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                con.setFixedLengthStreamingMode(b.length);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(b);
                }
            }
            return result(c, con, auth);
        } finally {
            con.disconnect();
        }
    }

    static JSONObject result(Context c, HttpURLConnection con, boolean auth) throws IOException {
        int code = con.getResponseCode();
        InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
        String text = in == null ? "" : read(in);
        if (code >= 200 && code < 300) {
            try {
                return text.isEmpty() ? new JSONObject() : new JSONObject(text);
            } catch (Exception e) {
                throw new IOException("Портал: неожиданный ответ");
            }
        }
        throw error(code, text, con.getHeaderField("Retry-After"), c, auth);
    }

    static ApiException error(int http, String body, String retryHeader, Context c, boolean auth) {
        String code = "http_" + http, msg = "Портал ответил ошибкой " + http;
        int retry = 0;
        try {
            JSONObject e = new JSONObject(body).getJSONObject("error");
            code = e.optString("code", code);
            msg = e.optString("message", msg);
            retry = e.optInt("retry_after", 0);
        } catch (Exception ignored) {
            // not the portal's JSON error (proxy page, timeout page)
        }
        if (retry == 0 && retryHeader != null) try {
            retry = Integer.parseInt(retryHeader.trim());
        } catch (NumberFormatException ignored) {
        }
        if (http == 429 && retry > 0) msg = msg + " Повторите через " + (retry >= 60 ? retry / 60 + " мин" : retry + " с") + ".";
        if (http == 401 && auth && c != null) forget(c); // revoked / expired: pairing is needed again
        return new ApiException(http, code, msg, retry);
    }

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) {
            b.write(buf, 0, n);
            if (b.size() > (4 << 20)) throw new IOException("Слишком большой ответ портала");
        }
        return b.toString("UTF-8");
    }

    // ------------------------------------------------------------------ API

    public static JSONObject meta(Context c) throws IOException {
        JSONObject m = call(c, "GET", "/meta", null, false);
        prefs(c).edit().putString("qvk3_portal_meta", m.toString()).apply();
        return m;
    }

    /** Last known /meta (categories, statuses) for offline forms; null if never loaded. */
    public static JSONObject cachedMeta(Context c) {
        try {
            String s = prefs(c).getString("qvk3_portal_meta", null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject linkStart(Context c, String deviceName) throws Exception {
        return call(c, "POST", "/link/start", new JSONObject().put("device_name", deviceName), false);
    }

    /** Returns the poll answer; on "approved" the token is stored and removed from the returned object. */
    public static JSONObject linkPoll(Context c, String pollToken) throws Exception {
        JSONObject r = call(c, "POST", "/link/poll", new JSONObject().put("poll_token", pollToken), false);
        if ("approved".equals(r.optString("status")) && !r.optString("token").isEmpty()) {
            JSONObject u = r.optJSONObject("user");
            saveToken(c, r.getString("token"), u == null ? "" : u.optString("name"));
            r.remove("token");
        }
        return r;
    }

    public static JSONObject me(Context c) throws IOException {
        return call(c, "GET", "/me", null, true);
    }

    public static void logout(Context c) {
        try {
            call(c, "POST", "/logout", new JSONObject(), true);
        } catch (Exception ignored) {
            // the token is dropped locally either way
        }
        forget(c);
    }

    /** Uploads up to 5 files in one multipart request; returns their ids (all or nothing, as the portal does). */
    public static JSONArray upload(Context c, List<Part> parts) throws IOException {
        String t = token(c);
        if (t == null) throw new ApiException(401, "unauthorized", "Подключите аккаунт портала бета-теста", 0);
        String boundary = "qvk" + Long.toHexString(System.nanoTime()) + Long.toHexString(Double.doubleToLongBits(Math.random()));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Part p : parts) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + p.name.replace("\"", "")
                    + "\"\r\nContent-Type: " + p.mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(p.data);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpURLConnection con = (HttpURLConnection) new URL(BASE + "/attachments").openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(60000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Authorization", "Bearer " + t);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        byte[] b = body.toByteArray();
        con.setFixedLengthStreamingMode(b.length);
        try {
            try (OutputStream os = con.getOutputStream()) {
                os.write(b);
            }
            JSONObject r = result(c, con, true);
            JSONArray ids = new JSONArray();
            JSONArray a = r.optJSONArray("attachments");
            if (a != null) for (int i = 0; i < a.length(); i++) ids.put(a.getJSONObject(i).getString("id"));
            return ids;
        } catch (org.json.JSONException e) {
            throw new IOException("Портал: неожиданный ответ на загрузку");
        } finally {
            con.disconnect();
        }
    }

    public static JSONArray similar(Context c, String kind, String title, String description, String category) throws Exception {
        JSONObject q = new JSONObject().put("kind", kind).put("title", title).put("description", description);
        if (category != null) q.put("category", category);
        JSONArray items = call(c, "POST", "/similar", q, true).optJSONArray("items");
        return items == null ? new JSONArray() : items;
    }

    public static JSONObject device(Context c) throws Exception {
        return new JSONObject().put("model", (Build.MANUFACTURER + " " + Build.MODEL).trim())
                .put("android", Build.VERSION.RELEASE).put("app_version", appVersion(c)).put("firmware", Build.DISPLAY);
    }

    /** "3.0 alpha30" — the same form as the portal's current_version (BUG-0019). */
    public static String appVersion(Context c) {
        String n = Qvk3Updates.installedName(c);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("qvk-(\\d+\\.\\d+)(?:\\.\\d+)?-?([a-z]+\\d*)?").matcher(n);
        if (m.find()) return m.group(1) + (m.group(2) == null ? "" : " " + m.group(2));
        return n;
    }

    public static JSONObject createTicket(Context c, JSONObject ticket) throws IOException {
        return call(c, "POST", "/tickets", ticket, true).optJSONObject("ticket");
    }

    public static JSONObject tickets(Context c, String query) throws IOException {
        return call(c, "GET", "/tickets?" + query, null, true);
    }

    public static JSONObject ticket(Context c, String code) throws IOException {
        JSONObject r = call(c, "GET", "/tickets/" + code, null, true);
        return r.has("ticket") ? r.optJSONObject("ticket") : r;
    }

    public static JSONObject comment(Context c, String code, String text, JSONArray attachments, boolean addendum) throws Exception {
        JSONObject b = new JSONObject().put("text", text).put("addendum", addendum);
        if (attachments != null && attachments.length() > 0) b.put("attachments", attachments);
        return call(c, "POST", "/tickets/" + code + "/comments", b, true);
    }

    public static JSONObject meToo(Context c, String code, String comment, JSONArray attachments) throws Exception {
        JSONObject b = new JSONObject();
        if (comment != null && !comment.isEmpty()) b.put("comment", comment);
        if (attachments != null && attachments.length() > 0) b.put("attachments", attachments);
        return call(c, "POST", "/tickets/" + code + "/me-too", b, true);
    }

    public static JSONObject verify(Context c, String code, boolean fixed, String comment) throws Exception {
        JSONObject b = new JSONObject().put("result", fixed ? "fixed" : "not_fixed");
        if (comment != null && !comment.isEmpty()) b.put("comment", comment);
        return call(c, "POST", "/tickets/" + code + "/verify", b, true);
    }

    public static JSONArray notifications(Context c, boolean unread) throws IOException {
        JSONObject r = call(c, "GET", "/notifications" + (unread ? "?unread=1" : ""), null, true);
        JSONArray a = r.optJSONArray("items");
        if (a == null) a = r.optJSONArray("notifications");
        return a == null ? new JSONArray() : a;
    }

    public static void markRead(Context c, JSONArray ids) throws Exception {
        call(c, "POST", "/notifications/read", ids == null ? new JSONObject() : new JSONObject().put("ids", ids), true);
    }
}
