package re.sova.qmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared view toolkit and palette of the QVK 3.0 screens (QVK Music, settings centre, updates).
 * Everything is built in code so no resources have to be added to VK's resource table.
 * Text sizes use SP so system font scaling is respected; touch targets are at least 48 dp.
 */
public final class Qvk3Ui {
    public static final class Palette {
        public final int bg, surface, surface2, text, text2, accent, onAccent, divider;
        public final boolean dark;

        Palette(boolean dark, int bg, int surface, int surface2, int text, int text2, int accent, int onAccent, int divider) {
            this.dark = dark;
            this.bg = bg;
            this.surface = surface;
            this.surface2 = surface2;
            this.text = text;
            this.text2 = text2;
            this.accent = accent;
            this.onAccent = onAccent;
            this.divider = divider;
        }
    }

    public static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Accent from the QVK design tokens (falls back to VK blue). */
    public static int accent(Context c) {
        return Qvk3Appearance.current(c).accent;
    }

    public static Palette palette(Context c, String style) {
        int accent = accent(c);
        boolean dark = night(c);
        int onAccent = Qvk3DesignTokens.onColor(accent);
        switch (style) {
            case "amoled":
                return new Palette(true, Color.BLACK, Color.BLACK, 0xFF111111, Color.WHITE, 0xFF9A9A9A, accent, onAccent, 0xFF1C1C1C);
            case "glass":
                return dark ? new Palette(true, 0xFF0E1116, 0x33FFFFFF, 0x22FFFFFF, Color.WHITE, 0xCCFFFFFF, accent, onAccent, 0x22FFFFFF)
                        : new Palette(false, 0xFFEFF2F7, 0x99FFFFFF, 0x66FFFFFF, 0xFF0F1115, 0xFF5B6270, accent, onAccent, 0x22000000);
            case "classic":
                return dark ? new Palette(true, 0xFF19191A, 0xFF232324, 0xFF2C2D2E, 0xFFE1E3E6, 0xFF939393, accent, onAccent, 0xFF363738)
                        : new Palette(false, 0xFFEDEEF0, Color.WHITE, 0xFFF2F3F5, 0xFF000000, 0xFF818C99, accent, onAccent, 0xFFDCE1E6);
            default: // modern / coverflow
                return dark ? new Palette(true, 0xFF101114, 0xFF1A1C20, 0xFF24272C, 0xFFF2F3F5, 0xFF9AA0A8, accent, onAccent, 0xFF2A2D33)
                        : new Palette(false, 0xFFF6F7F9, Color.WHITE, 0xFFEFF1F4, 0xFF111214, 0xFF6D7380, accent, onAccent, 0xFFE3E6EA);
        }
    }

    public static int dp(Context c, float v) {
        return Qvk3Compat.dpi(c, v);
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView line(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = text(c, s, sp, color, bold);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static Drawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    public static Drawable ripple(Context c, int bg, int pressed, float radius) {
        Drawable content = bg == 0 ? null : rounded(bg, radius);
        Drawable mask = rounded(Color.WHITE, radius);
        return new RippleDrawable(ColorStateList.valueOf(pressed), content, mask);
    }

    /** Pill button with an accessible label and a 48dp minimum touch target. */
    public static TextView button(Context c, Palette p, String label, boolean filled, View.OnClickListener l) {
        TextView b = text(c, label, 15, filled ? p.onAccent : p.accent, true);
        b.setGravity(Gravity.CENTER);
        int h = dp(c, 16);
        b.setPadding(h, dp(c, 10), h, dp(c, 10));
        b.setMinHeight(dp(c, 44));
        b.setMinWidth(dp(c, 48));
        b.setBackground(ripple(c, filled ? p.accent : (p.dark ? 0x1FFFFFFF : 0x14000000), 0x33888888, dp(c, 22)));
        b.setOnClickListener(l);
        b.setContentDescription(label);
        return b;
    }

    /** Round icon-like control drawn with a text glyph (no drawable resources needed). */
    public static TextView glyph(Context c, Palette p, String glyph, String description, float sp, View.OnClickListener l) {
        TextView b = text(c, glyph, sp, p.text, false);
        b.setGravity(Gravity.CENTER);
        int s = dp(c, 52);
        b.setMinWidth(s);
        b.setMinHeight(s);
        b.setBackground(ripple(c, 0, 0x33888888, s / 2f));
        b.setOnClickListener(l);
        b.setContentDescription(description);
        return b;
    }

    public static void clipRound(View v, float radius) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setClipToOutline(radius > 0);
    }

    public static ImageView cover(Context c, Palette p, int sizeDp, float radiusDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(rounded(p.surface2, dp(c, radiusDp)));
        clipRound(iv, dp(c, radiusDp));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return iv;
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static AlertDialog.Builder dialog(Activity a) {
        return new AlertDialog.Builder(a, night(a) ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    public static void toast(Context c, String s) {
        Qvk3Jobs.main(() -> android.widget.Toast.makeText(c.getApplicationContext(), s, android.widget.Toast.LENGTH_LONG).show());
    }

    public static ColorDrawable color(int c) {
        return new ColorDrawable(c);
    }

    /** Applies the palette to system bars (status/navigation) with correct light/dark icons. */
    public static void bars(Activity a, Palette p) {
        android.view.Window w = a.getWindow();
        w.setStatusBarColor(p.bg);
        w.setNavigationBarColor(p.bg);
        int flags = w.getDecorView().getSystemUiVisibility();
        if (!p.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.getDecorView().setSystemUiVisibility(flags);
    }
}
