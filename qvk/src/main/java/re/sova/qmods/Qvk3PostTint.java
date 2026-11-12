package re.sova.qmods;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.widget.ImageView;

/**
 * BUG-0011: «Цвет кнопок под записями» recoloured the like icon but not the repost and comment icons. Those two are
 * AppCompat ImageViews tinted with app:tint; AppCompat re-applies its own (support) tint on every state change, which
 * replaced the colour QVK set with setImageTintList. A colour filter is drawn on top of any tint, so QVK's colour now
 * wins. The filter is set only for QVK's own colour lists (disabled state = colour at 40 % alpha, as PostButtonColors
 * builds them) and removed for anything else, so restoring VK's colours also removes it. Called from patch 0015.
 */
public final class Qvk3PostTint {
    private Qvk3PostTint() {
    }

    public static void apply(ImageView v, ColorStateList list) {
        if (v == null) return;
        if (Qvk3Kill.off("post_tint")) {
            v.clearColorFilter();
            return;
        }
        if (isQvk(list)) v.setColorFilter(list.getDefaultColor(), PorterDuff.Mode.SRC_IN);
        else v.clearColorFilter();
    }

    static boolean isQvk(ColorStateList list) {
        if (list == null || !list.isStateful()) return false;
        int def = list.getDefaultColor();
        int disabled = list.getColorForState(new int[]{-android.R.attr.state_enabled}, def);
        return disabled == ((def & 0xFFFFFF) | 0x66000000);
    }
}
