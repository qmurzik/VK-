package re.sova.qmods;

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
        col.addView(previewHost);
        Runnable render = () -> {
            previewHost.removeAllViews();
            previewHost.addView(mock(a, cur[0]));
        };
        render.run();
        LinearLayout controls = new LinearLayout(a);
        controls.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 20);
        controls.setPadding(pad, Qvk3Ui.dp(a, 8), pad, pad);
        col.addView(controls);
        int tc = Qvk3Ui.night(a) ? 0xFFEEEEEE : 0xFF222222;

        String[][] colours = {{"accent", "Акцентный цвет"}, {"page", "Фон экранов"}, {"content", "Карточки"}, {"header", "Шапка и нижняя панель"},
                {"text", "Основной текст"}, {"text2", "Дополнительный текст"}, {"separator", "Разделители"},
                {"incoming", "Входящие сообщения"}, {"outgoing", "Исходящие сообщения"}};
        for (String[] c : colours) {
            TextView row = Qvk3Ui.text(a, c[1], 15, tc, false);
            row.setMinHeight(Qvk3Ui.dp(a, 44));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOnClickListener(v -> pickColor(a, c[1], colour(cur[0], c[0]), col2 -> {
                cur[0] = cur[0].with(c[0], col2);
                render.run();
            }));
            controls.addView(row);
        }
        controls.addView(slider(a, tc, "Скругление карточек и кнопок, dp", cur[0].radius, 28, v -> {
            cur[0] = cur[0].with("radius", v);
            render.run();
        }));
        controls.addView(slider(a, tc, "Размер текста, % (90–125)", cur[0].textScale - 90, 35, v -> {
            cur[0] = cur[0].with("textScale", 90 + v);
            render.run();
        }));
        controls.addView(choice(a, tc, "Шрифт", Qvk3DesignTokens.ThemeFonts.TITLES, () -> indexOf(Qvk3DesignTokens.ThemeFonts.ALL, cur[0].font), i -> {
            cur[0] = cur[0].with("font", Qvk3DesignTokens.ThemeFonts.ALL[i]);
            render.run();
        }));
        controls.addView(choice(a, tc, "Плотность и отступы", new String[]{"Компактно", "Обычно", "Просторно"},
                () -> indexOf(new String[]{"compact", "normal", "comfortable"}, cur[0].density), i -> {
                    cur[0] = cur[0].with("density", new String[]{"compact", "normal", "comfortable"}[i]);
                    render.run();
                }));
        controls.addView(choice(a, tc, "Форма элементов (списки, кнопки, панели)", new String[]{"Плоская ВК", "iOS", "Material"},
                () -> indexOf(new String[]{"flat", "ios", "material"}, cur[0].components), i -> {
                    cur[0] = cur[0].with("components", new String[]{"flat", "ios", "material"}[i]);
                    render.run();
                }));
        controls.addView(choice(a, tc, "Фон экранов", new String[]{"Сплошной", "Градиент", "Точки", "Сетка"},
                () -> indexOf(new String[]{"solid", "gradient", "dots", "grid"}, cur[0].background), i -> {
                    cur[0] = cur[0].with("background", new String[]{"solid", "gradient", "dots", "grid"}[i]);
                    render.run();
                }));
        controls.addView(choice(a, tc, "Схема", new String[]{"Светлая", "Тёмная"}, () -> cur[0].dark ? 1 : 0, i -> {
            cur[0] = cur[0].with("dark", i == 1);
            render.run();
        }));
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
        TextView t = Qvk3Ui.text(a, label + ": " + options[current.get()], 15, tc, false);
        t.setMinHeight(Qvk3Ui.dp(a, 44));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setOnClickListener(v -> Qvk3Ui.dialog(a).setTitle(label).setSingleChoiceItems(options, current.get(), (d, i) -> {
            sink.set(i);
            t.setText(label + ": " + options[i]);
            d.dismiss();
        }).show());
        return t;
    }

    /** HEX input + a palette of swatches. */
    static void pickColor(Activity a, String title, int initial, IntSink sink) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Qvk3Ui.dp(a, 20), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 20), 0);
        EditText hex = new EditText(a);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setText(String.format("#%06X", initial & 0xFFFFFF));
        col.addView(hex);
        int[] swatches = {0xFF2688EB, 0xFF007AFF, 0xFF3F8AE0, 0xFF8B75CB, 0xFFFF2BD6, 0xFFE64646, 0xFFFF9500, 0xFFC07A4F, 0xFF4BB34B,
                0xFF00B3A4, 0xFF000000, 0xFF19191A, 0xFF2C2D2E, 0xFF818C99, 0xFFEBEDF0, 0xFFF6F7F9, 0xFFFFFFFF, 0xFFF7F1EA};
        LinearLayout grid = null;
        for (int i = 0; i < swatches.length; i++) {
            if (i % 6 == 0) {
                grid = new LinearLayout(a);
                col.addView(grid);
            }
            final int c = swatches[i];
            View v = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setColor(c);
            g.setCornerRadius(Qvk3Ui.dp(a, 18));
            g.setStroke(Qvk3Ui.dp(a, 1), 0x33888888);
            v.setBackground(g);
            v.setContentDescription(String.format("Цвет #%06X", c & 0xFFFFFF));
            v.setOnClickListener(x -> hex.setText(String.format("#%06X", c & 0xFFFFFF)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 36), Qvk3Ui.dp(a, 36));
            lp.setMargins(Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 4));
            grid.addView(v, lp);
        }
        Qvk3Ui.dialog(a).setTitle(title).setView(col).setPositiveButton("OK", (d, w) -> {
            try {
                sink.set(Color.parseColor(hex.getText().toString().trim()));
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
