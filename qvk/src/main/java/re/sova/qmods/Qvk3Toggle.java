package re.sova.qmods;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** iOS-style switch: the thumb slides with a slight spring and the track crossfades to the «on» colour. */
final class Qvk3Toggle extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int onColor, offColor;
    private float pos;          // 0 = off … 1 = on
    private boolean checked;
    private ValueAnimator anim;

    Qvk3Toggle(Context c, boolean checked, int onColor, int offColor) {
        super(c);
        this.onColor = onColor;
        this.offColor = offColor;
        this.checked = checked;
        pos = checked ? 1f : 0f;
        thumb.setColor(0xFFFFFFFF);
        thumb.setShadowLayer(Qvk3Ui.dp(c, 2), 0, Qvk3Ui.dp(c, 1), 0x40000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // thumb shadow
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean on, boolean animate) {
        if (on == checked && (anim == null || !anim.isRunning())) return;
        checked = on;
        if (anim != null) anim.cancel();
        if (!animate) {
            pos = on ? 1f : 0f;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, on ? 1f : 0f);
        anim.setDuration(280);
        anim.setInterpolator(new android.view.animation.OvershootInterpolator(1.1f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Qvk3Ui.dp(getContext(), 51), Qvk3Ui.dp(getContext(), 31));
    }

    static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24), ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24), br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = h * 0.065f;
        track.setColor(blend(offColor, onColor, pos));
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2f, h / 2f, track);
        float rad = h / 2f - pad;
        float x = pad + rad + (w - 2 * pad - 2 * rad) * Math.max(-0.05f, Math.min(1.05f, pos));
        c.drawCircle(x, h / 2f, rad, thumb);
    }
}
