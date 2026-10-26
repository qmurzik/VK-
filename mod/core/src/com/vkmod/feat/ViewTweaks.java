package com.vkmod.feat;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.vkmod.core.Guard;
import com.vkmod.core.ModLog;
import com.vkmod.core.Prefs;
import com.vkmod.core.Registry;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Per-view tweaks applied to every freshly inflated XML subtree (hook: VKLayoutInflater.inflate).
 * Handles: hiding elements by id name, interface sounds/haptics, font, text selection, avatar shape.
 */
public final class ViewTweaks {
    private ViewTweaks() {}

    private static final int MAX_DEPTH = 48;
    private static volatile int builtFor = -1;
    private static volatile Set<String> hideIds = new HashSet<>();
    private static volatile boolean noHaptics, selectText, anyFont;
    private static volatile String avatarShape = "circle";
    private static volatile Typeface fontBase;
    private static final java.util.HashMap<Integer, String> NAMES = new java.util.HashMap<>();

    private static void refresh() {
        int v = Prefs.version();
        if (v == builtFor) return;
        synchronized (ViewTweaks.class) {
            if (v == builtFor) return;
            Set<String> ids = new HashSet<>();
            if (Registry.isOn("hide_view_ids")) for (String s : Registry.str("hide_view_ids").split("[,\\s;]+")) if (!s.isEmpty()) ids.add(s.toLowerCase(Locale.ROOT));
            if (Registry.isOn("hide_stories")) {
                ids.add("stories_block_title"); ids.add("story_block"); ids.add("stories_fake_block"); ids.add("news_interesting_stories_block");
            }
            if (Registry.isOn("hide_ad_labels")) { ids.add("ad_label"); ids.add("ads_title"); ids.add("ad_disclaimer"); }
            hideIds = ids;
            noHaptics = Registry.isOn("no_haptics");
            selectText = Registry.isOn("select_text");
            avatarShape = Registry.str("avatar_shape");
            fontBase = Guard.call("app_font", null, ViewTweaks::resolveFont);
            anyFont = fontBase != null;
            builtFor = v;
        }
    }

    private static Typeface resolveFont() {
        String f = Registry.str("app_font");
        switch (f) {
            case "serif": return Typeface.SERIF;
            case "mono": return Typeface.MONOSPACE;
            case "casual": return Typeface.create("casual", Typeface.NORMAL);
            case "custom": {
                String path = Prefs.getString("font.path", "");
                File file = new File(path);
                if (!path.isEmpty() && file.canRead()) return Typeface.createFromFile(file);
                return null;
            }
            default: return null;
        }
    }

    public static void apply(View root) {
        refresh();
        if (hideIds.isEmpty() && !noHaptics && !selectText && !anyFont && avatarShape.equals("circle")) return;
        walk(root, 0);
    }

    private static void walk(View v, int depth) {
        if (depth > MAX_DEPTH) return;
        tweak(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0, n = g.getChildCount(); i < n; i++) walk(g.getChildAt(i), depth + 1);
        }
    }

    private static void tweak(View v) {
        try {
            int id = v.getId();
            String name = id == View.NO_ID ? null : nameOf(v.getContext(), id);
            if (name != null && !hideIds.isEmpty() && hideIds.contains(name.toLowerCase(Locale.ROOT))) {
                v.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp != null && lp.height != 0) { lp.height = 0; v.setLayoutParams(lp); }
                return;
            }
            if (noHaptics) { v.setHapticFeedbackEnabled(false); v.setSoundEffectsEnabled(false); }
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                Typeface base = fontBase;
                if (base != null) {
                    Typeface cur = t.getTypeface();
                    int weight = cur == null ? 400 : cur.getWeight();
                    boolean italic = cur != null && cur.isItalic();
                    Typeface want = Typeface.create(base, weight, italic);
                    if (cur == null || !cur.equals(want)) t.setTypeface(want);
                }
                if (selectText && !(t instanceof EditText) && !t.isTextSelectable() && !t.isClickable() && !t.hasOnClickListeners() && !parentClickable(t)) {
                    t.setTextIsSelectable(true);
                }
            }
            if (!avatarShape.equals("circle") && v instanceof ImageView && isAvatar(v, name)) shape((ImageView) v);
        } catch (Throwable t) {
            ModLog.w("tweak failed on " + v.getClass().getSimpleName(), t);
        }
    }

    private static boolean parentClickable(View v) {
        View p = v;
        for (int i = 0; i < 5 && p.getParent() instanceof View; i++) {
            p = (View) p.getParent();
            if (p.isClickable() || p.hasOnClickListeners()) return true;
        }
        return false;
    }

    private static boolean isAvatar(View v, String name) {
        if (name != null && name.toLowerCase(Locale.ROOT).contains("avatar")) return true;
        String cn = v.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return cn.contains("avatar");
    }

    private static void shape(ImageView v) {
        final String s = avatarShape;
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                int w = view.getWidth(), h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                if (s.equals("square")) o.setRect(0, 0, w, h);
                else o.setRoundRect(0, 0, w, h, Math.min(w, h) * 0.22f);
            }
        });
        v.invalidateOutline();
    }

    private static String nameOf(Context c, int id) {
        if ((id >>> 24) < 0x7f) return null;
        synchronized (NAMES) {
            String n = NAMES.get(id);
            if (n == null) {
                try { n = c.getResources().getResourceEntryName(id); } catch (Resources.NotFoundException e) { n = ""; }
                NAMES.put(id, n);
            }
            return n.isEmpty() ? null : n;
        }
    }
}
