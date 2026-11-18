package re.sova.qmods;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * QVK 3.1: «Что нового» after an update — a full screen with a gradient header and a card per new feature that rises
 * into place one after another; each card can open the feature right away. Versions without highlights here keep the
 * plain notes dialog of {@link Qvk3PortalWatch}.
 */
final class Qvk3WhatsNew {
    private Qvk3WhatsNew() {
    }

    static final long V31 = 23847;

    static boolean has(long versionCode) {
        return versionCode == V31;
    }

    private static final class Feature {
        final String icon, title, text, action;
        final int c1, c2;
        final Runnable[] run = new Runnable[1];

        Feature(String icon, int c1, int c2, String title, String text, String action) {
            this.icon = icon;
            this.c1 = c1;
            this.c2 = c2;
            this.title = title;
            this.text = text;
            this.action = action;
        }
    }

    static void show(Activity a, org.json.JSONArray verify) {
        if (a.isFinishing() || a.isDestroyed()) return;
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(p.bg);
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(a, 24));
        sv.addView(col);

        // header: a gradient in the accent colours with the version
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(a, 24), dp(a, 56), dp(a, 24), dp(a, 28));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF7F5BFF, p.accent, 0xFFFF4E8A});
        head.setBackground(g);
        TextView kicker = Qvk3Ui.text(a, "ВНЕЗАПНОЕ ОБНОВЛЕНИЕ", 12, 0xCCFFFFFF, true);
        kicker.setLetterSpacing(0.12f);
        head.addView(kicker);
        TextView title = Qvk3Ui.text(a, "QVK 3.1", 40, Color.WHITE, true);
        head.addView(title);
        head.addView(Qvk3Ui.text(a, "Мы обещали, что 3.0.0 — последняя. Но вот ещё пять подарков.", 16, 0xE6FFFFFF, false));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        Feature[] fs = {
                new Feature(Qvk3Icons.PULSE, 0xFF7F5BFF, 0xFFFF4E8A, "Моя волна",
                        "Бесконечный поток по вашему вкусу: рекомендации ВК и ваши треки, а дальше — похожие на тот, что играет.",
                        "Включить волну"),
                new Feature(Qvk3Icons.QUEUE, 0xFF11998E, 0xFF38EF7D, "Очередь перетаскиванием",
                        "Потяните плеер вверх — появится очередь. Удерживайте трек и перетащите его на новое место.", null),
                new Feature(Qvk3Icons.TIMER, 0xFFF7971E, 0xFFFFC837, "Отложенные сообщения",
                        "В мессенджере QVK удерживайте «Отправить» и выберите время — сообщение уйдёт само.", null),
                new Feature(Qvk3Icons.CHAT, 0xFFE64646, 0xFFFF7A59, "Удалённые не исчезают",
                        "Если собеседник удалит сообщение у всех, у вас оно останется с пометкой «удалено». Включается по желанию.",
                        Qvk3ImStore.keepDeleted(a) ? null : "Включить"),
                new Feature(Qvk3Icons.WAND, 0xFF4776E6, 0xFF8E54E9, "Шрифт QVK",
                        "Inter, Noto, Open Sans или моноширинный — для настроек, музыки и мессенджера QVK.", "Выбрать шрифт"),
        };
        fs[0].run[0] = () -> {
            d.dismiss();
            Qvk3Wave.get(a).start(err -> {
                if (err != null) Qvk3Ui.toast(a, "Волна не запустилась: " + err);
                else a.startActivity(new android.content.Intent(a, Qvk3MusicActivity.class).putExtra(Qvk3MusicActivity.EXTRA_PLAYER_ONLY, true));
            });
        };
        fs[3].run[0] = () -> {
            Qvk3MusicSettings.prefs(a).edit().putBoolean(Qvk3ImStore.KEEP_DELETED, true).apply();
            Qvk3Ui.toast(a, "Сохранение удалённых включено");
        };
        fs[4].run[0] = () -> Qvk3Fonts.choose(a, null);

        int delay = 120;
        for (Feature f : fs) {
            View card = card(a, p, f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(dp(a, 16), dp(a, 14), dp(a, 16), 0);
            col.addView(card, lp);
            card.setAlpha(0f);
            card.setTranslationY(dp(a, 36));
            card.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            delay += 90;
        }

        if (verify != null && verify.length() > 0) {
            TextView h = Qvk3Ui.text(a, "ПРОВЕРЬТЕ ИСПРАВЛЕНИЯ", 12, p.text2, true);
            h.setLetterSpacing(0.06f);
            h.setPadding(dp(a, 24), dp(a, 24), dp(a, 24), dp(a, 6));
            col.addView(h);
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(a, 24), 0, dp(a, 24), 0);
            for (int i = 0; i < verify.length(); i++) {
                org.json.JSONObject t = verify.optJSONObject(i);
                if (t == null) continue;
                String code = t.optString("code");
                list.addView(Qvk3MyTickets.ticketRow(a, p, code, t.optString("title"), () -> {
                    d.dismiss();
                    Qvk3MyTickets.card(a, code);
                }));
            }
            col.addView(list);
        }

        TextView ok = Qvk3Ui.button(a, p, "Отлично", true, v -> d.dismiss());
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, dp(a, 52));
        ol.setMargins(dp(a, 16), dp(a, 24), dp(a, 16), dp(a, 8));
        col.addView(ok, ol);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(p.bg));
            w.setWindowAnimations(android.R.style.Animation_Dialog);
        }
        d.show();
        title.setScaleX(0.85f);
        title.setScaleY(0.85f);
        title.animate().scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
    }

    private static View card(Activity a, Qvk3Ui.Palette p, Feature f) {
        LinearLayout c = new LinearLayout(a);
        c.setGravity(Gravity.TOP);
        c.setPadding(dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 16));
        c.setBackground(Qvk3Ui.rounded(p.surface, dp(a, 20)));
        ImageView icon = new ImageView(a);
        icon.setImageDrawable(Qvk3Icons.of(f.icon, Color.WHITE));
        int ip = dp(a, 11);
        icon.setPadding(ip, ip, ip, ip);
        GradientDrawable ig = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{f.c1, f.c2});
        ig.setCornerRadius(dp(a, 14));
        icon.setBackground(ig);
        c.addView(icon, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(a, 14), 0, 0, 0);
        t.addView(Qvk3Ui.text(a, f.title, 17, p.text, true));
        TextView body = Qvk3Ui.text(a, f.text, 14, p.text2, false);
        body.setPadding(0, dp(a, 4), 0, 0);
        body.setLineSpacing(0, 1.12f);
        t.addView(body);
        if (f.action != null) {
            TextView b = Qvk3Ui.text(a, f.action + "  ›", 14, p.accent, true);
            b.setPadding(0, dp(a, 10), 0, 0);
            b.setOnClickListener(v -> {
                if (f.run[0] != null) f.run[0].run();
                b.setEnabled(false);
                b.setAlpha(0.5f);
            });
            t.addView(b);
        }
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Qvk3Motion.press(c);
        return c;
    }

    private static int dp(Activity a, float v) {
        return Qvk3Ui.dp(a, v);
    }
}
