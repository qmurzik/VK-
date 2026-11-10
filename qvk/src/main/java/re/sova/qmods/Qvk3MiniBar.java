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
    private boolean listening;

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

    @Override
    public void onActivityResumed(Activity a) {
        if (!vkScreen(a)) return;
        Qvk3MusicEngine e = Qvk3MusicEngine.peek();
        if (e == null || e.current() == null || e.state() == Qvk3MusicEngine.IDLE) return;
        if (Qvk3MusicSettings.prefs(a).getBoolean(HIDDEN, false) && !e.isPlaying()) return;
        attach(a, e);
    }

    @Override
    public void onActivityPaused(Activity a) {
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
        bar.setAlpha(0f);
        bar.setTranslationY(Qvk3Ui.dp(a, 24));
        bar.animate().alpha(1f).translationY(0).setDuration(220).start();
        e.addListener(this);
        listening = true;
        onState(e);
    }

    private void detach() {
        Qvk3MusicEngine e = Qvk3MusicEngine.peek();
        if (listening && e != null) e.removeListener(this);
        listening = false;
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
        play = Qvk3Ui.icon(a, Qvk3Icons.PLAY, Color.WHITE, 48, 24, "Играть", v -> e.toggle());
        l.addView(play);
        l.addView(Qvk3Ui.icon(a, Qvk3Icons.NEXT, Color.WHITE, 44, 20, "Следующий трек", v -> e.next()));
        l.addView(Qvk3Ui.icon(a, Qvk3Icons.CLOSE, 0x99FFFFFF, 40, 16, "Закрыть мини-плеер", v -> {
            e.stop();
            detach();
        }));
        l.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, Qvk3MusicActivity.class)
                .putExtra(Qvk3MusicActivity.EXTRA_PLAYER, true)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
        l.setContentDescription("QVK Music. Нажмите, чтобы открыть плеер");
        return l;
    }

    @Override
    public void onState(Qvk3MusicEngine e) {
        Activity a = host.get();
        if (bar == null || a == null) return;
        Qvk3Track t = e.current();
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
