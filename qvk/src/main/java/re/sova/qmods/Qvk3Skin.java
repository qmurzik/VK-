package re.sova.qmods;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Skin engine for the stock VK screens: restyles the REAL views (found by resource id name) instead of
 * replacing them, so every stock function stays available. Two layers:
 *   1. the bottom navigation bar of VK (floating, rounded, translucent) — ids taken from VK's own layout
 *      bottom_navigation_container.xml: bottom_navigation_container → bottom_navigation (+ bottom_navigation_shadow);
 *   2. generic rules for any view id: hide, corner radius, background colour, opacity, margins.
 * Changes are re-applied after layout passes (debounced), tracked per view and fully reversible.
 */
public final class Qvk3Skin implements Application.ActivityLifecycleCallbacks {
    public static final String DOCK = "qvk3_skin_dock";           // "off" | "floating"
    public static final String DOCK_RADIUS = "qvk3_skin_dock_radius";   // dp
    public static final String DOCK_MARGIN = "qvk3_skin_dock_margin";   // dp
    public static final String DOCK_ALPHA = "qvk3_skin_dock_alpha";     // 40..100 (% opacity)
    public static final String RULES = "qvk3_skin_rules";               // JSON array

    private static Qvk3Skin instance;
    private static WeakReference<Activity> lastVk = new WeakReference<>(null);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> listeners = new WeakHashMap<>();
    private final WeakHashMap<Activity, Runnable> pending = new WeakHashMap<>();

    /** Original state of a restyled view, so turning a rule off restores VK exactly. */
    private static final class Orig {
        Drawable background;
        int visibility = -1;
        float alpha = -1;
        int[] margins;
        ViewOutlineProvider outline;
        boolean clip;
        int baseColor;
        boolean hasBase;
    }

    private final WeakHashMap<View, Orig> origs = new WeakHashMap<>();

    public static synchronized void install(Application app) {
        if (instance != null || Qvk3Kill.off(app, "skin")) return; // remote switch-off applies from the next start
        instance = new Qvk3Skin();
        app.registerActivityLifecycleCallbacks(instance);
        Activity top = Qvk3MiniBar.resumedNow(); // VK's first screen resumed before QVK started (BUG-0034)
        if (top != null) instance.onActivityResumed(top);
    }

    public static Activity lastVkActivity() {
        return lastVk.get();
    }

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    private static boolean stock(Activity a) {
        return !a.getClass().getName().startsWith("re.sova.qmods.");
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onActivityResumed(Activity a) {
        Qvk3DayNight.sync(a);
        Qvk3Look.settingsOnTop = !stock(a);
        if (!stock(a)) return;
        lastVk = new WeakReference<>(a);
        if (!active(a)) {
            // nothing to restyle: no layout listener, no tree walks (they cost battery on every VK redraw)
            ViewTreeObserver.OnGlobalLayoutListener old = listeners.remove(a);
            if (old != null) {
                try {
                    a.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(old);
                } catch (Throwable ignored) {
                    // the window is gone
                }
                schedule(a, 0); // one last pass restores VK's original look
            }
            return;
        }
        schedule(a, 0);
        if (listeners.containsKey(a)) return;
        ViewTreeObserver.OnGlobalLayoutListener l = () -> schedule(a, 300);
        listeners.put(a, l);
        try {
            a.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(l);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.skin.observe", t);
        }
    }

    /**
     * BUG-0016: the "VK interface" layer (floating dock + element rules) is retired — QVK keeps a single
     * appearance system, themes. This always returns false, so VK is never restyled by QVK: onActivityResumed
     * still tracks the current VK activity and runs the day/night theme sync, but schedules no dock/rule passes,
     * and a previously-applied dock/rules is restored to VK's original look on the next resume.
     */
    static boolean active(Context c) {
        return false;
    }

    private final WeakHashMap<Activity, Long> lastApply = new WeakHashMap<>();

    private void schedule(Activity a, long delay) {
        // VK redraws constantly (images, counters, typing): re-apply at most once a second per screen
        Long last = lastApply.get(a);
        if (delay > 0 && last != null) delay = Math.max(delay, 1000 - (android.os.SystemClock.uptimeMillis() - last));
        if (delay > 0 && pending.containsKey(a)) return; // a pass is already queued; it will see the latest tree
        Runnable old = pending.get(a);
        if (old != null) main.removeCallbacks(old);
        Runnable r = () -> {
            pending.remove(a);
            lastApply.put(a, android.os.SystemClock.uptimeMillis());
            if (a.isFinishing() || a.isDestroyed()) return;
            try {
                applyTo(a);
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.skin.apply", t);
            }
        };
        pending.put(a, r);
        main.postDelayed(r, delay);
    }

    @Override
    public void onActivityDestroyed(Activity a) {
        listeners.remove(a);
        pending.remove(a);
    }

    // ------------------------------------------------------------------ lookup

    static View find(Activity a, String name) {
        int id = a.getResources().getIdentifier(name, "id", a.getPackageName());
        return id == 0 ? null : a.findViewById(id);
    }

    private Orig orig(View v) {
        Orig o = origs.get(v);
        if (o == null) {
            o = new Orig();
            origs.put(v, o);
        }
        return o;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ apply

    void applyTo(Activity a) {
        SharedPreferences p = prefs(a);
        applyDock(a, "floating".equals(p.getString(DOCK, "off")), p);
        applyRules(a, p);
    }

    /** Colour of VK's current page-content background (what the stock bar uses). */
    private static int contentColor(Activity a) {
        TypedValue tv = new TypedValue();
        int attr = a.getResources().getIdentifier("background_content", "attr", a.getPackageName());
        if (attr != 0 && a.getTheme().resolveAttribute(attr, tv, true) && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) return tv.data;
        return Qvk3Ui.night(a) ? 0xFF1C1C1E : Color.WHITE;
    }

    private void applyDock(Activity a, boolean on, SharedPreferences p) {
        View container = find(a, "bottom_navigation_container"), nav = find(a, "bottom_navigation"), shadow = find(a, "bottom_navigation_shadow");
        if (container == null || nav == null) return;
        Orig oc = orig(container), on_ = orig(nav);
        View host = contentHost(container);
        if (!on) {
            if (host != null) giveBackBottom(host);
            restore(container);
            restore(nav);
            if (shadow != null) restore(shadow);
            return;
        }
        final int radius = dp(a, Math.max(0, Math.min(40, p.getInt(DOCK_RADIUS, 24))));
        final int margin = dp(a, Math.max(0, Math.min(32, p.getInt(DOCK_MARGIN, 12))));
        int alphaPct = Math.max(40, Math.min(100, p.getInt(DOCK_ALPHA, 96)));

        // margins: remember VK's own, then float the bar
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            if (oc.margins == null) oc.margins = new int[]{m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin};
            if (m.leftMargin != margin || m.rightMargin != margin || m.bottomMargin != oc.margins[3] + margin) {
                m.leftMargin = margin;
                m.rightMargin = margin;
                m.bottomMargin = oc.margins[3] + margin;
                container.setLayoutParams(m);
            }
        }
        if (shadow != null) {
            Orig os = orig(shadow);
            if (os.visibility < 0) os.visibility = shadow.getVisibility();
            shadow.setVisibility(View.GONE);
        }
        // background: VK's content colour, rounded, optionally translucent
        if (!on_.hasBase) {
            Drawable bg = nav.getBackground();
            on_.background = bg;
            on_.baseColor = bg instanceof ColorDrawable ? ((ColorDrawable) bg).getColor() : contentColor(a);
            on_.hasBase = true;
        }
        int base = on_.baseColor;
        int color = Color.argb(Math.round(255 * alphaPct / 100f), Color.red(base), Color.green(base), Color.blue(base));
        Drawable now = nav.getBackground();
        if (!(now instanceof GradientDrawable) || !Integer.valueOf(color).equals(nav.getTag(TAG_COLOR)) || !Integer.valueOf(radius).equals(nav.getTag(TAG_RADIUS))) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(color);
            g.setCornerRadius(radius);
            nav.setBackground(g);
            nav.setTag(TAG_COLOR, color);
            nav.setTag(TAG_RADIUS, radius);
        }
        ViewOutlineProvider round = new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                o.setAlpha(1f);
            }
        };
        if (oc.outline == null) {
            oc.outline = container.getOutlineProvider();
            oc.clip = container.getClipToOutline();
        }
        container.setOutlineProvider(round);   // the elevation shadow follows the rounded shape
        container.setClipToOutline(false);
        if (on_.outline == null) {
            on_.outline = nav.getOutlineProvider();
            on_.clip = nav.getClipToOutline();
        }
        nav.setOutlineProvider(round);
        nav.setClipToOutline(true);            // icons and the active-tab indicator stay inside the corners
        if (container instanceof ViewGroup) ((ViewGroup) container).setClipChildren(false);
        // BUG-0033: VK keeps the screen content above the bar (bottomMargin = bar height), so around a floating bar
        // the page background showed instead of the feed. Content now runs under the bar, as a floating bar should.
        if (host != null) freeBottom(host);
        else if (!loggedNoHost) {
            loggedNoHost = true;
            android.util.Log.w("QVK3Skin", "dock: no content host next to the bar in " + (container.getParent() == null ? "-" : container.getParent().getClass().getName()));
        }
    }

    private boolean loggedNoHost;

    private static Orig instanceOrig(View v) {
        Qvk3Skin k = instance;
        return k == null ? null : k.origs.get(v);
    }

    /** VK's screen host: the full-height sibling of the bar container that VK keeps above the bar. */
    private static View contentHost(View container) {
        if (!(container.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) container.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c == container || c.getVisibility() == View.GONE) continue;
            // BUG-0033 (alpha37): the host is whatever view VK passed in (not always a FitSystemWindowsFrameLayout) —
            // recognise it by what VK does to it: full height with the bar's height as bottom margin
            ViewGroup.LayoutParams lp = c.getLayoutParams();
            if (!(lp instanceof ViewGroup.MarginLayoutParams) || lp.height != ViewGroup.LayoutParams.MATCH_PARENT) continue;
            Orig o = instanceOrig(c);
            if (((ViewGroup.MarginLayoutParams) lp).bottomMargin > 0 || (o != null && o.margins != null)) return c;
        }
        return null;
    }

    private void freeBottom(View host) {
        ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) host.getLayoutParams();
        if (m.bottomMargin <= 0) return;
        Orig o = orig(host);
        o.margins = new int[]{m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin};
        android.util.Log.i("QVK3Skin", "dock: content runs under the bar (" + m.bottomMargin + " px freed, " + host.getClass().getSimpleName() + ")");
        m.bottomMargin = 0;
        host.setLayoutParams(m);
    }

    /** Floating bar off: VK's own margin comes back (VK sets it again itself whenever the bar shows or hides). */
    private void giveBackBottom(View host) {
        Orig o = origs.get(host);
        if (o == null || o.margins == null) return;
        ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) host.getLayoutParams();
        if (m.bottomMargin == 0) {
            m.bottomMargin = o.margins[3];
            host.setLayoutParams(m);
        }
        o.margins = null;
    }

    /** setTag keys must be application ids (high byte >= 2); 0x7e.. never collides with aapt-assigned 0x7f.. ids. */
    private static final int TAG_COLOR = 0x7e000001, TAG_RADIUS = 0x7e000002;

    private void restore(View v) {
        Orig o = origs.get(v);
        if (o == null) return;
        if (o.background != null && v.getBackground() != o.background) v.setBackground(o.background);
        if (o.visibility >= 0) v.setVisibility(o.visibility);
        if (o.alpha >= 0) v.setAlpha(o.alpha);
        if (o.margins != null && v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (m.leftMargin != o.margins[0] || m.rightMargin != o.margins[2] || m.bottomMargin != o.margins[3]) {
                m.setMargins(o.margins[0], o.margins[1], o.margins[2], o.margins[3]);
                v.setLayoutParams(m);
            }
        }
        if (o.outline != null || v.getClipToOutline() != o.clip) {
            v.setOutlineProvider(o.outline != null ? o.outline : ViewOutlineProvider.BACKGROUND);
            v.setClipToOutline(o.clip);
        }
        origs.remove(v);
        v.setTag(TAG_COLOR, null);
        v.setTag(TAG_RADIUS, null);
    }

    // ------------------------------------------------------------------ generic rules

    public static final class Rule {
        public String id = "";
        public boolean hide;
        public int radius = -1;      // dp, -1 = untouched
        public int bg;               // ARGB, 0 = untouched
        public int opacity = 100;    // %
        public int margin = -1;      // dp on every side, -1 = untouched
        public int vmargin = -1;     // dp above and below only (spacing between messages / posts), -1 = untouched

        public JSONObject json() throws org.json.JSONException {
            return new JSONObject().put("id", id).put("hide", hide).put("radius", radius).put("bg", bg).put("opacity", opacity).put("margin", margin).put("vmargin", vmargin);
        }

        static Rule from(JSONObject o) {
            Rule r = new Rule();
            r.id = o.optString("id");
            r.hide = o.optBoolean("hide");
            r.radius = o.optInt("radius", -1);
            r.bg = o.optInt("bg");
            r.opacity = o.optInt("opacity", 100);
            r.margin = o.optInt("margin", -1);
            r.vmargin = o.optInt("vmargin", -1);
            return r;
        }
    }

    private static String rulesRaw;
    private static java.util.List<Rule> rulesParsed = new java.util.ArrayList<>();

    /** Parsed rules; re-parsed only when the stored text changes (this is called on every restyle pass). */
    public static synchronized java.util.List<Rule> rules(Context c) {
        String raw = prefs(c).getString(RULES, "[]");
        if (!raw.equals(rulesRaw)) {
            rulesParsed = parse(raw);
            rulesRaw = raw;
        }
        return new java.util.ArrayList<>(rulesParsed);
    }

    private static java.util.List<Rule> parse(String raw) {
        java.util.List<Rule> out = new java.util.ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                Rule r = Rule.from(a.getJSONObject(i));
                if (r.id.matches("[A-Za-z0-9_]{1,80}")) out.add(r);
            }
        } catch (Exception ignored) {
            // damaged rules are ignored, VK is left as is
        }
        return out;
    }

    public static void saveRules(Context c, java.util.List<Rule> rules) {
        JSONArray a = new JSONArray();
        try {
            for (Rule r : rules) a.put(r.json());
        } catch (org.json.JSONException e) {
            return;
        }
        Qvk3SettingsHistory.before(c, RULES, "Правила интерфейса");
        prefs(c).edit().putString(RULES, a.toString()).apply();
    }

    private void applyRules(Activity a, SharedPreferences p) {
        java.util.Set<String> active = new java.util.HashSet<>();
        for (Rule r : rules(a)) {
            java.util.List<View> all = findAll(a, r.id);
            if (all.isEmpty()) continue;
            active.add(r.id);
            for (View v : all) applyRule(a, r, v);
        }
        restoreStale(a, active);
    }

    /** Every view carrying this id: feeds and lists repeat the same id in each row, so a rule must reach all of them. */
    static java.util.List<View> findAll(Activity a, String name) {
        java.util.List<View> out = new java.util.ArrayList<>();
        int id = a.getResources().getIdentifier(name, "id", a.getPackageName());
        if (id == 0) return out;
        View root = a.getWindow() == null ? null : a.getWindow().getDecorView();
        if (root != null) collect(root, id, out, 0);
        return out;
    }

    private static void collect(View v, int id, java.util.List<View> out, int depth) {
        if (v.getId() == id) out.add(v);
        if (depth > 60 || out.size() > 400 || !(v instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), id, out, depth + 1);
    }

    private void applyRule(Activity a, Rule r, View v) {
        {
            Orig o = orig(v);
            if (r.hide) {
                if (o.visibility < 0) o.visibility = v.getVisibility();
                if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
                return;
            }
            if (o.visibility >= 0 && v.getVisibility() == View.GONE) v.setVisibility(o.visibility);
            if (r.opacity < 100) {
                if (o.alpha < 0) o.alpha = v.getAlpha();
                v.setAlpha(r.opacity / 100f);
            }
            if (r.bg != 0 || r.radius >= 0) {
                if (o.background == null && !o.hasBase) {
                    o.background = v.getBackground();
                    o.hasBase = true;
                }
                // rounding alone keeps VK's own background (ripples, shapes) and only clips the corners
                boolean replace = r.bg != 0 || o.background == null || o.background instanceof ColorDrawable;
                GradientDrawable g = new GradientDrawable();
                int base = r.bg != 0 ? r.bg : (o.background instanceof ColorDrawable ? ((ColorDrawable) o.background).getColor() : 0);
                g.setColor(base);
                g.setCornerRadius(r.radius >= 0 ? dp(a, r.radius) : 0);
                if (replace && (!(v.getBackground() instanceof GradientDrawable) || !Integer.valueOf(r.bg * 31 + r.radius).equals(v.getTag(TAG_COLOR)))) {
                    v.setBackground(g);
                    v.setTag(TAG_COLOR, r.bg * 31 + r.radius);
                }
                if (r.radius >= 0) {
                    final int rad = dp(a, r.radius);
                    if (o.outline == null) {
                        o.outline = v.getOutlineProvider();
                        o.clip = v.getClipToOutline();
                    }
                    v.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad);
                        }
                    });
                    v.setClipToOutline(true);
                }
            }
            if (r.margin >= 0 && v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                if (o.margins == null) o.margins = new int[]{m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin};
                int mm = dp(a, r.margin);
                if (m.leftMargin != mm || m.topMargin != mm || m.rightMargin != mm || m.bottomMargin != mm) {
                    m.setMargins(mm, mm, mm, mm);
                    v.setLayoutParams(m);
                }
            } else if (r.vmargin >= 0 && v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                // even spacing between repeated items (messages, posts): half above, half below each one
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                if (o.margins == null) o.margins = new int[]{m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin};
                int half = dp(a, r.vmargin) / 2;
                if (m.topMargin != half || m.bottomMargin != half) {
                    m.topMargin = half;
                    m.bottomMargin = half;
                    v.setLayoutParams(m);
                }
            }
        }
    }

    private void restoreStale(Activity a, java.util.Set<String> active) {
        // views whose rule was removed go back to VK's original look
        for (java.util.Map.Entry<View, Orig> e : new java.util.ArrayList<>(origs.entrySet())) {
            View v = e.getKey();
            if (v == null) continue;
            int id = v.getId();
            String name = null;
            try {
                name = id == View.NO_ID ? null : a.getResources().getResourceEntryName(id);
            } catch (Exception ignored) {
            }
            if (name != null && !active.contains(name) && !name.startsWith("bottom_navigation")) restore(v);
        }
    }

    /** Re-applies immediately to the last stock VK screen (e.g. right after a settings change). */
    public static void refresh() {
        Activity a = lastVk.get();
        if (a != null && instance != null) instance.schedule(a, 0);
    }

    // ------------------------------------------------------------------ inspector

    /** Text dump of the real view tree: class, resource id name, size, visibility, background kind. */
    public static String dump(Activity a) {
        StringBuilder b = new StringBuilder();
        b.append("# ").append(a.getClass().getName()).append("  ").append(Qvk3Updates.installedName(a)).append('\n');
        walk(a, a.getWindow().getDecorView(), 0, b);
        return b.toString();
    }

    private static void walk(Activity a, View v, int depth, StringBuilder b) {
        if (b.length() > 400_000 || depth > 40) return;
        for (int i = 0; i < depth; i++) b.append("  ");
        String id = "";
        try {
            if (v.getId() != View.NO_ID) id = " #" + a.getResources().getResourceEntryName(v.getId());
        } catch (Exception e) {
            id = " #" + v.getId();
        }
        Drawable bg = v.getBackground();
        b.append(v.getClass().getName()).append(id).append(" [").append(v.getWidth()).append('x').append(v.getHeight()).append(']')
                .append(v.getVisibility() == View.VISIBLE ? "" : v.getVisibility() == View.GONE ? " GONE" : " INVISIBLE")
                .append(bg == null ? "" : " bg=" + (bg instanceof ColorDrawable ? String.format("#%08X", ((ColorDrawable) bg).getColor()) : bg.getClass().getSimpleName()))
                .append(v.getElevation() > 0 ? " elev=" + Math.round(v.getElevation()) : "").append('\n');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(a, g.getChildAt(i), depth + 1, b);
        }
    }

    // unused lifecycle callbacks
    @Override
    public void onActivityCreated(Activity a, Bundle b) {
    }

    @Override
    public void onActivityStarted(Activity a) {
    }

    @Override
    public void onActivityPaused(Activity a) {
    }

    @Override
    public void onActivityStopped(Activity a) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity a, Bundle b) {
    }
}
