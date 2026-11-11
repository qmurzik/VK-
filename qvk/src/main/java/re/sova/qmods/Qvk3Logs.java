package re.sova.qmods;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * In-app log collection, so nobody has to install adb/logcat. Android lets an app read only its own log lines, so
 * this records QVK/VK output of this app and nothing else. Everything stays on the device in files/qvk3_logs;
 * nothing is sent anywhere. Tokens and passwords are masked (Qvk3LogRedact) in everything the user copies or saves.
 */
final class Qvk3Logs {
    static final String PREF_CAPTURE = "qvk3_logs_capture";
    private static final long ROTATE_AT = 6L << 20;       // one file part, bytes
    private static final long COPY_TAIL = 300L << 10;     // clipboard transactions fail above ~1 MB
    private static volatile Process proc;
    private static volatile boolean running;

    private Qvk3Logs() {
    }

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(PREF_CAPTURE, true);
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "qvk3_logs");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Starts the background recorder (once per process). Cheap: one `logcat` child process and a file writer. */
    static synchronized void start(Context c) {
        if (running || !enabled(c)) return;
        running = true;
        final File d = dir(c);
        Qvk3Jobs.io(() -> {
            BufferedWriter w = null;
            try {
                File cur = new File(d, "current.log"), prev = new File(d, "previous.log"), mid = new File(d, "middle.log");
                mid.delete();
                if (cur.exists() && cur.length() > 0) {
                    prev.delete();
                    cur.renameTo(prev);
                }
                proc = new ProcessBuilder("logcat", "-v", "threadtime").redirectErrorStream(true).start();
                w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cur), StandardCharsets.UTF_8), 1 << 15);
                w.write("# QVK log, started " + stamp() + "\n");
                BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
                long size = 0;
                int since = 0;
                String line;
                while (running && (line = r.readLine()) != null) {
                    w.write(line);
                    w.write('\n');
                    size += line.length() + 1;
                    since++;
                    // flush as soon as the pipe is idle or a crash is being written, so a dying process loses nothing
                    if (since >= 200 || !r.ready() || line.contains("FATAL") || line.contains("AndroidRuntime")) {
                        w.flush();
                        since = 0;
                    }
                    if (size > ROTATE_AT) {
                        w.close();
                        mid.delete();
                        cur.renameTo(mid);
                        w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cur), StandardCharsets.UTF_8), 1 << 15);
                        size = 0;
                    }
                }
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.logs.capture", t);
            } finally {
                running = false;
                try {
                    if (w != null) w.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        });
    }

    static synchronized void stop() {
        running = false;
        Process p = proc;
        if (p != null) p.destroy();
        proc = null;
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(PREF_CAPTURE, on).apply();
        if (on) start(c.getApplicationContext());
        else stop();
    }

    static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    /** Device, versions and the module fault list: the header every report starts with. */
    static String header(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("=== QVK отчёт ").append(stamp()).append(" ===\n");
        b.append("QVK: ").append(Qvk3Updates.installedName(c)).append(" (").append(Qvk3Updates.installedVersion(c)).append(")\n");
        b.append("Основа: VK ").append(Qvk3UpdateManifest.VK_BASE).append('\n');
        b.append("Пакет: ").append(c.getPackageName()).append('\n');
        b.append("Устройство: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n");
        b.append("Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append(", ").append(Build.DISPLAY).append('\n');
        b.append("ABI: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?").append('\n');
        b.append("Запись логов: ").append(enabled(c) ? "включена" : "выключена").append('\n');
        try {
            b.append("\n--- Сбои модулей ---\n").append(RuntimeFaults.hasFailures() ? RuntimeFaults.report() : "не зарегистрировано").append('\n');
        } catch (Throwable t) {
            b.append("\n--- Сбои модулей: недоступны: ").append(Qvk3Jobs.message(t)).append('\n');
        }
        return b.toString();
    }

    /** Log text oldest-first, secrets masked. tailBytes <= 0 means everything that is stored. */
    static String body(Context c, long tailBytes) {
        File d = dir(c);
        File[] parts = {new File(d, "previous.log"), new File(d, "middle.log"), new File(d, "current.log")};
        StringBuilder b = new StringBuilder();
        long total = 0;
        for (File f : parts) if (f.isFile()) total += f.length();
        long skip = tailBytes > 0 && total > tailBytes ? total - tailBytes : 0;
        for (File f : parts) {
            if (!f.isFile()) continue;
            b.append("\n--- ").append(f.getName()).append(" ---\n");
            long len = f.length();
            long from = Math.min(len, Math.max(0, skip));
            skip -= Math.min(skip, len);
            if (from >= len) continue;
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                raf.seek(from);
                byte[] buf = new byte[(int) (len - from)];
                raf.readFully(buf);
                String s = new String(buf, StandardCharsets.UTF_8);
                if (from > 0) {
                    int nl = s.indexOf('\n');
                    if (nl >= 0) s = s.substring(nl + 1);   // start on a whole line
                }
                b.append(Qvk3LogRedact.clean(s));
            } catch (IOException e) {
                b.append("(не прочитано: ").append(e.getMessage()).append(")\n");
            }
        }
        return b.toString();
    }

    static long size(Context c) {
        long n = 0;
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) n += f.length();
        return n;
    }

    static void clear(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) f.delete();
        if (running) {
            stop();
            start(c.getApplicationContext());
        }
    }

    /** Copies the header plus the newest part of the log (the full log is too big for the clipboard). */
    static void copy(Activity a) {
        Qvk3Jobs.io(() -> {
            String s = header(a) + body(a, COPY_TAIL);
            Qvk3Jobs.main(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("QVK log", s));
                    Qvk3Ui.toast(a, "Скопировано: " + (s.length() / 1024) + " КБ. Весь журнал — кнопка «В файл»");
                } catch (Throwable t) {
                    Qvk3Ui.toast(a, "Не удалось скопировать: " + Qvk3Jobs.message(t) + ". Сохраните в файл");
                }
            });
        });
    }

    /** Saves the whole report to Downloads/QVK (or app storage on old Android). */
    static void save(Activity a) {
        Qvk3Jobs.io(() -> {
            String msg;
            try {
                String text = header(a) + body(a, 0);
                String name = "QVK-log-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
                msg = "Сохранено: " + write(a, name, text.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable t) {
                msg = "Не удалось сохранить: " + Qvk3Jobs.message(t);
            }
            final String m = msg;
            Qvk3Jobs.main(() -> Qvk3Ui.toast(a, m));
        });
    }

    private static String write(Context c, String name, byte[] data) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/QVK");
            Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new IOException("MediaStore отказал");
            try (OutputStream os = cr.openOutputStream(u)) {
                if (os == null) throw new IOException("нет потока записи");
                os.write(data);
            }
            return "Загрузки/QVK/" + name;
        }
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        File f = new File(dir, name);
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(data);
        }
        return f.getAbsolutePath();
    }
}
