package re.sova.qmods;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * «Орбита» — the QVK 3 update screen. A full-screen sheet over VK: a slowly drifting aurora with twinkling stars, a
 * glowing progress orb (gradient ring, a comet on its head, the percentage counting up inside), a huge gradient
 * version number, chips (size · signature · channel), a four-step track «Загрузка → Проверка → Подпись → Установка»,
 * live speed and time left, «Что нового» on a glass card whose lines rise one after another, and a pill button with
 * a moving shine. Installing ends with a confetti burst and a drawn check mark.
 *
 * <p>All numbers are real: bytes and stages come from {@link Qvk3Updates}; nothing is simulated.</p>
 */
final class Qvk3UpdateScreen {
    private static final int C1 = 0xFF7C4DFF, C2 = 0xFF2979FF, C3 = 0xFF00E5FF, C4 = 0xFFFF4FA3;
    private static final int TEXT = 0xFFFFFFFF, TEXT2 = 0xB3FFFFFF, TEXT3 = 0x73FFFFFF;

    private final Activity a;
    private final Dialog dialog;
    private final FrameLayout root;
    private final AuroraView aurora;
    private final OrbView orb;
    private final ConfettiView confetti;
    private final TextView kicker, version, sub, percent, bytesText, statusText, speedText, etaText;
    private final LinearLayout chips, steps, notesBox, statsRow, sheet;
    private final PillButton primary;
    private final TextView later, mirrors;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Qvk3UpdateManifest.Release release;
    private Qvk3Updates.Check check;
    private int shownStage = -1;
    private float shownPercent;
    private long lastBytes = -1, lastAt;
    private double speed; // bytes per second, smoothed
    private boolean closed;
    private Qvk3Updates.Listener previous;

    private Qvk3UpdateScreen(Activity a) {
        this.a = a;
        dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        root = new FrameLayout(a);
        aurora = new AuroraView(a);
        root.addView(aurora, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setGravity(Gravity.CENTER_HORIZONTAL);
        sheet.setPadding(dp(22), dp(18), dp(22), dp(18));
        scroll.addView(sheet, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // top bar: QVK chip on the left, close on the right
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = label("QVK", 13, TEXT, true);
        logo.setLetterSpacing(0.18f);
        logo.setPadding(dp(12), dp(6), dp(12), dp(6));
        logo.setBackground(pill(0x26FFFFFF, 0x33FFFFFF));
        top.addView(logo);
        top.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        TextView close = label("✕", 18, TEXT2, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(circleBg());
        close.setContentDescription("Закрыть");
        close.setOnClickListener(v -> dismiss());
        top.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        sheet.addView(top, new LinearLayout.LayoutParams(-1, -2));

        kicker = label("ОБНОВЛЕНИЕ QVK", 12, C3, true);
        kicker.setLetterSpacing(0.22f);
        kicker.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-2, -2);
        kl.topMargin = dp(8);
        sheet.addView(kicker, kl);

        // the orb with the percentage inside
        FrameLayout orbBox = new FrameLayout(a);
        orb = new OrbView(a);
        orbBox.addView(orb, new FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER));
        LinearLayout center = new LinearLayout(a);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        percent = label("", 46, TEXT, true);
        percent.setGravity(Gravity.CENTER);
        percent.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        center.addView(percent);
        bytesText = label("", 13, TEXT2, false);
        bytesText.setGravity(Gravity.CENTER);
        center.addView(bytesText);
        orbBox.addView(center, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, dp(262));
        ol.topMargin = dp(2);
        sheet.addView(orbBox, ol);

        version = label("", 40, TEXT, true);
        version.setGravity(Gravity.CENTER);
        version.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        sheet.addView(version, new LinearLayout.LayoutParams(-2, -2));
        sub = label("", 14, TEXT2, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = dp(4);
        sheet.addView(sub, sl);

        chips = new LinearLayout(a);
        chips.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = dp(14);
        sheet.addView(chips, cl);

        steps = new LinearLayout(a);
        steps.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(-1, -2);
        stl.topMargin = dp(22);
        sheet.addView(steps, stl);

        statsRow = new LinearLayout(a);
        statsRow.setGravity(Gravity.CENTER);
        speedText = label("", 13, TEXT2, false);
        etaText = label("", 13, TEXT2, false);
        statsRow.addView(speedText);
        View dot = new View(a);
        dot.setBackground(dotBg(TEXT3));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(4), dp(4));
        dl.leftMargin = dp(10);
        dl.rightMargin = dp(10);
        statsRow.addView(dot, dl);
        statsRow.addView(etaText);
        statsRow.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams srl = new LinearLayout.LayoutParams(-2, -2);
        srl.topMargin = dp(10);
        sheet.addView(statsRow, srl);

        statusText = label("", 13, TEXT2, false);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stt = new LinearLayout.LayoutParams(-1, -2);
        stt.topMargin = dp(6);
        sheet.addView(statusText, stt);

        notesBox = new LinearLayout(a);
        notesBox.setOrientation(LinearLayout.VERTICAL);
        notesBox.setPadding(dp(18), dp(16), dp(18), dp(10));
        notesBox.setBackground(glass());
        notesBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = dp(20);
        sheet.addView(notesBox, nl);

        View spacer = new View(a);
        sheet.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        primary = new PillButton(a);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(60));
        pl.topMargin = dp(24);
        sheet.addView(primary, pl);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        later = label("Не сейчас", 14, TEXT2, false);
        later.setPadding(dp(16), dp(14), dp(16), dp(14));
        later.setOnClickListener(v -> {
            Qvk3UpdateManifest.Release r = release;
            dismiss();
            if (r != null && Qvk3Updates.stage != Qvk3Updates.DOWNLOAD) Qvk3UpdatePrompt.skipBar(a, r);
        });
        mirrors = label("Ссылки и зеркала", 14, TEXT2, false);
        mirrors.setPadding(dp(16), dp(14), dp(16), dp(14));
        mirrors.setOnClickListener(v -> {
            if (release != null) Qvk3UpdatePrompt.mirrors(a, release);
        });
        row.addView(later);
        View sep = new View(a);
        sep.setBackground(dotBg(TEXT3));
        row.addView(sep, new LinearLayout.LayoutParams(dp(4), dp(4)));
        row.addView(mirrors);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.topMargin = dp(4);
        sheet.addView(row, rl);

        confetti = new ConfettiView(a);
        root.addView(confetti, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, in) -> {
            sheet.setPadding(dp(22), dp(18) + in.getSystemWindowInsetTop(), dp(22), dp(18) + in.getSystemWindowInsetBottom());
            return in.consumeSystemWindowInsets();
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            w.setWindowAnimations(0);
        }
        dialog.setOnDismissListener(x -> {
            closed = true;
            main.removeCallbacksAndMessages(null);
            if (Qvk3Updates.listener == listener) Qvk3Updates.listener = previous;
        });
    }

    private final Qvk3Updates.Listener listener = this::sync;

    // ------------------------------------------------------------------ entry points

    /** Shows a release that is already known (the «Доступно обновление» prompt). */
    static void show(Activity a, Qvk3UpdateManifest.Release r) {
        Qvk3UpdateScreen s = new Qvk3UpdateScreen(a);
        s.release = r;
        s.open();
        s.bindRelease();
    }

    /** From settings: checks the channel first, with the orb spinning. */
    static void check(Activity a) {
        Qvk3UpdateScreen s = new Qvk3UpdateScreen(a);
        s.open();
        s.bindChecking();
        Qvk3Jobs.io(() -> {
            Qvk3Updates.Check c = Qvk3Updates.check(a);
            Qvk3Jobs.main(() -> {
                if (s.closed) return;
                s.check = c;
                s.release = c.release;
                if (c.release != null) s.bindRelease();
                else s.bindLatest(c.message);
            });
        });
    }

    private void open() {
        previous = Qvk3Updates.listener;
        Qvk3Updates.listener = listener;
        dialog.show();
        // entrance: the sky fades in, the sheet rises with a soft overshoot, the orb grows out of the centre
        aurora.setAlpha(0f);
        aurora.animate().alpha(1f).setDuration(420).start();
        for (int i = 0; i < sheet.getChildCount(); i++) {
            View c = sheet.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(dp(26));
            c.animate().alpha(1f).translationY(0).setStartDelay(60L + i * 45L).setDuration(520)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        }
        orb.setScaleX(0.55f);
        orb.setScaleY(0.55f);
        orb.setRotation(-40f);
        orb.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(120).setDuration(900)
                .setInterpolator(new OvershootInterpolator(1.4f)).start();
        tick();
    }

    private void dismiss() {
        if (closed) return;
        closed = true;
        root.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            try {
                dialog.dismiss();
            } catch (Throwable ignored) {
            }
        }).start();
    }

    // ------------------------------------------------------------------ states

    private void bindChecking() {
        kicker.setText("ПРОВЕРЯЕМ ОБНОВЛЕНИЯ");
        setVersion(shortName(Qvk3Updates.installedName(a)));
        sub.setText("Спрашиваем канал QVK и зеркала…");
        percent.setText("");
        bytesText.setText("");
        orb.setMode(OrbView.SPIN);
        primary.set("Проверяем…", false);
        later.setText("Закрыть");
        mirrors.setVisibility(View.GONE);
    }

    private void bindLatest(String message) {
        kicker.setText("ВСЁ АКТУАЛЬНО");
        setVersion(shortName(Qvk3Updates.installedName(a)));
        sub.setText(message == null || message.isEmpty() ? "У вас последняя версия QVK" : message);
        orb.setMode(OrbView.DONE);
        percent.setText("");
        bytesText.setText("");
        primary.set("Отлично", true);
        primary.setOnClickListener(v -> dismiss());
        later.setText("История версий");
        later.setOnClickListener(v -> Qvk3InfoScreens.history(a));
    }

    private void bindRelease() {
        Qvk3UpdateManifest.Release r = release;
        kicker.setText("ДОСТУПНО ОБНОВЛЕНИЕ");
        setVersion(shortName(r.versionName));
        sub.setText("У вас " + shortName(Qvk3Updates.installedName(a)) + (r.published == null || r.published.isEmpty() ? "" : " · вышла " + r.published));
        chips.removeAllViews();
        if (r.size > 0) chips.addView(chip(mb(r.size) + " МБ", C2));
        chips.addView(chip(check == null || check.signed ? "Подпись проверена" : "Подпись не проверена", check == null || check.signed ? 0xFF2BD47D : 0xFFFFB300));
        chips.addView(chip(r.channel == null || r.channel.isEmpty() ? "alpha" : r.channel, C4));
        buildSteps();
        buildNotes(r.notes);
        mirrors.setVisibility(r.urls.isEmpty() ? View.GONE : View.VISIBLE);
        later.setText("Не сейчас");
        primary.setOnClickListener(v -> onPrimary());
        sync();
    }

    private void onPrimary() {
        if (release == null) return;
        int st = Qvk3Updates.stage;
        if (Qvk3Updates.busy() && st == Qvk3Updates.DOWNLOAD) {
            Qvk3Updates.cancel();   // pause — the download resumes from the same byte later
            return;
        }
        if (Qvk3Updates.busy()) return;
        primary.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        lastBytes = -1;
        speed = 0;
        Qvk3Updates.downloadAndInstall(a, release);
        orb.burst();
        sync();
    }

    /** Pulls the live state from Qvk3Updates; also runs on a 200 ms tick for smooth speed/ETA. */
    private void sync() {
        if (closed || release == null) return;
        boolean busy = Qvk3Updates.busy();
        int st = busy || Qvk3Updates.stage != Qvk3Updates.IDLE ? Qvk3Updates.stage : Qvk3Updates.IDLE;
        long total = release.size > 0 ? release.size : Qvk3Updates.total;
        long got = Qvk3Updates.bytes;
        float p = (st == Qvk3Updates.DOWNLOAD || st == Qvk3Updates.PAUSED) && total > 0 ? Math.min(1f, got / (float) total)
                : st >= Qvk3Updates.VERIFY && st <= Qvk3Updates.DONE ? 1f : Math.max(0f, Qvk3Updates.progress / 100f);
        if (!busy && st == Qvk3Updates.IDLE) p = 0f;
        orb.setProgress(p);
        animatePercent(p);
        if (st == Qvk3Updates.INSTALL || st == Qvk3Updates.DONE || st == Qvk3Updates.VERIFY) percent.setText(""); // the check mark / spinner speaks

        if (st == Qvk3Updates.DOWNLOAD && busy) {
            long now = SystemClock.uptimeMillis();
            if (lastBytes >= 0 && now > lastAt) {
                double inst = (got - lastBytes) * 1000.0 / (now - lastAt);
                speed = speed <= 0 ? inst : speed * 0.8 + inst * 0.2;
            }
            if (lastBytes < 0 || now - lastAt >= 400) {
                lastBytes = got;
                lastAt = now;
            }
            if (speed > 1) {
                speedText.setText(String.format(Locale.ROOT, "%.1f МБ/с", speed / 1048576.0).replace('.', ','));
                long left = (long) ((total - got) / speed);
                etaText.setText("осталось " + (left >= 60 ? left / 60 + " мин " + left % 60 + " с" : left + " с"));
                statsRow.setVisibility(View.VISIBLE);
            }
            bytesText.setText(mb(got) + " из " + mb(total) + " МБ");
        } else {
            statsRow.setVisibility(View.INVISIBLE);
            bytesText.setText(st == Qvk3Updates.IDLE ? (total > 0 ? mb(total) + " МБ" : "")
                    : st == Qvk3Updates.PAUSED && total > 0 ? mb(got) + " из " + mb(total) + " МБ" : "");
        }

        if (st != shownStage) {
            shownStage = st;
            switch (st) {
                case Qvk3Updates.DOWNLOAD:
                    orb.setMode(OrbView.PROGRESS);
                    primary.set("Пауза", true);
                    aurora.energy(1f);
                    break;
                case Qvk3Updates.VERIFY:
                    orb.setMode(OrbView.SPIN);
                    primary.set("Проверяем подпись…", false);
                    break;
                case Qvk3Updates.INSTALL:
                    orb.setMode(OrbView.DONE);
                    primary.set("Устанавливаем…", false);
                    celebrate();
                    break;
                case Qvk3Updates.DONE:
                    orb.setMode(OrbView.DONE);
                    primary.set("Готово", true);
                    primary.setOnClickListener(v -> dismiss());
                    break;
                case Qvk3Updates.PAUSED:
                    orb.setMode(OrbView.PROGRESS);
                    primary.set("Продолжить", true);
                    aurora.energy(0f);
                    break;
                case Qvk3Updates.ERROR:
                    orb.setMode(OrbView.ERROR);
                    primary.set("Повторить", true);
                    aurora.energy(0f);
                    shake(orb);
                    break;
                default:
                    orb.setMode(OrbView.IDLE);
                    primary.set(release.size > 0 ? "Обновить · " + mb(release.size) + " МБ" : "Обновить", true);
                    aurora.energy(0f);
            }
            markSteps(st);
        }
        String s = Qvk3Updates.status;
        boolean showStatus = st == Qvk3Updates.ERROR || st == Qvk3Updates.PAUSED || st == Qvk3Updates.INSTALL || st == Qvk3Updates.DONE;
        statusText.setText(showStatus && s != null ? s : "");
    }

    private void tick() {
        if (closed) return;
        sync();
        main.postDelayed(this::tick, 200);
    }

    private void animatePercent(float target) {
        if (Qvk3Updates.stage == Qvk3Updates.IDLE && !Qvk3Updates.busy()) {
            percent.setText("");
            shownPercent = 0;
            return;
        }
        shownPercent += (target - shownPercent) * 0.35f;
        if (Math.abs(target - shownPercent) < 0.002f) shownPercent = target;
        percent.setText(Math.round(shownPercent * 100) + "%");
    }

    private void celebrate() {
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        int[] loc = new int[2];
        orb.getLocationInWindow(loc);
        int[] rl = new int[2];
        root.getLocationInWindow(rl);
        confetti.burst(loc[0] - rl[0] + orb.getWidth() / 2f, loc[1] - rl[1] + orb.getHeight() / 2f);
    }

    // ------------------------------------------------------------------ steps & notes

    private final List<StepDot> stepDots = new ArrayList<>();
    private int lastActive;

    private void buildSteps() {
        steps.removeAllViews();
        stepDots.clear();
        String[] names = {"Загрузка", "Проверка", "Подпись", "Установка"};
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                View line = new View(a);
                line.setBackground(new ColorDrawable(0x26FFFFFF));
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, dp(2), 1);
                ll.bottomMargin = dp(18);
                steps.addView(line, ll);
            }
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            StepDot d = new StepDot(a);
            stepDots.add(d);
            col.addView(d, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView t = label(names[i], 11, TEXT2, false);
            t.setPadding(0, dp(5), 0, 0);
            col.addView(t);
            steps.addView(col);
        }
    }

    /** Download = step 0; SHA-256 check = 1; signature/package check = 2 (both happen in VERIFY); install = 3. */
    private void markSteps(int st) {
        int active = st == Qvk3Updates.DOWNLOAD || st == Qvk3Updates.PAUSED ? 0 : st == Qvk3Updates.VERIFY ? 1
                : st == Qvk3Updates.INSTALL ? 3 : st == Qvk3Updates.DONE ? 4 : -1;
        if (active >= 0) lastActive = active;
        for (int i = 0; i < stepDots.size(); i++) {
            int state = active < 0 ? 0 : i < active ? 2 : i == active ? 1 : 0;
            if (st == Qvk3Updates.VERIFY && i == 2) state = 1;
            if (st == Qvk3Updates.ERROR) state = i < lastActive ? 2 : i == lastActive ? 3 : 0;
            stepDots.get(i).set(state);
        }
    }

    private void buildNotes(String notes) {
        notesBox.removeAllViews();
        if (notes == null || notes.trim().isEmpty()) {
            notesBox.setVisibility(View.GONE);
            return;
        }
        notesBox.setVisibility(View.VISIBLE);
        TextView h = label("Что нового", 18, TEXT, true);
        h.setPadding(0, 0, 0, dp(8));
        notesBox.addView(h);
        int i = 0;
        for (String raw : notes.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("QVK 3.0 alpha")) continue;
            boolean bullet = line.startsWith("•") || line.startsWith("-");
            if (bullet) line = line.substring(1).trim();
            LinearLayout row = new LinearLayout(a);
            row.setPadding(0, dp(6), 0, dp(6));
            if (bullet) {
                View d = new View(a);
                d.setBackground(dotGradient());
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(8), dp(8));
                dl.topMargin = dp(7);
                dl.rightMargin = dp(12);
                row.addView(d, dl);
            }
            TextView t = label(line, 14.5f, bullet ? TEXT2 : TEXT, !bullet);
            t.setLineSpacing(dp(2), 1f);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            notesBox.addView(row);
            row.setAlpha(0f);
            row.setTranslationY(dp(14));
            row.animate().alpha(1f).translationY(0).setStartDelay(520L + i * 70L).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator()).start();
            i++;
        }
    }

    // ------------------------------------------------------------------ small helpers

    private void setVersion(String text) {
        version.setText(text);
        version.post(() -> {
            float w = version.getPaint().measureText(text);
            version.getPaint().setShader(new LinearGradient(0, 0, Math.max(1, w), 0, new int[]{0xFFFFFFFF, 0xFFB9A6FF, 0xFF7FE9FF},
                    null, Shader.TileMode.CLAMP));
            version.invalidate();
        });
    }

    static String shortName(String v) {
        if (v == null) return "";
        return v.replace("8.100-qvk-", "").replace("3.0.0-", "3.0 ");
    }

    private static String mb(long b) {
        return String.valueOf(Math.round(b / 1048576.0));
    }

    private void shake(View v) {
        ValueAnimator an = ValueAnimator.ofFloat(0, 1);
        an.setDuration(480);
        an.addUpdateListener(x -> {
            float f = (float) x.getAnimatedValue();
            v.setTranslationX((float) (Math.sin(f * Math.PI * 6) * dp(10) * (1 - f)));
        });
        an.start();
    }

    private int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }

    private TextView label(CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif" + (bold ? "-medium" : ""), Typeface.NORMAL));
        return t;
    }

    private TextView chip(String text, int color) {
        TextView t = label(text, 12, TEXT, true);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackground(pill((color & 0x00FFFFFF) | 0x33000000, (color & 0x00FFFFFF) | 0x66000000));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.leftMargin = dp(4);
        l.rightMargin = dp(4);
        t.setLayoutParams(l);
        return t;
    }

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(100));
        g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private GradientDrawable circleBg() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x1FFFFFFF);
        return g;
    }

    private GradientDrawable dotBg(int c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(c);
        return g;
    }

    private GradientDrawable dotGradient() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{C3, C1});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    private GradientDrawable glass() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x1FFFFFFF, 0x0DFFFFFF});
        g.setCornerRadius(dp(24));
        g.setStroke(Math.max(1, dp(1)), 0x29FFFFFF);
        return g;
    }

    // ------------------------------------------------------------------ views

    /** Night sky: three soft colour clouds drifting slowly, faster while downloading, and twinkling stars. */
    static final class AuroraView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), star = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = {0xCC7C4DFF, 0xB32979FF, 0x99FF4FA3};
        private final float[][] stars;
        private final long start = SystemClock.uptimeMillis();
        private float energy, energyTarget, phase;
        private long last;

        AuroraView(Context c) {
            super(c);
            Random r = new Random(7);
            stars = new float[60][4];
            for (float[] s : stars) {
                s[0] = r.nextFloat();
                s[1] = r.nextFloat();
                s[2] = 0.5f + r.nextFloat() * 1.4f;
                s[3] = r.nextFloat() * 6.28f;
            }
            star.setColor(Color.WHITE);
        }

        void energy(float e) {
            energyTarget = e;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : (now - last) / 1000f;
            last = now;
            energy += (energyTarget - energy) * Math.min(1f, dt * 2f);
            phase += dt * (0.18f + energy * 0.55f);
            c.drawColor(0xFF06070D);
            float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < colors.length; i++) {
                float t = phase + i * 2.1f;
                float cx = w * (0.5f + 0.38f * (float) Math.sin(t * 0.7 + i));
                float cy = h * (0.30f + 0.22f * (float) Math.cos(t * 0.5 + i * 1.7) + i * 0.16f);
                float rad = Math.max(w, h) * (0.55f + 0.06f * (float) Math.sin(t * 1.3));
                p.setShader(new RadialGradient(cx, cy, rad, colors[i], 0x00000000, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rad, p);
            }
            p.setShader(new LinearGradient(0, 0, 0, h, 0x00000000, 0xB306070D, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            float secs = (now - start) / 1000f;
            for (float[] s : stars) {
                float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(secs * (1.2f + s[2]) + s[3]));
                star.setAlpha((int) (tw * 170));
                c.drawCircle(s[0] * w, s[1] * h * 0.9f, s[2] * d * 0.8f, star);
            }
            postInvalidateOnAnimation();
        }
    }

    /** The orb: glow, track, gradient arc with a comet head; spinner, check mark and error modes. */
    static final class OrbView extends View {
        static final int IDLE = 0, SPIN = 1, PROGRESS = 2, DONE = 3, ERROR = 4;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
                glow = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG),
                inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Matrix m = new Matrix();
        private final Path check = new Path(), part = new Path();
        private int mode = IDLE;
        private float progress, shown, rot, checkT, burstT = 1f;
        private long modeAt = SystemClock.uptimeMillis(), last;

        OrbView(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(10 * d);
            track.setColor(0x1AFFFFFF);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(10 * d);
            arc.setStrokeCap(Paint.Cap.ROUND);
            mark.setStyle(Paint.Style.STROKE);
            mark.setStrokeWidth(9 * d);
            mark.setStrokeCap(Paint.Cap.ROUND);
            mark.setStrokeJoin(Paint.Join.ROUND);
            mark.setColor(Color.WHITE);
        }

        void setMode(int m) {
            if (m == mode) return;
            mode = m;
            modeAt = SystemClock.uptimeMillis();
            checkT = 0;
            invalidate();
        }

        void setProgress(float p) {
            progress = p;
        }

        void burst() {
            burstT = 0f;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : (now - last) / 1000f;
            last = now;
            float cx = w / 2, cy = h / 2, rad = Math.min(w, h) / 2 - 22 * d;
            float secs = (now - modeAt) / 1000f;
            boolean err = mode == ERROR;
            int a1 = err ? 0xFFFF5252 : C3, a2 = err ? 0xFFFF1744 : C1, a3 = err ? 0xFFFF8A80 : C4;

            // breathing glow; brighter while working, a flash on start
            burstT = Math.min(1f, burstT + dt * 1.6f);
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 520.0);
            float glowA = (mode == PROGRESS || mode == SPIN ? 0.55f : 0.35f) + pulse * 0.15f + (1 - burstT) * 0.5f;
            glow.setShader(new RadialGradient(cx, cy, rad * 1.45f, new int[]{(Math.min(255, (int) (glowA * 255)) << 24) | (a2 & 0xFFFFFF), 0x00000000},
                    null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad * 1.45f, glow);
            inner.setShader(new RadialGradient(cx, cy - rad * 0.3f, rad, 0x22FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad - 8 * d, inner);

            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            c.drawArc(r, 0, 360, false, track);

            SweepGradient sg = new SweepGradient(cx, cy, new int[]{a1, a2, a3, a1}, null);
            rot += dt * (mode == SPIN ? 260 : 40);
            m.setRotate(rot, cx, cy);
            sg.setLocalMatrix(m);
            arc.setShader(sg);

            float sweep, startAngle = -90;
            if (mode == SPIN) {
                sweep = 60 + 200 * (0.5f + 0.5f * (float) Math.sin(secs * 3.2));
                startAngle = rot * 1.3f;
            } else if (mode == DONE) {
                shown += (1 - shown) * Math.min(1f, dt * 6);
                sweep = 360 * shown;
            } else if (mode == IDLE) {
                shown += (0 - shown) * Math.min(1f, dt * 6);
                sweep = Math.max(0, 360 * shown);
            } else {
                shown += (progress - shown) * Math.min(1f, dt * 5);
                sweep = Math.max(err ? 360 : 2, 360 * shown);
            }
            if (sweep > 0.5f) c.drawArc(r, startAngle, sweep, false, arc);

            // comet head at the end of the arc
            if ((mode == PROGRESS || mode == SPIN) && sweep > 1) {
                double ang = Math.toRadians(startAngle + sweep);
                float hx = cx + rad * (float) Math.cos(ang), hy = cy + rad * (float) Math.sin(ang);
                head.setShader(new RadialGradient(hx, hy, 18 * d, new int[]{0xFFFFFFFF, (a1 & 0xFFFFFF) | 0x88000000, 0x00000000},
                        new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(hx, hy, 18 * d, head);
            }

            if (mode == DONE) {
                checkT = Math.min(1f, checkT + dt * 2.2f);
                check.reset();
                float s = rad * 0.42f;
                check.moveTo(cx - s * 0.62f, cy + s * 0.02f);
                check.lineTo(cx - s * 0.12f, cy + s * 0.5f);
                check.lineTo(cx + s * 0.7f, cy - s * 0.45f);
                PathMeasure pm = new PathMeasure(check, false);
                part.reset();
                float e = (float) (1 - Math.pow(1 - checkT, 3));
                pm.getSegment(0, pm.getLength() * e, part, true);
                c.drawPath(part, mark);
            }
            postInvalidateOnAnimation();
        }
    }

    /** One step of «Загрузка → Проверка → Подпись → Установка»: 0 waiting, 1 active (pulsing), 2 done, 3 failed. */
    static final class StepDot extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), mk = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int state;
        private float doneT;

        StepDot(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2 * d);
            mk.setStyle(Paint.Style.STROKE);
            mk.setStrokeWidth(2.4f * d);
            mk.setStrokeCap(Paint.Cap.ROUND);
            mk.setColor(Color.WHITE);
        }

        void set(int s) {
            if (s != state && s == 2) doneT = 0;
            state = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, rad = Math.min(w, h) / 2 - 2;
            if (state == 0) {
                ring.setColor(0x40FFFFFF);
                c.drawCircle(cx, cy, rad * 0.55f, ring);
            } else if (state == 1) {
                float p = 0.5f + 0.5f * (float) Math.sin(SystemClock.uptimeMillis() / 260.0);
                fill.setShader(new RadialGradient(cx, cy, rad, new int[]{(((int) (90 + 80 * p)) << 24) | 0x00E5FF, 0x0000E5FF}, null, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rad, fill);
                fill.setShader(null);
                fill.setColor(C3);
                c.drawCircle(cx, cy, rad * 0.42f, fill);
                postInvalidateOnAnimation();
            } else if (state == 2) {
                doneT = Math.min(1f, doneT + 0.08f);
                fill.setShader(new LinearGradient(0, 0, w, h, C3, C1, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rad * (0.6f + 0.4f * doneT), fill);
                Path p = new Path();
                p.moveTo(cx - rad * 0.38f, cy);
                p.lineTo(cx - rad * 0.08f, cy + rad * 0.3f);
                p.lineTo(cx + rad * 0.42f, cy - rad * 0.3f);
                c.drawPath(p, mk);
                if (doneT < 1f) postInvalidateOnAnimation();
            } else {
                fill.setShader(null);
                fill.setColor(0xFFFF5252);
                c.drawCircle(cx, cy, rad * 0.8f, fill);
            }
        }
    }

    /** Gradient pill with a moving shine; dims while an action is not available. */
    static final class PillButton extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private String text = "";
        private boolean enabled = true;
        private final long start = SystemClock.uptimeMillis();

        PillButton(Context c) {
            super(c);
            txt.setColor(Color.WHITE);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTextSize(17 * c.getResources().getDisplayMetrics().scaledDensity);
            txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            setClickable(true);
            setFocusable(true);
            setElevation(10 * c.getResources().getDisplayMetrics().density);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getHeight() / 2f);
                }
            });
        }

        void set(String t, boolean on) {
            text = t;
            enabled = on;
            setEnabled(on);
            setContentDescription(t);
            animate().alpha(on ? 1f : 0.72f).setDuration(200).start();
            invalidate();
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (!enabled) return true;
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
                animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new OvershootInterpolator(2f)).start();
            return super.onTouchEvent(e);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            r.set(0, 0, w, h);
            bg.setShader(new LinearGradient(0, 0, w, h, new int[]{C1, C2, C3}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, h / 2, h / 2, bg);
            // shine: a soft diagonal band crossing every 2.6 s
            float t = ((SystemClock.uptimeMillis() - start) % 2600) / 2600f;
            float x = -w * 0.4f + t * w * 1.8f;
            shine.setShader(new LinearGradient(x - h, 0, x + h, h, new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, h / 2, h / 2, shine);
            Paint.FontMetrics fm = txt.getFontMetrics();
            c.drawText(TextUtils.ellipsize(text, new android.text.TextPaint(txt), w - h, TextUtils.TruncateAt.END).toString(),
                    w / 2, h / 2 - (fm.ascent + fm.descent) / 2, txt);
            if (enabled) postInvalidateOnAnimation();
        }
    }

    /** Confetti burst from a point; particles fall with gravity and spin. */
    static final class ConfettiView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<float[]> parts = new ArrayList<>(); // x, y, vx, vy, rot, vr, color, life, size
        private final int[] colors = {C1, C2, C3, C4, 0xFFFFD54F, 0xFF69F0AE};
        private long last;

        ConfettiView(Context c) {
            super(c);
            setClickable(false);
        }

        void burst(float x, float y) {
            Random r = new Random();
            float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < 110; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                float sp = (260 + r.nextFloat() * 620) * d;
                parts.add(new float[]{x, y, (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp - 260 * d,
                        r.nextFloat() * 360, (r.nextFloat() - 0.5f) * 720, r.nextInt(colors.length), 1f, (4 + r.nextFloat() * 6) * d});
            }
            last = 0;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (parts.isEmpty()) return;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            float g = 900 * getResources().getDisplayMetrics().density;
            for (int i = parts.size() - 1; i >= 0; i--) {
                float[] q = parts.get(i);
                q[2] *= 0.985f;
                q[3] = q[3] * 0.985f + g * dt;
                q[0] += q[2] * dt;
                q[1] += q[3] * dt;
                q[4] += q[5] * dt;
                q[7] -= dt * 0.45f;
                if (q[7] <= 0 || q[1] > getHeight() + 40) {
                    parts.remove(i);
                    continue;
                }
                p.setColor(colors[(int) q[6]]);
                p.setAlpha((int) (255 * Math.min(1f, q[7] * 1.6f)));
                c.save();
                c.rotate(q[4], q[0], q[1]);
                c.drawRect(q[0] - q[8], q[1] - q[8] * 0.45f, q[0] + q[8], q[1] + q[8] * 0.45f, p);
                c.restore();
            }
            if (!parts.isEmpty()) postInvalidateOnAnimation();
        }
    }
}
