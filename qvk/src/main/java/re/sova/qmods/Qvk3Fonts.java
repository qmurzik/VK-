package re.sova.qmods;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * QVK 3.1: the font of QVK's own screens (settings, QVK Music, the QVK messenger, sheets). Uses the fonts already
 * shipped in the APK (Inter, Noto, Open Sans, DejaVu Mono — their licences are under «Лицензии шрифтов и эмодзи»);
 * «Системный» keeps Android's font. VK's own screens keep VK's font.
 */
public final class Qvk3Fonts {
    private Qvk3Fonts() {
    }

    static final String PREF = "qvk3_font";
    static final String[] IDS = {"system", "inter", "noto-sans", "open-sans", "noto-serif", "dejavu-mono"};
    static final String[] NAMES = {"Системный", "Inter", "Noto Sans", "Open Sans", "Noto Serif", "DejaVu Mono"};
    private static final String[] FILES = {null, "fonts/inter.ttf", "builtin-fonts/noto-sans.ttf", "builtin-fonts/open-sans.ttf",
            "builtin-fonts/noto-serif.ttf", "builtin-fonts/dejavu-mono.ttf"};

    private static final Map<String, Typeface> CACHE = new HashMap<>();
    static String current(Context c) {
        return Qvk3MusicSettings.prefs(c).getString(PREF, "system");   // in-memory map: cheap; follows «Отменить»
    }

    static void set(Context c, String id) {
        String title = NAMES[0];
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) title = NAMES[i];
        Qvk3SettingsHistory.before(c, PREF, "Шрифт QVK: " + title);
        Qvk3MusicSettings.prefs(c).edit().putString(PREF, id).apply();
    }

    static String name(Context c) {
        String id = current(c);
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return NAMES[0];
    }

    /** The typeface for a text of QVK's screens; null keeps the TextView's default. */
    static Typeface typeface(Context c, String id, boolean bold) {
        if (id == null || "system".equals(id)) return bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : null;
        String key = id + (bold ? "#b" : "");
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) return CACHE.get(key);
        }
        Typeface t = null;
        try {
            String file = null;
            for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) file = FILES[i];
            if (file != null) {
                Typeface base = Typeface.createFromAsset(c.getAssets(), file);
                t = bold ? Typeface.create(base, Typeface.BOLD) : base;
            }
        } catch (Throwable e) {
            RuntimeFaults.record("qvk3.fonts", e);
        }
        synchronized (CACHE) {
            CACHE.put(key, t);
        }
        return t;
    }

    static Typeface typeface(Context c, boolean bold) {
        return typeface(c, current(c), bold);
    }

    /** Picker with a live preview of each font. */
    static void choose(android.app.Activity a, Runnable changed) {
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
        android.widget.LinearLayout col = new android.widget.LinearLayout(a);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setPadding(Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 4));
        final android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        String cur = current(a);
        for (int i = 0; i < IDS.length; i++) {
            final String id = IDS[i];
            android.widget.LinearLayout row = new android.widget.LinearLayout(a);
            row.setOrientation(android.widget.LinearLayout.VERTICAL);
            row.setPadding(Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 10), Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 10));
            boolean on = id.equals(cur);
            row.setBackground(Qvk3Ui.ripple(a, on ? (p.accent & 0x00FFFFFF) | 0x22000000 : 0, 0x22888888, Qvk3Ui.dp(a, 14)));
            android.widget.TextView name = new android.widget.TextView(a);
            name.setText(NAMES[i] + (on ? "  ✓" : ""));
            name.setTextSize(17);
            name.setTextColor(on ? p.accent : p.text);
            Typeface tf = typeface(a, id, true);
            if (tf != null) name.setTypeface(tf);
            android.widget.TextView sample = new android.widget.TextView(a);
            sample.setText("Съешь же ещё этих мягких французских булок · 0123456789");
            sample.setTextSize(14);
            sample.setTextColor(p.text2);
            Typeface ts = typeface(a, id, false);
            if (ts != null) sample.setTypeface(ts);
            row.addView(name);
            row.addView(sample);
            row.setOnClickListener(v -> {
                set(a, id);
                if (dlg[0] != null) dlg[0].dismiss();
                if (changed != null) changed.run();
            });
            col.addView(row);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(col);
        dlg[0] = Qvk3Ui.dialog(a).setTitle("Шрифт QVK").setView(sv).setNegativeButton("Закрыть", null).show();
    }
}
