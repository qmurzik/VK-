package re.sova.qmods;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * "Оформление": one screen for styles and the visual editor.
 * Pinned live preview on top, a carousel of style cards, then every token in grouped cards. Nothing is
 * applied until "Применить"; the draft survives rotation.
 */
public final class Qvk3StylesActivity extends Activity {
    private Qvk3Ui.Palette p;
    private Qvk3DesignTokens draft;
    private boolean previewDark;
    private FrameLayout preview;
    private LinearLayout carousel, controls;
    private TextView apply;

    private int dp(float v) {
        return Qvk3Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean dark = Qvk3Ui.night(this);
        int accent = Qvk3Ui.accent(this);
        p = dark ? new Qvk3Ui.Palette(true, 0xFF0E0F12, 0xFF1C1D21, 0xFF2A2B30, 0xFFF2F3F5, 0xFF8E9199, accent, Color.WHITE, 0xFF2C2D32)
                : new Qvk3Ui.Palette(false, 0xFFF2F3F7, Color.WHITE, 0xFFE9EAEE, 0xFF0B0B0D, 0xFF7C7F87, accent, Color.WHITE, 0xFFE4E5EA);
        Qvk3Ui.bars(this, p);
        draft = Qvk3Appearance.current(this);
        previewDark = Qvk3Appearance.vkDark(this);
        if (state != null) {
            try {
                draft = Qvk3DesignTokens.fromJson(new JSONObject(state.getString("draft", "{}")), draft);
                previewDark = state.getBoolean("dark", previewDark);
            } catch (Exception ignored) {
                // keep the current style
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setFitsSystemWindows(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(6), dp(12), dp(4));
        head.addView(Qvk3Ui.icon(this, Qvk3Icons.BACK, p.text, 48, 24, "Назад", v -> finish()));
        TextView title = Qvk3Ui.line(this, "Оформление", 26, p.text, true);
        title.setAccessibilityHeading(true);
        title.setPadding(dp(8), 0, 0, 0);
        head.addView(title, Qvk3Ui.weight(1));
        TextView mode = Qvk3Ui.text(this, "", 13, p.accent, true);
        mode.setPadding(dp(12), dp(8), dp(12), dp(8));
        mode.setBackground(Qvk3Ui.ripple(this, p.dark ? 0xFF2A2C33 : 0xFFE4EAF5, 0x33888888, dp(14)));
        mode.setOnClickListener(v -> {
            previewDark = !previewDark;
            mode.setText(previewDark ? "Тёмная тема" : "Светлая тема");
            render();
        });
        mode.setText(previewDark ? "Тёмная тема" : "Светлая тема");
        mode.setContentDescription("Переключить предпросмотр: светлая или тёмная тема ВК");
        head.addView(mode);
        root.addView(head, Qvk3Ui.match());

        // pinned preview
        preview = new FrameLayout(this);
        preview.setBackground(Qvk3Ui.rounded(p.surface, dp(22)));
        preview.setClipToOutline(true);
        preview.setElevation(dp(3));
        LinearLayout.LayoutParams pl = Qvk3Ui.match();
        pl.setMargins(dp(16), dp(4), dp(16), dp(10));
        root.addView(preview, pl);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(24));
        scroll.addView(body);

        body.addView(caption("Готовые стили"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(dp(12), 0, dp(12), 0);
        carousel = new LinearLayout(this);
        hs.addView(carousel);
        body.addView(hs);

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(16), 0, dp(16), 0);
        body.addView(controls);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // sticky actions
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(16), dp(10), dp(16), dp(12));
        bar.setBackgroundColor(p.bg);
        TextView reset = button("Сбросить", false);
        reset.setOnClickListener(v -> Qvk3Ui.dialog(this).setTitle("Сбросить всё оформление?")
                .setMessage("Тема и стили вернутся к стандартным. Остальные настройки и данные не изменятся.")
                .setPositiveButton("Сбросить", (d, w) -> {
                    Qvk3Appearance.reset(this);
                    finish();
                }).setNegativeButton("Отмена", null).show());
        bar.addView(reset, Qvk3Ui.weight(1));
        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        apply = button("Применить", true);
        apply.setOnClickListener(v -> {
            if (!Qvk3Appearance.hasOwnTheme(this)) {
                applyStyle(true);
                return;
            }
            // the user has their own theme: say plainly what the style will do to it
            Qvk3Ui.dialog(this).setTitle("Применить «" + draft.title + "»")
                    .setMessage("У вас своя тема с цветами. Стиль может заменить её своими цветами (вашу тему можно вернуть кнопкой «Отменить» в настройках) или поменять только форму, скругления и шрифты, оставив ваши цвета.")
                    .setPositiveButton("Стиль с цветами", (d, w) -> applyStyle(true))
                    .setNeutralButton("Только форма", (d, w) -> applyStyle(false))
                    .setNegativeButton("Отмена", null).show();
        });
        bar.addView(apply, Qvk3Ui.weight(2));
        root.addView(bar, Qvk3Ui.match());
        setContentView(root);
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("draft", draft.toJson().toString());
        out.putBoolean("dark", previewDark);
    }

    private TextView caption(String s) {
        TextView t = Qvk3Ui.text(this, s.toUpperCase(java.util.Locale.ROOT), 12, p.text2, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(32), dp(18), dp(16), dp(8));
        t.setAccessibilityHeading(true);
        return t;
    }

    private void applyStyle(boolean colors) {
        try {
            Qvk3Appearance.apply(this, draft, colors);
            Qvk3Ui.toast(this, "Стиль «" + draft.title + "» применён" + (colors ? "" : " без замены цветов"));
            finish();
        } catch (Exception e) {
            Qvk3Ui.toast(this, "Не удалось применить: " + Qvk3Jobs.message(e));
        }
    }

    private TextView button(String label, boolean primary) {
        TextView b = Qvk3Ui.text(this, label, 16, primary ? Color.WHITE : p.text, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(50));
        b.setBackground(Qvk3Ui.ripple(this, primary ? p.accent : p.surface2, 0x33888888, dp(14)));
        return b;
    }

    private void set(String field, Object value) {
        draft = draft.with(field, value);
        render();
    }

    /** Re-renders preview, carousel selection and controls from the draft. */
    private void render() {
        Qvk3DesignTokens shown = draft.variant(previewDark);
        preview.removeAllViews();
        preview.addView(Qvk3StyleEditor.mock(this, shown));

        carousel.removeAllViews();
        for (Qvk3DesignTokens t : Qvk3DesignTokens.PRESETS.values()) carousel.addView(styleCard(t));

        controls.removeAllViews();
        // colours
        controls.addView(captionIn("Цвета"));
        LinearLayout colors = Qvk3Ui.card(this, p);
        colorRow(colors, "Акцент", "accent", draft.accent);
        Qvk3DesignTokens v = draft.variant(previewDark);
        if (v == draft) {
            // the edited variant matches the previewed VK theme: its surfaces can be edited directly
            colorRow(colors, "Фон экранов", "page", draft.page);
            colorRow(colors, "Карточки", "content", draft.content);
            colorRow(colors, "Шапка и нижняя панель", "header", draft.header);
            colorRow(colors, "Основной текст", "text", draft.text);
            colorRow(colors, "Дополнительный текст", "text2", draft.text2);
            colorRow(colors, "Входящие сообщения", "incoming", draft.incoming);
            colorRow(colors, "Исходящие сообщения", "outgoing", draft.outgoing);
        } else {
            TextView note = Qvk3Ui.text(this, "Для " + (previewDark ? "тёмной" : "светлой") + " темы ВК фон и текст подбираются автоматически из акцента. "
                    + "Чтобы задать их вручную, переключите предпросмотр на " + (draft.dark ? "тёмную" : "светлую") + " тему.", 13, p.text2, false);
            note.setPadding(dp(16), dp(10), dp(16), dp(12));
            colors.addView(note);
        }
        controls.addView(colors);

        controls.addView(captionIn("Форма и размер"));
        LinearLayout shape = Qvk3Ui.card(this, p);
        slider(shape, "Скругление", draft.radius + " dp", draft.radius, 28, x -> set("radius", x));
        slider(shape, "Размер текста", draft.textScale + "%", draft.textScale - 90, 35, x -> set("textScale", 90 + x));
        segmented(shape, "Плотность", new String[]{"Компактно", "Обычно", "Просторно"}, new String[]{"compact", "normal", "comfortable"}, draft.density, x -> set("density", x));
        controls.addView(shape);

        controls.addView(captionIn("Шрифт"));
        LinearLayout font = Qvk3Ui.card(this, p);
        chips(font, Qvk3DesignTokens.ThemeFonts.TITLES, Qvk3DesignTokens.ThemeFonts.ALL, draft.font, x -> set("font", x));
        controls.addView(font);

        controls.addView(captionIn("Элементы и фон"));
        LinearLayout comp = Qvk3Ui.card(this, p);
        segmented(comp, "Вид списков и кнопок", new String[]{"ВК", "iOS", "Material"}, new String[]{"flat", "ios", "material"}, draft.components, x -> set("components", x));
        segmented(comp, "Фон экранов", new String[]{"Сплошной", "Градиент", "Точки", "Сетка"}, new String[]{"solid", "gradient", "dots", "grid"}, draft.background, x -> set("background", x));
        controls.addView(comp);

        boolean changed = !draft.toJson().toString().equals(Qvk3Appearance.current(this).toJson().toString())
                || !Qvk3MusicSettings.prefs(this).contains(Qvk3Appearance.STYLE);
        apply.setAlpha(changed ? 1f : 0.5f);
        apply.setText(changed ? "Применить «" + draft.title + "»" : "Уже применено");
    }

    private TextView captionIn(String s) {
        TextView t = caption(s);
        t.setPadding(dp(16), dp(20), dp(16), dp(8));
        return t;
    }

    private View styleCard(Qvk3DesignTokens t) {
        Qvk3DesignTokens v = t.variant(previewDark).checked();
        boolean selected = t.id.equals(draft.id);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        // miniature: page with a card, an accent pill and two bubbles
        FrameLayout mini = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Qvk3DesignTokens.over(v.page, 0xFF000000));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(selected ? 3 : 1), selected ? p.accent : p.divider);
        mini.setBackground(bg);
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setPadding(dp(10), dp(10), dp(10), dp(10));
        float r = dp(Math.max(2, v.radius * 0.45f));
        View c1 = new View(this);
        c1.setBackground(Qvk3Ui.rounded(Qvk3DesignTokens.over(v.content, Qvk3DesignTokens.over(v.page, 0xFF000000)), r));
        in.addView(c1, new LinearLayout.LayoutParams(-1, dp(30)));
        View pill = new View(this);
        pill.setBackground(Qvk3Ui.rounded(v.accent, r));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(46), dp(14));
        pp.topMargin = dp(8);
        in.addView(pill, pp);
        View b1 = new View(this);
        b1.setBackground(Qvk3Ui.rounded(Qvk3DesignTokens.over(v.incoming, Qvk3DesignTokens.over(v.page, 0xFF000000)), r));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(52), dp(14));
        bp.topMargin = dp(8);
        in.addView(b1, bp);
        View b2 = new View(this);
        b2.setBackground(Qvk3Ui.rounded(Qvk3DesignTokens.over(v.outgoing, Qvk3DesignTokens.over(v.page, 0xFF000000)), r));
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(dp(44), dp(14));
        bp2.topMargin = dp(5);
        bp2.gravity = Gravity.END;
        in.addView(b2, bp2);
        mini.addView(in);
        card.addView(mini, new LinearLayout.LayoutParams(dp(104), dp(124)));
        TextView name = Qvk3Ui.line(this, t.title.replace("QVK ", ""), 13, selected ? p.accent : p.text, selected);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(6), 0, 0);
        card.addView(name, new LinearLayout.LayoutParams(dp(104), -2));
        LinearLayout.LayoutParams lp = Qvk3Ui.wrap();
        lp.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(lp);
        card.setContentDescription(t.title + ". " + t.description + (selected ? ". Выбран" : ""));
        card.setOnClickListener(x -> {
            draft = t;
            render();
        });
        return card;
    }

    private void divider(LinearLayout card) {
        if (card.getChildCount() == 0) return;
        View d = new View(this);
        d.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.6f)));
        lp.leftMargin = dp(16);
        card.addView(d, lp);
    }

    private void colorRow(LinearLayout card, String label, String field, int color) {
        divider(card);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(52));
        r.setPadding(dp(16), dp(6), dp(16), dp(6));
        r.setBackground(Qvk3Ui.ripple(this, 0, 0x22888888, 0));
        r.addView(Qvk3Ui.text(this, label, 16, p.text, false), Qvk3Ui.weight(1));
        TextView hex = Qvk3Ui.text(this, String.format("#%06X", color & 0xFFFFFF), 13, p.text2, false);
        hex.setPadding(0, 0, dp(10), 0);
        r.addView(hex);
        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color | 0xFF000000);
        g.setStroke(dp(1), p.divider);
        dot.setBackground(g);
        r.addView(dot, new LinearLayout.LayoutParams(dp(28), dp(28)));
        r.setContentDescription(label + ", " + hex.getText());
        r.setOnClickListener(x -> Qvk3StyleEditor.pickColor(this, label, color, c -> set(field, c | 0xFF000000)));
        card.addView(r, Qvk3Ui.match());
    }

    interface IntSink {
        void set(int v);
    }

    interface StrSink {
        void set(String v);
    }

    private void slider(LinearLayout card, String label, String value, int progress, int max, IntSink sink) {
        divider(card);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(6));
        LinearLayout top = new LinearLayout(this);
        top.addView(Qvk3Ui.text(this, label, 16, p.text, false), Qvk3Ui.weight(1));
        TextView val = Qvk3Ui.text(this, value, 14, p.text2, false);
        top.addView(val);
        col.addView(top);
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(progress);
        s.setContentDescription(label);
        Qvk3Ui.styleSeek(this, s, p.accent, p.dark ? 0xFF3A3B40 : 0xFFDCDDE2);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int v, boolean user) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                sink.set(sb.getProgress()); // re-render once, when the finger is lifted
            }
        });
        col.addView(s);
        card.addView(col, Qvk3Ui.match());
    }

    /** iOS-like segmented control. */
    private void segmented(LinearLayout card, String label, String[] titles, String[] values, String current, StrSink sink) {
        divider(card);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(12));
        col.addView(Qvk3Ui.text(this, label, 16, p.text, false));
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(Qvk3Ui.rounded(p.surface2, dp(11)));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < titles.length; i++) {
            final String val = values[i];
            boolean sel = val.equals(current);
            TextView t = Qvk3Ui.line(this, titles[i], 13, p.text, sel);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(36));
            if (sel) {
                t.setBackground(Qvk3Ui.rounded(p.dark ? 0xFF4A4B52 : Color.WHITE, dp(9)));
                t.setElevation(dp(1));
            }
            t.setContentDescription(titles[i] + (sel ? ", выбрано" : ""));
            t.setOnClickListener(x -> sink.set(val));
            seg.addView(t, Qvk3Ui.weight(1));
        }
        LinearLayout.LayoutParams lp = Qvk3Ui.match();
        lp.topMargin = dp(8);
        col.addView(seg, lp);
        card.addView(col, Qvk3Ui.match());
    }

    /** Wrapping chip group (fonts). */
    private void chips(LinearLayout card, String[] titles, String[] values, String current, StrSink sink) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(10), dp(12), dp(10));
        int perRow = 3;
        LinearLayout row = null;
        List<LinearLayout> rows = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                rows.add(row);
                col.addView(row, Qvk3Ui.match());
            }
            final String val = values[i];
            boolean sel = val.equals(current);
            TextView t = Qvk3Ui.line(this, titles[i], 13, sel ? Color.WHITE : p.text, sel);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(40));
            t.setBackground(Qvk3Ui.ripple(this, sel ? p.accent : p.surface2, 0x33888888, dp(12)));
            t.setTypeface(android.graphics.Typeface.create(val.contains("serif") ? "serif" : val.contains("mono") ? "monospace"
                    : val.equals("condensed") ? "sans-serif-condensed" : "sans-serif", sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            t.setOnClickListener(x -> sink.set(val));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(t, lp);
        }
        // pad the last row so chips keep equal widths
        if (row != null) for (int i = row.getChildCount(); i < perRow; i++) {
            View sp = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(sp, lp);
        }
        card.addView(col, Qvk3Ui.match());
    }
}
