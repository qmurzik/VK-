package com.vkmod.theme;

import java.util.EnumMap;
import java.util.Map;

/** Slot -> ARGB override. Only the slots that differ from VK's own theme are present. */
public final class Palette {
    public final Map<ColorSlot, Integer> colors = new EnumMap<>(ColorSlot.class);

    public static final Palette EMPTY = new Palette();

    public Palette put(ColorSlot s, long argb) { colors.put(s, (int) argb); return this; }
    public Integer get(ColorSlot s) { return colors.get(s); }
    public boolean isEmpty() { return colors.isEmpty(); }

    public Palette copy() { Palette p = new Palette(); p.colors.putAll(colors); return p; }

    public static String hex(int argb) { return String.format("#%08X", argb); }

    public static int parse(String s) {
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        long v = Long.parseLong(s, 16);
        if (s.length() <= 6) v |= 0xFF000000L;
        return (int) v;
    }
}
