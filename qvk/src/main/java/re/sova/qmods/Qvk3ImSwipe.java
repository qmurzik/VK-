package re.sova.qmods;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Swipe a message to the left to reply (a small reply arrow appears, a tick of vibration at the threshold). */
final class Qvk3ImSwipe {
    private Qvk3ImSwipe() {
    }

    static void attach(View row, View bubble, boolean out, Runnable onReply) {
        final float[] start = new float[2];
        final boolean[] dragging = {false}, armed = {false};
        int slop = ViewConfiguration.get(row.getContext()).getScaledTouchSlop();
        float limit = Qvk3Ui.dp(row.getContext(), 72), threshold = Qvk3Ui.dp(row.getContext(), 56);
        bubble.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawX();
                    start[1] = e.getRawY();
                    dragging[0] = false;
                    armed[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - start[0], dy = e.getRawY() - start[1];
                    if (!dragging[0] && dx < -slop * 2 && Math.abs(dx) > Math.abs(dy) * 2) {
                        dragging[0] = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        MotionEvent c = MotionEvent.obtain(e);
                        c.setAction(MotionEvent.ACTION_CANCEL);
                        v.onTouchEvent(c);         // no click / long press after a swipe
                        c.recycle();
                    }
                    if (dragging[0]) {
                        float t = Math.max(-limit, Math.min(0, dx));
                        row.setTranslationX(t);
                        boolean now = -t >= threshold;
                        if (now && !armed[0]) v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                        armed[0] = now;
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        row.animate().translationX(0).setDuration(180).start();
                        if (armed[0] && e.getActionMasked() == MotionEvent.ACTION_UP) onReply.run();
                        dragging[0] = false;
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
    }
}
