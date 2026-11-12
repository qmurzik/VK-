package re.sova.qmods;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Floating QVK Music mini-player shown on VK screens while QVK Music has a track, so the player is always
 * one tap away from anywhere in VK. Attached to the content frame of resumed VK activities through
 * ActivityLifecycleCallbacks (no change to VK's layouts); removed on pause and when playback is closed.
 */
public final class Qvk3MiniBar implements Application.ActivityLifecycleCallbacks, Qvk3MusicEngine.Listener {
    static final String HIDDEN = "qvk3_minibar_hidden";
    private static Qvk3MiniBar instance;
    private WeakReference<Activity> host = new WeakReference<>(null);
    private View bar;
    private ImageView cover, play;
    private TextView title, artist;
    private String coverFor = "";

    static synchronized void install(Application app) {
        if (instance != null) return;
        instance = new Qvk3MiniBar();
        app.registerActivityLifecycleCallbacks(instance);
    }

    static boolean vkScreen(Activity a) {
        String n = a.getClass().getName();
        return (n.startsWith("com.vk") || n.startsWith("com.vkontakte")) && !n.contains("Auth") && !n.contains("Login")
                && !n.contains("Video") && !n.contains("Camera") && !n.contains("Story") && !n.contains("Call");
    }

    private WeakReference<Activity> resumed = new WeakReference<>(null);
    private boolean engineHooked;

    /** Called once by the engine when it is created, so the bar appears as soon as playback starts. */
    static void engineCreated(Qvk3MusicEngine e) {
        Qvk3MiniBar m = instance;
        if (m == null || m.engineHooked) return;
        m.engineHooked = true;
        e.addListener(m);
    }

    @Override
    public void onActivityResumed(Activity a) {
        if (!vkScreen(a) || !Qvk3MusicSettings.prefs(a).getBoolean(Qvk3MusicSettings.MINI_ON_VK, false) || Qvk3Kill.off(a, "mini_on_vk")) return;
        resumed = new WeakReference<>(a);
        Qvk3MusicEngine e = Qvk3MusicEngine.peek();
        if (e == null) return;
        engineCreated(e);
        if (e.current() == null || e.state() == Qvk3MusicEngine.IDLE) return;
        if (e.current().key().equals(hiddenFor)) return; // swiped away in "hide" mode
        attach(a, e);
    }

    @Override
    public void onActivityPaused(Activity a) {
        if (resumed.get() == a) resumed = new WeakReference<>(null);
        if (host.get() == a) detach();
    }

    private void attach(Activity a, Qvk3MusicEngine e) {
        detach();
        ViewGroup content = a.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        host = new WeakReference<>(a);
        bar = build(a, e);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, Qvk3Ui.dp(a, 60), Gravity.BOTTOM);
        int m = Qvk3Ui.dp(a, 10);
        lp.setMargins(m, 0, m, Qvk3Ui.dp(a, 72)); // above VK's bottom navigation
        content.addView(bar, lp);
        hideWithKeyboard(bar);
        bar.setAlpha(0f);
        bar.setTranslationY(Qvk3Ui.dp(a, 24));
        bar.animate().alpha(1f).translationY(0).setDuration(220).start();
        onState(e);
    }

    /** IDEA-0005: the bar steps aside while the keyboard is open (typing a message), and comes back after. */
    static void hideWithKeyboard(View v) {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        v.setOnApplyWindowInsetsListener((view, in) -> {
            boolean ime = in.isVisible(android.view.WindowInsets.Type.ime());
            view.animate().cancel();
            view.setVisibility(ime ? View.INVISIBLE : View.VISIBLE);
            return in;
        });
        v.requestApplyInsets();
        // VK's window may not pass IME insets down; the bar is re-laid out when the keyboard resizes the screen
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> view.post(() -> {
            android.view.WindowInsets in = view.getRootWindowInsets();
            boolean ime = in != null && in.isVisible(android.view.WindowInsets.Type.ime());
            if (!ime) {
                android.graphics.Rect frame = new android.graphics.Rect();
                view.getWindowVisibleDisplayFrame(frame);
                int h = view.getRootView().getHeight();
                ime = h > 0 && h - frame.bottom > h * 0.15f;
            }
            int want = ime ? View.INVISIBLE : View.VISIBLE;
            if (view.getVisibility() != want) view.setVisibility(want);
        }));
    }

    private void detach() {
        if (bar != null && bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
        bar = null;
        coverFor = "";
        host = new WeakReference<>(null);
    }

    private View build(Activity a, Qvk3MusicEngine e) {
        LinearLayout l = new LinearLayout(a);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(Qvk3Ui.dp(a, 8), 0, Qvk3Ui.dp(a, 4), 0);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF2232530, 0xF2161820});
        bg.setCornerRadius(Qvk3Ui.dp(a, 18));
        l.setBackground(bg);
        l.setElevation(Qvk3Ui.dp(a, 10));
        cover = new ImageView(a);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Qvk3Ui.rounded(0x33FFFFFF, Qvk3Ui.dp(a, 10)));
        Qvk3Ui.clipRound(cover, Qvk3Ui.dp(a, 10));
        l.addView(cover, new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 44), Qvk3Ui.dp(a, 44)));
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Qvk3Ui.dp(a, 12), 0, Qvk3Ui.dp(a, 4), 0);
        title = Qvk3Ui.line(a, "", 15, Color.WHITE, true);
        artist = Qvk3Ui.line(a, "", 13, 0xB3FFFFFF, false);
        t.addView(title);
        t.addView(artist);
        l.addView(t, Qvk3Ui.weight(1));
        // BUG-0022: next on the left, play/pause at the very right; swipe right closes the bar
        l.addView(Qvk3Ui.icon(a, Qvk3Icons.NEXT, Color.WHITE, 44, 20, "Следующий трек", v -> e.next()));
        play = Qvk3Ui.icon(a, Qvk3Icons.PLAY, Color.WHITE, 48, 24, "Играть", v -> e.toggle());
        l.addView(play);
        Qvk3MiniSwipe.attach(l, () -> {
            Qvk3Track cur = e.current();
            hiddenFor = cur == null ? "" : cur.key();
            if (Qvk3MiniSwipe.stops(a)) e.stop();
            detach();
        });
        l.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, Qvk3MusicActivity.class)
                .putExtra(Qvk3MusicActivity.EXTRA_PLAYER_ONLY, true)
                ));
        l.setContentDescription("QVK Music. Нажмите, чтобы открыть плеер");
        return l;
    }

    /** Track the bar was swiped away on in "hide" mode; it comes back with the next track. */
    private String hiddenFor = "";

    @Override
    public void onState(Qvk3MusicEngine e) {
        Qvk3Track t = e.current();
        if (t != null && t.key().equals(hiddenFor)) return;
        if (t != null) hiddenFor = "";
        if (bar == null) {
            // playback started while a VK screen is on top: show the bar right away
            Activity top = resumed.get();
            if (top != null && t != null && e.state() != Qvk3MusicEngine.IDLE && !top.isFinishing()) attach(top, e);
            return;
        }
        Activity a = host.get();
        if (a == null) return;
        if (t == null || e.state() == Qvk3MusicEngine.IDLE) {
            detach();
            return;
        }
        title.setText(t.title);
        artist.setText(e.state() == Qvk3MusicEngine.ERROR ? e.error() : t.artist);
        boolean playing = e.isPlaying();
        Qvk3Ui.setIcon(play, playing ? Qvk3Icons.PAUSE : Qvk3Icons.PLAY, Color.WHITE);
        play.setContentDescription(playing ? "Пауза" : "Играть");
        if (!t.key().equals(coverFor)) {
            coverFor = t.key();
            cover.setImageBitmap(null);
            Qvk3Images.get(a).load(t.cover, 144, b -> {
                if (t.key().equals(coverFor) && cover != null && b != null) cover.setImageDrawable(Qvk3Ui.roundedBitmap(b, Qvk3Ui.dp(a, 10)));
            });
        }
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
        if (host.get() == a) detach();
    }
}
