package re.sova.qmods;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Album download pictogram for CoverFlow: a download arrow that fills a ring clockwise while the album is
 * being cached and turns into a check mark when every available track is saved.
 */
public final class Qvk3Ring extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Qvk3Icons down, check;
    private float progress; // 0..1
    private boolean active;

    public Qvk3Ring(Context c, int color, int accent) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        track.setColor((color & 0x00FFFFFF) | 0x33000000);
        arc.setColor(accent);
        down = Qvk3Icons.of(Qvk3Icons.DOWNLOAD, color);
        check = Qvk3Icons.of(Qvk3Icons.CHECK, accent);
    }

    /** @param active true while tracks of this album are queued or downloading */
    public void set(float progress, boolean active) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.active = active;
        setContentDescription(this.progress >= 1f ? "Альбом сохранён на устройстве"
                : active ? "Альбом скачивается: " + Math.round(this.progress * 100) + " процентов" : "Скачать альбом");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), stroke = s * 0.07f;
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        r.set((w - s) / 2 + stroke, (h - s) / 2 + stroke, (w + s) / 2 - stroke, (h + s) / 2 - stroke);
        if (active || (progress > 0 && progress < 1)) {
            c.drawOval(r, track);
            c.drawArc(r, -90, 360 * progress, false, arc);
        }
        Qvk3Icons icon = progress >= 1f ? check : down;
        int pad = Math.round(s * 0.28f);
        icon.setBounds(Math.round((w - s) / 2) + pad, Math.round((h - s) / 2) + pad, Math.round((w + s) / 2) - pad, Math.round((h + s) / 2) - pad);
        icon.draw(c);
    }
}
