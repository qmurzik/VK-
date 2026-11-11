package re.sova.qmods;

/**
 * Pacing for QVK 2.x's screen runtime (ScreenRuntime, patch 0011). It re-applies all 28 appearance modules to the whole
 * screen after every layout change. Those modules change views themselves, which causes another layout on the next
 * frame, so on an idle screen the pass used to repeat about 12 times a second for as long as the app was open — a
 * long-standing battery drain. Here a layout that comes right after a pass (within 60 ms: caused by the pass itself)
 * makes the next pass wait longer (200 → 400 → 800 → 1600 ms); scrolling, focus or any layout that is not self-caused
 * brings it straight back to 200 ms, so real changes are still restyled quickly.
 */
public final class Qvk3UiThrottle {
    private static final long BASE = 200, MAX_SHIFT = 3, SELF_CAUSED_MS = 60;
    private static int streak;
    private static boolean input;

    private Qvk3UiThrottle() {
    }

    /** User moved something (scroll / focus): restyle promptly. */
    public static void input(Object root) {
        input = true;
    }

    /** Minimum spacing between passes; sinceLast = time since the previous pass started. */
    public static long interval(Object root, long sinceLast) {
        return next(sinceLast);
    }

    static synchronized long next(long sinceLast) {
        if (input) {
            input = false;
            streak = 0;
        } else if (sinceLast >= 0 && sinceLast < SELF_CAUSED_MS) {
            if (streak < 30) streak++;
        } else {
            streak = 0;
        }
        return BASE << Math.min(MAX_SHIFT, streak);
    }

    /** Test hook. */
    static synchronized void reset() {
        streak = 0;
        input = false;
    }
}
