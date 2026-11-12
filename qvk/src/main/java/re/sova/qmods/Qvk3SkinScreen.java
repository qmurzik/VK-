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

import java.util.ArrayList;
import java.util.List;

/** Settings of the VK skin engine: floating bottom bar, per-id rules and the interface inspector. */
final class Qvk3SkinScreen {
    static void dock(Activity a) {
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        android.content.SharedPreferences pr = Qvk3Skin.prefs(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 24);
        col.setPadding(pad, 0, pad, Qvk3Ui.dp(a, 8));
        LinearLayout g = Qvk3Sheet.group(a, p);
        g.addView(Qvk3Sheet.switchRow(a, p, "Плавающая панель", "Скруглённая нижняя панель над контентом",
                "floating".equals(pr.getString(Qvk3Skin.DOCK, "off")), v -> {
                    Qvk3SettingsHistory.before(a, Qvk3Skin.DOCK, v ? "Плавающая нижняя панель включена" : "Нижняя панель ВК: стандартная");
                    pr.edit().putString(Qvk3Skin.DOCK, v ? "floating" : "off").apply();
                    Qvk3Skin.refresh();
                }));
        col.addView(g);
        col.addView(Qvk3Sheet.section(a, p, "Форма"));
        LinearLayout f = Qvk3Sheet.group(a, p);
        f.addView(Qvk3SoundDialog.slider(a, p, "Скругление", pr.getInt(Qvk3Skin.DOCK_RADIUS, 24), 40, v -> save(a, Qvk3Skin.DOCK_RADIUS, v), v -> v + " dp"));
        f.addView(Qvk3SoundDialog.slider(a, p, "Отступ от краёв", pr.getInt(Qvk3Skin.DOCK_MARGIN, 12), 32, v -> save(a, Qvk3Skin.DOCK_MARGIN, v), v -> v + " dp"));
        f.addView(Qvk3SoundDialog.slider(a, p, "Непрозрачность", pr.getInt(Qvk3Skin.DOCK_ALPHA, 96), 100, v -> save(a, Qvk3Skin.DOCK_ALPHA, Math.max(40, v)), v -> Math.max(40, v) + "%"));
        col.addView(f);
        col.addView(Qvk3Sheet.note(a, p, "Меняется настоящая панель ВК: вкладки и счётчики работают как раньше. Изменение видно после возврата на экран ВК."));
        Qvk3Ui.dialog(a).setTitle("Нижняя панель ВК").setView(col).setPositiveButton("Готово", null).show();
    }

    static void save(Activity a, String key, int v) {
        Qvk3SettingsHistory.before(a, key, "Нижняя панель ВК");
        Qvk3Skin.prefs(a).edit().putInt(key, v).apply();
        Qvk3Skin.refresh();
    }

    static void rules(Activity a) {
        List<Qvk3Skin.Rule> rules = Qvk3Skin.rules(a);
        List<String> labels = new ArrayList<>();
        for (Qvk3Skin.Rule r : rules) labels.add(r.id + "\n" + describe(r));
        labels.add("Настроить касанием\nВыберите элемент прямо на экране ВК");
        labels.add("Новое правило\nПо имени элемента");
        labels.add("Поделиться оформлением\nКод правил копируется в буфер");
        labels.add("Вставить оформление\nИз кода в буфере обмена");
        labels.add("Инспектор интерфейса\nКак узнать имя элемента");
        labels.add("Без теней\nУбрать тени у шапки чата и панели");
        labels.add("Прозрачная «Написать»\nПолупрозрачная кнопка в чатах");
        labels.add("Плоский вид\nУбрать разделители и тени везде");
        labels.add("Сбросить все правила");
        Qvk3Ui.dialog(a).setTitle("Правила интерфейса ВК").setItems(labels.toArray(new String[0]), (d, i) -> {
            int k = i - rules.size();
            if (i < rules.size()) edit(a, rules, i);
            else if (k == 0) Qvk3ElementPicker.arm(a);
            else if (k == 1) edit(a, rules, -1);
            else if (k == 2) Qvk3LookShare.share(a);
            else if (k == 3) Qvk3LookShare.paste(a);
            else if (k == 4) inspector(a);
            else if (k == 5) preset(a, rules, true, "dialog_shadow", "bottom_navigation_shadow");
            else if (k == 6) preset(a, rules, false, "vkim_fab");
            else if (k == 7) preset(a, rules, true, "shadow", "shadow_view", "app_bar_shadow_view", "header_shadow", "divider", "separator", "top_divider", "bottom_divider", "tool_bar_divider", "dialog_shadow", "bottom_navigation_shadow");
            else {
                rules.clear();
                Qvk3Skin.saveRules(a, rules);
                Qvk3Skin.refresh();
                rules(a);
            }
        }).setNegativeButton("Закрыть", null).show();
    }

    static String describe(Qvk3Skin.Rule r) {
        List<String> d = new ArrayList<>();
        if (r.hide) d.add("скрыт");
        if (r.radius >= 0) d.add("скругление " + r.radius);
        if (r.bg != 0) d.add(String.format("фон #%06X", r.bg & 0xFFFFFF));
        if (r.opacity < 100) d.add(r.opacity + "%");
        if (r.margin >= 0) d.add("отступ " + r.margin);
        if (r.vmargin >= 0) d.add("интервал " + r.vmargin);
        return d.isEmpty() ? "без изменений" : String.join(" · ", d);
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
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 24);
        col.setPadding(pad, 0, pad, Qvk3Ui.dp(a, 8));
        EditText id = Qvk3Sheet.field(a, p, new EditText(a));
        id.setHint("Имя элемента, например bottom_navigation");
        id.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        id.setText(r.id);
        col.addView(id);
        final boolean[] hidden = {r.hide};
        LinearLayout g = Qvk3Sheet.group(a, p);
        g.addView(Qvk3Sheet.switchRow(a, p, "Скрыть элемент", null, r.hide, v -> hidden[0] = v));
        LinearLayout.LayoutParams glp = Qvk3Ui.match();
        glp.topMargin = Qvk3Ui.dp(a, 12);
        col.addView(g, glp);
        col.addView(Qvk3Sheet.section(a, p, "Вид"));
        LinearLayout look = Qvk3Sheet.group(a, p);
        final int[] radius = {r.radius}, opacity = {r.opacity}, margin = {r.margin};
        look.addView(Qvk3SoundDialog.slider(a, p, "Скругление", Math.max(0, r.radius), 40, v -> radius[0] = v, v -> v == 0 ? "квадрат" : v + " dp"));
        look.addView(Qvk3SoundDialog.slider(a, p, "Непрозрачность", r.opacity, 100, v -> opacity[0] = Math.max(10, v), v -> Math.max(10, v) + "%"));
        look.addView(Qvk3SoundDialog.slider(a, p, "Отступы", Math.max(0, r.margin), 32, v -> margin[0] = v, v -> v + " dp"));
        col.addView(look);
        EditText color = Qvk3Sheet.field(a, p, new EditText(a));
        color.setHint("Цвет фона #RRGGBB (необязательно)");
        color.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (r.bg != 0) color.setText(String.format("#%06X", r.bg & 0xFFFFFF));
        LinearLayout.LayoutParams clp = Qvk3Ui.match();
        clp.topMargin = Qvk3Ui.dp(a, 12);
        col.addView(color, clp);
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
                    r.hide = hidden[0];
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
