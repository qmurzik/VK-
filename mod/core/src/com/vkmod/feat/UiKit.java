package com.vkmod.feat;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vkmod.theme.ColorSlot;
import com.vkmod.theme.Palette;
import com.vkmod.theme.ThemeEngine;

/** Small programmatic-UI toolkit. The mod's own screens take their colours from the active theme. */
public final class UiKit {
    private UiKit() {}

    public static final class Colors {
        public int bg, card, text, text2, accent, sep, field, onAccent;
    }

    /** Colours for the mod screens: effective palette of the current brightness over a neutral base. */
    public static Colors colors(Context ctx) {
        boolean dark = (ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Colors c = new Colors();
        if (dark) {
            c.bg = 0xFF121212; c.card = 0xFF1E1E1E; c.text = 0xFFE6E6E6; c.text2 = 0xFF9A9A9A; c.accent = 0xFF71AAEB; c.sep = 0xFF2C2C2C; c.field = 0xFF2A2A2A;
        } else {
            c.bg = 0xFFF0F2F5; c.card = 0xFFFFFFFF; c.text = 0xFF111111; c.text2 = 0xFF6B6B6B; c.accent = 0xFF2688EB; c.sep = 0xFFE3E4E8; c.field = 0xFFEDEEF0;
        }
        try {
            if (com.vkmod.core.Registry.isOn("theme_engine")) {
                Palette p = ThemeEngine.effective(dark);
                Integer v;
                if ((v = p.get(ColorSlot.BACKGROUND)) != null) c.bg = v;
                if ((v = p.get(ColorSlot.CARD)) != null) c.card = v;
                if ((v = p.get(ColorSlot.TEXT)) != null) c.text = v;
                if ((v = p.get(ColorSlot.TEXT_SECONDARY)) != null) c.text2 = v;
                if ((v = p.get(ColorSlot.ACCENT)) != null) c.accent = v;
                if ((v = p.get(ColorSlot.SEPARATOR)) != null) c.sep = v;
                if ((v = p.get(ColorSlot.FIELD)) != null) c.field = v;
            }
        } catch (Throwable ignored) { }
        c.onAccent = Color.red(c.accent) * 0.299 + Color.green(c.accent) * 0.587 + Color.blue(c.accent) * 0.114 > 150 ? 0xFF000000 : 0xFFFFFFFF;
        return c;
    }

    public static int dp(Context c, float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics())); }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static LinearLayout vbox(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public static LinearLayout hbox(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    public static ViewGroup.MarginLayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static TextView button(Context c, String label, Colors col, View.OnClickListener l) {
        TextView b = text(c, label, 14, col.onAccent, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), round(col.accent, 10, c), null));
        b.setOnClickListener(l);
        return b;
    }

    public static void applyStatusBar(Activity a, Colors col) {
        try {
            a.getWindow().setStatusBarColor(col.bg);
            a.getWindow().setNavigationBarColor(col.bg);
            boolean light = Color.red(col.bg) * 0.299 + Color.green(col.bg) * 0.587 + Color.blue(col.bg) * 0.114 > 150;
            int f = a.getWindow().getDecorView().getSystemUiVisibility();
            f = light ? (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) : (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            a.getWindow().getDecorView().setSystemUiVisibility(f);
        } catch (Throwable ignored) { }
    }
}
