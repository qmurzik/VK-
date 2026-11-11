package re.sova.qmods;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * "Настроить элемент на экране": instead of typing element names, the user taps any element of a real VK screen and
 * restyles it on the spot — hide it, round its corners, fade it, give it a background colour, add space around it.
 * Changes are ordinary skin rules (Qvk3Skin), so they apply to every element with that name (all posts, all rows),
 * survive restarts and can be edited or removed in «Правила интерфейса». Nothing is sent anywhere.
 *
 * Armed from the settings centre; it opens on the next VK screen the user goes to. "Шире" walks up to the enclosing
 * element when the touched one is too small (a text inside a card → the card).
 */
final class Qvk3ElementPicker extends FrameLayout {
    private static boolean armed;

    static void arm(Activity from) {
        armed = true;
        Qvk3Ui.toast(from, "Перейдите на нужный экран ВК и коснитесь элемента, который хотите изменить");
        from.finish();
    }

    /** Called by Qvk3Skin when a VK screen resumes. */
    static void maybeStart(Activity a) {
        if (!armed) return;
        armed = false;
        View decor = a.getWindow() == null ? null : a.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        decor.postDelayed(() -> {
            if (a.isFinishing() || a.isDestroyed()) return;
            Qvk3ElementPicker p = new Qvk3ElementPicker(a);
            ((ViewGroup) decor).addView(p, new FrameLayout.LayoutParams(-1, -1));
        }, 350);
    }

    private final Activity a;
    private final Qvk3Ui.Palette p;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final TextView hint, title;
    private final LinearLayout panel;
    private View target;
    private String name;

    private Qvk3ElementPicker(Activity a) {
        super(a);
        this.a = a;
        this.p = Qvk3Ui.palette(a, "modern");
        setWillNotDraw(false);
        setElevation(dp(40));
        setClickable(true);
        fill.setColor((p.accent & 0xFFFFFF) | 0x33000000);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(p.accent);

        hint = Qvk3Ui.text(a, "Коснитесь элемента, который хотите изменить", 14, Color.WHITE, true);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(16), dp(10), dp(16), dp(10));
        hint.setBackground(Qvk3Ui.rounded(0xE6202226, dp(18)));
        LayoutParams hl = new LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hl.topMargin = dp(56);
        addView(hint, hl);

        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.dark ? 0xF21C1D21 : 0xF7FFFFFF);
        bg.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        panel.setBackground(bg);
        panel.setElevation(dp(16));
        panel.setClickable(true);
        title = Qvk3Ui.line(a, "", 15, p.text, true);
        panel.addView(title, Qvk3Ui.match());
        panel.setVisibility(GONE);
        addView(panel, new LayoutParams(-1, -2, Gravity.BOTTOM));

        // a close button that is always reachable
        TextView close = Qvk3Ui.text(a, "Готово", 14, Color.WHITE, true);
        close.setPadding(dp(14), dp(8), dp(14), dp(8));
        close.setBackground(Qvk3Ui.rounded(p.accent, dp(16)));
        close.setOnClickListener(v -> finish());
        LayoutParams cl = new LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cl.topMargin = dp(10);
        cl.rightMargin = dp(12);
        addView(close, cl);
    }

    private int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }

    private void finish() {
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        Qvk3Ui.toast(a, "Изменения сохранены. Их можно поправить в «Правила интерфейса»");
    }

    // ------------------------------------------------------------------ picking

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            View root = getRootView();
            View hit = deepestNamed(root, (int) e.getRawX(), (int) e.getRawY());
            if (hit != null) select(hit);
        }
        return true; // VK below does not react while picking
    }

    /** The innermost visible view under the point that has a resource name (only those can carry a rule). */
    private View deepestNamed(View v, int x, int y) {
        if (v == this || v.getVisibility() != VISIBLE) return null;
        Rect r = new Rect();
        if (!v.getGlobalVisibleRect(r) || !r.contains(x, y)) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View found = deepestNamed(g.getChildAt(i), x, y);
                if (found != null) return found;
            }
        }
        return nameOf(v) != null ? v : null;
    }

    private String nameOf(View v) {
        int id = v.getId();
        if (id == View.NO_ID || (id >>> 24) != 0x7f) return null; // app resources only, not android.R ids
        try {
            String n = v.getResources().getResourceEntryName(id);
            return n != null && n.matches("[A-Za-z0-9_]{1,80}") ? n : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void select(View v) {
        target = v;
        name = nameOf(v);
        Rect r = new Rect();
        v.getGlobalVisibleRect(r);
        int[] me = new int[2];
        getLocationOnScreen(me);
        int[] it = new int[2];
        v.getLocationOnScreen(it);
        box.set(it[0] - me[0], it[1] - me[1], it[0] - me[0] + v.getWidth(), it[1] - me[1] + v.getHeight());
        hint.setVisibility(GONE);
        buildPanel();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (target == null) return;
        c.drawRoundRect(box, dp(6), dp(6), fill);
        c.drawRoundRect(box, dp(6), dp(6), stroke);
    }

    // ------------------------------------------------------------------ actions

    private Qvk3Skin.Rule rule(List<Qvk3Skin.Rule> rules) {
        for (Qvk3Skin.Rule r : rules) if (r.id.equals(name)) return r;
        Qvk3Skin.Rule r = new Qvk3Skin.Rule();
        r.id = name;
        rules.add(r);
        return r;
    }

    private interface Change {
        void on(Qvk3Skin.Rule r);
    }

    private void change(Change c) {
        List<Qvk3Skin.Rule> rules = Qvk3Skin.rules(a);
        c.on(rule(rules));
        Qvk3Skin.saveRules(a, rules);
        Qvk3Skin.refresh();
        postDelayed(this::buildPanel, 120);
    }

    private void buildPanel() {
        if (name == null) return;
        while (panel.getChildCount() > 1) panel.removeViewAt(1);
        int same = Qvk3Skin.findAll(a, name).size();
        Qvk3Skin.Rule cur = null;
        for (Qvk3Skin.Rule r : Qvk3Skin.rules(a)) if (r.id.equals(name)) cur = r;
        title.setText(friendly(name) + (same > 1 ? "  ·  таких на экране: " + same : ""));
        TextView sub = Qvk3Ui.line(a, "#" + name + "  ·  " + target.getWidth() + "×" + target.getHeight() + " px", 12, p.text2, false);
        panel.addView(sub, Qvk3Ui.match());

        final Qvk3Skin.Rule r = cur;
        LinearLayout row = chips();
        row.addView(chip(r != null && r.hide ? "Показать" : "Скрыть", () -> change(x -> x.hide = !x.hide)));
        int[] radii = {-1, 8, 16, 24};
        int ri = 0;
        for (int i = 0; i < radii.length; i++) if (r != null && r.radius == radii[i]) ri = i;
        final int nextR = radii[(ri + 1) % radii.length];
        row.addView(chip("Скругление: " + (r == null || r.radius < 0 ? "нет" : r.radius + " dp"), () -> change(x -> x.radius = nextR)));
        int[] ops = {100, 80, 60, 40};
        int oi = 0;
        for (int i = 0; i < ops.length; i++) if (r != null && r.opacity == ops[i]) oi = i;
        final int nextO = ops[(oi + 1) % ops.length];
        row.addView(chip("Прозрачность: " + (r == null ? 100 : r.opacity) + "%", () -> change(x -> x.opacity = nextO)));
        int[] ms = {-1, 4, 8, 12};
        int mi = 0;
        for (int i = 0; i < ms.length; i++) if (r != null && r.margin == ms[i]) mi = i;
        final int nextM = ms[(mi + 1) % ms.length];
        row.addView(chip("Отступ: " + (r == null || r.margin < 0 ? "нет" : r.margin + " dp"), () -> change(x -> x.margin = nextM)));
        panel.addView(scroll(row), Qvk3Ui.match());

        LinearLayout colors = chips();
        int[] palette = {0, 0xFF1C1C1E, 0xFF2C2C2E, 0xFFF2F2F7, 0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2};
        for (int c : palette) colors.addView(swatch(c, r != null && r.bg == c, () -> change(x -> x.bg = c)));
        panel.addView(scroll(colors), Qvk3Ui.match());

        LinearLayout bottom = chips();
        View parentNamed = parentNamed(target);
        if (parentNamed != null) bottom.addView(chip("Шире ↑", () -> select(parentNamed)));
        if (r != null) bottom.addView(chip("Вернуть как было", () -> {
            List<Qvk3Skin.Rule> rules = Qvk3Skin.rules(a);
            rules.removeIf(x -> x.id.equals(name));
            Qvk3Skin.saveRules(a, rules);
            Qvk3Skin.refresh();
            postDelayed(this::buildPanel, 120);
        }));
        bottom.addView(chip("Выбрать другой", () -> {
            target = null;
            panel.setVisibility(GONE);
            hint.setVisibility(VISIBLE);
            invalidate();
        }));
        panel.addView(scroll(bottom), Qvk3Ui.match());
        panel.setVisibility(VISIBLE);
    }

    private View parentNamed(View v) {
        for (android.view.ViewParent q = v.getParent(); q instanceof View; q = q.getParent()) {
            if (nameOf((View) q) != null && q != getRootView()) return (View) q;
        }
        return null;
    }

    /** A few VK ids turned into words people recognise; the rest show the technical name. */
    static String friendly(String id) {
        if (id.contains("bottom_navigation")) return "Нижняя панель";
        if (id.contains("toolbar")) return "Шапка экрана";
        if (id.contains("writebar") || id.contains("write_bar")) return "Поле ввода сообщения";
        if (id.contains("likes") || id.contains("footer")) return "Кнопки под записью";
        if (id.contains("header")) return "Заголовок";
        if (id.contains("avatar") || id.contains("photo")) return "Фото";
        if (id.contains("fab")) return "Плавающая кнопка";
        if (id.contains("divider") || id.contains("separator") || id.contains("shadow")) return "Разделитель";
        if (id.contains("story") || id.contains("stories")) return "Истории";
        if (id.contains("dialog")) return "Список диалогов";
        if (id.contains("msg") || id.contains("message")) return "Сообщения";
        if (id.contains("post") || id.contains("wall")) return "Запись в ленте";
        if (id.contains("card")) return "Карточка";
        if (id.contains("button") || id.contains("btn")) return "Кнопка";
        if (id.contains("title") || id.contains("name")) return "Название";
        if (id.contains("search")) return "Поиск";
        if (id.contains("tab")) return "Вкладки";
        return "Элемент";
    }

    private LinearLayout chips() {
        LinearLayout l = new LinearLayout(a);
        l.setPadding(0, dp(10), 0, 0);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private View scroll(LinearLayout row) {
        HorizontalScrollView h = new HorizontalScrollView(a);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(row);
        return h;
    }

    private TextView chip(String label, Runnable r) {
        TextView t = Qvk3Ui.text(a, label, 13, p.text, true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(Qvk3Ui.ripple(a, p.dark ? 0xFF2C2D33 : 0xFFEDEEF2, 0x33888888, dp(14)));
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private View swatch(int color, boolean selected, Runnable r) {
        View v = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color == 0 ? Color.TRANSPARENT : color);
        g.setStroke(dp(selected ? 3 : 1), selected ? p.accent : 0x66888888);
        v.setBackground(g);
        v.setContentDescription(color == 0 ? "Без цвета" : String.format("Цвет #%06X", color & 0xFFFFFF));
        v.setOnClickListener(x -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
        lp.rightMargin = dp(10);
        v.setLayoutParams(lp);
        return v;
    }
}
