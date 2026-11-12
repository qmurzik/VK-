package re.sova.qmods;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * QVK 3.0 update system. Not tied to one GitHub repository:
 *  - sources: any number of manifest URLs (mirrors, tried in order) + QVK's existing community channel;
 *  - authenticity: ECDSA-signed manifest when a trusted key is configured; APK SHA-256 always;
 *    package name, versionCode and signer certificate of the downloaded APK must match the installed QVK;
 *  - resumable downloads (HTTP Range), mirror fallback, app-specific "updates" folder (scoped storage),
 *    automatic removal of stale APKs, version history, installation through PackageInstaller.
 */
public final class Qvk3Updates {
    static final String SOURCES = "qvk3_update_sources";
    static final String KEYS = "qvk3_update_keys";
    static final String COMMUNITY = "qvk3_update_community";
    static final String HISTORY = "qvk3_update_history";
    static final String LAST = "qvk3_update_last";
    /** Update channel: "alpha" — every build (default for the beta), "stable" — only builds marked stable. */
    public static final String CHANNEL = "qvk3_update_channel";

    public static boolean stableOnly(Context c) {
        return "stable".equals(prefs(c).getString(CHANNEL, "alpha"));
    }
    static final String ACTION_INSTALL = "ru.qmods.qvk.QVK3_INSTALL_STATUS";

    private static final AtomicBoolean busy = new AtomicBoolean();
    private static final AtomicBoolean cancel = new AtomicBoolean();
    public static volatile String status = "";
    public static volatile int progress = -1;

    public interface Listener {
        void changed();
    }

    static volatile Listener listener;

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    static void set(String s, int p) {
        status = s;
        progress = p;
        Listener l = listener;
        if (l != null) Qvk3Jobs.main(l::changed);
    }

    /**
     * Official QVK 3 channel: a signed manifest on Yandex Disk (public link stays the same when the file is replaced).
     * Used when the user has not set own sources; the signature is checked with the key in assets/qvk3_update_keys.json.
     */
    public static final String DEFAULT_SOURCE = "https://yadi.sk/d/CEfYrKqqYoX6PQ";

    public static List<String> sources(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : prefs(c).getString(SOURCES, "").split("\n")) if (!s.trim().isEmpty()) out.add(s.trim());
        if (out.isEmpty()) out.add(DEFAULT_SOURCE);
        return out;
    }

    /** Yandex Disk public links (yadi.sk, disk.yandex.*) point to a web page; the API gives a short-lived direct link. */
    static boolean yandexPublic(String url) {
        String h = host(url).toLowerCase(java.util.Locale.ROOT);
        return h.equals("yadi.sk") || h.equals("disk.yandex.ru") || h.equals("disk.yandex.com") || h.equals("disk.360.yandex.ru");
    }

    static String direct(String url) throws IOException {
        if (!yandexPublic(url)) return url;
        String api = "https://cloud-api.yandex.net/v1/disk/public/resources/download?public_key=" + java.net.URLEncoder.encode(url, "UTF-8");
        try {
            String href = new JSONObject(plain(api, 64 * 1024)).optString("href");
            if (href.isEmpty()) throw new IOException("Яндекс Диск не дал ссылку на файл");
            Qvk3UpdateManifest.httpsUrl(href);
            return href;
        } catch (org.json.JSONException e) {
            throw new IOException("Яндекс Диск: неожиданный ответ");
        }
    }

    public static void setSources(Context c, List<String> urls) {
        StringBuilder b = new StringBuilder();
        for (String u : urls) {
            Qvk3UpdateManifest.httpsUrl(u.trim());
            b.append(u.trim()).append('\n');
        }
        prefs(c).edit().putString(SOURCES, b.toString()).apply();
    }

    /** Trusted manifest keys: built-in (assets/qvk3_update_keys.json) + user-added. */
    public static List<String> keys(Context c) {
        List<String> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("qvk3_update_keys.json")) {
            byte[] b = new byte[65536];
            int n = Qvk3Id3.readFully(in, b);
            JSONArray a = new JSONObject(new String(b, 0, n, "UTF-8")).optJSONArray("keys");
            if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i).getString("public_key"));
        } catch (Exception ignored) {
            // no built-in keys
        }
        for (String s : prefs(c).getString(KEYS, "").split("\n")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    public static boolean communityEnabled(Context c) {
        return prefs(c).getBoolean(COMMUNITY, true);
    }

    public static long installedVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String installedName(Context c) {
        try {
            String n = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return n == null ? "?" : n;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Separate, scoped-storage friendly folder for downloaded updates. */
    static File dir(Context c) throws IOException {
        File base = c.getExternalFilesDir("updates");
        if (base == null) base = new File(c.getFilesDir(), "updates");
        if (!base.isDirectory() && !base.mkdirs()) throw new IOException("Нет папки для обновлений");
        return base;
    }

    public static final class Check {
        public final Qvk3UpdateManifest.Release release;
        public final String source;
        public final boolean signed;
        public final String message;
        public final List<Qvk3UpdateManifest.Release> all;

        Check(Qvk3UpdateManifest.Release release, String source, boolean signed, String message, List<Qvk3UpdateManifest.Release> all) {
            this.release = release;
            this.source = source;
            this.signed = signed;
            this.message = message;
            this.all = all;
        }
    }

    /** Blocking: queries every source and returns the best release. Never throws; errors are in message. */
    public static Check check(Context c) {
        long installed = installedVersion(c);
        List<String> errors = new ArrayList<>();
        Qvk3UpdateManifest.Release best = null;
        String bestSource = null;
        boolean bestSigned = false;
        List<Qvk3UpdateManifest.Release> history = new ArrayList<>();
        List<String> keys = keys(c);
        boolean killsSaved = false;
        for (String url : sources(c)) {
            try {
                Qvk3UpdateManifest m = Qvk3UpdateManifest.parse(get(url, 512 * 1024), keys);
                history.addAll(m.releases);
                if (m.signed && !killsSaved) { // remote switch-offs: only from a verified manifest, first one wins
                    Qvk3Kill.save(c, m.killsFor(installed));
                    killsSaved = true;
                }
                Qvk3UpdateManifest.Release r = m.best(installed, Build.VERSION.SDK_INT, stableOnly(c));
                if (r != null && (best == null || r.versionCode > best.versionCode)) {
                    best = r;
                    bestSource = url;
                    bestSigned = m.signed;
                }
                if (best != null) break; // first working mirror with an update wins
            } catch (Exception e) {
                errors.add(host(url) + ": " + Qvk3Jobs.message(e));
            }
        }
        if (best == null && communityEnabled(c)) {
            try {
                // QVK 2.x community channel (qmods.ru), parsed by QVK's own validated reader.
                JSONObject rel = UpdateMirrors.communityRelease(UpdateMirrors.communityReader.read(CommunityCatalog.endpoint()));
                if (rel != null) {
                    Qvk3UpdateManifest m = Qvk3UpdateManifest.fromLegacy(rel);
                    history.addAll(m.releases);
                    Qvk3UpdateManifest.Release r = m.best(installed, Build.VERSION.SDK_INT, stableOnly(c));
                    if (r != null) {
                        best = r;
                        bestSource = "канал сообщества QVK";
                    }
                }
            } catch (Exception e) {
                errors.add("канал сообщества: " + Qvk3Jobs.message(e));
            }
        }
        saveHistory(c, history);
        String msg;
        if (best != null) msg = "Доступна версия " + best.versionName;
        else if (sources(c).isEmpty() && !communityEnabled(c)) msg = "Не настроен ни один источник обновлений";
        else if (!errors.isEmpty() && history.isEmpty()) msg = "Не удалось проверить: " + String.join("; ", errors);
        else msg = "Установлена последняя версия" + (errors.isEmpty() ? "" : " (недоступно: " + String.join("; ", errors) + ")");
        prefs(c).edit().putLong(LAST, System.currentTimeMillis()).apply();
        return new Check(best, bestSource, bestSigned, msg, history);
    }

    static String host(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    static String get(String url, int limit) throws IOException {
        return plain(direct(url), limit);
    }

    static String plain(String url, int limit) throws IOException {
        Qvk3UpdateManifest.httpsUrl(url);
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Cache-Control", "no-cache");
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                byte[] b = new byte[limit];
                int n = Qvk3Id3.readFully(in, b);
                if (n == limit) throw new IOException("Слишком большой ответ");
                return new String(b, 0, n, "UTF-8");
            }
        } finally {
            con.disconnect();
        }
    }

    static void saveHistory(Context c, List<Qvk3UpdateManifest.Release> releases) {
        try {
            JSONArray old = new JSONArray(prefs(c).getString(HISTORY, "[]"));
            java.util.Map<Long, JSONObject> byCode = new java.util.TreeMap<>(java.util.Collections.reverseOrder());
            for (int i = 0; i < old.length(); i++) byCode.put(old.getJSONObject(i).getLong("code"), old.getJSONObject(i));
            for (Qvk3UpdateManifest.Release r : releases) {
                byCode.put(r.versionCode, new JSONObject().put("code", r.versionCode).put("name", r.versionName)
                        .put("notes", r.notes).put("published", r.published));
            }
            JSONArray a = new JSONArray();
            for (JSONObject o : byCode.values()) if (a.length() < 50) a.put(o);
            prefs(c).edit().putString(HISTORY, a.toString()).apply();
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.updates.history", e);
        }
    }

    public static JSONArray history(Context c) {
        try {
            return new JSONArray(prefs(c).getString(HISTORY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static boolean busy() {
        return busy.get();
    }

    public static void cancel() {
        cancel.set(true);
    }

    /** Downloads (resuming partial files, falling back across mirrors), verifies and starts installation. */
    public static void downloadAndInstall(Context c, Qvk3UpdateManifest.Release r) {
        if (!busy.compareAndSet(false, true)) return;
        cancel.set(false);
        Context app = c.getApplicationContext();
        Qvk3Jobs.download(() -> {
            try {
                File apk = download(app, r);
                set("Проверка APK…", 100);
                verify(app, apk, r);
                set("Установка " + r.versionName + "…", 100);
                install(app, apk);
            } catch (InterruptedIOException e) {
                set("Загрузка приостановлена. Нажмите «Скачать», чтобы продолжить с того же места", -1);
            } catch (Exception e) {
                set("Ошибка: " + Qvk3Jobs.message(e), -1);
                RuntimeFaults.record("qvk3.updates.install", e);
            } finally {
                busy.set(false);
            }
        });
    }

    static File download(Context c, Qvk3UpdateManifest.Release r) throws Exception {
        File d = dir(c);
        File done = new File(d, "qvk-" + r.versionCode + ".apk");
        if (done.isFile() && done.length() == r.size && sha256(done).equals(r.sha256)) return done;
        File part = new File(d, "qvk-" + r.versionCode + ".apk.part");
        if (d.getUsableSpace() < r.size - (part.isFile() ? part.length() : 0) + (32L << 20)) throw new IOException("Не хватает места для обновления");
        Exception last = null;
        for (String url : r.urls) {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    fetch(url, part, r.size);
                    if (part.length() != r.size) throw new IOException("Размер не совпадает");
                    if (!sha256(part).equals(r.sha256)) {
                        part.delete(); // corrupted: restart from zero on the next mirror
                        throw new IOException("Контрольная сумма не совпала");
                    }
                    if (done.exists()) done.delete();
                    if (!part.renameTo(done)) throw new IOException("Не удалось сохранить APK");
                    return done;
                } catch (InterruptedIOException e) {
                    throw e;
                } catch (Exception e) {
                    last = e;
                    if (e.getMessage() != null && e.getMessage().startsWith("HTTP 4")) break; // next mirror
                    Thread.sleep(1500L << attempt);
                }
            }
        }
        throw last != null ? last : new IOException("Нет адресов загрузки");
    }

    static void fetch(String url, File part, long size) throws IOException {
        Qvk3UpdateManifest.httpsUrl(url);
        long have = part.isFile() ? part.length() : 0;
        if (have >= size) return;
        String from = url;
        url = direct(url); // Yandex Disk: fresh direct link on every attempt (they expire)
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(true);
        if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
        try {
            int code = con.getResponseCode();
            if (code != 200 && code != 206) throw new IOException("HTTP " + code);
            if (code == 200) have = 0;
            try (InputStream in = con.getInputStream(); RandomAccessFile out = new RandomAccessFile(part, "rw")) {
                out.setLength(have);
                out.seek(have);
                byte[] buf = new byte[65536];
                int n, lastP = -1;
                while ((n = in.read(buf)) != -1) {
                    if (cancel.get()) throw new InterruptedIOException("cancel");
                    if (have + n > size) throw new IOException("Файл больше заявленного");
                    out.write(buf, 0, n);
                    have += n;
                    int p = (int) (have * 100 / size);
                    if (p != lastP) {
                        lastP = p;
                        set("Загрузка обновления · " + p + "% · " + host(from), p);
                    }
                }
            }
        } finally {
            con.disconnect();
        }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder b = new StringBuilder();
        for (byte x : md.digest()) b.append(String.format("%02x", x));
        return b.toString();
    }

    /** The APK must be QVK, newer than the installed build, and signed with the same certificate. */
    static void verify(Context c, File apk, Qvk3UpdateManifest.Release r) throws Exception {
        PackageManager pm = c.getPackageManager();
        // GET_SIGNATURES as well: before Android 13 archive infos may come without signingInfo.
        @SuppressWarnings("deprecation") int flags = PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES;
        PackageInfo info = pm.getPackageArchiveInfo(apk.getPath(), flags);
        if (info == null) throw new IOException("Файл не является APK");
        if (!c.getPackageName().equals(info.packageName)) throw new SecurityException("APK другого приложения: " + info.packageName);
        long code = info.getLongVersionCode();
        if (code != r.versionCode) throw new SecurityException("Версия APK не совпадает с манифестом");
        if (code <= installedVersion(c)) throw new IOException("Эта или более новая версия уже установлена");
        String apkCert = cert(info);
        PackageInfo self = pm.getPackageInfo(c.getPackageName(), flags);
        String selfCert = cert(self);
        if (apkCert == null || !apkCert.equals(selfCert)) {
            throw new SecurityException("Подпись APK отличается от установленного QVK — установка поверх невозможна");
        }
        if (!r.signerSha256.isEmpty() && !r.signerSha256.equals(apkCert)) throw new SecurityException("Подпись APK не совпадает с манифестом");
    }

    @SuppressWarnings("deprecation")
    static String cert(PackageInfo info) throws Exception {
        Signature[] s;
        if (info.signingInfo != null) {
            s = info.signingInfo.hasMultipleSigners() ? info.signingInfo.getApkContentsSigners() : info.signingInfo.getSigningCertificateHistory();
        } else {
            s = info.signatures;
        }
        if (s == null || s.length == 0) return null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        StringBuilder b = new StringBuilder();
        for (byte x : md.digest(s[s.length - 1].toByteArray())) b.append(String.format("%02x", x));
        return b.toString();
    }

    static void install(Context c, File apk) throws Exception {
        if (!c.getPackageManager().canRequestPackageInstalls()) {
            set("Разрешите QVK устанавливать приложения: Настройки → «Установка неизвестных приложений» → QVK, затем нажмите «Установить»", -1);
            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + c.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return;
        }
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        params.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("qvk.apk", 0, apk.length())) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent cb = new Intent(c, Qvk3UpdateReceiver.class).setAction(ACTION_INSTALL);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getBroadcast(c, id, cb, flags).getIntentSender());
        }
    }

    /** Removes APKs that are installed or older, the legacy QVK 2.x download folder, and stale partial files. */
    public static void cleanup(Context c) {
        long installed = installedVersion(c);
        try {
            File[] files = dir(c).listFiles();
            if (files != null) for (File f : files) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("qvk-(\\d+)\\.apk(\\.part)?").matcher(f.getName());
                boolean stalePart = f.getName().endsWith(".part") && System.currentTimeMillis() - f.lastModified() > 14L * 86400_000;
                if ((m.matches() && Long.parseLong(m.group(1)) <= installed) || stalePart) f.delete();
            }
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.updates.cleanup", e);
        }
        File legacy = c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (legacy != null) {
            File[] old = new File(legacy, "qmods_updates").listFiles();
            if (old != null) for (File f : old) if (f.getName().endsWith(".apk") || f.getName().endsWith(".assembling")) f.delete();
        }
    }

    /** Receives PackageInstaller results; shows the system confirmation when user action is required. */
    public static final class Receiver {
        static void handle(Context c, Intent intent) {
            int st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            switch (st) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                    Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            c.startActivity(confirm);
                        } catch (Exception e) {
                            set("Откройте QVK и подтвердите установку", -1);
                        }
                    }
                    break;
                }
                case PackageInstaller.STATUS_SUCCESS:
                    set("Обновление установлено", -1);
                    break;
                default:
                    String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    set(st == PackageInstaller.STATUS_FAILURE_ABORTED ? "Установка отменена" :
                            st == PackageInstaller.STATUS_FAILURE_CONFLICT ? "Конфликт подписи: установите обновление, подписанное тем же ключом" :
                                    st == PackageInstaller.STATUS_FAILURE_STORAGE ? "Недостаточно места для установки" :
                                            "Установка не удалась" + (msg == null ? "" : ": " + msg), -1);
            }
        }
    }
}
