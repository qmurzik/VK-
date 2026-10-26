package com.vkmod;

import android.app.Application;

import com.vkmod.core.ModLog;
import com.vkmod.core.Prefs;
import com.vkmod.core.Registry;
import com.vkmod.feat.Lifecycle;
import com.vkmod.feat.ProxyConfig;

/** Mod bootstrap: storage, log, registry and the lifecycle-driven features. */
public final class Mod {
    private Mod() {}

    public static final String VERSION = "1.0.0";
    private static volatile Application app;
    private static volatile boolean attached;

    public static Application app() { return app; }

    public static synchronized void attach(Application a) {
        if (attached) return;
        attached = true;
        app = a;
        try {
            Prefs.init(a);
            ModLog.init(a.getFilesDir());
            ModLog.i("VK Mod " + VERSION + " attached in " + processName(a));
            Registry.ensureLoaded();
        } catch (Throwable t) {
            ModLog.e("init failed", t);
            return;
        }
        try {
            Lifecycle.install(a);
        } catch (Throwable t) {
            ModLog.e("lifecycle install failed", t);
        }
        try {
            ProxyConfig.apply();
        } catch (Throwable t) {
            ModLog.e("proxy config failed", t);
        }
    }

    private static String processName(Application a) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        } catch (Throwable ignored) { }
        return a.getPackageName();
    }
}
