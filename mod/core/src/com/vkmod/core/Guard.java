package com.vkmod.core;

import java.util.concurrent.Callable;

/**
 * Failure isolation for every hook. A throwing feature is switched off (latched in prefs) and logged;
 * the host application keeps running with the original behaviour.
 */
public final class Guard {
    private Guard() {}

    public static void run(String featureId, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            fail(featureId, t);
        }
    }

    public static <T> T call(String featureId, T fallback, Callable<T> c) {
        try {
            return c.call();
        } catch (Throwable t) {
            fail(featureId, t);
            return fallback;
        }
    }

    /** For hooks that are not tied to a toggle (infrastructure): log only. */
    public static <T> T infra(String what, T fallback, Callable<T> c) {
        try {
            return c.call();
        } catch (Throwable t) {
            ModLog.e("infra failure in " + what, t);
            return fallback;
        }
    }

    public static void fail(String featureId, Throwable t) {
        ModLog.e("feature '" + featureId + "' disabled after error", t);
        try { Registry.markCrashed(featureId); } catch (Throwable ignored) { }
    }
}
