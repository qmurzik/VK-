package re.sova.qmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * «Сообщить о проблеме»: a bug or an idea for the beta portal, written inside QVK.
 *
 * Device, Android, firmware and the QVK version are filled in automatically. Attached on request (all on by default, each can be
 * unticked, nothing leaves the phone before «Отправить»): the app log with secrets masked (Qvk3LogRedact), a screenshot of the VK
 * screen the report was started from, and the QVKLOOK code of the current look. Before sending, similar tickets are offered —
 * «У меня тоже» adds the log to an existing one instead of a duplicate.
 */
final class Qvk3Report {
    static final String[][] CATEGORIES = {{"music", "Музыка"}, {"messages", "Сообщения"}, {"notifications", "Уведомления"},
            {"settings", "Настройки"}, {"ui", "Интерфейс"}, {"video", "Видео"}, {"photos", "Фотографии"}, {"auth", "Авторизация"},
            {"performance", "Производительность"}, {"other", "Другое"}};
    static final String[][] FREQUENCIES = {{"always", "Всегда"}, {"often", "Часто"}, {"sometimes", "Иногда"}, {"rare", "Редко"}, {"once", "Один раз"}};

    private Qvk3Report() {
    }

    /** Draws the current window of a VK screen (software, no permission needed); null when it cannot be drawn. */
    static Bitmap capture(Activity a) {
        try {
            View v = a.getWindow().getDecorView();
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return null;
            float s = Math.min(1f, 1600f / Math.max(w, h));
            Bitmap b = Bitmap.createBitmap(Math.round(w * s), Math.round(h * s), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(s, s);
            v.draw(c);
            return b;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.report.capture", t);
            return null;
        }
    }

    static String[][] categories(Activity a) {
        JSONObject m = Qvk3Portal.cachedMeta(a);
        JSONArray c = m == null ? null : m.optJSONArray("categories");
        if (c == null || c.length() == 0) return CATEGORIES;
        String[][] out = new String[c.length()][];
        for (int i = 0; i < c.length(); i++) out[i] = new String[]{c.optJSONObject(i).optString("code"), c.optJSONObject(i).optString("label")};
        return out;
    }

    /** Opens the form. screenshot and crash may be null; crash pre-fills a crash bug. */
    static void open(Activity a, Bitmap screenshot, String crash) {
        open(a, screenshot, crash, null, null, null);
    }

    /** With a pre-filled bug (title, description, category), e.g. from the battery screen. */
    static void open(Activity a, Bitmap screenshot, String crash, String title, String desc, String category) {
        Qvk3Jobs.io(() -> {
            try {
                Qvk3Portal.meta(a.getApplicationContext()); // fresh categories; the form works from the cache if offline
            } catch (Exception ignored) {
            }
        });
        Qvk3PortalLink.ensure(a, () -> form(a, screenshot, crash, title, desc, category));
    }

    private static void form(Activity a, Bitmap screenshot, String crash) {
        form(a, screenshot, crash, null, null, null);
    }

    private static void form(Activity a, Bitmap screenshot, String crash, String preTitle, String preDesc, String preCategory) {
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 24);
        col.setPadding(pad, 0, pad, Qvk3Ui.dp(a, 8));
        final String[] kind = {"bug"}, category = {preCategory != null ? preCategory : crash != null ? "performance" : null}, frequency = {crash != null ? "once" : null};

        LinearLayout kinds = new LinearLayout(a);
        col.addView(kinds);
        EditText title = field(a, p, col, "Название (4–140 символов)", false);
        EditText desc = field(a, p, col, "Что случилось / что предлагаете", true);
        LinearLayout bugBox = new LinearLayout(a);
        bugBox.setOrientation(LinearLayout.VERTICAL);
        EditText steps = field(a, p, bugBox, "Шаги: 1. … 2. …", true);
        EditText expected = field(a, p, bugBox, "Ожидалось (необязательно)", true);
        EditText actual = field(a, p, bugBox, "Что на самом деле (необязательно)", true);
        bugBox.addView(label(a, p, "Как часто"));
        bugBox.addView(chips(a, p, FREQUENCIES, frequency));
        LinearLayout ideaBox = new LinearLayout(a);
        ideaBox.setOrientation(LinearLayout.VERTICAL);
        EditText how = field(a, p, ideaBox, "Как это должно работать", true);
        EditText purpose = field(a, p, ideaBox, "Для чего (необязательно)", true);
        ideaBox.setVisibility(View.GONE);
        col.addView(label(a, p, "Раздел"));
        col.addView(chips(a, p, categories(a), category));
        col.addView(bugBox);
        col.addView(ideaBox);

        for (String[] k : new String[][]{{"bug", "Баг"}, {"idea", "Идея"}}) {
            TextView t = Qvk3Ui.button(a, p, k[1], k[0].equals(kind[0]), null);
            t.setOnClickListener(v -> {
                kind[0] = k[0];
                bugBox.setVisibility("bug".equals(k[0]) ? View.VISIBLE : View.GONE);
                ideaBox.setVisibility("idea".equals(k[0]) ? View.VISIBLE : View.GONE);
                for (int i = 0; i < kinds.getChildCount(); i++) {
                    TextView o = (TextView) kinds.getChildAt(i);
                    boolean on = o == v;
                    o.setTextColor(on ? p.onAccent : p.accent);
                    o.setBackground(Qvk3Ui.ripple(a, on ? p.accent : (p.dark ? 0x1FFFFFFF : 0x14000000), 0x33888888, Qvk3Ui.dp(a, 22)));
                }
            });
            LinearLayout.LayoutParams lp = Qvk3Ui.weight(1);
            lp.rightMargin = Qvk3Ui.dp(a, 8);
            kinds.addView(t, lp);
        }

        col.addView(label(a, p, "Приложить"));
        CheckBox log = check(a, p, col, "Журнал приложения (токены и пароли скрыты)", true);
        CheckBox shot = null;
        if (screenshot != null) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            shot = check(a, p, row, "Снимок экрана ВК", true);
            ((LinearLayout.LayoutParams) shot.getLayoutParams()).weight = 1;
            ImageView thumb = new ImageView(a);
            thumb.setImageBitmap(screenshot);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Qvk3Ui.clipRound(thumb, Qvk3Ui.dp(a, 8));
            row.addView(thumb, new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 54), Qvk3Ui.dp(a, 96)));
            col.addView(row);
        }
        TextView auto = Qvk3Ui.text(a, "Подставится само: " + safeDevice(a), 12, p.text2, false);
        auto.setPadding(0, Qvk3Ui.dp(a, 8), 0, 0);
        col.addView(auto);

        if (preTitle != null) title.setText(preTitle);
        if (preDesc != null) {
            desc.setText(preDesc);
            desc.setSelection(desc.getText().length());
        }
        if (crash != null) {
            title.setText(crashTitle(crash));
            desc.setText("QVK закрылся с ошибкой. Что я делал перед этим: ");
            desc.setSelection(desc.getText().length());
            steps.setText("1. ");
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        AlertDialog d = Qvk3Ui.dialog(a).setTitle(crash != null ? "Отчёт о падении" : "Сообщить о проблеме").setView(sv)
                .setPositiveButton("Отправить", null).setNegativeButton("Отмена", null).create();
        final CheckBox shotBox = shot;
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String err = validate(kind[0], category[0], frequency[0], title, desc, steps, how);
            if (err != null) {
                Qvk3Ui.toast(a, err);
                return;
            }
            try {
                JSONObject t = new JSONObject().put("kind", kind[0]).put("category", category[0])
                        .put("title", title.getText().toString().trim()).put("description", desc.getText().toString().trim());
                if ("bug".equals(kind[0])) {
                    t.put("steps", steps.getText().toString().trim()).put("frequency", frequency[0]);
                    putIf(t, "expected", expected);
                    putIf(t, "actual", actual);
                } else {
                    putIf(t, "how_it_works", how);
                    putIf(t, "purpose", purpose);
                }
                Attach att = new Attach(log.isChecked(), shotBox != null && shotBox.isChecked() ? screenshot : null, false);
                d.dismiss();
                submit(a, t, att);
            } catch (Exception e) {
                Qvk3Ui.toast(a, Qvk3Jobs.message(e));
            }
        }));
        d.show();
    }

    static String safeDevice(Activity a) {
        try {
            JSONObject dv = Qvk3Portal.device(a);
            return dv.optString("model") + " · Android " + dv.optString("android") + " · QVK " + dv.optString("app_version");
        } catch (Exception e) {
            return "устройство и версия QVK";
        }
    }

    static String crashTitle(String crash) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z0-9_.]+(?:Exception|Error))").matcher(crash);
        String ex = m.find() ? m.group(1).substring(m.group(1).lastIndexOf('.') + 1) : "ошибка";
        return "Падение QVK: " + ex;
    }

    static String validate(String kind, String category, String frequency, EditText title, EditText desc, EditText steps, EditText how) {
        int tl = title.getText().toString().trim().length(), dl = desc.getText().toString().trim().length();
        if (tl < 4 || tl > 140) return "Название: от 4 до 140 символов";
        if (dl < 10) return "Опишите подробнее — хотя бы 10 символов";
        if (dl > 6000) return "Описание длиннее 6000 символов";
        if (category == null) return "Выберите раздел";
        if ("bug".equals(kind)) {
            if (steps.getText().toString().trim().length() < 5) return "Напишите шаги, чтобы повторить баг";
            if (frequency == null) return "Укажите, как часто это происходит";
        } else if (how.getText().toString().trim().length() < 5) return "Опишите, как это должно работать";
        return null;
    }

    static void putIf(JSONObject o, String key, EditText e) throws Exception {
        String s = e.getText().toString().trim();
        if (!s.isEmpty()) o.put(key, s.length() > 2000 ? s.substring(0, 2000) : s);
    }

    static final class Attach {
        final boolean log, look;
        final Bitmap shot;

        Attach(boolean log, Bitmap shot, boolean look) {
            this.log = log;
            this.shot = shot;
            this.look = look;
        }
    }

    /** Builds the files on the phone (only what is ticked). */
    static List<Qvk3Portal.Part> parts(Activity a, Attach att) {
        List<Qvk3Portal.Part> out = new ArrayList<>();
        String stamp = Qvk3Logs.stamp().replace(' ', '_').replace(':', '-');
        if (att.log) {
            String text = Qvk3Logs.header(a) + "\n" + Qvk3Logs.body(a, 1_500_000);
            byte[] b = text.getBytes(StandardCharsets.UTF_8);
            if (b.length > Qvk3Portal.MAX_FILE - 4096) {
                text = text.substring(text.length() - (int) ((Qvk3Portal.MAX_FILE - 65536) / 3));
                b = text.getBytes(StandardCharsets.UTF_8);
            }
            out.add(new Qvk3Portal.Part("qvk-log-" + stamp + ".txt", "text/plain", b));
        }
        if (att.shot != null) {
            for (int q = 85; q >= 40; q -= 15) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                att.shot.compress(Bitmap.CompressFormat.JPEG, q, o);
                if (o.size() < Qvk3Portal.MAX_FILE - 4096) {
                    out.add(new Qvk3Portal.Part("qvk-screen-" + stamp + ".jpg", "image/jpeg", o.toByteArray()));
                    break;
                }
            }
        }
        return out;
    }

    private static void submit(Activity a, JSONObject t, Attach att) {
        AlertDialog wait = Qvk3Ui.dialog(a).setMessage("Проверяем похожие обращения…").setCancelable(false).show();
        Qvk3Jobs.io(() -> {
            JSONArray similar;
            try {
                t.put("device", Qvk3Portal.device(a));
                similar = Qvk3Portal.similar(a, t.optString("kind"), t.optString("title"), t.optString("description"), t.optString("category"));
            } catch (Exception e) {
                similar = new JSONArray(); // the search is a hint; never block sending on it
            }
            final JSONArray sim = similar;
            Qvk3Jobs.main(() -> {
                wait.dismiss();
                if (sim.length() == 0) send(a, t, att);
                else offerSimilar(a, t, att, sim);
            });
        });
    }

    private static void offerSimilar(Activity a, JSONObject t, Attach att, JSONArray sim) {
        String[] labels = new String[sim.length()];
        for (int i = 0; i < sim.length(); i++) {
            JSONObject s = sim.optJSONObject(i);
            JSONObject st = s.optJSONObject("status");
            labels[i] = s.optString("code") + " · " + (st == null ? "" : st.optString("emoji") + " " + st.optString("label")) + "\n" + s.optString("title");
        }
        Qvk3Ui.dialog(a).setTitle("Похоже, об этом уже писали").setItems(labels, (x, i) -> {
            String code = sim.optJSONObject(i).optString("code");
            Qvk3Ui.dialog(a).setTitle(code).setMessage(sim.optJSONObject(i).optString("title"))
                    .setPositiveButton("У меня тоже", (y, w) -> meToo(a, code, t, att))
                    .setNeutralButton("Открыть", (y, w) -> Qvk3MyTickets.card(a, code))
                    .setNegativeButton("Назад", (y, w) -> offerSimilar(a, t, att, sim)).show();
        }).setPositiveButton("Всё равно отправить", (x, w) -> send(a, t, att)).setNegativeButton("Отмена", null).show();
    }

    private static void meToo(Activity a, String code, JSONObject t, Attach att) {
        AlertDialog wait = Qvk3Ui.dialog(a).setMessage("Отправляем…").setCancelable(false).show();
        Qvk3Jobs.io(() -> {
            String msg;
            try {
                JSONArray ids = att.log || att.shot != null ? Qvk3Portal.upload(a, parts(a, att)) : null;
                String comment = t.optString("description");
                Qvk3Portal.meToo(a, code, comment.length() > 4000 ? comment.substring(0, 4000) : comment, ids);
                msg = "Отмечено «У меня тоже» в " + code + (ids != null ? ", файлы приложены" : "");
            } catch (Exception e) {
                msg = "Не отправлено: " + Qvk3Jobs.message(e);
            }
            final String m = msg;
            Qvk3Jobs.main(() -> {
                wait.dismiss();
                Qvk3Ui.toast(a, m);
            });
        });
    }

    private static void send(Activity a, JSONObject t, Attach att) {
        AlertDialog wait = Qvk3Ui.dialog(a).setMessage("Отправляем обращение…").setCancelable(false).show();
        Qvk3Jobs.io(() -> {
            String code = null, err = null;
            try {
                List<Qvk3Portal.Part> files = parts(a, att);
                if (!files.isEmpty()) t.put("attachments", Qvk3Portal.upload(a, files));
                JSONObject r = Qvk3Portal.createTicket(a, t);
                code = r == null ? null : r.optString("code");
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            final String c = code, e = err;
            Qvk3Jobs.main(() -> {
                wait.dismiss();
                if (c != null) {
                    Qvk3Ui.dialog(a).setTitle("Обращение " + c + " создано")
                            .setMessage("Спасибо! Статус и ответы команды — в «Мои обращения». Уведомления придут и сюда, и в Telegram.")
                            .setPositiveButton("Открыть", (x, w) -> Qvk3MyTickets.card(a, c)).setNegativeButton("Готово", null).show();
                } else {
                    Qvk3Ui.dialog(a).setTitle("Не отправлено").setMessage(e)
                            .setPositiveButton("Повторить", (x, w) -> send(a, t, att)).setNegativeButton("Закрыть", null).show();
                }
            });
        });
    }

    // ------------------------------------------------------------------ small widgets

    static EditText field(Activity a, Qvk3Ui.Palette p, LinearLayout parent, String hint, boolean multi) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(p.text);
        e.setHintTextColor(p.text2);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multi) e.setMaxLines(6);
        // a soft filled field instead of the old underline
        e.setBackground(Qvk3Ui.rounded(p.dark ? 0x14FFFFFF : 0x0A000000, Qvk3Ui.dp(a, 14)));
        e.setPadding(Qvk3Ui.dp(a, 14), Qvk3Ui.dp(a, 12), Qvk3Ui.dp(a, 14), Qvk3Ui.dp(a, 12));
        LinearLayout.LayoutParams lp = Qvk3Ui.match();
        lp.topMargin = Qvk3Ui.dp(a, 8);
        parent.addView(e, lp);
        return e;
    }

    static TextView label(Activity a, Qvk3Ui.Palette p, String s) {
        TextView t = Qvk3Ui.text(a, s, 13, p.text2, true);
        t.setPadding(0, Qvk3Ui.dp(a, 12), 0, Qvk3Ui.dp(a, 4));
        return t;
    }

    static CheckBox check(Activity a, Qvk3Ui.Palette p, LinearLayout parent, String s, boolean on) {
        CheckBox c = new CheckBox(a);
        c.setText(s);
        c.setTextColor(p.text);
        c.setButtonTintList(android.content.res.ColorStateList.valueOf(p.accent));
        c.setChecked(on);
        parent.addView(c, new LinearLayout.LayoutParams(-2, -2));
        return c;
    }

    /** Single-choice chip row; the chosen code goes to out[0]. */
    static View chips(Activity a, Qvk3Ui.Palette p, String[][] items, String[] out) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        hs.addView(row);
        List<TextView> all = new ArrayList<>();
        for (String[] it : items) {
            TextView t = Qvk3Ui.text(a, it[1], 14, p.text, false);
            t.setPadding(Qvk3Ui.dp(a, 14), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 14), Qvk3Ui.dp(a, 8));
            t.setContentDescription(it[1]);
            all.add(t);
            Runnable paint = () -> {
                for (int i = 0; i < all.size(); i++) {
                    boolean on = items[i][0].equals(out[0]);
                    all.get(i).setTextColor(on ? p.onAccent : p.text);
                    all.get(i).setBackground(Qvk3Ui.rounded(on ? p.accent : (p.dark ? 0x1FFFFFFF : 0x14000000), Qvk3Ui.dp(a, 16)));
                    all.get(i).setSelected(on);
                }
            };
            t.setOnClickListener(v -> {
                out[0] = it[0];
                paint.run();
            });
            LinearLayout.LayoutParams lp = Qvk3Ui.wrap();
            lp.rightMargin = Qvk3Ui.dp(a, 6);
            row.addView(t, lp);
            paint.run();
        }
        return hs;
    }
}
