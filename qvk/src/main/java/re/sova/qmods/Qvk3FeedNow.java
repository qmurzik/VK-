package re.sova.qmods;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/**
 * IDEA-0011: a small "now playing" ring button (same as in QVK Music's header) in the header of VK's «Главная», left of the
 * «+» button. Tap = pause / resume, long press = open the QVK Music player. VK's header (fragment_home_united:
 * header_container, scroll flags enterAlways|scroll|snap) hides while the feed scrolls, and the button with it.
 *
 * Battery: no layout listeners. While a track is loaded the engine already reports state; at most once every 3 s
 * the button looks for the header if it is not attached yet. Nothing runs when nothing is playing.
 */
public final class Qvk3FeedNow implements Application.ActivityLifecycleCallbacks, Qvk3MusicEngine.Listener {
    public static final String PREF = "qvk3_feed_now";
    private static Qvk3FeedNow instance;
    private WeakReference<Activity> resumed = new WeakReference<>(null);
    private WeakReference<Qvk3NowButton> button = new WeakReference<>(null);
    private WeakReference<View> anchor = new WeakReference<>(null);
    private boolean listening;
    private long lastLook;

    static synchronized void install(Application app) {
        if (instance != null || Qvk3Kill.off(app, "feed_now")) return; // remote switch-off applies from the next start
        instance = new Qvk3FeedNow();
        app.registerActivityLifecycleCallbacks(instance);
        Activity top = Qvk3MiniBar.resumedNow(); // VK's first screen resumed before QVK started (BUG-0034)
        if (top != null) instance.onActivityResumed(top);
    }

    /** The engine may be created while a VK screen is already open (first play). */
    static void engineCreated(Qvk3MusicEngine e) {
        Qvk3FeedNow m = instance;
        if (m == null || m.listening || m.resumed.get() == null) return;
        e.addListener(m);
        m.listening = true;
    }

    @Override
    public void onActivityResumed(Activity a) {
        if (!Qvk3MiniBar.vkScreen(a)) return;
        resumed = new WeakReference<>(a);
        lastLook = 0;
        Qvk3MusicEngine e = Qvk3MusicEngine.peek();
        if (e == null) return;
        if (!listening) {
            e.addListener(this);
            listening = true;
        }
        onState(e);
        // BUG-0053: right after resume the header is not laid out yet — look again shortly instead of on the next tick
        View root = a.getWindow() != null ? a.getWindow().getDecorView() : null;
        // BUG-0071: on pause the engine does not tick, so after switching tabs nobody moved the button to the new
        // header. React to the screen's layout changes instead (throttled; cheap when the button is in place).
        if (root != null) {
            if (layoutHook != null && layoutRoot.get() != null) {
                layoutRoot.get().getViewTreeObserver().removeOnGlobalLayoutListener(layoutHook);
            }
            layoutRoot = new WeakReference<>(root);
            layoutHook = () -> {
                if (resumed.get() != a) return;
                Qvk3MusicEngine en = Qvk3MusicEngine.peek();
                if (en == null || en.current() == null || en.isPlaying()) return; // playing: the ticks handle it
                long now = SystemClock.uptimeMillis();
                if (now - lastLayoutCheck < 300) return;
                lastLayoutCheck = now;
                Qvk3NowButton b = button.get();
                View an = anchor.get();
                if (b != null && b.isAttachedToWindow() && an != null && an.isShown()) return;
                lastLook = 0;   // the hook has its own 300 ms throttle; don't let the attach throttle swallow it
                onState(en);
            };
            root.getViewTreeObserver().addOnGlobalLayoutListener(layoutHook);
        }
        if (root != null) for (long d : new long[]{150, 600}) root.postDelayed(() -> {
            if (resumed.get() != a || button.get() != null) return;
            lastLook = 0;
            onState(e);
        }, d);
    }

    private android.view.ViewTreeObserver.OnGlobalLayoutListener layoutHook;
    private WeakReference<View> layoutRoot = new WeakReference<>(null);
    private long lastLayoutCheck;

    @Override
    public void onActivityPaused(Activity a) {
        if (resumed.get() != a) return;
        resumed = new WeakReference<>(null);
        View lr = layoutRoot.get();
        if (lr != null && layoutHook != null) lr.getViewTreeObserver().removeOnGlobalLayoutListener(layoutHook);
        layoutHook = null;
        layoutRoot = new WeakReference<>(null);
        Qvk3MusicEngine e = Qvk3MusicEngine.peek();
        if (e != null && listening) {
            e.removeListener(this);
            listening = false;
        }
    }

    @Override
    public void onState(Qvk3MusicEngine e) {
        Activity a = resumed.get();
        if (a == null) return;
        boolean show = e.current() != null && e.state() != Qvk3MusicEngine.IDLE
                && Qvk3MusicSettings.prefs(a).getBoolean(PREF, true);
        Qvk3NowButton b = button.get();
        View anchor0 = anchor.get();
        if (b != null && (b.getParent() == null || !b.isAttachedToWindow() || b.getContext() != a || anchor0 == null || !anchor0.isShown())) {
            // IDEA-0015: the tab changed (or the screen) — the button moves to the header that is shown now
            if (b.getParent() instanceof ViewGroup) ((ViewGroup) b.getParent()).removeView(b);
            b = null;
            button = new WeakReference<>(null);
        }
        if (b == null) {
            if (!show) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastLook < 800) return; // BUG-0053: was 3 s — the button came late
            lastLook = now;
            b = attach(a, e);
            if (b == null) return;
        }
        int want = show ? View.VISIBLE : View.GONE;
        if (b.getVisibility() != want) b.setVisibility(want);
        if (show) b.set(e.duration() > 0 ? e.position() / (float) e.duration() : 0f, e.isPlaying());
    }

    /**
     * Finds the header of the VK screen that is shown: «Главная» (header_container), and — IDEA-0015 — the headers of
     * «Мессенджер», «Сообщества», «Поиск», «Сервисы»: a full-width bar at the top whose id names a toolbar/header.
     */
    static View findHeader(Activity a) {
        View content = a.findViewById(android.R.id.content);
        if (content == null) return null;
        int screenW = content.getWidth();
        int maxTop = Qvk3Ui.dp(a, 150);
        java.util.ArrayDeque<View> q = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<Integer> d = new java.util.ArrayDeque<>();
        q.add(content);
        d.add(0);
        android.graphics.Rect r = new android.graphics.Rect();
        while (!q.isEmpty()) {
            View v = q.poll();
            int depth = d.poll();
            if (v.getVisibility() != View.VISIBLE) continue;
            if (v instanceof ViewGroup && v.getId() != View.NO_ID && headerName(a, v.getId())
                    && v.getWidth() >= screenW * 0.7f && v.getHeight() >= Qvk3Ui.dp(a, 44) && v.getHeight() <= Qvk3Ui.dp(a, 84)
                    && v.isShown() && v.getGlobalVisibleRect(r) && r.top < maxTop && r.height() > Qvk3Ui.dp(a, 30)) return v;
            if (v instanceof ViewGroup && depth < 16) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    q.add(g.getChildAt(i));
                    d.add(depth + 1);
                }
            }
        }
        return null;
    }

    static boolean headerName(Activity a, int id) {
        try {
            String n = a.getResources().getResourceEntryName(id);
            return n.equals("header_container") || n.contains("toolbar") || n.equals("header") || n.endsWith("_header");
        } catch (Exception e) {
            return false;
        }
    }

    /** Containers where a child without constraints/gravity sits at the top-left, so translation positions it exactly. */
    static boolean free(ViewGroup g) {
        String n = g.getClass().getName();
        return g instanceof android.widget.FrameLayout || g instanceof android.widget.RelativeLayout || n.contains("ConstraintLayout")
                || n.contains("CoordinatorLayout");
    }

    private Qvk3NowButton attach(Activity a, Qvk3MusicEngine e) {
        try {
            View head = findHeader(a);
            if (head == null) return null;
            ViewGroup container = null;
            if (free((ViewGroup) head) && !head.getClass().getName().contains("Toolbar")) container = (ViewGroup) head;
            else {
                // BUG-0053: «Чаты» keep the Toolbar in a horizontal LinearLayout — use the nearest free ancestor
                View content = a.findViewById(android.R.id.content);
                for (android.view.ViewParent v = head.getParent(); v instanceof ViewGroup && container == null; v = v.getParent()) {
                    if (free((ViewGroup) v)) container = (ViewGroup) v;
                    if (v == content) break;
                }
            }
            if (container == null) return null;
            final ViewGroup host = container;
            int tint = 0xFF888888;
            ImageTint:
            for (View v : rightIcons(head)) {
                if (v instanceof android.widget.ImageView && ((android.widget.ImageView) v).getImageTintList() != null) {
                    tint = ((android.widget.ImageView) v).getImageTintList().getDefaultColor();
                    break ImageTint;
                }
            }
            Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
            Qvk3NowButton b = new Qvk3NowButton(a, tint, p.accent);
            b.setOnClickListener(v -> e.toggle());
            b.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                try {
                    a.startActivity(new android.content.Intent(a, Qvk3MusicActivity.class)
                            .putExtra(Qvk3MusicActivity.EXTRA_PLAYER_ONLY, true));
                } catch (Throwable t) {
                    RuntimeFaults.record("qvk3.feedNow.open", t);
                }
                return true;
            });
            int size = Qvk3Ui.dp(a, 44);
            int plate = headerColor(head);
            if (plate != 0) b.setPlate(plate);
            container.addView(b, plate != 0 ? size + Qvk3Ui.dp(a, 20) : size, size);
            Runnable place = () -> position(host, head, b);
            View.OnLayoutChangeListener l = (v, x1, y1, x2, y2, o1, o2, o3, o4) -> place.run();
            head.addOnLayoutChangeListener(l);
            b.addOnLayoutChangeListener(l);
            // headers that hide while the list scrolls are moved by offsets, not layouts
            android.view.ViewTreeObserver.OnScrollChangedListener sc = place::run;
            head.getViewTreeObserver().addOnScrollChangedListener(sc);
            b.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    head.removeOnLayoutChangeListener(l);
                    if (head.getViewTreeObserver().isAlive()) head.getViewTreeObserver().removeOnScrollChangedListener(sc);
                }
            });
            container.post(place);
            button = new WeakReference<>(b);
            anchor = new WeakReference<>(head);
            return b;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.feedNow.attach", t);
            return null;
        }
    }

    /** Opaque colour of the header (or the nearest ancestor that paints one); 0 when unknown. */
    static int headerColor(View head) {
        for (View v = head; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            android.graphics.drawable.Drawable d = v.getBackground();
            if (d instanceof android.graphics.drawable.ColorDrawable) {
                int c = ((android.graphics.drawable.ColorDrawable) d).getColor();
                if ((c >>> 24) == 0xFF) return c;
            }
            if (d instanceof android.graphics.drawable.GradientDrawable && d.getConstantState() != null) {
                android.content.res.ColorStateList cs = ((android.graphics.drawable.GradientDrawable) d).getColor();
                if (cs != null && (cs.getDefaultColor() >>> 24) == 0xFF) return cs.getDefaultColor();
            }
        }
        return 0;
    }

    /** Visible icon-like views in the right half of the header (one level of nesting: action menus, button containers). */
    static java.util.List<View> rightIcons(View head) {
        java.util.List<View> out = new java.util.ArrayList<>();
        if (!(head instanceof ViewGroup)) return out;
        ViewGroup g = (ViewGroup) head;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getWidth() == 0 || c instanceof Qvk3NowButton) continue;
            if (c.getLeft() <= head.getWidth() / 2) continue;
            out.add(c);
            if (c instanceof ViewGroup) for (int k = 0; k < ((ViewGroup) c).getChildCount(); k++) out.add(((ViewGroup) c).getChildAt(k));
        }
        return out;
    }

    private static void position(ViewGroup container, View head, View b) {
        int left = head.getWidth() - Qvk3Ui.dp(head.getContext(), 4);
        for (View c : rightIcons(head)) if (c.getParent() == head && c.getLeft() < left) left = c.getLeft();
        // the header's offset inside the container (it may be the container itself)
        float hx = 0, hy = 0;
        for (View v = head; v != container && v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            hx += v.getLeft() + v.getTranslationX();
            hy += v.getTop() + v.getTranslationY();
        }
        float x = hx + left - b.getWidth() - b.getLeft(), y = hy + (head.getHeight() - b.getHeight()) / 2f - b.getTop();
        if (b.getTranslationX() != x) b.setTranslationX(x);
        if (b.getTranslationY() != y) b.setTranslationY(y);
        float alpha = head.isShown() ? Math.max(0f, Math.min(1f, head.getAlpha())) : 0f;
        if (b.getAlpha() != alpha) b.setAlpha(alpha);
    }

    @Override
    public void onActivityCreated(Activity a, Bundle b) {
    }

    @Override
    public void onActivityStarted(Activity a) {
    }

    @Override
    public void onActivityStopped(Activity a) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity a, Bundle b) {
    }

    @Override
    public void onActivityDestroyed(Activity a) {
    }
}
