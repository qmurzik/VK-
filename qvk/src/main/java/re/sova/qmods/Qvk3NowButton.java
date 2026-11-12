package re.sova.qmods;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Small "now playing" button for headers (BUG-0020): a ring that fills clockwise as the track plays, with play/pause
 * in the middle. Tap = pause / resume, long press = open the full player.
 */
final class Qvk3NowButton extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int iconColor;
    private float progress;
    private boolean playing;
    private Drawable icon;
    /** BUG-0053: header colour under the button with a soft left edge, so a long title fades out instead of overlapping. */
    private int plate;
    private final Paint platePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    void setPlate(int color) {
        plate = color;
        platePaint.setShader(null);
        invalidate();
    }

    Qvk3NowButton(Context c, int color, int accent) {
        super(c);
        iconColor = color;
        track.setStyle(Paint.Style.STROKE);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        track.setColor((color & 0x00FFFFFF) | 0x33000000);
        arc.setColor(accent);
        setContentDescription("Пауза. Удерживайте, чтобы открыть плеер");
        setIcon(false);
    }

    void set(float progress, boolean playing) {
        float p = Math.max(0f, Math.min(1f, progress));
        if (playing != this.playing) setIcon(playing);
        if (Math.abs(p - this.progress) < 0.002f && playing == this.playing) return;
        this.progress = p;
        this.playing = playing;
        setContentDescription((playing ? "Пауза" : "Играть") + ". Удерживайте, чтобы открыть плеер");
        invalidate();
    }

    private void setIcon(boolean playing) {
        icon = Qvk3Icons.of(playing ? Qvk3Icons.PAUSE : Qvk3Icons.PLAY, iconColor);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        platePaint.setShader(null);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()), stroke = s * 0.07f, pad = s * 0.16f;
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        if ((plate >>> 24) != 0) {
            float fade = Math.max(1f, getWidth() - s);
            if (platePaint.getShader() == null)
                platePaint.setShader(new android.graphics.LinearGradient(0, 0, fade, 0, plate & 0x00FFFFFF, plate, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, getWidth(), getHeight(), platePaint);
        }
        // the ring sits at the right end; the extra width on the left is the fade
        float cx = getWidth() - s / 2f, cy = getHeight() / 2f, rad = s / 2f - pad;
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        c.drawOval(r, track);
        if (progress > 0f) c.drawArc(r, -90f, 360f * progress, false, arc);
        if (icon != null) {
            int is = Math.round(rad * 1.05f);
            icon.setBounds(Math.round(cx - is / 2f), Math.round(cy - is / 2f), Math.round(cx + is / 2f), Math.round(cy + is / 2f));
            icon.draw(c);
        }
    }
}
