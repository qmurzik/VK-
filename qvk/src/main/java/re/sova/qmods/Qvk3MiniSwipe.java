package re.sova.qmods;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * BUG-0022: swipe the mini player to the right to close it. What closing does is a setting
 * («Действие при смахивании»): stop the music (default) or only hide the bar until the next track.
 * Taps still open the player; only a clear sideways drag is taken over.
 */
final class Qvk3MiniSwipe {
    interface Dismiss {
        void done();
    }

    private Qvk3MiniSwipe() {
    }

    static boolean stops(Context c) {
        return !"hide".equals(Qvk3MusicSettings.prefs(c).getString(Qvk3MusicSettings.MINI_SWIPE, "stop"));
    }

    static void attach(View bar, Dismiss onDismiss) {
        final int slop = ViewConfiguration.get(bar.getContext()).getScaledTouchSlop();
        final float[] down = new float[2];
        final boolean[] dragging = {false};
        bar.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    dragging[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (!dragging[0] && dx > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        dragging[0] = true;
                        v.setPressed(false);
                        v.cancelLongPress();
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (dragging[0]) {
                        float x = Math.max(0, dx);
                        v.setTranslationX(x);
                        v.setAlpha(1f - Math.min(0.8f, x / Math.max(1f, v.getWidth())));
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (!dragging[0]) return false;
                    dragging[0] = false;
                    float dx = e.getRawX() - down[0];
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && dx > v.getWidth() * 0.35f) {
                        v.animate().translationX(v.getWidth()).alpha(0f).setDuration(160).withEndAction(() -> {
                            v.setTranslationX(0);
                            v.setAlpha(1f);
                            onDismiss.done();
                        }).start();
                    } else {
                        v.animate().translationX(0).alpha(1f).setDuration(160).start();
                    }
                    return true;
                }
                default:
                    return dragging[0];
            }
        });
    }
}
