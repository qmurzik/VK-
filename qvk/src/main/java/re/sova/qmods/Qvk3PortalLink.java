package re.sova.qmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Pairing QVK with a beta-portal account: a one-time code confirmed in the QVK Telegram bot or on the site, then polling
 * /link/poll until the portal hands out the personal token. No password ever goes through the app.
 */
final class Qvk3PortalLink {
    private Qvk3PortalLink() {
    }

    /** Runs onLinked right away when already paired, otherwise after a successful pairing. */
    static void ensure(Activity a, Runnable onLinked) {
        if (Qvk3Portal.linked(a)) {
            onLinked.run();
            return;
        }
        Qvk3Ui.dialog(a).setTitle("Портал бета-теста QVK")
                .setMessage("Обращения из приложения уходят от имени вашего аккаунта на портале бета-теста. Подключите его один раз: "
                        + "QVK покажет код, подтвердите его в Telegram-боте QVK или на сайте. Пароль в приложение не вводится.")
                .setPositiveButton("Подключить", (d, w) -> start(a, onLinked))
                .setNegativeButton("Отмена", null).show();
    }

    static void start(Activity a, Runnable onLinked) {
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 22);
        col.setPadding(pad, Qvk3Ui.dp(a, 6), pad, Qvk3Ui.dp(a, 8));
        TextView code = Qvk3Ui.text(a, "…", 34, p.text, true);
        code.setGravity(Gravity.CENTER);
        code.setLetterSpacing(0.08f);
        code.setTextIsSelectable(true);
        TextView status = Qvk3Ui.text(a, "Получаем код…", 14, p.text2, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Qvk3Ui.dp(a, 8), 0, Qvk3Ui.dp(a, 12));
        col.addView(code);
        col.addView(status);
        TextView tg = Qvk3Ui.button(a, p, "Подтвердить в Telegram", true, null);
        TextView site = Qvk3Ui.button(a, p, "Подтвердить на сайте", false, null);
        tg.setEnabled(false);
        site.setEnabled(false);
        col.addView(tg, Qvk3Ui.match());
        LinearLayout.LayoutParams lp = Qvk3Ui.match();
        lp.topMargin = Qvk3Ui.dp(a, 8);
        col.addView(site, lp);
        TextView hint = Qvk3Ui.text(a, "Или на сайте: Профиль → «Приложение QVK» → «Ввести код». Код действует 10 минут.", 12, p.text2, false);
        hint.setPadding(0, Qvk3Ui.dp(a, 12), 0, 0);
        col.addView(hint);
        AlertDialog d = Qvk3Ui.dialog(a).setTitle("Подключение к порталу").setView(col).setNegativeButton("Отмена", null).create();
        final boolean[] open = {true};
        d.setOnDismissListener(x -> open[0] = false);
        d.show();
        Handler h = new Handler(Looper.getMainLooper());
        String name = (Build.MANUFACTURER + " " + Build.MODEL + " · QVK " + Qvk3Portal.appVersion(a)).trim();
        Qvk3Jobs.io(() -> {
            try {
                JSONObject r = Qvk3Portal.linkStart(a.getApplicationContext(), name.length() > 80 ? name.substring(0, 80) : name);
                String poll = r.getString("poll_token");
                long interval = Math.max(2, r.optInt("poll_interval", 3)) * 1000L;
                long until = System.currentTimeMillis() + r.optInt("expires_in", 600) * 1000L;
                Qvk3Jobs.main(() -> {
                    code.setText(r.optString("code"));
                    status.setText("Подтвердите код — QVK ждёт ответа портала");
                    tg.setEnabled(true);
                    site.setEnabled(true);
                    tg.setOnClickListener(v -> open(a, r.optString("telegram_url")));
                    site.setOnClickListener(v -> open(a, r.optString("confirm_url")));
                    poll(a, d, h, open, poll, interval, until, status, onLinked);
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> status.setText("Не удалось получить код: " + Qvk3Jobs.message(e)));
            }
        });
    }

    private static void poll(Activity a, AlertDialog d, Handler h, boolean[] open, String token, long interval, long until,
                             TextView status, Runnable onLinked) {
        h.postDelayed(() -> {
            if (!open[0]) return;
            if (System.currentTimeMillis() > until) {
                status.setText("Код истёк. Закройте окно и попробуйте снова.");
                return;
            }
            Qvk3Jobs.io(() -> {
                String st;
                String err = null;
                JSONObject r = null;
                try {
                    r = Qvk3Portal.linkPoll(a.getApplicationContext(), token);
                    st = r.optString("status");
                } catch (Exception e) {
                    st = "error";
                    err = Qvk3Jobs.message(e);
                }
                final String s = st, e = err;
                final JSONObject res = r;
                Qvk3Jobs.main(() -> {
                    if (!open[0]) return;
                    switch (s) {
                        case "approved":
                            JSONObject u = res == null ? null : res.optJSONObject("user");
                            Qvk3Ui.toast(a, "Подключено: " + (u == null ? "аккаунт портала" : u.optString("name")));
                            d.dismiss();
                            if (onLinked != null) onLinked.run();
                            break;
                        case "denied":
                            status.setText("Подключение отклонено.");
                            break;
                        case "expired":
                        case "used":
                            status.setText("Код больше не действует. Закройте окно и попробуйте снова.");
                            break;
                        case "error":
                            status.setText("Нет связи с порталом (" + e + "). Повторяем…");
                            poll(a, d, h, open, token, interval * 2, until, status, onLinked);
                            break;
                        default:
                            poll(a, d, h, open, token, interval, until, status, onLinked);
                    }
                });
            });
        }, interval);
    }

    static void open(Activity a, String url) {
        if (url == null || url.isEmpty()) return;
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Нет приложения, чтобы открыть ссылку");
        }
    }
}
