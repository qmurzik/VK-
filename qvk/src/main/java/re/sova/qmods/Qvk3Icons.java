package re.sova.qmods;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Vector icon set of QVK 3.0 (24-unit grid, 2-unit round strokes, Feather-like geometry). Drawn with Paths,
 * so no drawable resources are added to VK's resource table and every icon scales crisply and is tintable.
 */
public final class Qvk3Icons extends Drawable {
    public static final String PLAY = "play", PAUSE = "pause", NEXT = "next", PREV = "prev", SHUFFLE = "shuffle",
            REPEAT = "repeat", REPEAT_ONE = "repeat1", QUEUE = "queue", LYRICS = "lyrics", HEART = "heart",
            HEART_FILL = "heartfill", DOWNLOAD = "download", TIMER = "timer", SPEED = "speed", DOWN = "down",
            RIGHT = "right", BACK = "back", MORE = "more", SEARCH = "search", TUNE = "tune", MUSIC = "music",
            PALETTE = "palette", BELL = "bell", LOCK = "lock", CHAT = "chat", NAV = "nav", IMAGE = "image",
            FOLDER = "folder", BATTERY = "battery", CLOUD = "cloud", UPDATE = "update", PULSE = "pulse", INFO = "info",
            STAR = "star", HOME = "home", LIST = "list", UNDO = "undo", EQ = "eq", CHECK = "check", PLUS = "plus",
            GRID = "grid", SEND = "send", USER = "user", LAYERS = "layers", WAND = "wand", SLIDERS = "sliders", CLOSE = "close";

    private final String name;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();
    private final RectF r = new RectF();

    public Qvk3Icons(String name, int color) {
        this.name = name;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
    }

    public static Qvk3Icons of(String name, int color) {
        return new Qvk3Icons(name, color);
    }

    @Override
    public void draw(Canvas c) {
        android.graphics.Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        stroke.setStrokeWidth(2f);
        p.reset();
        switch (name) {
            case PLAY:
                p.moveTo(7, 4.5f);
                p.lineTo(19.5f, 12);
                p.lineTo(7, 19.5f);
                p.close();
                fill.setPathEffect(new android.graphics.CornerPathEffect(2.2f));
                c.drawPath(p, fill);
                fill.setPathEffect(null);
                break;
            case PAUSE:
                r.set(6, 4.5f, 10, 19.5f);
                c.drawRoundRect(r, 1.4f, 1.4f, fill);
                r.set(14, 4.5f, 18, 19.5f);
                c.drawRoundRect(r, 1.4f, 1.4f, fill);
                break;
            case NEXT:
                p.moveTo(5, 5);
                p.lineTo(15.5f, 12);
                p.lineTo(5, 19);
                p.close();
                fill.setPathEffect(new android.graphics.CornerPathEffect(1.6f));
                c.drawPath(p, fill);
                fill.setPathEffect(null);
                r.set(16.5f, 5, 19.5f, 19);
                c.drawRoundRect(r, 1.2f, 1.2f, fill);
                break;
            case PREV:
                p.moveTo(19, 5);
                p.lineTo(8.5f, 12);
                p.lineTo(19, 19);
                p.close();
                fill.setPathEffect(new android.graphics.CornerPathEffect(1.6f));
                c.drawPath(p, fill);
                fill.setPathEffect(null);
                r.set(4.5f, 5, 7.5f, 19);
                c.drawRoundRect(r, 1.2f, 1.2f, fill);
                break;
            case SHUFFLE:
                line(c, 16, 3, 21, 3);
                line(c, 21, 3, 21, 8);
                line(c, 4, 20, 21, 3);
                line(c, 21, 16, 21, 21);
                line(c, 21, 21, 16, 21);
                line(c, 15, 15, 21, 21);
                line(c, 4, 4, 9, 9);
                break;
            case REPEAT:
            case REPEAT_ONE:
                p.moveTo(17, 1.5f);
                p.lineTo(21, 5.5f);
                p.lineTo(17, 9.5f);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(3, 11.5f);
                p.lineTo(3, 9.5f);
                p.quadTo(3, 5.5f, 7, 5.5f);
                p.lineTo(21, 5.5f);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(7, 22.5f);
                p.lineTo(3, 18.5f);
                p.lineTo(7, 14.5f);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(21, 12.5f);
                p.lineTo(21, 14.5f);
                p.quadTo(21, 18.5f, 17, 18.5f);
                p.lineTo(3, 18.5f);
                c.drawPath(p, stroke);
                if (name.equals(REPEAT_ONE)) {
                    stroke.setStrokeWidth(1.8f);
                    line(c, 11, 10.5f, 12.5f, 9.5f);
                    line(c, 12.5f, 9.5f, 12.5f, 14.5f);
                }
                break;
            case QUEUE:
                line(c, 3, 6, 15, 6);
                line(c, 3, 12, 15, 12);
                line(c, 3, 18, 10, 18);
                p.moveTo(15, 15);
                p.lineTo(21, 18.5f);
                p.lineTo(15, 22);
                p.close();
                c.drawPath(p, fill);
                break;
            case LYRICS:
                r.set(3, 3, 21, 18);
                c.drawRoundRect(r, 3, 3, stroke);
                line(c, 7, 8, 17, 8);
                line(c, 7, 12.5f, 13, 12.5f);
                p.moveTo(8, 18);
                p.lineTo(8, 22);
                p.lineTo(12, 18);
                c.drawPath(p, stroke);
                break;
            case HEART:
            case HEART_FILL:
                p.moveTo(12, 20.5f);
                p.cubicTo(5, 15.5f, 2.5f, 12, 2.5f, 8.5f);
                p.cubicTo(2.5f, 5.5f, 4.8f, 3.5f, 7.5f, 3.5f);
                p.cubicTo(9.5f, 3.5f, 11, 4.6f, 12, 6.2f);
                p.cubicTo(13, 4.6f, 14.5f, 3.5f, 16.5f, 3.5f);
                p.cubicTo(19.2f, 3.5f, 21.5f, 5.5f, 21.5f, 8.5f);
                p.cubicTo(21.5f, 12, 19, 15.5f, 12, 20.5f);
                p.close();
                c.drawPath(p, name.equals(HEART_FILL) ? fill : stroke);
                break;
            case DOWNLOAD:
                line(c, 12, 3, 12, 15);
                p.moveTo(7, 10);
                p.lineTo(12, 15);
                p.lineTo(17, 10);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(3, 15);
                p.lineTo(3, 19);
                p.quadTo(3, 21, 5, 21);
                p.lineTo(19, 21);
                p.quadTo(21, 21, 21, 19);
                p.lineTo(21, 15);
                c.drawPath(p, stroke);
                break;
            case TIMER:
                c.drawCircle(12, 13, 8, stroke);
                line(c, 12, 13, 12, 9);
                line(c, 12, 13, 15, 15);
                line(c, 9, 2.5f, 15, 2.5f);
                break;
            case SPEED:
                p.addArc(new RectF(3, 5, 21, 23), 180, 180);
                c.drawPath(p, stroke);
                line(c, 12, 14, 16.5f, 9.5f);
                c.drawCircle(12, 14, 1.6f, fill);
                break;
            case DOWN:
                p.moveTo(6, 9);
                p.lineTo(12, 15);
                p.lineTo(18, 9);
                c.drawPath(p, stroke);
                break;
            case RIGHT:
                p.moveTo(9, 6);
                p.lineTo(15, 12);
                p.lineTo(9, 18);
                c.drawPath(p, stroke);
                break;
            case BACK:
                line(c, 20, 12, 4, 12);
                p.moveTo(10, 6);
                p.lineTo(4, 12);
                p.lineTo(10, 18);
                c.drawPath(p, stroke);
                break;
            case CLOSE:
                line(c, 6, 6, 18, 18);
                line(c, 18, 6, 6, 18);
                break;
            case MORE:
                c.drawCircle(12, 5, 1.8f, fill);
                c.drawCircle(12, 12, 1.8f, fill);
                c.drawCircle(12, 19, 1.8f, fill);
                break;
            case SEARCH:
                c.drawCircle(10.5f, 10.5f, 6.5f, stroke);
                line(c, 15.5f, 15.5f, 21, 21);
                break;
            case TUNE:
            case SLIDERS:
                line(c, 4, 7, 20, 7);
                line(c, 4, 17, 20, 17);
                c.drawCircle(9, 7, 2.8f, fill);
                c.drawCircle(15, 17, 2.8f, fill);
                break;
            case MUSIC:
                line(c, 9, 18, 9, 5);
                line(c, 9, 5, 21, 3);
                line(c, 21, 3, 21, 16);
                c.drawCircle(6, 18, 3, stroke);
                c.drawCircle(18, 16, 3, stroke);
                break;
            case PALETTE:
                p.moveTo(12, 2.5f);
                p.cubicTo(6.5f, 2.5f, 2.5f, 6.6f, 2.5f, 12);
                p.cubicTo(2.5f, 17.4f, 6.6f, 21.5f, 12, 21.5f);
                p.cubicTo(13.4f, 21.5f, 14, 20.6f, 13.4f, 19.5f);
                p.cubicTo(12.6f, 18, 13.6f, 16.5f, 15.2f, 16.5f);
                p.lineTo(17, 16.5f);
                p.cubicTo(19.6f, 16.5f, 21.5f, 14.6f, 21.5f, 12);
                p.cubicTo(21.5f, 6.6f, 17.4f, 2.5f, 12, 2.5f);
                c.drawPath(p, stroke);
                c.drawCircle(7.5f, 11, 1.4f, fill);
                c.drawCircle(10.5f, 7, 1.4f, fill);
                c.drawCircle(15, 7.5f, 1.4f, fill);
                break;
            case BELL:
                p.moveTo(6, 16.5f);
                p.lineTo(6, 10.5f);
                p.cubicTo(6, 7, 8.7f, 4.5f, 12, 4.5f);
                p.cubicTo(15.3f, 4.5f, 18, 7, 18, 10.5f);
                p.lineTo(18, 16.5f);
                p.lineTo(20, 18.5f);
                p.lineTo(4, 18.5f);
                p.close();
                c.drawPath(p, stroke);
                line(c, 10, 21.5f, 14, 21.5f);
                break;
            case LOCK:
                r.set(4.5f, 10.5f, 19.5f, 21);
                c.drawRoundRect(r, 2.5f, 2.5f, stroke);
                p.moveTo(8, 10.5f);
                p.lineTo(8, 7.5f);
                p.cubicTo(8, 5.2f, 9.8f, 3.5f, 12, 3.5f);
                p.cubicTo(14.2f, 3.5f, 16, 5.2f, 16, 7.5f);
                p.lineTo(16, 10.5f);
                c.drawPath(p, stroke);
                break;
            case CHAT:
                p.moveTo(4, 5.5f);
                p.quadTo(4, 3.5f, 6, 3.5f);
                p.lineTo(18, 3.5f);
                p.quadTo(20, 3.5f, 20, 5.5f);
                p.lineTo(20, 14.5f);
                p.quadTo(20, 16.5f, 18, 16.5f);
                p.lineTo(9, 16.5f);
                p.lineTo(4, 20.5f);
                p.close();
                c.drawPath(p, stroke);
                break;
            case NAV:
                r.set(3, 3, 21, 21);
                c.drawRoundRect(r, 4, 4, stroke);
                line(c, 3, 16, 21, 16);
                c.drawCircle(7.5f, 18.6f, 1, fill);
                c.drawCircle(12, 18.6f, 1, fill);
                c.drawCircle(16.5f, 18.6f, 1, fill);
                break;
            case IMAGE:
                r.set(3, 4, 21, 20);
                c.drawRoundRect(r, 3, 3, stroke);
                c.drawCircle(8.5f, 9, 1.6f, fill);
                p.moveTo(21, 15);
                p.lineTo(16, 10);
                p.lineTo(5, 20);
                c.drawPath(p, stroke);
                break;
            case FOLDER:
                p.moveTo(3, 6.5f);
                p.quadTo(3, 4.5f, 5, 4.5f);
                p.lineTo(9.5f, 4.5f);
                p.lineTo(11.5f, 7);
                p.lineTo(19, 7);
                p.quadTo(21, 7, 21, 9);
                p.lineTo(21, 17.5f);
                p.quadTo(21, 19.5f, 19, 19.5f);
                p.lineTo(5, 19.5f);
                p.quadTo(3, 19.5f, 3, 17.5f);
                p.close();
                c.drawPath(p, stroke);
                break;
            case BATTERY:
                r.set(2.5f, 7, 19, 17);
                c.drawRoundRect(r, 2.2f, 2.2f, stroke);
                line(c, 21.5f, 10.5f, 21.5f, 13.5f);
                r.set(5, 9.5f, 12, 14.5f);
                c.drawRoundRect(r, 0.8f, 0.8f, fill);
                break;
            case CLOUD:
                p.moveTo(7, 19);
                p.cubicTo(4.2f, 19, 2.5f, 17, 2.5f, 14.6f);
                p.cubicTo(2.5f, 12.4f, 4.2f, 10.6f, 6.4f, 10.4f);
                p.cubicTo(7, 7.2f, 9.6f, 5, 12.6f, 5);
                p.cubicTo(16.2f, 5, 19, 7.8f, 19, 11.3f);
                p.cubicTo(20.6f, 11.6f, 21.5f, 13.1f, 21.5f, 15);
                p.cubicTo(21.5f, 17.3f, 19.8f, 19, 17.5f, 19);
                p.close();
                c.drawPath(p, stroke);
                break;
            case UPDATE:
                p.addArc(new RectF(4, 4, 20, 20), -60, 300);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(20.5f, 3.5f);
                p.lineTo(20.5f, 8.5f);
                p.lineTo(15.5f, 8.5f);
                c.drawPath(p, stroke);
                break;
            case PULSE:
                p.moveTo(2.5f, 12);
                p.lineTo(7, 12);
                p.lineTo(9.5f, 5);
                p.lineTo(14.5f, 19);
                p.lineTo(17, 12);
                p.lineTo(21.5f, 12);
                c.drawPath(p, stroke);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, stroke);
                line(c, 12, 11, 12, 16.5f);
                c.drawCircle(12, 7.6f, 1.2f, fill);
                break;
            case STAR:
                star(c);
                break;
            case HOME:
                p.moveTo(3.5f, 10.5f);
                p.lineTo(12, 3.5f);
                p.lineTo(20.5f, 10.5f);
                p.lineTo(20.5f, 19);
                p.quadTo(20.5f, 20.5f, 19, 20.5f);
                p.lineTo(5, 20.5f);
                p.quadTo(3.5f, 20.5f, 3.5f, 19);
                p.close();
                c.drawPath(p, stroke);
                break;
            case LIST:
                for (int i = 0; i < 3; i++) {
                    c.drawCircle(4.5f, 6 + i * 6, 1.3f, fill);
                    line(c, 9, 6 + i * 6, 20.5f, 6 + i * 6);
                }
                break;
            case UNDO:
                p.moveTo(8, 4);
                p.lineTo(3.5f, 8.5f);
                p.lineTo(8, 13);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(3.5f, 8.5f);
                p.lineTo(15, 8.5f);
                p.cubicTo(18.6f, 8.5f, 21, 11, 21, 14.3f);
                p.cubicTo(21, 17.6f, 18.6f, 20, 15, 20);
                p.lineTo(9, 20);
                c.drawPath(p, stroke);
                break;
            case EQ:
                line(c, 6, 3, 6, 21);
                line(c, 12, 3, 12, 21);
                line(c, 18, 3, 18, 21);
                c.drawCircle(6, 15, 2.8f, fill);
                c.drawCircle(12, 8, 2.8f, fill);
                c.drawCircle(18, 13, 2.8f, fill);
                break;
            case CHECK:
                p.moveTo(4.5f, 12.5f);
                p.lineTo(9.5f, 17.5f);
                p.lineTo(19.5f, 6.5f);
                c.drawPath(p, stroke);
                break;
            case SEND:
                line(c, 12, 19, 12, 5.5f);
                p.moveTo(6, 11);
                p.lineTo(12, 5);
                p.lineTo(18, 11);
                c.drawPath(p, stroke);
                break;
            case PLUS:
                line(c, 12, 5, 12, 19);
                line(c, 5, 12, 19, 12);
                break;
            case GRID:
                for (int i = 0; i < 2; i++)
                    for (int j = 0; j < 2; j++) {
                        r.set(3.5f + i * 9.5f, 3.5f + j * 9.5f, 11f + i * 9.5f, 11f + j * 9.5f);
                        c.drawRoundRect(r, 2, 2, stroke);
                    }
                break;
            case USER:
                c.drawCircle(12, 8, 4, stroke);
                p.moveTo(4, 21);
                p.cubicTo(4, 16.6f, 7.6f, 14, 12, 14);
                p.cubicTo(16.4f, 14, 20, 16.6f, 20, 21);
                c.drawPath(p, stroke);
                break;
            case LAYERS:
                p.moveTo(12, 3);
                p.lineTo(21.5f, 8);
                p.lineTo(12, 13);
                p.lineTo(2.5f, 8);
                p.close();
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(2.5f, 12.5f);
                p.lineTo(12, 17.5f);
                p.lineTo(21.5f, 12.5f);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(2.5f, 16.5f);
                p.lineTo(12, 21.5f);
                p.lineTo(21.5f, 16.5f);
                c.drawPath(p, stroke);
                break;
            case WAND:
                line(c, 4, 20, 15, 9);
                line(c, 17, 3, 17, 7);
                line(c, 15, 5, 19, 5);
                line(c, 20, 10, 20, 12);
                line(c, 19, 11, 21, 11);
                line(c, 9, 4, 9, 6);
                line(c, 8, 5, 10, 5);
                break;
            default:
                c.drawCircle(12, 12, 8, stroke);
        }
        c.restore();
    }

    private void star(Canvas c) {
        p.reset();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 5 * i - Math.PI / 2;
            float rad = i % 2 == 0 ? 9.5f : 4.2f;
            float x = 12 + (float) (Math.cos(a) * rad), y = 12.8f + (float) (Math.sin(a) * rad);
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        c.drawPath(p, stroke);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    public void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        stroke.setColorFilter(cf);
        fill.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return -1;
    }
}
