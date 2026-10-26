package com.vkmod.feat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.vkmod.core.Guard;
import com.vkmod.core.ModLog;
import com.vkmod.core.Registry;
import com.vkmod.ui.SettingsActivity;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Bottom navigation customisation: hide tabs/labels/badges, start tab, long-press shortcut to mod settings. */
public final class NavTweaks {
    private NavTweaks() {}

    private static final String ITEM_CLASS = "com.vkontakte.android.ui.bottomnavigation.BottomNavigationItemView";
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static boolean startTabDone;

    public static void schedule(Activity a) {
        if (a instanceof LockActivity || a instanceof SettingsActivity) return;
        for (long d : new long[]{0, 350, 1200, 3000}) H.postDelayed(() -> Guard.run("nav", () -> apply(a)), d);
    }

    private static void apply(Activity a) {
        if (a.isFinishing()) return;
        View root = a.getWindow().getDecorView();
        Set<String> hide = new HashSet<>();
        if (Registry.isOn("nav_hide_tabs")) for (String s : Registry.str("nav_hide_tabs").split("[,\\s;]+")) if (!s.isEmpty()) hide.add(norm(s));
        boolean labels = Registry.isOn("nav_hide_labels"), badges = Registry.isOn("hide_badges");
        String start = Registry.str("start_tab");
        boolean wantStart = !startTabDone && !start.equals("default") && a.getClass().getName().endsWith("MainActivity");
        Runnable[] startClick = new Runnable[1];
        visit(root, 0, hide, labels, badges, wantStart ? start : null, a, startClick);
        if (startClick[0] != null) { startTabDone = true; startClick[0].run(); }
    }

    private static String norm(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("tab_") ? s : "tab_" + s;
    }

    private static void visit(View v, int depth, Set<String> hide, boolean labels, boolean badges, String start, Activity a, Runnable[] startClick) {
        if (depth > 24) return;
        if (isItem(v)) {
            item(v, hide, labels, badges, start, a, startClick);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) visit(g.getChildAt(i), depth + 1, hide, labels, badges, start, a, startClick);
        }
    }

    private static boolean isItem(View v) {
        for (Class<?> c = v.getClass(); c != null && c != View.class; c = c.getSuperclass()) if (c.getName().equals(ITEM_CLASS)) return true;
        return false;
    }

    private static void item(View item, Set<String> hide, boolean labels, boolean badges, String start, Activity a, Runnable[] startClick) {
        String name;
        try { name = item.getResources().getResourceEntryName(item.getId()); } catch (Throwable t) { name = ""; }
        if (hide.contains(name)) { item.setVisibility(View.GONE); return; }
        if (name.equals("tab_profile")) item.setOnLongClickListener(v -> { openSettings(v.getContext()); return true; });
        if (start != null && name.equals(start)) startClick[0] = item::performClick;
        if (labels || badges) walkTexts(item, labels, badges, 0);
    }

    private static void walkTexts(View v, boolean labels, boolean badges, int depth) {
        if (depth > 6) return;
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            String s = t == null ? "" : t.toString().trim();
            boolean numeric = !s.isEmpty() && s.matches("[0-9+.,kKкК]{1,5}");
            if (numeric && badges) {
                v.setVisibility(View.GONE);
                if (v.getParent() instanceof View && !isItem((View) v.getParent())) ((View) v.getParent()).setVisibility(View.GONE);
            } else if (!numeric && labels && !s.isEmpty()) {
                v.setVisibility(View.GONE);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walkTexts(g.getChildAt(i), labels, badges, depth + 1);
        }
    }

    public static void openSettings(Context c) {
        try {
            Intent i = new Intent(c, SettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) { ModLog.w("open settings failed", t); }
    }
}
