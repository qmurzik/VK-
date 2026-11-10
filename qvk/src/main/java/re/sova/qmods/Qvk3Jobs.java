package re.sova.qmods;

import android.os.Handler;
import android.os.Looper;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded background executors for QVK 3.0 modules (network/disk never run on the main thread). */
public final class Qvk3Jobs {
    private static final AtomicInteger n = new AtomicInteger();
    private static final ExecutorService IO = pool("qvk3-io", 3);
    private static final ExecutorService DOWNLOADS = pool("qvk3-dl", 1);
    public static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static ExecutorService pool(String name, int threads) {
        ThreadPoolExecutor e = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, name + "-" + n.incrementAndGet());
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    t.setDaemon(true);
                    return t;
                });
        e.allowCoreThreadTimeOut(true);
        return e;
    }

    public static Future<?> io(Runnable r) {
        return IO.submit(guard(r));
    }

    /** Single-threaded lane: downloads run strictly one after another. */
    public static Future<?> download(Runnable r) {
        return DOWNLOADS.submit(guard(r));
    }

    public static void main(Runnable r) {
        MAIN.post(r);
    }

    private static Runnable guard(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                RuntimeFaults.record("qvk3.job", e);
            }
        };
    }

    /** Short user-facing description of a failure. */
    public static String message(Throwable e) {
        while (e.getCause() != null && (e.getMessage() == null || e instanceof java.lang.reflect.InvocationTargetException)) e = e.getCause();
        if (e instanceof UnknownHostException) return "Нет подключения к интернету";
        if (e instanceof SocketTimeoutException) return "Сервер не ответил вовремя";
        if (e instanceof InterruptedIOException) return "Операция отменена";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
