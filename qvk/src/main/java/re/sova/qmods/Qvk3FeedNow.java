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
    private boolean listening;
    private long lastLook;

    static synchronized void install(Application app) {
        if (instance != null) return;
        instance = new Qvk3FeedNow();
        app.registerActivityLifecycleCallbacks(instance);
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
    }

    @Override
    public void onActivityPaused(Activity a) {
        if (resumed.get() != a) return;
        resumed = new WeakReference<>(null);
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
        if (b != null && (b.getParent() == null || !b.isAttachedToWindow() || b.getContext() != a)) {
            b = null;
            button = new WeakReference<>(null);
        }
        if (b == null) {
            if (!show) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastLook < 3000) return;
            lastLook = now;
            b = attach(a, e);
            if (b == null) return;
        }
        int want = show ? View.VISIBLE : View.GONE;
        if (b.getVisibility() != want) b.setVisibility(want);
        if (show) b.set(e.duration() > 0 ? e.position() / (float) e.duration() : 0f, e.isPlaying());
    }

    private Qvk3NowButton attach(Activity a, Qvk3MusicEngine e) {
        try {
            int hid = a.getResources().getIdentifier("header_container", "id", a.getPackageName());
            int pid = a.getResources().getIdentifier("posting_button_container", "id", a.getPackageName());
            if (hid == 0 || pid == 0) return null;
            View posting = a.findViewById(pid);
            if (posting == null || !(posting.getParent() instanceof ViewGroup)) return null;
            ViewGroup header = (ViewGroup) posting.getParent();
            if (header.getId() != hid || !header.isShown()) return null;
            int tint = 0xFF888888;
            View search = header.findViewById(a.getResources().getIdentifier("search_button", "id", a.getPackageName()));
            if (search instanceof android.widget.ImageView && ((android.widget.ImageView) search).getImageTintList() != null)
                tint = ((android.widget.ImageView) search).getImageTintList().getDefaultColor();
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
            // header_container is a ConstraintLayout with obfuscated constraint fields: the button is added without
            // constraints (top-left) and placed left of the first visible right-side action on every header layout
            header.addView(b, size, size);
            View.OnLayoutChangeListener place = (v, l, t, r, bt, ol, ot, or, ob) -> position(header, b);
            header.addOnLayoutChangeListener(place);
            b.addOnLayoutChangeListener(place);
            header.post(() -> position(header, b));
            button = new WeakReference<>(b);
            return b;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.feedNow.attach", t);
            return null;
        }
    }

    private static void position(ViewGroup header, View b) {
        int left = header.getWidth();
        for (int i = 0; i < header.getChildCount(); i++) {
            View c = header.getChildAt(i);
            if (c == b || c.getVisibility() != View.VISIBLE || c.getWidth() == 0) continue;
            // the right-hand action group: everything that starts in the right half of the header
            if (c.getLeft() > header.getWidth() / 2 && c.getLeft() < left) left = c.getLeft();
        }
        float x = left - b.getWidth() - b.getLeft(), y = (header.getHeight() - b.getHeight()) / 2f - b.getTop();
        if (b.getTranslationX() != x) b.setTranslationX(x);
        if (b.getTranslationY() != y) b.setTranslationY(y);
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
