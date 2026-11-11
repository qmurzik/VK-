package re.sova.qmods;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Cover loader for QVK Music: bounded memory LRU (1/8 of the heap), bounded disk cache, request
 * coalescing, LIFO scheduling (the most recently requested covers — the ones on screen — load first) and
 * explicit prefetch, so CoverFlow never decodes images while the user is flinging.
 */
public final class Qvk3Images {
    public interface Callback {
        void done(Bitmap bitmap);
    }

    private static volatile Qvk3Images instance;

    public static Qvk3Images get(Context c) {
        if (instance == null) {
            synchronized (Qvk3Images.class) {
                if (instance == null) instance = new Qvk3Images(c.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context app;
    private final LruCache<String, Bitmap> memory;
    private final File dir;
    private final ExecutorService pool;
    private final Map<String, List<Callback>> inflight = new HashMap<>();

    private Qvk3Images(Context c) {
        app = c;
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 6);
        memory = new LruCache<String, Bitmap>(kb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        // BUG-0025: not in cacheDir — HyperOS/MIUI cleaners empty app caches, and covers were downloaded again and again.
        // Same size limit as before (Qvk3MusicSettings.cacheLimitMb), so it never grows without bound.
        dir = new File(c.getFilesDir(), "qvk3_covers");
        dir.mkdirs();
        File old = new File(c.getCacheDir(), "qvk3_covers");
        if (old.isDirectory()) Qvk3Jobs.io(() -> {
            File[] files = old.listFiles();
            if (files != null) for (File f : files) if (!f.renameTo(new File(dir, f.getName()))) f.delete();
            old.delete();
        });
        LinkedBlockingDeque<Runnable> lifo = new LinkedBlockingDeque<Runnable>() {
            @Override
            public boolean offer(Runnable r) {
                return offerFirst(r);
            }
        };
        ThreadPoolExecutor e = new ThreadPoolExecutor(3, 3, 20, TimeUnit.SECONDS, lifo);
        e.allowCoreThreadTimeOut(true);
        pool = e;
    }

    static String key(String url, int size) {
        return size + ":" + stable(url);
    }

    /**
     * BUG-0025: VK image links carry a per-request signature and a random server (sun9-12…, ?sign=…&quality=…), so the same
     * cover got a new cache key every time the album list was refreshed and was downloaded again. The image itself is the path
     * plus its size parameter; that is the key for memory and disk.
     */
    /**
     * BUG-0025 (2): newer VK image links carry the signature inside the path (/s/v1/ig2/<signed>.jpg), so not even the path
     * is stable. Screens that know what the picture is (an album, a playlist) register its identity here, and the cache uses
     * that identity instead of the link.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> aliases = new java.util.concurrent.ConcurrentHashMap<>();

    public static void alias(String url, String identity) {
        if (url != null && !url.isEmpty() && identity != null && aliases.size() < 5000) aliases.put(url, "id:" + identity);
    }

    public static String stable(String url) {
        if (url == null) return "";
        String a = aliases.get(url);
        if (a != null) return a;
        try {
            java.net.URI u = new java.net.URI(url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(java.util.Locale.ROOT);
            if (!(host.endsWith("userapi.com") || host.endsWith("vk.com") || host.endsWith("vkuser.net") || host.endsWith("vk-cdn.net"))) return url;
            String size = "";
            if (u.getRawQuery() != null) for (String p : u.getRawQuery().split("&")) if (p.startsWith("size=") || p.startsWith("type=")) size += "&" + p;
            return "vkimg:" + u.getRawPath() + size;
        } catch (Exception e) {
            return url;
        }
    }

    /** Any cached size of this cover (a smaller one is a better placeholder than an empty frame). */
    public Bitmap cachedAny(String url, int prefer) {
        if (url == null || url.isEmpty()) return null;
        Bitmap b = memory.get(key(url, prefer));
        if (b == null) b = memory.get(key(url, prefer == 600 ? 300 : 600));
        return b;
    }

    public Bitmap cached(String url, int size) {
        return url == null || url.isEmpty() ? null : memory.get(key(url, size));
    }

    /** Loads (or returns from cache) a cover scaled to roughly {@code size} px; callback on the main thread. */
    public void load(String url, int size, Callback cb) {
        if (url == null || url.isEmpty()) {
            if (cb != null) cb.done(null);
            return;
        }
        String k = key(url, size);
        Bitmap hit = memory.get(k);
        if (hit != null) {
            if (cb != null) cb.done(hit);
            return;
        }
        synchronized (inflight) {
            List<Callback> waiting = inflight.get(k);
            if (waiting != null) {
                if (cb != null) waiting.add(cb);
                return;
            }
            List<Callback> l = new ArrayList<>();
            if (cb != null) l.add(cb);
            inflight.put(k, l);
        }
        pool.execute(() -> {
            Bitmap b = null;
            try {
                b = fetch(url, size);
            } catch (Throwable t) {
                // a missing cover is not an error worth recording
            }
            if (b != null) memory.put(k, b);
            List<Callback> l;
            synchronized (inflight) {
                l = inflight.remove(k);
            }
            final Bitmap result = b;
            if (l != null && !l.isEmpty()) Qvk3Jobs.main(() -> {
                for (Callback c : l) c.done(result);
            });
        });
    }

    /** Warms the cache without a callback (neighbouring covers in CoverFlow / next track). */
    public void prefetch(String url, int size) {
        if (cached(url, size) == null) load(url, size, null);
    }

    /** Downloads the cover file into the disk cache only (no decoding, no memory): album lists of 150 covers. */
    public void warmDisk(String url) {
        if (url == null || url.isEmpty()) return;
        pool.execute(() -> {
            try {
                file(url);
            } catch (Throwable ignored) {
                // a missing cover is not an error worth recording
            }
        });
    }

    private Bitmap fetch(String url, int size) throws Exception {
        File f = file(url);
        if (f == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565; // covers have no transparency: half the memory, twice as many stay cached
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** The cover file in the disk cache, downloaded if missing; null when the server has no image. */
    private File file(String url) throws Exception {
        File f = new File(dir, hash(stable(url)));
        if (f.isFile()) {
            f.setLastModified(System.currentTimeMillis());
            return f;
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) {
            c.disconnect();
            return null;
        }
        File tmp = new File(dir, f.getName() + "." + Thread.currentThread().getId() + ".part");
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            long total = 0;
            int r;
            while ((r = in.read(buf)) != -1) {
                total += r;
                if (total > 8 << 20) throw new java.io.IOException("cover too large");
                out.write(buf, 0, r);
            }
        } finally {
            c.disconnect();
        }
        if (!tmp.renameTo(f)) tmp.delete();
        trim();
        return f.isFile() ? f : null;
    }

    /** Keeps the disk cache under the user's cache limit by removing least recently used files. */
    void trim() {
        long limit = (long) Qvk3MusicSettings.cacheLimitMb(app) << 20;
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= limit) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= limit * 8 / 10) break;
            total -= f.length();
            f.delete();
        }
    }

    public long diskBytes() {
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    public void clear() {
        memory.evictAll();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    static String hash(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format("%02x", x));
        return b.toString();
    }
}
