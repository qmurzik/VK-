package com.vkmod.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single registry of mod features: lookup, state, auto-disable after a failed hook. */
public final class Registry {
    private static final Map<String, Feature> BY_ID = new LinkedHashMap<>();
    private static volatile boolean loaded;

    private Registry() {}

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        for (Feature f : Catalog.all()) BY_ID.put(f.id, f);
        loaded = true;
    }

    public static List<Feature> all() { ensureLoaded(); return Collections.unmodifiableList(new ArrayList<>(BY_ID.values())); }
    public static Feature get(String id) { ensureLoaded(); return BY_ID.get(id); }

    /** True when the feature is switched on by the user and has not been disabled by a failure. */
    public static boolean isOn(String id) {
        Feature f = get(id);
        if (f == null) return false;
        if (Prefs.getBool("crash." + id, false)) return false;
        if (f.kind == Feature.Kind.ACTION) return false;
        if (f.kind == Feature.Kind.CHOICE) return true; // value-driven: the chosen option decides
        return Prefs.getBool(f.enabledKey(), f.defaultOn);
    }

    public static void setOn(String id, boolean on) {
        Feature f = get(id);
        if (f == null) return;
        Prefs.putBool(f.enabledKey(), on);
        if (on) Prefs.remove("crash." + id); // re-enabling by hand clears the failure latch
    }

    public static boolean crashed(String id) { return Prefs.getBool("crash." + id, false); }
    public static void markCrashed(String id) { Prefs.putBool("crash." + id, true); }

    public static String str(String id) {
        Feature f = get(id);
        return Prefs.getString(f == null ? "v." + id : f.valueKey(), f == null ? "" : f.defString);
    }

    public static int num(String id) {
        Feature f = get(id);
        return Prefs.getInt(f == null ? "v." + id : f.valueKey(), f == null ? 0 : f.defInt);
    }

    public static int countOn() {
        int n = 0;
        for (Feature f : all()) if (f.kind != Feature.Kind.ACTION && isOn(f.id)) n++;
        return n;
    }
}
