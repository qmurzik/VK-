package re.sova.qmods;

import android.animation.TimeInterpolator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Motion for QVK screens: staggered entrance, slide between screens, press feedback. All through ViewPropertyAnimator, so
 * Android's «animation scale» (and «remove animations» in accessibility) applies automatically; on weak devices the
 * entrance is a plain fade.
 */
final class Qvk3Motion {
    static final TimeInterpolator OUT = new DecelerateInterpolator(2.2f);
    static final TimeInterpolator SPRING = new OvershootInterpolator(1.4f);

    private Qvk3Motion() {
    }

    /** Children of the container rise and fade in one after another (at most ~12 are delayed). */
    static void stagger(ViewGroup parent, boolean light) {
        if (Qvk3Kill.off(parent.getContext(), "settings_motion")) return;
        float dy = Qvk3Ui.dp(parent.getContext(), light ? 6 : 12);
        // BUG-0029: only what is on screen animates; everything below is shown as is. Cards below the screen that started
        // transparent could stay invisible on some firmware (HyperOS) — a whole screen of «empty space» under the items.
        int visibleBottom = parent.getRootView().getHeight();
        int[] at = new int[2];
        parent.getLocationInWindow(at);
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            v.animate().cancel();
            if (visibleBottom > 0 && at[1] + v.getTop() > visibleBottom) {
                v.setAlpha(1f);
                v.setTranslationY(0f);
                continue;
            }
            v.setAlpha(0f);
            v.setTranslationY(dy);
            v.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(i, 12) * (light ? 18L : 32L))
                    .setDuration(light ? 200 : 340).setInterpolator(OUT).start();
        }
    }

    /** The whole screen slides in from the right (forward) or the left (back). */
    static void slideIn(View v, boolean forward) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationX(Qvk3Ui.dp(v.getContext(), forward ? 36 : -36));
        v.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(OUT).start();
    }

    /** Pressed views sink a little and spring back; clicks keep working (the listener never consumes). */
    static void press(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.965f).scaleY(0.965f).setStartDelay(0).setDuration(90).setInterpolator(OUT).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0).setDuration(260).setInterpolator(SPRING).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** A short pop for something that just changed (a value, a badge). */
    static void pop(View v) {
        v.animate().cancel();
        v.setScaleX(0.86f);
        v.setScaleY(0.86f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(SPRING).start();
    }
}
