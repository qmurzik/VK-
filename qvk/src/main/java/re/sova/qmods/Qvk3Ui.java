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

    /**
     * Day or night for everything QVK draws: VK's actual theme (BUG-0039…0047). VK can be dark while the phone is light
     * (and back); deciding some parts by Android's night mode and others by VK's theme put light-theme text and
     * buttons on dark VK, with any style that has day and night variants. Falls back to Android's night mode.
     */
    public static boolean night(Context c) {
        try {
            return NativeTheme.dark(c);
        } catch (Throwable t) {
            return systemNight(c);
        }
    }

    /** Android's own night mode (only where the phone's setting itself is meant). */
    public static boolean systemNight(Context c) {
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
        // the same buttons as the QVK sheet: filled main action, tonal accent for the rest, 16dp corners, a press spring
        TextView b = text(c, label, 15, filled ? p.onAccent : p.accent, true);
        b.setGravity(Gravity.CENTER);
        b.setMaxLines(1);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int h = dp(c, 14);
        b.setPadding(h, dp(c, 12), h, dp(c, 12));
        b.setMinHeight(dp(c, 48));
        b.setMinWidth(dp(c, 48));
        b.setBackground(ripple(c, filled ? p.accent : (p.accent & 0x00FFFFFF) | 0x1F000000, filled ? 0x33FFFFFF : (p.accent & 0x00FFFFFF) | 0x33000000, dp(c, 16)));
        b.setOnClickListener(l);
        b.setContentDescription(label);
        Qvk3Motion.press(b);
        return b;
    }

    /** Label without decorative glyphs: leading «＋ ▶ ⬇ ☝ ⇪ ⇩ ★», trailing «→». Icons do that job now. */
    public static String plain(CharSequence label) {
        String s = label.toString().replaceAll("[→▶⬇＋☝⇪⇩★✓✗•]", "").replaceAll("\\s+·\\s+·\\s+", " · ").replaceAll("\\s{2,}", " ").trim();
        if (s.startsWith("· ")) s = s.substring(2);
        return s;
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

    /** Every QVK 3 dialog: the QVK bottom sheet (Qvk3Sheet) — VK theme colours, handle, readable text, proper buttons. */
    public static AlertDialog.Builder dialog(Activity a) {
        return new Qvk3Sheet.Builder(a);
    }

    public static void toast(Context c, String s) {
        Qvk3Jobs.main(() -> android.widget.Toast.makeText(c.getApplicationContext(), s, android.widget.Toast.LENGTH_LONG).show());
    }

    /** Round icon button with ripple, 48dp touch target, TalkBack label. */
    public static android.widget.ImageView icon(Context c, String name, int color, int boxDp, int iconDp, String description, View.OnClickListener l) {
        android.widget.ImageView v = new android.widget.ImageView(c);
        v.setImageDrawable(Qvk3Icons.of(name, color));
        int pad = dp(c, (boxDp - iconDp) / 2f);
        v.setPadding(pad, pad, pad, pad);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, boxDp), dp(c, boxDp)));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33888888), null, rounded(Color.WHITE, dp(c, boxDp) / 2f)));
        v.setContentDescription(description);
        v.setOnClickListener(l);
        return v;
    }

    public static void setIcon(android.widget.ImageView v, String name, int color) {
        v.setImageDrawable(Qvk3Icons.of(name, color));
    }

    /** Slim rounded progress track with a small round thumb. */
    public static void styleSeek(Context c, android.widget.SeekBar s, int active, int track) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(track);
        bg.setCornerRadius(dp(c, 3));
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(active);
        fg.setCornerRadius(dp(c, 3));
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(fg, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(new Drawable[]{bg, clip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        int h = dp(c, 4);
        layers.setLayerHeight(0, h);
        layers.setLayerHeight(1, h);
        layers.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        layers.setLayerGravity(1, Gravity.CENTER_VERTICAL);
        s.setProgressDrawable(layers);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(active);
        thumb.setSize(dp(c, 14), dp(c, 14));
        s.setThumb(thumb);
        s.setSplitTrack(false);
        s.setMinHeight(dp(c, 32));
        s.setMaxHeight(dp(c, 32));
    }

    /** Card container: rounded surface with optional title above it. */
    public static LinearLayout card(Context c, Palette p) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(p.surface, dp(c, 18)));
        l.setClipToOutline(true);
        return l;
    }

    /** Square rounded tile with a white icon, used as the leading icon of settings rows. */
    public static View tile(Context c, String icon, int color, int sizeDp) {
        android.widget.ImageView v = new android.widget.ImageView(c);
        v.setImageDrawable(Qvk3Icons.of(icon, Color.WHITE));
        int pad = dp(c, sizeDp * 0.2f);
        v.setPadding(pad, pad, pad, pad);
        v.setBackground(rounded(color, dp(c, sizeDp * 0.28f)));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** Bitmap drawable with rounded corners drawn by a shader (independent of outline clipping support). */
    public static Drawable roundedBitmap(final android.graphics.Bitmap b, final float radius) {
        return new Drawable() {
            final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
            final android.graphics.RectF r = new android.graphics.RectF();

            @Override
            public void draw(android.graphics.Canvas c) {
                android.graphics.Rect bounds = getBounds();
                android.graphics.BitmapShader sh = new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                android.graphics.Matrix m = new android.graphics.Matrix();
                float s = Math.max(bounds.width() / (float) b.getWidth(), bounds.height() / (float) b.getHeight());
                m.setScale(s, s);
                m.postTranslate(bounds.left + (bounds.width() - b.getWidth() * s) / 2f, bounds.top + (bounds.height() - b.getHeight() * s) / 2f);
                sh.setLocalMatrix(m);
                paint.setShader(sh);
                r.set(bounds);
                c.drawRoundRect(r, radius, radius, paint);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter cf) {
                paint.setColorFilter(cf);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    /** Average colour of a bitmap (for player backgrounds). */
    public static int average(android.graphics.Bitmap b) {
        if (b == null) return 0xFF3A3F4A;
        android.graphics.Bitmap s = android.graphics.Bitmap.createScaledBitmap(b, 12, 12, true);
        long r = 0, g = 0, bl = 0;
        for (int x = 0; x < 12; x++)
            for (int y = 0; y < 12; y++) {
                int px = s.getPixel(x, y);
                r += (px >> 16) & 0xff;
                g += (px >> 8) & 0xff;
                bl += px & 0xff;
            }
        return 0xFF000000 | ((int) (r / 144) << 16) | ((int) (g / 144) << 8) | (int) (bl / 144);
    }

    /** Darkened, saturated version of a colour suitable as a background under white text. */
    public static int deep(int color, float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.2f + 0.1f);
        hsv[2] = value;
        return Color.HSVToColor(hsv);
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
