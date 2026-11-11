package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostics for BUG "cache growth": how big the app's data is, which files are the biggest, and how the total changed
 * between launches. Read-only: nothing is deleted or changed here, so conversation history is never at risk.
 */
final class Qvk3Growth {
    private static final String KEY = "qvk3_growth_history";
    private static final int KEEP = 40;                 // samples kept
    private static final long MIN_GAP = 12L * 3_600_000; // one sample per 12 h at most: walking the data folder costs IO

    private Qvk3Growth() {
    }

    static final class Entry {
        final String path;
        final long bytes;

        Entry(String path, long bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }

    static long size(File f, int depth) {
        if (f == null || depth > 8) return 0;
        if (f.isFile()) return f.length();
        File[] list = f.listFiles();
        long n = 0;
        if (list != null) for (File c : list) n += size(c, depth + 1);
        return n;
    }

    private static File root(Context c) {
        File f = c.getFilesDir().getParentFile();
        return f != null ? f : c.getFilesDir();
    }

    /** Largest files under the app's data directory (databases, cache, files, no_backup), biggest first. */
    static List<Entry> biggest(Context c, int limit) {
        List<Entry> all = new ArrayList<>();
        walk(root(c), root(c).getAbsolutePath().length() + 1, all, 0);
        Collections.sort(all, (x, y) -> Long.compare(y.bytes, x.bytes));
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    private static void walk(File f, int cut, List<Entry> out, int depth) {
        if (depth > 8) return;
        File[] list = f.listFiles();
        if (list == null) return;
        for (File x : list) {
            if (x.isDirectory()) walk(x, cut, out, depth + 1);
            else if (x.length() > 0) out.add(new Entry(x.getAbsolutePath().substring(Math.min(cut, x.getAbsolutePath().length())), x.length()));
        }
    }

    static long databases(Context c) {
        return size(new File(root(c), "databases"), 0);
    }

    static long caches(Context c) {
        long n = size(c.getCacheDir(), 0);
        File ext = c.getExternalCacheDir();
        return n + (ext == null ? 0 : size(ext, 0));
    }

    /** Called once per launch from a background thread: appends one sample when enough time has passed. */
    static void sample(Context c) {
        try {
            SharedPreferences p = Qvk3MusicSettings.prefs(c);
            JSONArray a = new JSONArray(p.getString(KEY, "[]"));
            long now = System.currentTimeMillis();
            if (a.length() > 0 && now - a.getJSONObject(a.length() - 1).optLong("t") < MIN_GAP) return;
            long db = databases(c), cache = caches(c), all = size(root(c), 0);
            a.put(new JSONObject().put("t", now).put("db", db).put("cache", cache).put("all", all));
            JSONArray cut = new JSONArray();
            for (int i = Math.max(0, a.length() - KEEP); i < a.length(); i++) cut.put(a.get(i));
            p.edit().putString(KEY, cut.toString()).apply();
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.growth.sample", t);
        }
    }

    static String mb(long b) {
        return b >= (10L << 20) ? (b >> 20) + " МБ" : String.format(Locale.US, "%.1f МБ", b / 1048576.0);
    }

    /** Human-readable report: current sizes, change since the first and the previous sample, top files. */
    static String report(Context c) {
        StringBuilder s = new StringBuilder();
        long db = databases(c), cache = caches(c), all = size(root(c), 0);
        s.append("Данные приложения: ").append(mb(all)).append("\nБазы данных (сообщения и кэши ВК): ").append(mb(db)).append("\nВременный кэш: ").append(mb(cache)).append('\n');
        try {
            JSONArray a = new JSONArray(Qvk3MusicSettings.prefs(c).getString(KEY, "[]"));
            SimpleDateFormat f = new SimpleDateFormat("dd.MM HH:mm", Locale.US);
            if (a.length() >= 2) {
                JSONObject first = a.getJSONObject(0), prev = a.getJSONObject(a.length() - 1);
                long days = Math.max(1, (System.currentTimeMillis() - first.optLong("t")) / 86_400_000L);
                s.append("\nС ").append(f.format(new Date(first.optLong("t")))).append(" базы выросли с ").append(mb(first.optLong("db"))).append(" до ").append(mb(db))
                        .append(" (").append(db >= first.optLong("db") ? "+" : "").append(mb(db - first.optLong("db"))).append(", ").append(days).append(" дн.)\n");
                s.append("С прошлого замера (").append(f.format(new Date(prev.optLong("t")))).append("): ").append(db >= prev.optLong("db") ? "+" : "").append(mb(db - prev.optLong("db"))).append('\n');
                s.append("\nИстория замеров (базы):\n");
                for (int i = Math.max(0, a.length() - 8); i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    s.append("  ").append(f.format(new Date(o.optLong("t")))).append("  ").append(mb(o.optLong("db"))).append('\n');
                }
            } else {
                s.append("\nИстории роста пока нет: замер не чаще раза в 12 часов при запуске, нужно хотя бы два.\n");
            }
        } catch (Exception ignored) {
            // history is optional
        }
        s.append("\nСамые большие файлы:\n");
        for (Entry e : biggest(c, 8)) s.append("  ").append(mb(e.bytes)).append("  ").append(e.path).append('\n');
        s.append("\nЕсли база растёт резко после вступления в беседу или канал, запомните время и сделайте скриншот этого экрана и логи.");
        return s.toString();
    }
}
