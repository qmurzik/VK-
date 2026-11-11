package re.sova.qmods;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** iOS-messenger look of the QVK shell: palette and shared pieces (avatars, badges). */
final class Qvk3Ios {
    final boolean dark;
    final int bg, bar, text, text2, sep, tint = 0xFF007AFF, bubbleIn, field, red = 0xFFFF3B30, green = 0xFF34C759;

    Qvk3Ios(Context c) {
        dark = Qvk3Ui.night(c);
        bg = dark ? 0xFF000000 : 0xFFFFFFFF;
        bar = dark ? 0xF21C1C1E : 0xF2F9F9F9;
        text = dark ? 0xFFFFFFFF : 0xFF000000;
        text2 = dark ? 0xFF8E8E93 : 0xFF8A8A8E;
        sep = dark ? 0xFF38383A : 0xFFD1D1D6;
        bubbleIn = dark ? 0xFF262629 : 0xFFE9E9EB;
        field = dark ? 0xFF1C1C1E : 0xFFEFEFF4;
    }

    void bars(Activity a) {
        Qvk3Ui.bars(a, new Qvk3Ui.Palette(dark, bg, bg, bg, text, text2, tint, Color.WHITE, sep));
    }

    static final int[] AVATAR = {0xFFFF9500, 0xFF34C759, 0xFF5AC8FA, 0xFF007AFF, 0xFFAF52DE, 0xFFFF2D55, 0xFF5856D6};

    /** Round avatar: photo, or initials on a coloured circle while it loads / when there is none. */
    FrameLayout avatar(Context c, String title, String url, int sizeDp, long seed) {
        FrameLayout f = new FrameLayout(c);
        int s = Qvk3Ui.dp(c, sizeDp);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Qvk3DesignTokens.mix(AVATAR[(int) (Math.abs(seed) % AVATAR.length)], 0xFFFFFFFF, 0.25f), AVATAR[(int) (Math.abs(seed) % AVATAR.length)]});
        g.setShape(GradientDrawable.OVAL);
        f.setBackground(g);
        String ini = "";
        for (String w : title.trim().split("\\s+")) if (!w.isEmpty() && ini.length() < 2) ini += w.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        TextView t = Qvk3Ui.text(c, ini, sizeDp * 0.38f, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        f.addView(t, new FrameLayout.LayoutParams(-1, -1));
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        f.setLayoutParams(new android.widget.LinearLayout.LayoutParams(s, s));
        f.setTag(url);
        if (url != null && !url.isEmpty()) Qvk3Images.get(c).load(url, 144, (Bitmap b) -> {
            if (b != null && url.equals(f.getTag())) {
                iv.setImageDrawable(Qvk3Ui.roundedBitmap(b, s / 2f));
                t.setVisibility(android.view.View.GONE);
            }
        });
        f.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return f;
    }
}
