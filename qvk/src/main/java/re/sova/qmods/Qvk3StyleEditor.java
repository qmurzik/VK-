package re.sova.qmods;

import java.util.ArrayList;
import java.util.List;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Visual style editor with a live preview of the main VK screens (header, feed post, chat bubbles, bottom
 * bar). The preview is drawn from the same tokens that are compiled for VK, including the automatic
 * contrast correction, so what the user sees is what gets applied.
 */
public final class Qvk3StyleEditor {
    /** Preview of a preset with an Apply button. */
    public static void preview(Activity a, Qvk3DesignTokens t) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(mock(a, t));
        TextView d = Qvk3Ui.text(a, t.description + "\nСкругление " + t.radius + " dp · текст " + t.textScale + "% · " + density(t.density)
                + (t.heavy && Qvk3Appearance.weakDevice(a) ? "\nТяжёлые эффекты будут отключены на этом устройстве" : ""), 13, t.dark ? 0xFFCCCCCC : 0xFF555555, false);
        d.setPadding(Qvk3Ui.dp(a, 20), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 20), 0);
        col.addView(d);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        Qvk3Ui.dialog(a).setTitle(t.title).setView(sv)
                .setPositiveButton("Применить", (dlg, w) -> apply(a, t))
                .setNeutralButton("Настроить", (dlg, w) -> show(a, t))
                .setNegativeButton("Отмена", null).show();
    }

    static String density(String d) {
        return d.equals("compact") ? "компактно" : d.equals("comfortable") ? "просторно" : "обычная плотность";
    }

    static void apply(Activity a, Qvk3DesignTokens t) {
        try {
            Qvk3Appearance.apply(a, t);
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Не удалось применить стиль: " + Qvk3Jobs.message(e));
        }
    }

    public static void show(Activity a) {
        show(a, Qvk3Appearance.current(a));
    }

    /** Token editor: every change re-renders the preview immediately. */
    public static void show(Activity a, Qvk3DesignTokens start) {
        final Qvk3DesignTokens[] cur = {start};
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        FrameLayout previewHost = new FrameLayout(a);
        col.addView(previewHost, Qvk3Ui.match());
        Runnable render = () -> {
            previewHost.removeAllViews();
            previewHost.addView(mock(a, cur[0]));
        };
        render.run();
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        int pad = Qvk3Ui.dp(a, 24);
        // the preview as a rounded card inside the sheet, not an edge-to-edge strip
        LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) previewHost.getLayoutParams();
        plp.leftMargin = plp.rightMargin = pad;
        previewHost.setLayoutParams(plp);
        Qvk3Ui.clipRound(previewHost, Qvk3Ui.dp(a, 18));
        LinearLayout controls = new LinearLayout(a);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(pad, 0, pad, Qvk3Ui.dp(a, 8));
        col.addView(controls);

        String[][] colours = {{"accent", "Акцент"}, {"page", "Фон экранов"}, {"content", "Карточки"}, {"header", "Шапка и нижняя панель"},
                {"text", "Основной текст"}, {"text2", "Дополнительный текст"}, {"separator", "Разделители"},
                {"incoming", "Входящие сообщения"}, {"outgoing", "Исходящие сообщения"}};
        controls.addView(Qvk3Sheet.section(a, p, "Цвета"));
        LinearLayout cg = Qvk3Sheet.group(a, p);
        for (String[] c : colours) {
            View[] row = new View[1];
            row[0] = Qvk3Sheet.swatchRow(a, p, c[1], colour(cur[0], c[0]), v -> pickColor(a, c[1], colour(cur[0], c[0]), col2 -> {
                cur[0] = cur[0].with(c[0], col2);
                Qvk3Sheet.setSwatch(a, p, row[0], col2);
                render.run();
            }));
            cg.addView(row[0]);
        }
        controls.addView(cg);
        controls.addView(Qvk3Sheet.section(a, p, "Форма"));
        LinearLayout fg = Qvk3Sheet.group(a, p);
        fg.addView(Qvk3SoundDialog.slider(a, p, "Скругление", cur[0].radius, 28, v -> {
            cur[0] = cur[0].with("radius", v);
            render.run();
        }, v -> v + " dp"));
        fg.addView(Qvk3SoundDialog.slider(a, p, "Размер текста", cur[0].textScale - 90, 35, v -> {
            cur[0] = cur[0].with("textScale", 90 + v);
            render.run();
        }, v -> (90 + v) + "%"));
        controls.addView(fg);
        controls.addView(Qvk3Sheet.section(a, p, "Стиль"));
        LinearLayout sg = Qvk3Sheet.group(a, p);
        sg.addView(choice(a, p, "Шрифт", Qvk3DesignTokens.ThemeFonts.TITLES, () -> indexOf(Qvk3DesignTokens.ThemeFonts.ALL, cur[0].font), i -> {
            cur[0] = cur[0].with("font", Qvk3DesignTokens.ThemeFonts.ALL[i]);
            render.run();
        }));
        sg.addView(choice(a, p, "Плотность", new String[]{"Компактно", "Обычно", "Просторно"},
                () -> indexOf(new String[]{"compact", "normal", "comfortable"}, cur[0].density), i -> {
                    cur[0] = cur[0].with("density", new String[]{"compact", "normal", "comfortable"}[i]);
                    render.run();
                }));
        sg.addView(choice(a, p, "Форма элементов", new String[]{"Плоская ВК", "iOS", "Material"},
                () -> indexOf(new String[]{"flat", "ios", "material"}, cur[0].components), i -> {
                    cur[0] = cur[0].with("components", new String[]{"flat", "ios", "material"}[i]);
                    render.run();
                }));
        sg.addView(choice(a, p, "Фон экранов", new String[]{"Сплошной", "Градиент", "Точки", "Сетка"},
                () -> indexOf(new String[]{"solid", "gradient", "dots", "grid"}, cur[0].background), i -> {
                    cur[0] = cur[0].with("background", new String[]{"solid", "gradient", "dots", "grid"}[i]);
                    render.run();
                }));
        sg.addView(choice(a, p, "Схема", new String[]{"Светлая", "Тёмная"}, () -> cur[0].dark ? 1 : 0, i -> {
            cur[0] = cur[0].with("dark", i == 1);
            render.run();
        }));
        controls.addView(sg);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        Qvk3Ui.dialog(a).setTitle("Конструктор оформления").setView(sv)
                .setPositiveButton("Применить", (d, w) -> apply(a, cur[0]))
                .setNegativeButton("Отмена", null).show();
    }

    static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    static int colour(Qvk3DesignTokens t, String k) {
        switch (k) {
            case "accent":
                return t.accent;
            case "page":
                return t.page;
            case "content":
                return t.content;
            case "header":
                return t.header;
            case "text":
                return t.text;
            case "text2":
                return t.text2;
            case "separator":
                return t.separator;
            case "incoming":
                return t.incoming;
            default:
                return t.outgoing;
        }
    }

    interface IntSink {
        void set(int v);
    }

    interface IntSource {
        int get();
    }

    static View slider(Activity a, int tc, String label, int value, int max, IntSink sink) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = Qvk3Ui.text(a, label, 14, tc, false);
        SeekBar s = new SeekBar(a);
        s.setMax(max);
        s.setProgress(value);
        s.setContentDescription(label);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int v, boolean user) {
                if (user) sink.set(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        l.addView(t);
        l.addView(s);
        return l;
    }

    static View choice(Activity a, int tc, String label, String[] options, IntSource current, IntSink sink) {
        return choice(a, Qvk3Sheet.palette(a), label, options, current, sink);
    }

    static View choice(Activity a, Qvk3Ui.Palette p, String label, String[] options, IntSource current, IntSink sink) {
        View[] row = new View[1];
        row[0] = Qvk3Sheet.navRow(a, p, label + ": " + options[current.get()], v -> Qvk3Ui.dialog(a).setTitle(label).setSingleChoiceItems(options, current.get(), (d, i) -> {
            sink.set(i);
            Qvk3Sheet.setNavValue(row[0], options[i]);
            d.dismiss();
        }).show());
        return row[0];
    }

    /** HEX input + a palette of swatches. */
    static void pickColor(Activity a, String title, int initial, IntSink sink) {
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 24);
        col.setPadding(pad, 0, pad, 0);
        final int[] cur = {initial | 0xFF000000};
        View big = new View(a);
        col.addView(big, new LinearLayout.LayoutParams(-1, Qvk3Ui.dp(a, 64)));
        EditText hex = Qvk3Sheet.field(a, p, new EditText(a));
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setSingleLine(true);
        LinearLayout.LayoutParams hl = Qvk3Ui.match();
        hl.topMargin = Qvk3Ui.dp(a, 12);
        col.addView(hex, hl);
        int[] swatches = {0xFF2688EB, 0xFF007AFF, 0xFF3F8AE0, 0xFF8B75CB, 0xFFFF2BD6, 0xFFE64646, 0xFFFF9500, 0xFFC07A4F, 0xFF4BB34B,
                0xFF00B3A4, 0xFF000000, 0xFF19191A, 0xFF2C2D2E, 0xFF818C99, 0xFFEBEDF0, 0xFFF6F7F9, 0xFFFFFFFF, 0xFFF7F1EA};
        List<View> dots = new ArrayList<>();
        Runnable paint = () -> {
            big.setBackground(Qvk3Ui.rounded(cur[0], Qvk3Ui.dp(a, 18)));
            for (int k = 0; k < dots.size(); k++) {
                int c = swatches[k];
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(c);
                boolean sel = (c & 0xFFFFFF) == (cur[0] & 0xFFFFFF);
                g.setStroke(Qvk3Ui.dp(a, sel ? 3 : 1), sel ? p.text : (p.dark ? 0x40FFFFFF : 0x26000000));
                dots.get(k).setBackground(g);
            }
        };
        LinearLayout grid = null;
        for (int i = 0; i < swatches.length; i++) {
            if (i % 6 == 0) {
                grid = new LinearLayout(a);
                LinearLayout.LayoutParams gl = Qvk3Ui.match();
                gl.topMargin = Qvk3Ui.dp(a, i == 0 ? 14 : 8);
                col.addView(grid, gl);
            }
            final int c = swatches[i];
            FrameLayout cell = new FrameLayout(a);
            View v = new View(a);
            v.setContentDescription(String.format("Цвет #%06X", c & 0xFFFFFF));
            v.setOnClickListener(x -> hex.setText(String.format("#%06X", c & 0xFFFFFF)));
            cell.addView(v, new FrameLayout.LayoutParams(Qvk3Ui.dp(a, 38), Qvk3Ui.dp(a, 38), Gravity.CENTER));
            grid.addView(cell, new LinearLayout.LayoutParams(0, Qvk3Ui.dp(a, 44), 1f));
            dots.add(v);
        }
        hex.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence q, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence q, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                try {
                    String t = e.toString().trim();
                    if (t.matches("#?[0-9A-Fa-f]{6}")) {
                        cur[0] = Color.parseColor(t.startsWith("#") ? t : "#" + t) | 0xFF000000;
                        paint.run();
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        });
        hex.setText(String.format("#%06X", initial & 0xFFFFFF));
        paint.run();
        Qvk3Ui.dialog(a).setTitle(title).setView(col).setPositiveButton("Готово", (d, w) -> {
            try {
                String t = hex.getText().toString().trim();
                sink.set(Color.parseColor(t.startsWith("#") ? t : "#" + t));
            } catch (IllegalArgumentException e) {
                Qvk3Ui.toast(a, "Цвет в формате #RRGGBB");
            }
        }).setNegativeButton("Отмена", null).show();
    }

    /** Miniature of the VK UI drawn from tokens (after contrast correction). */
    static View mock(Activity a, Qvk3DesignTokens raw) {
        Qvk3DesignTokens t = raw.checked();
        float scale = t.textScale / 100f;
        int gap = Qvk3Ui.dp(a, t.density.equals("compact") ? 6 : t.density.equals("comfortable") ? 14 : 10);
        float r = Qvk3Ui.dp(a, t.radius);
        Typeface tf = Typeface.create(t.font.contains("serif") ? "serif" : t.font.contains("mono") ? "monospace"
                : t.font.equals("condensed") ? "sans-serif-condensed" : "sans-serif", Typeface.NORMAL);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = t.background.equals("gradient")
                ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Qvk3DesignTokens.over(t.page, 0xFF000000), Qvk3DesignTokens.over(t.highlight, Qvk3DesignTokens.over(t.page, 0xFF000000))})
                : new GradientDrawable();
        if (!t.background.equals("gradient")) bg.setColor(Qvk3DesignTokens.over(t.page, 0xFF000000));
        root.setBackground(bg);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        root.setContentDescription("Предпросмотр оформления");
        TextView header = Qvk3Ui.line(a, "Новости", 18 * scale, t.text, true);
        header.setTypeface(tf, Typeface.BOLD);
        header.setBackgroundColor(Qvk3DesignTokens.over(t.header, 0xFF000000));
        header.setPadding(gap * 2, gap, gap * 2, gap);
        root.addView(header);
        LinearLayout post = new LinearLayout(a);
        post.setOrientation(LinearLayout.VERTICAL);
        post.setBackground(Qvk3Ui.rounded(Qvk3DesignTokens.over(t.content, Qvk3DesignTokens.over(t.page, 0xFF000000)), r));
        post.setPadding(gap * 2, gap, gap * 2, gap);
        TextView author = Qvk3Ui.line(a, "QVK · сообщество", 15 * scale, t.text, true);
        author.setTypeface(tf, Typeface.BOLD);
        TextView body = Qvk3Ui.text(a, "Так будут выглядеть записи в ленте с выбранными цветами, шрифтом и отступами.", 14 * scale, t.text, false);
        body.setTypeface(tf);
        TextView meta = Qvk3Ui.line(a, "2 мин назад · ♥ 128", 12 * scale, t.text2, false);
        TextView btn = Qvk3Ui.line(a, "Подписаться", 14 * scale, Qvk3DesignTokens.onColor(t.accent), true);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(Qvk3Ui.rounded(t.accent, t.components.equals("ios") ? r / 2 : r));
        btn.setPadding(gap * 2, gap / 2 + 4, gap * 2, gap / 2 + 4);
        post.addView(author);
        post.addView(body);
        post.addView(meta);
        LinearLayout.LayoutParams blp = Qvk3Ui.wrap();
        blp.topMargin = gap;
        post.addView(btn, blp);
        LinearLayout.LayoutParams plp = Qvk3Ui.match();
        plp.setMargins(gap, gap, gap, gap / 2);
        root.addView(post, plp);
        root.addView(bubble(a, t, "Привет! Как тебе новый QVK?", false, r, gap, scale, tf));
        root.addView(bubble(a, t, "Выглядит как новый клиент 😊", true, r, gap, scale, tf));
        LinearLayout bar = new LinearLayout(a);
        bar.setBackgroundColor(Qvk3DesignTokens.over(t.header, 0xFF000000));
        String[] tabs = {"Главная", "Сервисы", "Мессенджер", "Музыка"};
        for (int i = 0; i < tabs.length; i++) {
            TextView tb = Qvk3Ui.line(a, tabs[i], 11 * scale, i == 0 ? t.accent : t.text2, i == 0);
            tb.setGravity(Gravity.CENTER);
            tb.setPadding(0, gap, 0, gap);
            bar.addView(tb, Qvk3Ui.weight(1));
        }
        View sep = new View(a);
        sep.setBackgroundColor(Qvk3DesignTokens.over(t.separator, 0xFF000000));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, Math.max(1, Qvk3Ui.dp(a, 0.7f)));
        slp.topMargin = gap;
        root.addView(sep, slp);
        root.addView(bar);
        return root;
    }

    static View bubble(Activity a, Qvk3DesignTokens t, String text, boolean out, float r, int gap, float scale, Typeface tf) {
        int bgc = Qvk3DesignTokens.over(out ? t.outgoing : t.incoming, Qvk3DesignTokens.over(t.page, 0xFF000000));
        TextView b = Qvk3Ui.text(a, text, 14 * scale, Qvk3DesignTokens.ensureContrast(t.text, bgc, 4.5), false);
        b.setTypeface(tf);
        b.setBackground(Qvk3Ui.rounded(bgc, Math.max(r, Qvk3Ui.dp(a, 8))));
        b.setPadding(gap + 6, gap / 2 + 4, gap + 6, gap / 2 + 4);
        LinearLayout wrap = new LinearLayout(a);
        wrap.setGravity(out ? Gravity.END : Gravity.START);
        wrap.setPadding(gap, gap / 2, gap, 0);
        wrap.addView(b);
        return wrap;
    }
}
