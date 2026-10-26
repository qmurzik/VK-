package com.vkmod;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.vkmod.core.Guard;
import com.vkmod.core.ModLog;
import com.vkmod.core.Registry;
import com.vkmod.feat.ViewTweaks;
import com.vkmod.net.NetHook;
import com.vkmod.theme.ThemeEngine;

import java.util.List;

/**
 * The only entry points called from patched smali. Every method swallows all errors and returns the
 * original value, so a bug in the mod can never crash VK.
 */
public final class Hooks {
    private Hooks() {}

    /** VKApplication.attachBaseContext, after super. */
    public static void attach(Application app) {
        Mod.attach(app);
    }

    /** Resolver of VK theme colours: (attr, context) -> colour. */
    public static int color(int attr, Context ctx, int orig) {
        try {
            if (!Registry.isOn("theme_engine")) return orig;
            return ThemeEngine.resolve(attr, ctx, orig);
        } catch (Throwable t) {
            Guard.fail("theme_engine", t);
            return orig;
        }
    }

    /** OkHttpClient(Builder): application interceptors list. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List okInterceptors(List original) {
        try {
            return NetHook.wrap((List<Object>) original);
        } catch (Throwable t) {
            ModLog.e("okInterceptors", t);
            return original;
        }
    }

    /** VKLayoutInflater.inflate(int, ViewGroup, boolean) result. */
    public static View inflated(View v, int layoutRes, ViewGroup root, boolean attached) {
        try {
            // inflate(res, root, true) returns the root; the new subtree is its last child
            View target = v;
            if (attached && root != null && root.getChildCount() > 0) target = root.getChildAt(root.getChildCount() - 1);
            if (target != null) ViewTweaks.apply(target);
        } catch (Throwable t) {
            ModLog.e("inflated", t);
        }
        return v;
    }
}
