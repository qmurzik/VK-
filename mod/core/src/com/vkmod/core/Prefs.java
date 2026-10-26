package com.vkmod.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Map;

/** Single settings store of the mod. Values live in one SharedPreferences file; backup is a JSON dump. */
public final class Prefs {
    private static final String FILE = "vkmod_prefs";
    private static volatile SharedPreferences sp;
    private static final java.util.concurrent.atomic.AtomicInteger VERSION = new java.util.concurrent.atomic.AtomicInteger();

    /** Bumped on every write; lets consumers cache values derived from settings. */
    public static int version() { return VERSION.get(); }

    private Prefs() {}

    public static void init(Context ctx) {
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Test seam: use an arbitrary SharedPreferences implementation. */
    public static void attach(SharedPreferences prefs) {
        sp = prefs;
        VERSION.incrementAndGet();
    }

    private static SharedPreferences s() { return sp; }

    public static boolean ready() { return sp != null; }

    public static boolean getBool(String key, boolean def) {
        SharedPreferences p = s();
        return p == null ? def : p.getBoolean(key, def);
    }

    public static String getString(String key, String def) {
        SharedPreferences p = s();
        return p == null ? def : p.getString(key, def);
    }

    public static int getInt(String key, int def) {
        SharedPreferences p = s();
        if (p == null) return def;
        try { return p.getInt(key, def); } catch (ClassCastException e) {
            try { return Integer.parseInt(p.getString(key, "")); } catch (Exception ignored) { return def; }
        }
    }

    public static void putBool(String key, boolean v) { SharedPreferences p = s(); if (p != null) { p.edit().putBoolean(key, v).apply(); VERSION.incrementAndGet(); } }
    public static void putString(String key, String v) { SharedPreferences p = s(); if (p != null) { p.edit().putString(key, v).apply(); VERSION.incrementAndGet(); } }
    public static void putInt(String key, int v) { SharedPreferences p = s(); if (p != null) { p.edit().putInt(key, v).apply(); VERSION.incrementAndGet(); } }
    public static void remove(String key) { SharedPreferences p = s(); if (p != null) { p.edit().remove(key).apply(); VERSION.incrementAndGet(); } }

    /** Everything as JSON for backup/export. */
    public static String exportJson() {
        JSONObject o = new JSONObject();
        SharedPreferences p = s();
        if (p == null) return o.toString();
        try {
            for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
                if (e.getKey().startsWith("crash.")) continue; // runtime state, not settings
                o.put(e.getKey(), e.getValue());
            }
        } catch (Throwable t) { ModLog.w("export failed", t); }
        return o.toString();
    }

    /** Replaces all settings with the JSON dump. Returns the number of imported keys, or -1 on bad input. */
    public static int importJson(String json) {
        SharedPreferences p = s();
        if (p == null) return -1;
        try {
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor ed = p.edit().clear();
            int n = 0;
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = o.get(k);
                if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                else if (v instanceof Long) ed.putLong(k, (Long) v);
                else if (v instanceof Double) ed.putFloat(k, ((Double) v).floatValue());
                else ed.putString(k, String.valueOf(v));
                n++;
            }
            ed.apply();
            VERSION.incrementAndGet();
            return n;
        } catch (Throwable t) {
            ModLog.w("import failed", t);
            return -1;
        }
    }

    public static void resetAll() { SharedPreferences p = s(); if (p != null) { p.edit().clear().apply(); VERSION.incrementAndGet(); } }
}
