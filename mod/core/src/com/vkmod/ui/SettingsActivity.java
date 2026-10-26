package com.vkmod.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.vkmod.Mod;
import com.vkmod.core.Feature;
import com.vkmod.core.ModLog;
import com.vkmod.core.Prefs;
import com.vkmod.core.Registry;
import com.vkmod.feat.AppLock;
import com.vkmod.feat.IconSwitcher;
import com.vkmod.feat.UiKit;
import com.vkmod.feat.Updater;
import com.vkmod.theme.ThemeEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/** "Настройки мода": search, categories, one row per feature with its description and status. */
public class SettingsActivity extends Activity {
    private static final int REQ_EXPORT_THEME = 1, REQ_IMPORT_THEME = 2, REQ_FONT = 3, REQ_BACKUP = 4, REQ_RESTORE = 5;

    private UiKit.Colors col;
    private LinearLayout list;
    private TextView counter;
    private String query = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!com.vkmod.core.Prefs.ready()) {
            Toast.makeText(this, "Мод ещё не инициализирован — откройте VK и вернитесь сюда", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        build();
    }

    @Override protected void onResume() { super.onResume(); if (Prefs.ready()) build(); }

    private void build() {
        col = UiKit.colors(this);
        UiKit.applyStatusBar(this, col);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(col.bg);
        LinearLayout root = UiKit.vbox(this);
        int pad = UiKit.dp(this, 16);
        root.setPadding(pad, pad + UiKit.dp(this, 8), pad, pad);
        sv.addView(root);

        TextView title = UiKit.text(this, "Настройки мода", 26, col.text, true);
        root.addView(title);
        TextView sub = UiKit.text(this, "VK Mod " + Mod.VERSION + " · для VK 8.200", 13, col.text2, false);
        root.addView(sub);
        counter = UiKit.text(this, "", 13, col.accent, true);
        counter.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 10));
        root.addView(counter);

        EditText search = new EditText(this);
        search.setHint("Поиск по настройкам");
        search.setSingleLine(true);
        search.setTextColor(col.text);
        search.setHintTextColor(col.text2);
        search.setText(query);
        search.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 10), UiKit.dp(this, 14), UiKit.dp(this, 10));
        search.setBackground(UiKit.round(col.field, 12, this));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable e) { query = e.toString().trim().toLowerCase(Locale.ROOT); render(); }
        });
        root.addView(search);

        list = UiKit.vbox(this);
        root.addView(list);
        setContentView(sv);
        render();
    }

    private void render() {
        list.removeAllViews();
        counter.setText("Включено функций: " + Registry.countOn() + " · ⚠ — работает частично");
        for (Feature.Category cat : Feature.Category.values()) {
            LinearLayout card = null;
            for (Feature f : Registry.all()) {
                if (f.category != cat || !matches(f)) continue;
                if (card == null) {
                    TextView h = UiKit.text(this, cat.title.toUpperCase(Locale.ROOT), 12, col.accent, true);
                    h.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 18), 0, UiKit.dp(this, 6));
                    list.addView(h);
                    card = UiKit.vbox(this);
                    card.setBackground(UiKit.round(col.card, 16, this));
                    list.addView(card);
                } else {
                    View sep = new View(this);
                    sep.setBackgroundColor(col.sep);
                    card.addView(sep, new LinearLayout.LayoutParams(-1, 1));
                }
                card.addView(row(f));
            }
        }
    }

    private boolean matches(Feature f) {
        if (query.isEmpty()) return true;
        return f.title.toLowerCase(Locale.ROOT).contains(query) || f.description.toLowerCase(Locale.ROOT).contains(query)
                || f.id.contains(query) || f.category.title.toLowerCase(Locale.ROOT).contains(query);
    }

    private View row(final Feature f) {
        LinearLayout row = UiKit.vbox(this);
        int p = UiKit.dp(this, 14);
        row.setPadding(p, UiKit.dp(this, 12), p, UiKit.dp(this, 12));

        LinearLayout top = UiKit.hbox(this);
        LinearLayout texts = UiKit.vbox(this);
        TextView t = UiKit.text(this, f.title + (f.status == Feature.Status.PARTIAL ? "  ⚠" : ""), 16, col.text, true);
        texts.addView(t);
        TextView d = UiKit.text(this, f.description, 13, col.text2, false);
        texts.addView(d);
        if (f.status == Feature.Status.PARTIAL) {
            TextView n = UiKit.text(this, "Частично: " + f.partialNote, 12, 0xFFE0A030, false);
            n.setPadding(0, UiKit.dp(this, 3), 0, 0);
            texts.addView(n);
        }
        if (Registry.crashed(f.id)) {
            TextView n = UiKit.text(this, "Отключена после ошибки (см. лог). Переключите вручную, чтобы включить снова.", 12, 0xFFE05050, false);
            texts.addView(n);
        }
        top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        switch (f.kind) {
            case TOGGLE:
            case INT:
            case TEXT: {
                Switch sw = new Switch(this);
                sw.setChecked(Registry.isOn(f.id));
                sw.setOnCheckedChangeListener((v, on) -> { Registry.setOn(f.id, on); counter.setText("Включено функций: " + Registry.countOn() + " · ⚠ — работает частично"); });
                top.addView(sw);
                break;
            }
            case CHOICE: {
                TextView val = UiKit.text(this, labelOf(f, Registry.str(f.id)), 14, col.accent, true);
                top.addView(val);
                row.setOnClickListener(v -> chooseOption(f));
                break;
            }
            case ACTION:
                row.setOnClickListener(v -> runAction(f));
                break;
        }
        row.addView(top);

        if (f.kind == Feature.Kind.INT) row.addView(intControl(f));
        if (f.kind == Feature.Kind.TEXT) row.addView(textControl(f));
        return row;
    }

    private View intControl(final Feature f) {
        LinearLayout box = UiKit.hbox(this);
        box.setPadding(0, UiKit.dp(this, 8), 0, 0);
        final TextView val = UiKit.text(this, String.valueOf(Registry.num(f.id)), 14, col.accent, true);
        val.setMinWidth(UiKit.dp(this, 44));
        SeekBar bar = new SeekBar(this);
        bar.setMax(f.max - f.min);
        bar.setProgress(Registry.num(f.id) - f.min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { val.setText(String.valueOf(p + f.min)); if (user) Prefs.putInt(f.valueKey(), p + f.min); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { Prefs.putInt(f.valueKey(), s.getProgress() + f.min); }
        });
        box.addView(bar, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(val);
        return box;
    }

    private View textControl(final Feature f) {
        EditText e = new EditText(this);
        e.setText(Registry.str(f.id));
        e.setHint("значение");
        e.setTextColor(col.text);
        e.setHintTextColor(col.text2);
        e.setTextSize(14);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setBackground(UiKit.round(col.field, 10, this));
        e.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 8), UiKit.dp(this, 12), UiKit.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = UiKit.dp(this, 8);
        e.setLayoutParams(lp);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable ed) { Prefs.putString(f.valueKey(), ed.toString()); }
        });
        return e;
    }

    private String labelOf(Feature f, String id) {
        for (int i = 0; i < f.options.length; i++) if (f.options[i].equals(id)) return f.optionLabels[i];
        return id;
    }

    private void chooseOption(final Feature f) {
        final String cur = Registry.str(f.id);
        int sel = 0;
        for (int i = 0; i < f.options.length; i++) if (f.options[i].equals(cur)) sel = i;
        new AlertDialog.Builder(this).setTitle(f.title)
                .setSingleChoiceItems(f.optionLabels, sel, (dlg, which) -> {
                    Prefs.putString(f.valueKey(), f.options[which]);
                    if (f.id.equals("theme_preset") && !f.options[which].equals("none")) Registry.setOn("theme_engine", true);
                    dlg.dismiss();
                    render();
                }).setNegativeButton("Отмена", null).show();
    }

    // ---- actions ----

    private void runAction(Feature f) {
        switch (f.id) {
            case "theme_editor": startActivity(new Intent(this, ThemeEditorActivity.class)); break;
            case "theme_io": themeIo(); break;
            case "app_font_file": pick(REQ_FONT, "*/*"); break;
            case "app_icon": iconDialog(); break;
            case "app_lock_pin": pinDialog(); break;
            case "media_log": startActivity(new Intent(this, MediaLogActivity.class)); break;
            case "backup": backupDialog(); break;
            case "mod_log": startActivity(new Intent(this, LogActivity.class)); break;
            case "update_check": checkUpdates(); break;
            case "reset_all":
                new AlertDialog.Builder(this).setTitle("Сбросить все настройки?").setMessage("Все функции вернутся к значениям по умолчанию, PIN-код будет удалён.")
                        .setPositiveButton("Сбросить", (d, w) -> { Prefs.resetAll(); toast("Настройки сброшены"); render(); })
                        .setNegativeButton("Отмена", null).show();
                break;
            default: break;
        }
    }

    private void themeIo() {
        new AlertDialog.Builder(this).setTitle("Темы")
                .setItems(new String[]{"Экспортировать тему в файл", "Импортировать тему из файла"}, (d, w) -> {
                    if (w == 0) create(REQ_EXPORT_THEME, "vkmod-theme.json"); else pick(REQ_IMPORT_THEME, "*/*");
                }).show();
    }

    private void backupDialog() {
        new AlertDialog.Builder(this).setTitle("Резервная копия")
                .setItems(new String[]{"Сохранить настройки в файл", "Восстановить из файла"}, (d, w) -> {
                    if (w == 0) create(REQ_BACKUP, "vkmod-backup.json"); else pick(REQ_RESTORE, "*/*");
                }).show();
    }

    private void iconDialog() {
        final List<IconSwitcher.Alias> aliases = IconSwitcher.list(this);
        if (aliases.isEmpty()) { toast("Альтернативные иконки не найдены"); return; }
        String[] names = new String[aliases.size()];
        int sel = -1;
        for (int i = 0; i < names.length; i++) { names[i] = aliases.get(i).label.toString(); if (aliases.get(i).enabled) sel = i; }
        new AlertDialog.Builder(this).setTitle("Иконка приложения")
                .setSingleChoiceItems(names, sel, (d, w) -> {
                    toast(IconSwitcher.select(this, aliases.get(w).name) ? "Иконка изменена, лаунчер обновит её не сразу" : "Не удалось сменить иконку");
                    d.dismiss();
                }).setNegativeButton("Закрыть", null).show();
    }

    private void pinDialog() {
        final EditText e1 = pinField("Новый PIN (4–8 цифр)");
        final EditText e2 = pinField("Повторите PIN");
        LinearLayout box = UiKit.vbox(this);
        box.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 8), UiKit.dp(this, 20), 0);
        box.addView(e1);
        box.addView(e2);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("PIN-код").setView(box)
                .setPositiveButton("Сохранить", null).setNegativeButton("Отмена", null);
        if (AppLock.hasPin()) b.setNeutralButton("Удалить PIN", (d, w) -> { AppLock.clearPin(); toast("PIN удалён"); });
        final AlertDialog dlg = b.create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = e1.getText().toString(), c = e2.getText().toString();
            if (a.length() < 4 || a.length() > 8) { toast("PIN должен содержать 4–8 цифр"); return; }
            if (!a.equals(c)) { toast("PIN-коды не совпадают"); return; }
            AppLock.setPin(a);
            Registry.setOn("app_lock", true);
            toast("PIN сохранён, блокировка включена");
            dlg.dismiss();
            render();
        }));
        dlg.show();
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    private void checkUpdates() {
        toast("Проверяем…");
        new Thread(() -> {
            final Updater.Result r = Updater.check();
            runOnUiThread(() -> {
                String msg = r.error != null ? r.error : r.newer ? "Доступна версия " + r.tag : "У вас последняя версия (" + Mod.VERSION + ")";
                AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Обновления мода").setMessage(msg).setPositiveButton("Закрыть", null);
                if (r.newer && !r.url.isEmpty()) b.setNeutralButton("Открыть", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url))));
                b.show();
            });
        }).start();
    }

    // ---- file pickers ----

    private void pick(int req, String type) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);
        startActivityForResult(i, req);
    }

    private void create(int req, String name) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, req);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            switch (req) {
                case REQ_EXPORT_THEME: write(uri, ThemeEngine.exportTheme("VK Mod theme")); toast("Тема сохранена"); break;
                case REQ_IMPORT_THEME: {
                    String err = ThemeEngine.importTheme(read(uri));
                    toast(err == null ? "Тема загружена" : err);
                    break;
                }
                case REQ_BACKUP: write(uri, Prefs.exportJson()); toast("Резервная копия сохранена"); break;
                case REQ_RESTORE: {
                    int n = Prefs.importJson(read(uri));
                    toast(n >= 0 ? "Восстановлено настроек: " + n : "Файл не похож на резервную копию");
                    break;
                }
                case REQ_FONT: {
                    byte[] bytes = readBytes(uri);
                    File f = new File(getFilesDir(), "custom_font.ttf");
                    try (FileOutputStream o = new FileOutputStream(f)) { o.write(bytes); }
                    try { android.graphics.Typeface.createFromFile(f); } catch (Throwable t) { f.delete(); toast("Это не шрифт TTF"); break; }
                    Prefs.putString("font.path", f.getAbsolutePath());
                    Prefs.putString("v.app_font", "custom");
                    toast("Шрифт загружен; он применится к новым экранам");
                    break;
                }
                default: break;
            }
        } catch (Throwable t) {
            ModLog.e("file action failed", t);
            toast("Ошибка: " + t.getMessage());
        }
        render();
    }

    private String read(Uri u) throws Exception { return new String(readBytes(u), "UTF-8"); }

    private byte[] readBytes(Uri u) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) { o.write(buf, 0, n); if (o.size() > 16 * 1024 * 1024) throw new IllegalStateException("файл слишком большой"); }
            return o.toByteArray();
        }
    }

    private void write(Uri u, String s) throws Exception {
        if (s == null) throw new IllegalStateException("нечего сохранять");
        try (OutputStream o = getContentResolver().openOutputStream(u, "wt")) { o.write(s.getBytes("UTF-8")); }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
