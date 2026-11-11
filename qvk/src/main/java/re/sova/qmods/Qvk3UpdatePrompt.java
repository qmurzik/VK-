package re.sova.qmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * IDEA-0013: an update the user does not have to look for.
 *
 * At most once every 6 hours, when a VK screen comes to the front, the configured update sources are checked in the
 * background (one HTTPS request per source; nothing is scheduled while the app is closed). A newer release that was not
 * skipped is offered in a dialog: «Да» lists the download links (first link, mirrors) plus in-app download with
 * SHA-256/signature checks; «Нет» shows a small bar above the bottom navigation, «Пропустить это обновление?» with a
 * ✓ button and a ring-with-× that drains clockwise and closes the bar after 5 seconds; «Не показывать» (far right)
 * turns the prompt off — it stays available in Настройки QVK → Обновления.
 */
public final class Qvk3UpdatePrompt implements Application.ActivityLifecycleCallbacks {
    public static final String PREF = "qvk3_update_prompt";
    static final String SKIP = "qvk3_update_skip";
    static final String LAST_AUTO = "qvk3_update_auto_last";
    static final long PERIOD = 6 * 3_600_000L;

    private static Qvk3UpdatePrompt instance;
    private WeakReference<Activity> resumed = new WeakReference<>(null);
    private Qvk3UpdateManifest.Release pending;
    private boolean checking, shownThisRun;

    static synchronized void install(Application app) {
        if (instance != null) return;
        instance = new Qvk3UpdatePrompt();
        app.registerActivityLifecycleCallbacks(instance);
    }

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    @Override
    public void onActivityResumed(Activity a) {
        if (!Qvk3MiniBar.vkScreen(a)) return;
        resumed = new WeakReference<>(a);
        if (pending != null) {
            show(a);
            return;
        }
        SharedPreferences p = prefs(a);
        if (checking || shownThisRun || !p.getBoolean(PREF, true)) return;
        long now = System.currentTimeMillis();
        if (now - p.getLong(LAST_AUTO, 0) < PERIOD) return;
        if (Qvk3Updates.sources(a).isEmpty() && !Qvk3Updates.communityEnabled(a)) return;
        checking = true;
        p.edit().putLong(LAST_AUTO, now).apply();
        Context app = a.getApplicationContext();
        Qvk3Jobs.io(() -> {
            Qvk3Updates.Check c = Qvk3Updates.check(app);
            Qvk3Jobs.main(() -> {
                checking = false;
                Qvk3UpdateManifest.Release r = c.release;
                if (r == null || r.versionCode == prefs(app).getLong(SKIP, 0)) return;
                pending = r;
                Activity top = resumed.get();
                if (top != null && !top.isFinishing()) show(top);
            });
        });
    }

    @Override
    public void onActivityPaused(Activity a) {
        if (resumed.get() == a) resumed = new WeakReference<>(null);
    }

    private void show(Activity a) {
        Qvk3UpdateManifest.Release r = pending;
        pending = null;
        if (r == null || shownThisRun) return;
        shownThisRun = true;
        try {
            ask(a, r);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.updatePrompt", t);
        }
    }

    static void ask(Activity a, Qvk3UpdateManifest.Release r) {
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 22);
        col.setPadding(pad, Qvk3Ui.dp(a, 6), pad, Qvk3Ui.dp(a, 12));
        String text = "Установлено: " + Qvk3Updates.installedName(a) + "\nНовая: " + r.versionName
                + (r.size > 0 ? " · " + (r.size >> 20) + " МБ" : "") + (r.notes.isEmpty() ? "" : "\n\n" + clip(r.notes, 600));
        col.addView(Qvk3Ui.text(a, text, 14, p.text2, false));
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Qvk3Ui.dp(a, 16), 0, 0);
        Button yes = button(a, "Да", p.accent, Color.WHITE, true);
        Button no = button(a, "Нет", 0x22888888, p.text, true);
        Button never = button(a, "Не показывать", 0, p.text2, false);
        row.addView(yes, new LinearLayout.LayoutParams(0, Qvk3Ui.dp(a, 44), 1f));
        View gap = new View(a);
        row.addView(gap, new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 8), 1));
        row.addView(no, new LinearLayout.LayoutParams(0, Qvk3Ui.dp(a, 44), 1f));
        // «Не показывать» sits apart, at the far right, so it is not hit by accident
        View spacer = new View(a);
        row.addView(spacer, new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 18), 1));
        row.addView(never, new LinearLayout.LayoutParams(-2, Qvk3Ui.dp(a, 44)));
        col.addView(row);
        AlertDialog d = Qvk3Ui.dialog(a).setTitle("Доступно обновление QVK — обновиться?").setView(col).create();
        yes.setOnClickListener(v -> {
            d.dismiss();
            links(a, r);
        });
        no.setOnClickListener(v -> {
            d.dismiss();
            skipBar(a, r);
        });
        never.setOnClickListener(v -> {
            d.dismiss();
            prefs(a).edit().putBoolean(PREF, false).apply();
            Qvk3Ui.toast(a, "Больше не предлагать. Включить снова: Настройки QVK → Обновления");
        });
        d.show();
    }

    static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).trim() + "…";
    }

    static Button button(Activity a, String label, int bg, int fg, boolean filled) {
        Button b = new Button(a);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(filled ? 15 : 13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(Qvk3Ui.dp(a, 10), 0, Qvk3Ui.dp(a, 10), 0);
        b.setBackground(filled ? Qvk3Ui.rounded(bg, Qvk3Ui.dp(a, 14)) : null);
        b.setStateListAnimator(null);
        return b;
    }

    /** «Да»: link 1, mirrors, and an in-app download with SHA-256 and signature checks. */
    static void links(Activity a, Qvk3UpdateManifest.Release r) {
        List<String> labels = new ArrayList<>();
        labels.add("Скачать и установить в QVK (с проверкой)");
        for (int i = 0; i < r.urls.size(); i++) labels.add((i == 0 ? "Ссылка 1" : i == 1 ? "Зеркало" : "Зеркало " + i) + " · " + Qvk3Updates.host(r.urls.get(i)));
        Qvk3Ui.dialog(a).setTitle("QVK " + r.versionName).setItems(labels.toArray(new String[0]), (x, i) -> {
            if (i == 0) {
                Qvk3InfoScreens.updates(a);
                return;
            }
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.urls.get(i - 1))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t) {
                Qvk3Ui.toast(a, "Нет приложения, чтобы открыть ссылку");
            }
        }).setNegativeButton("Закрыть", null).show();
    }

    /** «Нет»: a 5-second bar «Пропустить это обновление?» with ✓ and a draining ring-with-×. */
    static void skipBar(Activity a, Qvk3UpdateManifest.Release r) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Qvk3Ui.dp(a, 16), 0, Qvk3Ui.dp(a, 6), 0);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF2232530, 0xF2161820});
        bg.setCornerRadius(Qvk3Ui.dp(a, 18));
        bar.setBackground(bg);
        bar.setElevation(Qvk3Ui.dp(a, 12));
        bar.addView(Qvk3Ui.line(a, "Пропустить это обновление?", 15, Color.WHITE, false), Qvk3Ui.weight(1));
        Runnable close = () -> {
            if (bar.getParent() != null) bar.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> { if (bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar); }).start();
        };
        bar.addView(Qvk3Ui.icon(a, Qvk3Icons.CHECK, 0xFF4BB34B, 48, 24, "Пропустить версию " + r.versionName, v -> {
            prefs(a).edit().putLong(SKIP, r.versionCode).apply();
            Qvk3Ui.toast(a, "Версия " + r.versionName + " пропущена");
            close.run();
        }));
        CountdownClose ring = new CountdownClose(a, 5000, close);
        ring.setContentDescription("Закрыть");
        ring.setOnClickListener(v -> close.run());
        bar.addView(ring, new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 44), Qvk3Ui.dp(a, 44)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, Qvk3Ui.dp(a, 60), Gravity.BOTTOM);
        int m = Qvk3Ui.dp(a, 10);
        lp.setMargins(m, 0, m, Qvk3Ui.dp(a, 72)); // above VK's bottom navigation, like the mini player
        content.addView(bar, lp);
        bar.setAlpha(0f);
        bar.setTranslationY(Qvk3Ui.dp(a, 24));
        bar.animate().alpha(1f).translationY(0).setDuration(220).start();
        ring.start();
    }

    /** A × inside a ring that empties clockwise over the given time, then runs onDone. */
    static final class CountdownClose extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), cross = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final long duration;
        private final Runnable onDone;
        private long start;
        private boolean done;

        CountdownClose(Context c, long duration, Runnable onDone) {
            super(c);
            this.duration = duration;
            this.onDone = onDone;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            ring.setColor(0xCCFFFFFF);
            cross.setStyle(Paint.Style.STROKE);
            cross.setStrokeCap(Paint.Cap.ROUND);
            cross.setColor(Color.WHITE);
        }

        void start() {
            start = SystemClock.uptimeMillis();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()), stroke = s * 0.07f;
            ring.setStrokeWidth(stroke);
            cross.setStrokeWidth(stroke);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, rad = s * 0.34f;
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            float left = start == 0 ? 1f : Math.max(0f, 1f - (SystemClock.uptimeMillis() - start) / (float) duration);
            // the remaining part of the ring: it "melts" clockwise from 12 o'clock
            c.drawArc(r, -90f + 360f * (1f - left), 360f * left, false, ring);
            float x = rad * 0.45f;
            c.drawLine(cx - x, cy - x, cx + x, cy + x, cross);
            c.drawLine(cx - x, cy + x, cx + x, cy - x, cross);
            if (start == 0 || done) return;
            if (left <= 0f) {
                done = true;
                post(onDone);
            } else {
                postInvalidateOnAnimation();
            }
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
    }
}
