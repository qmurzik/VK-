package com.vkmod.theme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.util.SparseArray;
import android.util.TypedValue;

import com.vkmod.core.ModLog;
import com.vkmod.core.Prefs;
import com.vkmod.core.Registry;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Theme engine. VK funnels its own colour lookups through a single resolver (hooked in smali, see
 * {@link com.vkmod.Hooks#color}); the engine classifies the attribute by *name* into a semantic
 * [ColorSlot] and substitutes the colour from the effective palette:
 * preset < Material You < AMOLED < user colours, then bar transparency.
 */
public final class ThemeEngine {
    private ThemeEngine() {}

    private static final class Entry {
        final ColorSlot slot; final boolean state, alpha;
        Entry(String name) {
            slot = name.isEmpty() ? null : ColorSlot.classify(name);
            state = slot != null && ColorSlot.isStateVariant(name);
            alpha = slot != null && ColorSlot.isAlphaVariant(name);
        }
    }
    private static final SparseArray<Entry> ENTRIES = new SparseArray<>();
    private static final Map<Object, Boolean> DARK_BY_THEME = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> REENTRY = new ThreadLocal<>();

    private static volatile int builtFor = -1;
    private static volatile boolean active;
    private static volatile Palette light = Palette.EMPTY, dark = Palette.EMPTY;
    private static volatile int barsAlpha = 100;
    private static volatile int darkProbeAttr;
    private static volatile boolean materialYouTried;

    public static boolean isActive() { sync(null); return active; }

    /** Entry point used by the smali hook: returns the colour VK should see. */
    public static int resolve(int attrId, Context ctx, int orig) {
        if ((attrId >>> 24) < 0x7f || ctx == null) return orig;
        sync(ctx);
        if (!active) return orig;
        Entry e = entryOf(attrId, ctx);
        ColorSlot slot = e.slot;
        if (slot == null) return orig;
        boolean isDark = isDark(ctx);
        Palette p = isDark ? dark : light;
        Integer c = p.get(slot);
        int out = orig;
        if (c != null) {
            out = c;
            if (e.state) out = tintVariant(out, isDark);
            if (e.alpha) out = (orig & 0xFF000000) | (out & 0x00FFFFFF);
        }
        if (barsAlpha < 100 && (slot == ColorSlot.HEADER || slot == ColorSlot.TABBAR)) {
            int a = Math.round(((out >>> 24) * barsAlpha) / 100f);
            out = (a << 24) | (out & 0x00FFFFFF);
        }
        return out;
    }

    // ---- state ----

    private static void sync(Context ctx) {
        int v = Prefs.version();
        if (v == builtFor) return;
        synchronized (ThemeEngine.class) {
            if (v == builtFor) return;
            try {
                active = Registry.isOn("theme_engine");
                barsAlpha = Registry.isOn("bars_alpha") ? Math.max(0, Math.min(100, Registry.num("bars_alpha"))) : 100;
                Preset preset = Presets.byId(Registry.str("theme_preset"));
                Palette l = preset.light.copy(), d = preset.dark.copy();
                if (Registry.isOn("material_you") && Build.VERSION.SDK_INT >= 31 && ctx != null) applyMaterialYou(ctx, l, d);
                if (Registry.isOn("theme_amoled")) {
                    for (ColorSlot s : new ColorSlot[]{ColorSlot.BACKGROUND, ColorSlot.CARD, ColorSlot.HEADER, ColorSlot.TABBAR, ColorSlot.CHAT_BG})
                        d.put(s, 0xFF000000);
                    d.put(ColorSlot.FIELD, 0xFF0A0A0A);
                    d.put(ColorSlot.SEPARATOR, 0xFF1A1A1A);
                }
                for (ColorSlot s : ColorSlot.values()) {
                    if (Prefs.getBool("cs." + s.id + ".l", false)) l.put(s, Prefs.getInt("c." + s.id + ".l", 0));
                    if (Prefs.getBool("cs." + s.id + ".d", false)) d.put(s, Prefs.getInt("c." + s.id + ".d", 0));
                }
                light = l; dark = d;
                synchronized (ENTRIES) { ENTRIES.clear(); }
                DARK_BY_THEME.clear();
            } catch (Throwable t) {
                ModLog.e("theme rebuild failed", t);
                active = false;
            }
            builtFor = v;
        }
    }

    private static void applyMaterialYou(Context ctx, Palette l, Palette d) {
        try {
            Resources r = ctx.getResources();
            Context c = ctx;
            int a600 = c.getColor(android.R.color.system_accent1_600), a200 = c.getColor(android.R.color.system_accent1_200);
            int a100 = c.getColor(android.R.color.system_accent1_100), a700 = c.getColor(android.R.color.system_accent1_700);
            int n50 = c.getColor(android.R.color.system_neutral1_50), n0 = c.getColor(android.R.color.system_neutral1_0);
            int n100 = c.getColor(android.R.color.system_neutral1_100), n800 = c.getColor(android.R.color.system_neutral1_800);
            int n900 = c.getColor(android.R.color.system_neutral1_900), n700 = c.getColor(android.R.color.system_neutral1_700);
            int n1000 = c.getColor(android.R.color.system_neutral1_1000);
            int t300 = c.getColor(android.R.color.system_accent3_200);
            l.put(ColorSlot.ACCENT, a600).put(ColorSlot.LINK, a600).put(ColorSlot.BACKGROUND, n50).put(ColorSlot.CARD, n0)
                    .put(ColorSlot.HEADER, n0).put(ColorSlot.TABBAR, n0).put(ColorSlot.FIELD, n100)
                    .put(ColorSlot.BUBBLE_OUT, a100).put(ColorSlot.BUBBLE_IN, n100).put(ColorSlot.TABBAR_ACTIVE, a600);
            d.put(ColorSlot.ACCENT, a200).put(ColorSlot.LINK, a200).put(ColorSlot.BACKGROUND, n900).put(ColorSlot.CARD, n800)
                    .put(ColorSlot.HEADER, n800).put(ColorSlot.TABBAR, n800).put(ColorSlot.FIELD, n700)
                    .put(ColorSlot.BUBBLE_OUT, a700).put(ColorSlot.BUBBLE_IN, n700).put(ColorSlot.TABBAR_ACTIVE, a200);
        } catch (Throwable t) {
            if (!materialYouTried) { materialYouTried = true; ModLog.w("Material You palette unavailable", t); }
        }
    }

    // ---- classification ----

    private static Entry entryOf(int attrId, Context ctx) {
        synchronized (ENTRIES) {
            Entry e = ENTRIES.get(attrId);
            if (e == null) {
                String name;
                try { name = ctx.getResources().getResourceEntryName(attrId); } catch (Throwable t) { name = ""; }
                e = new Entry(name);
                ENTRIES.put(attrId, e);
            }
            return e;
        }
    }

    /** Brightness of the VK theme the context currently shows (VK's light/dark is independent of the system). */
    public static boolean isDark(Context ctx) {
        Resources.Theme theme = ctx.getTheme();
        Boolean cached = DARK_BY_THEME.get(theme);
        if (cached != null) return cached;
        boolean dark = false;
        try {
            if (darkProbeAttr == 0) {
                for (String n : new String[]{"vk_ui_background_content", "vk_ui_background", "vk_legacy_background_content"}) {
                    int id = ctx.getResources().getIdentifier(n, "attr", ctx.getPackageName());
                    if (id != 0) { darkProbeAttr = id; break; }
                }
            }
            TypedValue tv = new TypedValue();
            if (darkProbeAttr != 0 && theme.resolveAttribute(darkProbeAttr, tv, true)
                    && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                dark = luminance(tv.data) < 0.5;
            } else {
                dark = (ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignored) { }
        DARK_BY_THEME.put(theme, dark);
        return dark;
    }

    static double luminance(int color) {
        return 0.2126 * Color.red(color) / 255.0 + 0.7152 * Color.green(color) / 255.0 + 0.0722 * Color.blue(color) / 255.0;
    }

    /** Pressed/hover variants are nudged so they stay visible against the base colour. */
    static int tintVariant(int color, boolean dark) {
        double f = dark ? 1.18 : 0.90;
        int a = color >>> 24;
        int r = (int) Math.min(255, Math.max(0, Math.round(Color.red(color) * f)));
        int g = (int) Math.min(255, Math.max(0, Math.round(Color.green(color) * f)));
        int b = (int) Math.min(255, Math.max(0, Math.round(Color.blue(color) * f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---- editor / custom colours ----

    public static Palette effective(boolean forDark) { sync(null); return forDark ? dark : light; }

    public static void setCustom(ColorSlot slot, boolean forDark, Integer argb) {
        String sfx = forDark ? ".d" : ".l";
        if (argb == null) { Prefs.remove("cs." + slot.id + sfx); Prefs.remove("c." + slot.id + sfx); return; }
        Prefs.putInt("c." + slot.id + sfx, argb);
        Prefs.putBool("cs." + slot.id + sfx, true);
    }

    public static boolean hasCustom(ColorSlot slot, boolean forDark) { return Prefs.getBool("cs." + slot.id + (forDark ? ".d" : ".l"), false); }

    public static void clearCustom() {
        for (ColorSlot s : ColorSlot.values()) { setCustom(s, false, null); setCustom(s, true, null); }
    }

    // ---- import / export (shareable JSON) ----

    public static String exportTheme(String name) {
        try {
            JSONObject o = new JSONObject();
            o.put("format", "vkmod-theme/1");
            o.put("name", name == null ? "My theme" : name);
            o.put("preset", Registry.str("theme_preset"));
            o.put("amoled", Registry.isOn("theme_amoled"));
            JSONObject l = new JSONObject(), d = new JSONObject();
            for (ColorSlot s : ColorSlot.values()) {
                if (hasCustom(s, false)) l.put(s.id, Palette.hex(Prefs.getInt("c." + s.id + ".l", 0)));
                if (hasCustom(s, true)) d.put(s.id, Palette.hex(Prefs.getInt("c." + s.id + ".d", 0)));
            }
            o.put("light", l);
            o.put("dark", d);
            return o.toString(2);
        } catch (Throwable t) {
            ModLog.e("export theme failed", t);
            return null;
        }
    }

    /** @return null on success or a human-readable error. */
    public static String importTheme(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!"vkmod-theme/1".equals(o.optString("format"))) return "Это не файл темы мода (format != vkmod-theme/1)";
            clearCustom();
            String preset = o.optString("preset", "none");
            Prefs.putString("v.theme_preset", Presets.byId(preset).id);
            Registry.setOn("theme_amoled", o.optBoolean("amoled", false));
            Registry.setOn("theme_engine", true);
            for (int pass = 0; pass < 2; pass++) {
                JSONObject src = o.optJSONObject(pass == 0 ? "light" : "dark");
                if (src == null) continue;
                for (Iterator<String> it = src.keys(); it.hasNext(); ) {
                    String k = it.next();
                    ColorSlot s = ColorSlot.byId(k);
                    if (s == null) continue;
                    setCustom(s, pass == 1, Palette.parse(src.getString(k)));
                }
            }
            return null;
        } catch (Throwable t) {
            return "Не удалось прочитать тему: " + t.getMessage();
        }
    }
}
