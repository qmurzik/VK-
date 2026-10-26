package com.vkmod.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.vkmod.core.Prefs;
import com.vkmod.core.Registry;
import com.vkmod.feat.UiKit;
import com.vkmod.theme.ColorSlot;
import com.vkmod.theme.Palette;
import com.vkmod.theme.Preset;
import com.vkmod.theme.Presets;
import com.vkmod.theme.ThemeEngine;

/** Theme editor: live preview of the effective palette plus per-slot colour editing for light and dark. */
public class ThemeEditorActivity extends Activity {
    private UiKit.Colors ui;
    private boolean editDark;
    private LinearLayout previewHost, slotList;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = UiKit.colors(this);
        editDark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        UiKit.applyStatusBar(this, ui);
        build();
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(ui.bg);
        LinearLayout root = UiKit.vbox(this);
        int p = UiKit.dp(this, 16);
        root.setPadding(p, p + UiKit.dp(this, 8), p, p);
        sv.addView(root);
        root.addView(UiKit.text(this, "Редактор тем", 24, ui.text, true));
        root.addView(UiKit.text(this, "Изменения применяются сразу; в VK они появятся на новых экранах (или после перезапуска).", 13, ui.text2, false));

        LinearLayout bar = UiKit.hbox(this);
        bar.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 10));
        bar.addView(UiKit.button(this, editDark ? "Тёмная тема" : "Светлая тема", ui, v -> { editDark = !editDark; ((TextView) v).setText(editDark ? "Тёмная тема" : "Светлая тема"); refresh(); }));
        bar.addView(UiKit.button(this, "Пресет", ui, v -> presetDialog()));
        bar.addView(UiKit.button(this, "Сбросить мои цвета", ui, v -> { ThemeEngine.clearCustom(); refresh(); }));
        root.addView(bar);

        previewHost = UiKit.vbox(this);
        root.addView(previewHost);
        slotList = UiKit.vbox(this);
        slotList.setPadding(0, UiKit.dp(this, 12), 0, 0);
        root.addView(slotList);
        setContentView(sv);
        refresh();
    }

    private void refresh() {
        if (!Registry.isOn("theme_engine")) Registry.setOn("theme_engine", true);
        previewHost.removeAllViews();
        previewHost.addView(preview());
        slotList.removeAllViews();
        for (ColorSlot s : ColorSlot.values()) slotList.addView(slotRow(s));
    }

    private void presetDialog() {
        String[] names = new String[Presets.ALL.size()];
        int sel = 0;
        for (int i = 0; i < names.length; i++) { Preset pr = Presets.ALL.get(i); names[i] = pr.title + " — " + pr.description; if (pr.id.equals(Registry.str("theme_preset"))) sel = i; }
        new AlertDialog.Builder(this).setTitle("Готовая тема").setSingleChoiceItems(names, sel, (d, w) -> {
            Prefs.putString("v.theme_preset", Presets.ALL.get(w).id);
            d.dismiss();
            refresh();
        }).setNegativeButton("Отмена", null).show();
    }

    // ---- preview ----

    /** VK-like fallbacks for slots the current palette does not override. */
    private int c(Palette p, ColorSlot s, boolean dark) {
        Integer v = p.get(s);
        if (v != null) return v;
        switch (s) {
            case ACCENT: case LINK: case TABBAR_ACTIVE: return dark ? 0xFF71AAEB : 0xFF2688EB;
            case BACKGROUND: return dark ? 0xFF0A0A0A : 0xFFEBEDF0;
            case CHAT_BG: return dark ? 0xFF0A0A0A : 0xFFEBEDF0;
            case CARD: case HEADER: case TABBAR: return dark ? 0xFF1C1C1C : 0xFFFFFFFF;
            case HEADER_TEXT: case TEXT: return dark ? 0xFFE1E3E6 : 0xFF000000;
            case TEXT_SECONDARY: case ICON: case TABBAR_INACTIVE: return dark ? 0xFF8E8E93 : 0xFF818C99;
            case SEPARATOR: return dark ? 0xFF2A2A2A : 0xFFD7D8D9;
            case FIELD: return dark ? 0xFF2C2C2E : 0xFFF2F3F5;
            case BUBBLE_OUT: return dark ? 0xFF2C4B73 : 0xFFD6E8FF;
            case BUBBLE_IN: return dark ? 0xFF2C2C2E : 0xFFFFFFFF;
            default: return 0xFF888888;
        }
    }

    private View preview() {
        final boolean d = editDark;
        final Palette p = ThemeEngine.effective(d);
        LinearLayout phone = UiKit.vbox(this);
        phone.setBackground(UiKit.round(c(p, ColorSlot.BACKGROUND, d), 18, this));
        int pad = UiKit.dp(this, 10);

        TextView head = UiKit.text(this, "  Лента", 17, c(p, ColorSlot.HEADER_TEXT, d), true);
        head.setBackgroundColor(c(p, ColorSlot.HEADER, d));
        head.setPadding(pad, pad, pad, pad);
        phone.addView(head);

        LinearLayout card = UiKit.vbox(this);
        card.setBackground(UiKit.round(c(p, ColorSlot.CARD, d), 12, this));
        card.setPadding(pad, pad, pad, pad);
        card.addView(UiKit.text(this, "Иван Петров", 15, c(p, ColorSlot.TEXT, d), true));
        card.addView(UiKit.text(this, "вчера в 21:40", 12, c(p, ColorSlot.TEXT_SECONDARY, d), false));
        TextView post = UiKit.text(this, "Так выглядит запись в ленте. Ссылка: vk.com/club1", 14, c(p, ColorSlot.TEXT, d), false);
        post.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
        card.addView(post);
        View sep = new View(this);
        sep.setBackgroundColor(c(p, ColorSlot.SEPARATOR, d));
        card.addView(sep, new LinearLayout.LayoutParams(-1, 2));
        LinearLayout actions = UiKit.hbox(this);
        actions.setPadding(0, UiKit.dp(this, 6), 0, 0);
        actions.addView(UiKit.text(this, "♥ 12   ", 13, c(p, ColorSlot.ICON, d), false));
        actions.addView(UiKit.text(this, "Подробнее", 13, c(p, ColorSlot.LINK, d), true));
        card.addView(actions);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.setMargins(pad, pad, pad, pad / 2);
        phone.addView(card, cl);

        LinearLayout chat = UiKit.vbox(this);
        chat.setBackground(UiKit.round(c(p, ColorSlot.CHAT_BG, d), 12, this));
        chat.setPadding(pad, pad, pad, pad);
        TextView in = UiKit.text(this, "Привет! Как дела?", 14, c(p, ColorSlot.TEXT, d), false);
        in.setBackground(UiKit.round(c(p, ColorSlot.BUBBLE_IN, d), 14, this));
        in.setPadding(pad, pad / 2, pad, pad / 2);
        chat.addView(in, new LinearLayout.LayoutParams(-2, -2));
        TextView out = UiKit.text(this, "Отлично, спасибо", 14, c(p, ColorSlot.TEXT, d), false);
        out.setBackground(UiKit.round(c(p, ColorSlot.BUBBLE_OUT, d), 14, this));
        out.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, -2);
        ol.gravity = Gravity.END;
        ol.topMargin = pad / 2;
        chat.addView(out, ol);
        TextView field = UiKit.text(this, "Сообщение", 14, c(p, ColorSlot.TEXT_SECONDARY, d), false);
        field.setBackground(UiKit.round(c(p, ColorSlot.FIELD, d), 18, this));
        field.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = pad;
        chat.addView(field, fl);
        LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(-1, -2);
        chl.setMargins(pad, pad / 2, pad, pad);
        phone.addView(chat, chl);

        LinearLayout tabs = UiKit.hbox(this);
        tabs.setBackgroundColor(c(p, ColorSlot.TABBAR, d));
        tabs.setPadding(0, pad, 0, pad);
        String[] labels = {"Лента", "Сообщения", "Клипы", "Профиль"};
        for (int i = 0; i < labels.length; i++) {
            TextView t = UiKit.text(this, "●\n" + labels[i], 11, c(p, i == 0 ? ColorSlot.TABBAR_ACTIVE : ColorSlot.TABBAR_INACTIVE, d), false);
            t.setGravity(Gravity.CENTER);
            tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        phone.addView(tabs);
        return phone;
    }

    // ---- slot rows and colour dialog ----

    private View slotRow(final ColorSlot s) {
        final Palette p = ThemeEngine.effective(editDark);
        LinearLayout row = UiKit.hbox(this);
        row.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 10), UiKit.dp(this, 4), UiKit.dp(this, 10));
        View sw = new View(this);
        sw.setBackground(UiKit.round(c(p, s, editDark), 8, this));
        row.addView(sw, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));
        LinearLayout t = UiKit.vbox(this);
        t.setPadding(UiKit.dp(this, 12), 0, 0, 0);
        boolean custom = ThemeEngine.hasCustom(s, editDark);
        t.addView(UiKit.text(this, s.title + (custom ? "  ✎" : ""), 15, ui.text, true));
        t.addView(UiKit.text(this, Palette.hex(c(p, s, editDark)), 12, ui.text2, false));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setOnClickListener(v -> colorDialog(s));
        return row;
    }

    private void colorDialog(final ColorSlot s) {
        final int start = c(ThemeEngine.effective(editDark), s, editDark);
        final int[] cur = {start};
        LinearLayout box = UiKit.vbox(this);
        box.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 8), UiKit.dp(this, 20), 0);
        final View swatch = new View(this);
        swatch.setBackgroundColor(start);
        box.addView(swatch, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 48)));
        final EditText hex = new EditText(this);
        hex.setText(Palette.hex(start));
        hex.setSingleLine(true);
        box.addView(hex);
        final SeekBar[] bars = new SeekBar[3];
        final String[] names = {"R", "G", "B"};
        final boolean[] guard = {false};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(i == 0 ? Color.red(start) : i == 1 ? Color.green(start) : Color.blue(start));
            bars[i] = sb;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar x, int pr, boolean user) {
                    if (!user) return;
                    cur[0] = Color.argb(Color.alpha(cur[0]), bars[0].getProgress(), bars[1].getProgress(), bars[2].getProgress());
                    swatch.setBackgroundColor(cur[0]);
                    guard[0] = true; hex.setText(Palette.hex(cur[0])); guard[0] = false;
                }
                @Override public void onStartTrackingTouch(SeekBar x) { }
                @Override public void onStopTrackingTouch(SeekBar x) { }
            });
            LinearLayout line = UiKit.hbox(this);
            line.addView(UiKit.text(this, names[i] + " ", 13, ui.text2, true));
            line.addView(sb, new LinearLayout.LayoutParams(0, -2, 1f));
            box.addView(line);
        }
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void afterTextChanged(Editable e) {
                if (guard[0]) return;
                try {
                    int v = Palette.parse(e.toString());
                    cur[0] = v;
                    swatch.setBackgroundColor(v);
                    bars[0].setProgress(Color.red(v)); bars[1].setProgress(Color.green(v)); bars[2].setProgress(Color.blue(v));
                } catch (Exception ignored) { }
            }
        });
        new AlertDialog.Builder(this).setTitle(s.title + (editDark ? " (тёмная)" : " (светлая)")).setView(box)
                .setPositiveButton("Применить", (d, w) -> { ThemeEngine.setCustom(s, editDark, cur[0]); Registry.setOn("theme_engine", true); refresh(); })
                .setNeutralButton("Сбросить", (d, w) -> { ThemeEngine.setCustom(s, editDark, null); refresh(); })
                .setNegativeButton("Отмена", null).show();
    }
}
