package re.sova.qmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** «Мои обращения»: the user's tickets on the beta portal, team answers, comments and the «Требуется проверка» reply. */
final class Qvk3MyTickets {
    private Qvk3MyTickets() {
    }

    static void show(Activity a) {
        Qvk3PortalLink.ensure(a, () -> load(a));
    }

    private static void load(Activity a) {
        AlertDialog wait = Qvk3Ui.dialog(a).setMessage("Загружаем обращения…").setCancelable(true).show();
        Qvk3Jobs.io(() -> {
            List<JSONObject> items = new ArrayList<>();
            String err = null;
            int unread = 0;
            try {
                for (String kind : new String[]{"bug", "idea"}) {
                    JSONArray arr = Qvk3Portal.tickets(a, "scope=mine&kind=" + kind + "&limit=50").optJSONArray("items");
                    if (arr != null) for (int i = 0; i < arr.length(); i++) items.add(arr.getJSONObject(i));
                }
                items.sort((x, y) -> {
                    // waiting for the user first, then the latest activity
                    int ax = x.isNull("needs_action") ? 1 : 0, ay = y.isNull("needs_action") ? 1 : 0;
                    return ax != ay ? ax - ay : y.optString("updated").compareTo(x.optString("updated"));
                });
                unread = Qvk3Portal.me(a).optJSONObject("counts").optInt("unread_notifications");
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            final String e = err;
            final int u = unread;
            Qvk3Jobs.main(() -> {
                wait.dismiss();
                if (e != null) {
                    Qvk3Ui.dialog(a).setTitle("Мои обращения").setMessage(e).setPositiveButton("Повторить", (x, w) -> show(a))
                            .setNegativeButton("Закрыть", null).show();
                    return;
                }
                list(a, items, u);
            });
        });
    }

    static String statusLine(JSONObject t) {
        JSONObject st = t.optJSONObject("status");
        String s = st == null ? "" : (st.optString("emoji") + " " + st.optString("label")).trim();
        String act = t.optString("needs_action");
        if ("verify".equals(act)) s += " · проверьте исправление";
        else if ("need_info".equals(act)) s += " · нужны подробности";
        return s;
    }

    private static void list(Activity a, List<JSONObject> items, int unread) {
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            JSONObject t = items.get(i);
            labels[i] = t.optString("code") + " · " + statusLine(t) + "\n" + t.optString("title");
        }
        AlertDialog.Builder b = Qvk3Ui.dialog(a).setTitle("Мои обращения · " + Qvk3Portal.userName(a))
                .setPositiveButton("Новое", (x, w) -> Qvk3Report.open(a, null, null))
                .setNeutralButton(unread > 0 ? "Уведомления (" + unread + ")" : "Аккаунт", (x, w) -> {
                    if (unread > 0) notifications(a);
                    else account(a);
                })
                .setNegativeButton("Закрыть", null);
        if (items.isEmpty()) b.setMessage("Обращений пока нет. «Новое» — сообщить о баге или предложить идею; в ВК можно просто встряхнуть телефон.");
        else b.setItems(labels, (x, i) -> card(a, items.get(i).optString("code")));
        b.show();
    }

    static void card(Activity a, String code) {
        AlertDialog wait = Qvk3Ui.dialog(a).setMessage("Открываем " + code + "…").setCancelable(true).show();
        Qvk3Jobs.io(() -> {
            JSONObject t = null;
            String err = null;
            try {
                t = Qvk3Portal.ticket(a, code);
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            final JSONObject tk = t;
            final String e = err;
            Qvk3Jobs.main(() -> {
                wait.dismiss();
                if (tk == null) Qvk3Ui.toast(a, code + ": " + e);
                else render(a, tk);
            });
        });
    }

    private static void render(Activity a, JSONObject t) {
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 20);
        col.setPadding(pad, Qvk3Ui.dp(a, 4), pad, Qvk3Ui.dp(a, 8));
        col.addView(Qvk3Ui.text(a, t.optString("title"), 18, p.text, true));
        TextView st = Qvk3Ui.text(a, statusLine(t) + (t.optString("fixed_version").isEmpty() ? "" : " · " + t.optString("fixed_version")), 14, p.accent, true);
        st.setPadding(0, Qvk3Ui.dp(a, 4), 0, Qvk3Ui.dp(a, 8));
        col.addView(st);
        if (!t.optString("team_note").isEmpty()) col.addView(block(a, p, "Команда QVK", t.optString("team_note"), true));
        StringBuilder body = new StringBuilder(t.optString("description"));
        for (String[] f : new String[][]{{"steps", "Шаги"}, {"expected", "Ожидалось"}, {"actual", "На самом деле"}, {"how_it_works", "Как должно работать"}, {"purpose", "Для чего"}}) {
            if (!t.optString(f[0]).isEmpty()) body.append("\n\n").append(f[1]).append(":\n").append(t.optString(f[0]));
        }
        col.addView(block(a, p, null, body.toString(), false));
        JSONArray att = t.optJSONArray("attachments");
        if (att != null && att.length() > 0) col.addView(Qvk3Ui.text(a, "Вложений: " + att.length(), 12, p.text2, false));
        JSONArray comments = t.optJSONArray("comments");
        if (comments != null) for (int i = 0; i < comments.length(); i++) {
            JSONObject c = comments.optJSONObject(i);
            JSONObject au = c.optJSONObject("author");
            boolean team = au != null && au.optBoolean("is_team");
            String who = au == null ? "" : (team ? "Команда QVK" : au.optString("name")) + ("verify".equals(c.optString("kind")) ? " · проверка" : "addendum".equals(c.optString("kind")) ? " · дополнение" : "");
            JSONArray ca = c.optJSONArray("attachments");
            col.addView(block(a, p, who, c.optString("text") + (ca != null && ca.length() > 0 ? "\n📎 " + ca.length() : ""), team));
        }
        JSONObject can = t.optJSONObject("can");
        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, Qvk3Ui.dp(a, 12), 0, 0);
        col.addView(actions);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        String code = t.optString("code");
        AlertDialog d = Qvk3Ui.dialog(a).setTitle(code).setView(sv).setNegativeButton("Закрыть", null)
                .setNeutralButton("На сайте", (x, w) -> Qvk3PortalLink.open(a, t.optString("web_url"))).create();
        if (can != null && can.optBoolean("verify")) {
            actions.addView(action(a, p, "✅ Проверил — исправлено", true, () -> {
                d.dismiss();
                verify(a, code, true);
            }));
            actions.addView(action(a, p, "Не исправлено", false, () -> {
                d.dismiss();
                verify(a, code, false);
            }));
        }
        if (can != null && can.optBoolean("me_too") && !t.optBoolean("is_mine") && !t.optBoolean("me_too")) {
            actions.addView(action(a, p, "У меня тоже (приложить журнал)", false, () -> {
                d.dismiss();
                Qvk3Jobs.io(() -> {
                    String m;
                    try {
                        JSONArray ids = Qvk3Portal.upload(a, Qvk3Report.parts(a, new Qvk3Report.Attach(true, null, false)));
                        Qvk3Portal.meToo(a, code, null, ids);
                        m = "Отмечено, журнал приложен";
                    } catch (Exception e) {
                        m = Qvk3Jobs.message(e);
                    }
                    Qvk3Ui.toast(a, m);
                });
            }));
        }
        if (can == null || can.optBoolean("comment", true)) {
            actions.addView(action(a, p, "Написать комментарий", false, () -> {
                d.dismiss();
                comment(a, code, t.optBoolean("is_mine"));
            }));
        }
        d.show();
    }

    static TextView action(Activity a, Qvk3Ui.Palette p, String label, boolean filled, Runnable r) {
        TextView b = Qvk3Ui.button(a, p, label, filled, v -> r.run());
        LinearLayout.LayoutParams lp = Qvk3Ui.match();
        lp.topMargin = Qvk3Ui.dp(a, 6);
        b.setLayoutParams(lp);
        return b;
    }

    static TextView block(Activity a, Qvk3Ui.Palette p, String who, String text, boolean team) {
        TextView v = Qvk3Ui.text(a, (who == null || who.isEmpty() ? "" : who + "\n") + text, 14, p.text, false);
        v.setTextIsSelectable(true);
        int pd = Qvk3Ui.dp(a, 12);
        v.setPadding(pd, pd - 2, pd, pd - 2);
        v.setBackground(Qvk3Ui.rounded(team ? (p.accent & 0x00FFFFFF) | 0x26000000 : (p.dark ? 0x14FFFFFF : 0x0D000000), Qvk3Ui.dp(a, 12)));
        LinearLayout.LayoutParams lp = Qvk3Ui.match();
        lp.topMargin = Qvk3Ui.dp(a, 8);
        v.setLayoutParams(lp);
        return v;
    }

    private static void verify(Activity a, String code, boolean fixed) {
        EditText e = new EditText(a);
        e.setHint(fixed ? "Комментарий (необязательно)" : "Что всё ещё не так");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Qvk3Ui.dialog(a).setTitle(fixed ? "Исправлено" : "Не исправлено").setView(pad(a, e))
                .setPositiveButton("Отправить", (x, w) -> Qvk3Jobs.io(() -> {
                    String m;
                    try {
                        Qvk3Portal.verify(a, code, fixed, e.getText().toString().trim());
                        m = fixed ? code + " закрыто — спасибо за проверку!" : code + " возвращено команде";
                    } catch (Exception ex) {
                        m = Qvk3Jobs.message(ex);
                    }
                    Qvk3Ui.toast(a, m);
                })).setNegativeButton("Отмена", null).show();
    }

    private static void comment(Activity a, String code, boolean mine) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        EditText e = new EditText(a);
        e.setHint("Комментарий");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        col.addView(e);
        android.widget.CheckBox log = new android.widget.CheckBox(a);
        log.setText("Приложить журнал приложения");
        col.addView(log);
        Qvk3Ui.dialog(a).setTitle(code).setView(pad(a, col)).setPositiveButton("Отправить", (x, w) -> Qvk3Jobs.io(() -> {
            String m;
            try {
                JSONArray ids = log.isChecked() ? Qvk3Portal.upload(a, Qvk3Report.parts(a, new Qvk3Report.Attach(true, null, false))) : null;
                String text = e.getText().toString().trim();
                Qvk3Portal.comment(a, code, text.isEmpty() ? "Журнал приложения" : text, ids, mine);
                m = "Комментарий отправлен";
            } catch (Exception ex) {
                m = Qvk3Jobs.message(ex);
            }
            Qvk3Ui.toast(a, m);
        })).setNegativeButton("Отмена", null).show();
    }

    static android.view.View pad(Activity a, android.view.View v) {
        LinearLayout l = new LinearLayout(a);
        int p = Qvk3Ui.dp(a, 20);
        l.setPadding(p, Qvk3Ui.dp(a, 8), p, 0);
        l.addView(v, Qvk3Ui.match());
        return l;
    }

    private static void notifications(Activity a) {
        Qvk3Jobs.io(() -> {
            JSONArray n;
            try {
                n = Qvk3Portal.notifications(a, true);
                Qvk3Portal.markRead(a, null);
            } catch (Exception e) {
                Qvk3Ui.toast(a, Qvk3Jobs.message(e));
                return;
            }
            final JSONArray items = n;
            Qvk3Jobs.main(() -> {
                String[] labels = new String[items.length()];
                for (int i = 0; i < items.length(); i++) {
                    JSONObject o = items.optJSONObject(i);
                    labels[i] = (o.optString("ticket").isEmpty() ? "" : o.optString("ticket") + " · ") + o.optString("text");
                }
                Qvk3Ui.dialog(a).setTitle("Уведомления портала").setItems(labels, (x, i) -> {
                    String code = items.optJSONObject(i).optString("ticket");
                    if (!code.isEmpty()) card(a, code);
                }).setNegativeButton("Закрыть", null).show();
            });
        });
    }

    static void account(Activity a) {
        Qvk3Ui.dialog(a).setTitle("Аккаунт портала").setMessage("Подключено: " + Qvk3Portal.userName(a)
                        + "\n\nОтключение отзывает токен этого устройства на портале. Обращения остаются.")
                .setPositiveButton("Отключить", (x, w) -> Qvk3Jobs.io(() -> {
                    Qvk3Portal.logout(a);
                    Qvk3Ui.toast(a, "Аккаунт портала отключён");
                })).setNegativeButton("Закрыть", null).show();
    }
}
