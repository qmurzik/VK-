package re.sova.qmods;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Settings of the VK skin engine: floating bottom bar, per-id rules and the interface inspector. */
final class Qvk3SkinScreen {
    static void dock(Activity a) {
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
        android.content.SharedPreferences pr = Qvk3Skin.prefs(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 20);
        col.setPadding(pad, Qvk3Ui.dp(a, 8), pad, Qvk3Ui.dp(a, 8));
        Switch on = new Switch(a);
        on.setText("Плавающая скруглённая нижняя панель");
        on.setChecked("floating".equals(pr.getString(Qvk3Skin.DOCK, "off")));
        on.setOnCheckedChangeListener((b, v) -> {
            Qvk3SettingsHistory.before(a, Qvk3Skin.DOCK, v ? "Плавающая нижняя панель включена" : "Нижняя панель ВК: стандартная");
            pr.edit().putString(Qvk3Skin.DOCK, v ? "floating" : "off").apply();
            Qvk3Skin.refresh();
        });
        col.addView(on);
        col.addView(Qvk3SoundDialog.slider(a, p, "Скругление, dp", pr.getInt(Qvk3Skin.DOCK_RADIUS, 24), 40, v -> save(a, Qvk3Skin.DOCK_RADIUS, v)));
        col.addView(Qvk3SoundDialog.slider(a, p, "Отступ от краёв, dp", pr.getInt(Qvk3Skin.DOCK_MARGIN, 12), 32, v -> save(a, Qvk3Skin.DOCK_MARGIN, v)));
        col.addView(Qvk3SoundDialog.slider(a, p, "Непрозрачность, % (40–100)", pr.getInt(Qvk3Skin.DOCK_ALPHA, 96), 100, v -> save(a, Qvk3Skin.DOCK_ALPHA, Math.max(40, v))));
        TextView n = Qvk3Ui.text(a, "Меняется настоящая панель ВК: все вкладки и счётчики работают как раньше. Изменение видно после возврата на экран ВК. Выключите переключатель, чтобы вернуть штатный вид.", 13, p.text2, false);
        n.setPadding(0, Qvk3Ui.dp(a, 10), 0, 0);
        col.addView(n);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        Qvk3Ui.dialog(a).setTitle("Нижняя панель ВК").setView(sv).setPositiveButton("Готово", null).show();
    }

    static void save(Activity a, String key, int v) {
        Qvk3SettingsHistory.before(a, key, "Нижняя панель ВК");
        Qvk3Skin.prefs(a).edit().putInt(key, v).apply();
        Qvk3Skin.refresh();
    }

    static void rules(Activity a) {
        List<Qvk3Skin.Rule> rules = Qvk3Skin.rules(a);
        List<String> labels = new ArrayList<>();
        for (Qvk3Skin.Rule r : rules) labels.add("#" + r.id + (r.hide ? " · скрыт" : (r.radius >= 0 ? " · скругление " + r.radius : "") + (r.bg != 0 ? " · цвет" : "") + (r.opacity < 100 ? " · " + r.opacity + "%" : "") + (r.margin >= 0 ? " · отступ " + r.margin : "")));
        labels.add("＋ Новое правило по имени элемента");
        labels.add("Как найти имя элемента (Инспектор)");
        labels.add("Набор: убрать тени у шапки чата и панели");
        labels.add("Набор: полупрозрачная кнопка «Написать»");
        Qvk3Ui.dialog(a).setTitle("Правила интерфейса ВК").setItems(labels.toArray(new String[0]), (d, i) -> {
            if (i < rules.size()) edit(a, rules, i);
            else if (i == rules.size()) edit(a, rules, -1);
            else if (i == rules.size() + 1) inspector(a);
            else if (i == rules.size() + 2) preset(a, rules, true, "dialog_shadow", "bottom_navigation_shadow");
            else preset(a, rules, false, "vkim_fab");
        }).setNegativeButton("Закрыть", null).show();
    }

    /** Ready-made rule sets; names come from VK's own layouts (dialog_shadow, vkim_fab), rules replace same-id ones. */
    static void preset(Activity a, List<Qvk3Skin.Rule> rules, boolean hide, String... ids) {
        for (String id : ids) {
            Qvk3Skin.Rule r = null;
            for (Qvk3Skin.Rule x : rules) if (x.id.equals(id)) r = x;
            if (r == null) {
                r = new Qvk3Skin.Rule();
                r.id = id;
                rules.add(r);
            }
            r.hide = hide;
            if (!hide) r.opacity = 80;
        }
        Qvk3Skin.saveRules(a, rules);
        Qvk3Skin.refresh();
        rules(a);
    }

    static void edit(Activity a, List<Qvk3Skin.Rule> rules, int index) {
        Qvk3Skin.Rule r = index >= 0 ? rules.get(index) : new Qvk3Skin.Rule();
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 20);
        col.setPadding(pad, Qvk3Ui.dp(a, 8), pad, Qvk3Ui.dp(a, 8));
        EditText id = new EditText(a);
        id.setHint("Имя элемента, например bottom_navigation");
        id.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        id.setText(r.id);
        col.addView(id);
        Switch hide = new Switch(a);
        hide.setText("Скрыть элемент");
        hide.setChecked(r.hide);
        col.addView(hide);
        final int[] radius = {r.radius}, opacity = {r.opacity}, margin = {r.margin};
        col.addView(Qvk3SoundDialog.slider(a, p, "Скругление, dp (0 — квадрат)", Math.max(0, r.radius), 40, v -> radius[0] = v));
        col.addView(Qvk3SoundDialog.slider(a, p, "Непрозрачность, %", r.opacity, 100, v -> opacity[0] = Math.max(10, v)));
        col.addView(Qvk3SoundDialog.slider(a, p, "Отступы, dp", Math.max(0, r.margin), 32, v -> margin[0] = v));
        EditText color = new EditText(a);
        color.setHint("Цвет фона #RRGGBB (необязательно)");
        color.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (r.bg != 0) color.setText(String.format("#%06X", r.bg & 0xFFFFFF));
        col.addView(color);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        android.app.AlertDialog.Builder b = Qvk3Ui.dialog(a).setTitle(index >= 0 ? "Правило" : "Новое правило").setView(sv)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = id.getText().toString().trim().replaceFirst("^#", "");
                    if (!name.matches("[A-Za-z0-9_]{1,80}")) {
                        Qvk3Ui.toast(a, "Имя элемента: латиница, цифры и _");
                        return;
                    }
                    r.id = name;
                    r.hide = hide.isChecked();
                    r.radius = radius[0];
                    r.opacity = opacity[0];
                    r.margin = margin[0];
                    try {
                        String c = color.getText().toString().trim();
                        r.bg = c.isEmpty() ? 0 : android.graphics.Color.parseColor(c) | 0xFF000000;
                    } catch (IllegalArgumentException e) {
                        Qvk3Ui.toast(a, "Цвет в формате #RRGGBB");
                        return;
                    }
                    if (index < 0) rules.add(r);
                    Qvk3Skin.saveRules(a, rules);
                    Qvk3Skin.refresh();
                    rules(a);
                }).setNegativeButton("Отмена", null);
        if (index >= 0) b.setNeutralButton("Удалить", (d, w) -> {
            rules.remove(index);
            Qvk3Skin.saveRules(a, rules);
            Qvk3Skin.refresh();
            rules(a);
        });
        b.show();
    }

    /** Dumps the real view tree of the stock VK screen that is open 8 seconds from now. */
    static void inspector(Activity a) {
        Qvk3Ui.dialog(a).setTitle("Инспектор интерфейса")
                .setMessage("Нажмите «Снять», затем за 8 секунд перейдите на нужный экран ВК (лента, чат, профиль…). QVK скопирует список элементов этого экрана в буфер обмена: имена, размеры, цвета. Из него берутся имена для правил, и его можно прислать разработчику, чтобы подогнать оформление под настоящие экраны.\n\nЛичные тексты сообщений в список не попадают: только типы и имена элементов.")
                .setPositiveButton("Снять", (d, w) -> {
                    Qvk3Ui.toast(a, "Откройте нужный экран ВК: снимок через 8 секунд");
                    a.moveTaskToBack(true);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Activity v = Qvk3Skin.lastVkActivity();
                        Context ctx = a.getApplicationContext();
                        if (v == null) {
                            Qvk3Ui.toast(ctx, "Экран ВК не найден: откройте его и повторите");
                            return;
                        }
                        try {
                            String text = Qvk3Skin.dump(v);
                            ((ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("QVK inspector", text));
                            java.io.File f = new java.io.File(ctx.getExternalFilesDir(null), "qvk-inspector.txt");
                            java.nio.file.Files.write(f.toPath(), text.getBytes("UTF-8"));
                            Qvk3Ui.toast(ctx, "Скопировано (" + text.length() / 1024 + " КБ): " + v.getClass().getSimpleName() + ". Вставьте в чат. Файл: " + f.getPath());
                        } catch (Throwable t) {
                            Qvk3Ui.toast(ctx, "Не удалось снять: " + Qvk3Jobs.message(t));
                        }
                    }, 8000);
                }).setNegativeButton("Отмена", null).show();
    }
}
