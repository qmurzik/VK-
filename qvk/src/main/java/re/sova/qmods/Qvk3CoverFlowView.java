package re.sova.qmods;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.List;

/**
 * CoverFlow for QVK Music, written from scratch.
 *
 * - inertia: OverScroller fling whose end point is adjusted to the nearest album, so the motion decelerates
 *   naturally and ends centred instead of jumping;
 * - correct overlap: covers are drawn from the farthest to the centre one;
 * - no popping at the edges: covers fade in/out over the last visible slot;
 * - covers are never decoded during a fast fling; neighbours are prefetched once the view settles;
 * - the selected index survives rotation/process death (onSaveInstanceState) and the geometry is
 *   recomputed in onSizeChanged, so the current album stays centred after rotation.
 */
public final class Qvk3CoverFlowView extends View {
    public static final class Item {
        public final String cover;
        public final String title;
        public final String subtitle;

        public Item(String cover, String title, String subtitle) {
            this.cover = cover;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
        }
    }

    public interface Listener {
        /** Centre album tapped (or settled when {@code settled} is true). */
        void onItem(int index, boolean settled);
    }

    private static final int VISIBLE_SIDE = 5; // minimum covers drawn on each side
    /** Covers drawn on each side: enough to reach past the screen edge, so none appears on screen out of nowhere. */
    private int side = VISIBLE_SIDE;
    private static final float UNIT = 1000f;   // scroller works in integer units: 1 item = 1000

    private final List<Item> items = new ArrayList<>();
    private final Scroller scroller;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint placeholder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int touchSlop, minFling, maxFling;
    private VelocityTracker velocity;
    private float downX, lastX;
    private boolean dragging;
    private float pos; // current position in items (0 = first centred)
    private int coverPx;
    private int spacingPercent = 62;
    private float radius;
    private Listener listener;
    private boolean byUser, settledByUser;
    /** True when the settle came from a fast fling (browsing many covers) rather than a calm single swipe. */
    private boolean fling, settledByFling;
    private int lastSettled = -1;
    private boolean fastFling;

    public Qvk3CoverFlowView(Context c) {
        super(c);
        scroller = new Scroller(c, new android.view.animation.DecelerateInterpolator(2.2f));
        scroller.setFriction(ViewConfiguration.getScrollFriction() * 1.6f);
        ViewConfiguration vc = ViewConfiguration.get(c);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        title.setTextAlign(Paint.Align.CENTER);
        subtitle.setTextAlign(Paint.Align.CENTER);
        title.setTextSize(Qvk3Compat.dp(c, 17));
        title.setFakeBoldText(true);
        subtitle.setTextSize(Qvk3Compat.dp(c, 14));
        setColors(Color.WHITE, 0xB3FFFFFF, 0x33FFFFFF);
        radius = Qvk3Compat.dp(c, 10);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** Theme-aware colours (the old CoverFlow kept white text on light themes). */
    public void setColors(int text, int secondary, int placeholderColor) {
        title.setColor(text);
        subtitle.setColor(secondary);
        placeholder.setColor(placeholderColor);
        invalidate();
    }

    public void setSpacingPercent(int p) {
        spacingPercent = Math.max(30, Math.min(90, p));
        if (getWidth() > 0) computeSide(getWidth());
        invalidate();
    }

    public void setCornerRadius(float px) {
        radius = px;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setItems(List<Item> list, int select) {
        items.clear();
        items.addAll(list);
        scroller.forceFinished(true);
        pos = items.isEmpty() ? 0 : Math.max(0, Math.min(select, items.size() - 1));
        lastSettled = -1;
        byUser = false;
        fling = false;
        settle();
        invalidate();
    }

    /** Valid inside Listener.onItem(index, true): true when the finger (not code) moved the covers to this one. */
    public boolean settledByUser() {
        return settledByUser;
    }

    /** Valid inside Listener.onItem(index, true): true when a fast fling (browsing) brought us here, not a calm swipe. */
    public boolean settledByFling() {
        return settledByFling;
    }

    public int selected() {
        return items.isEmpty() ? -1 : Math.round(pos);
    }

    /** Animated jump ("к текущему альбому"). */
    public void smoothScrollTo(int index) {
        scrollTo(index, false);
    }

    /** BUG-0013: a slow swipe ends here too — the finger moved the covers, so the album it stops on is the user's choice. */
    private void scrollTo(int index, boolean user) {
        if (items.isEmpty()) return;
        index = Math.max(0, Math.min(index, items.size() - 1));
        byUser = user;
        scroller.forceFinished(true);
        int from = Math.round(pos * UNIT);
        int dist = Math.round(index * UNIT) - from;
        scroller.startScroll(from, 0, dist, 0, Math.min(900, 250 + Math.abs(dist) / 10));
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        int textSpace = Math.round(title.getTextSize() + subtitle.getTextSize() + Qvk3Compat.dp(getContext(), 24));
        // cover + reflection + caption must fit the height; the cover never exceeds ~46% of the width
        float share = h > w * 1.3f ? 0.58f : 0.46f;   // upright screens have height to spare: bigger covers
        coverPx = Math.max(1, Math.min(Math.round(w * share), Math.round((h - textSpace) / (1f + REFLECTION))));
        computeSide(w);
    }

    /** Width of a fully turned (70°) side cover on screen. */
    private float sideWidth() {
        return (float) Math.cos(Math.toRadians(70)) * coverPx * 0.96f;
    }

    /**
     * Centre of the first side cover. Spacing is tied to the cover size, not to pixels (BUG-0017): at the default
     * setting a clear gap of 1/5 of the cover separates the centre cover from its neighbours.
     */
    private float gap() {
        float k = spacingPercent / 62f;
        return coverPx / 2f + coverPx * 0.20f * k + sideWidth() / 2f;
    }

    /** Distance between side covers: each is fully visible with a clear gap of 1/10 of the cover. */
    private float step() {
        float k = spacingPercent / 62f;
        return sideWidth() + coverPx * 0.10f * k;
    }

    private void computeSide(int w) {
        // first index whose turned cover lies completely beyond the edge, plus one spare
        side = Math.max(VISIBLE_SIDE, Math.min(30, (int) Math.ceil((w / 2f + sideWidth() / 2f - gap()) / step()) + 2));
    }

    private boolean captionOnTop = true;

    /** Album title above the covers (the finger does not hide it while flicking, BUG-0017). */
    public void setCaptionOnTop(boolean top) {
        captionOnTop = top;
        invalidate();
    }

    private float coverTop() {
        float textSpace = title.getTextSize() + subtitle.getTextSize() + Qvk3Compat.dp(getContext(), 28);
        float free = getHeight() - coverPx * (1f + REFLECTION) - textSpace;
        float base = Math.max(Qvk3Compat.dp(getContext(), 8), free / 2f);
        return captionOnTop ? base + textSpace : base;
    }

    /** Where the centre cover is drawn (used by the flip animation). */
    public void centerCoverRect(RectF out) {
        float top = coverTop();
        float cx = getWidth() / 2f;
        out.set(cx - coverPx / 2f, top, cx + coverPx / 2f, top + coverPx);
    }

    public Item item(int i) {
        return i >= 0 && i < items.size() ? items.get(i) : null;
    }

    private float spacing() {
        return coverPx * spacingPercent / 100f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (scroller.computeScrollOffset()) {
            float np = scroller.getCurrX() / UNIT;
            fastFling = Math.abs(np - pos) > 0.12f; // more than ~7 albums per second: draw cached covers only
            pos = np;
            postInvalidateOnAnimation();
            if (scroller.isFinished()) settle();
        }
        if (items.isEmpty() || coverPx <= 1) return;
        int center = Math.round(pos);
        int from = Math.max(0, center - side - 1), to = Math.min(items.size() - 1, center + side + 1);
        // Draw farthest first so nearer covers overlap them.
        List<Integer> order = new ArrayList<>();
        for (int i = from; i <= to; i++) order.add(i);
        java.util.Collections.sort(order, (a, b) -> Float.compare(Math.abs(b - pos), Math.abs(a - pos)));
        float cx = getWidth() / 2f, top = coverTop();
        for (int i : order) drawCover(canvas, i, i - pos, cx, top);
        if (center >= 0 && center < items.size()) {
            Item it = items.get(center);
            float y = captionOnTop
                    ? top - Qvk3Compat.dp(getContext(), 16) - subtitle.getTextSize() - Qvk3Compat.dp(getContext(), 4) - title.descent()
                    : top + coverPx * (1f + REFLECTION) + Qvk3Compat.dp(getContext(), 8) - title.ascent();
            canvas.drawText(ellipsize(it.title, title), cx, y, title);
            canvas.drawText(ellipsize(it.subtitle, subtitle), cx, y + subtitle.getTextSize() + Qvk3Compat.dp(getContext(), 4), subtitle);
        }
    }

    private String ellipsize(String s, Paint p) {
        return TextUtils.ellipsize(s, new android.text.TextPaint(p), getWidth() - Qvk3Compat.dp(getContext(), 32), TextUtils.TruncateAt.END).toString();
    }

    private final float[] src = new float[8], dst = new float[8];
    private final Paint fade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final float REFLECTION = 0.42f; // reflection height as a share of the cover

    /**
     * iPhone (Cover Flow) geometry: the centre cover faces the viewer; covers on either side are turned about 70°
     * towards the centre and stacked closely; each cover has a faded mirror reflection. The edge towards the centre
     * is further away (shorter), the outer edge nearer (taller). Perspective is a Matrix.setPolyToPoly trapezoid.
     */
    private void drawCover(Canvas canvas, int i, float d, float cx, float top) {
        float ad = Math.abs(d);
        if (ad > side + 0.999f) return;
        float alpha = 1f; // the outermost drawn covers are already past the screen edge: no fading in on screen
        float sign = d < 0 ? -1f : 1f;
        float turn = Math.min(1f, ad);                       // 0 = facing us, 1 = fully turned
        float angle = (float) Math.toRadians(70f * turn);
        float W = coverPx;
        float gap = gap(), step = step();
        float off = ad <= 1f ? gap * ad : gap + (ad - 1f) * step;
        float x = cx + sign * off;
        float pw = (float) Math.max(0.05, Math.cos(angle)) * W * (1f - 0.04f * turn);
        float inner = 1f - 0.20f * (float) Math.sin(angle);       // far (inner) edge is shorter
        float cy = top + W / 2f;
        float left = x - pw / 2f, right = x + pw / 2f;
        float hLeft, hRight;
        if (sign < 0) {            // left covers: inner edge is the right one
            hLeft = W; hRight = W * inner;
        } else if (sign > 0 && ad > 0.001f) {
            hLeft = W * inner; hRight = W;
        } else {
            hLeft = hRight = W;
        }
        src[0] = 0; src[1] = 0; src[2] = W; src[3] = 0; src[4] = W; src[5] = W; src[6] = 0; src[7] = W;
        Item it = items.get(i);
        Bitmap b = fastFling ? Qvk3Images.get(getContext()).cachedAny(it.cover, coverBucket()) : bitmapFor(it, ad < 1.5f);
        int a = Math.round(alpha * 255);

        // --- cover
        dst[0] = left; dst[1] = cy - hLeft / 2f; dst[2] = right; dst[3] = cy - hRight / 2f;
        dst[4] = right; dst[5] = cy + hRight / 2f; dst[6] = left; dst[7] = cy + hLeft / 2f;
        matrix.reset();
        matrix.setPolyToPoly(src, 0, dst, 0, 4);
        canvas.save();
        canvas.concat(matrix);
        paintCover(canvas, b, a, false, sign, turn);
        canvas.restore();

        // --- reflection: the cover flipped vertically under its bottom edge, squeezed and fading out
        float rl = hLeft * REFLECTION, rr = hRight * REFLECTION;
        dst[0] = left; dst[1] = cy + hLeft / 2f + rl; dst[2] = right; dst[3] = cy + hRight / 2f + rr;
        dst[4] = right; dst[5] = cy + hRight / 2f; dst[6] = left; dst[7] = cy + hLeft / 2f;
        matrix.reset();
        matrix.setPolyToPoly(src, 0, dst, 0, 4);
        canvas.save();
        canvas.concat(matrix);
        paintCover(canvas, b, a, true);
        canvas.restore();
    }

    private void paintCover(Canvas canvas, Bitmap b, int alpha, boolean mirror) {
        paintCover(canvas, b, alpha, mirror, 0f, 0f);
    }

    /**
     * Draws one cover in its own (un-transformed) square. The reflection fades to transparent rather than to black,
     * so it sits on any background. Turned covers get a shade that deepens towards the edge facing the centre,
     * which gives the stack its depth; the centre cover gets a hairline highlight.
     */
    private void paintCover(Canvas canvas, Bitmap b, int alpha, boolean mirror, float sign, float turn) {
        rect.set(0, 0, coverPx, coverPx);
        Shader mask = mirror ? new android.graphics.LinearGradient(0, 0, 0, coverPx, 0x00000000, 0x73000000, Shader.TileMode.CLAMP) : null;
        if (b != null) {
            BitmapShader sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            float s = Math.max(coverPx / (float) b.getWidth(), coverPx / (float) b.getHeight());
            m.setScale(s, s);
            sh.setLocalMatrix(m);
            paint.setShader(mask == null ? sh : new android.graphics.ComposeShader(sh, mask, android.graphics.PorterDuff.Mode.DST_IN));
            paint.setAlpha(alpha);
            canvas.drawRect(rect, paint); // square corners, like the original
            paint.setShader(null);
            paint.setAlpha(255);
        } else {
            int base = placeholder.getAlpha();
            if (mask != null) {
                Paint pm = new Paint(placeholder);
                pm.setShader(new android.graphics.ComposeShader(new android.graphics.LinearGradient(0, 0, 0, coverPx, placeholder.getColor(), placeholder.getColor(), Shader.TileMode.CLAMP), mask, android.graphics.PorterDuff.Mode.DST_IN));
                pm.setAlpha(Math.round(base * alpha / 255f));
                canvas.drawRect(rect, pm);
            } else {
                placeholder.setAlpha(Math.round(base * alpha / 255f));
                canvas.drawRect(rect, placeholder);
                placeholder.setAlpha(base);
                // a note glyph on empty covers instead of a flat square
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.setColor(0x33FFFFFF);
                g.setTextAlign(Paint.Align.CENTER);
                g.setTextSize(coverPx * 0.38f);
                canvas.drawText("♪", coverPx / 2f, coverPx / 2f - (g.ascent() + g.descent()) / 2f, g);
            }
        }
        if (!mirror && turn > 0.001f) {
            int inner = Math.round(150 * turn), outer = Math.round(40 * turn);
            int left = sign < 0 ? outer : inner, right = sign < 0 ? inner : outer;
            fade.setShader(new android.graphics.LinearGradient(0, 0, coverPx, 0, left << 24, right << 24, Shader.TileMode.CLAMP));
            fade.setAlpha(alpha);
            canvas.drawRect(rect, fade);
            fade.setShader(null);
            fade.setAlpha(255);
        } else if (!mirror) {
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1f, coverPx / 300f));
            edge.setColor(0x38FFFFFF);
            canvas.drawRect(rect, edge);
        }
    }

    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Size bucket the covers are drawn at; the screen prefetches in the same bucket (BUG-0025). */
    int coverBucket() {
        return coverPx > 600 ? 600 : 300;
    }

    private Bitmap bitmapFor(Item it, boolean urgent) {
        Qvk3Images img = Qvk3Images.get(getContext());
        Bitmap b = img.cached(it.cover, coverBucket());
        if (b == null && it.cover != null && !it.cover.isEmpty() && (urgent || scroller.isFinished())) {
            img.load(it.cover, coverBucket(), bmp -> invalidate());
        }
        return b != null ? b : img.cachedAny(it.cover, coverBucket()); // smaller cached copy instead of an empty frame
    }

    private void settle() {
        fastFling = false;
        int c = selected();
        if (c < 0) return;
        Qvk3Images img = Qvk3Images.get(getContext());
        for (int i = Math.max(0, c - side); i <= Math.min(items.size() - 1, c + side); i++) {
            img.prefetch(items.get(i).cover, coverBucket());
        }
        if (c != lastSettled) {
            lastSettled = c;
            settledByUser = byUser;
            settledByFling = fling;
            byUser = false;
            fling = false;
            setContentDescription(items.get(c).title + ", " + items.get(c).subtitle + ". Альбом " + (c + 1) + " из " + items.size());
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            if (listener != null) listener.onItem(c, true);
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (items.isEmpty()) return false;
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                byUser = true;
                fling = false;
                scroller.forceFinished(true);
                downX = lastX = e.getX();
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - lastX;
                if (!dragging && Math.abs(e.getX() - downX) > touchSlop) dragging = true;
                if (dragging) {
                    pos = Math.max(-0.3f, Math.min(items.size() - 0.7f, pos - dx / step()));
                    lastX = e.getX();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                velocity.computeCurrentVelocity(1000, maxFling);
                float vx = velocity.getXVelocity();
                if (!dragging) {
                    tap(e.getX());
                } else if (Math.abs(vx) > minFling) {
                    fling = true;
                    // Inertia with weight (IDEA-0019): albums are heavy — a flick moves a few, even a very strong swipe
                    // at most ~12 (a soft saturation curve instead of 120), and they glide a little longer.
                    // The end point is an exact album, so the motion always finishes centred.
                    float itemsPerSecond = -vx / step();
                    float raw = Math.abs(itemsPerSecond) * 0.3f, cap = 12f;
                    float travel = Math.signum(itemsPerSecond) * cap * (1f - (float) Math.exp(-raw / cap));
                    if (Math.abs(travel) < 1f) travel = Math.signum(itemsPerSecond);
                    int target = Math.max(0, Math.min(items.size() - 1, Math.round(pos + travel)));
                    int start = Math.round(pos * UNIT);
                    int dist = Math.round(target * UNIT) - start;
                    int duration = (int) Math.min(2600, 520 + 420 * Math.sqrt(Math.abs(dist) / UNIT));
                    scroller.startScroll(start, 0, dist, 0, duration);
                    postInvalidateOnAnimation();
                } else {
                    scrollTo(Math.round(pos), true);
                }
                recycle();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                scrollTo(Math.round(pos), false);
                recycle();
                return true;
            default:
                return true;
        }
    }

    private void recycle() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        dragging = false;
    }

    private void tap(float x) {
        float dx = x - getWidth() / 2f, ad = Math.abs(dx);
        if (ad < coverPx / 2f) {
            int c = selected();
            if (listener != null && c >= 0) listener.onItem(c, false);
            performClick();
            return;
        }
        // which side cover was hit: the first sits at gap(), the rest every step()
        int n = 1 + Math.max(0, Math.round((ad - gap()) / step()));
        smoothScrollTo(selected() + (dx > 0 ? n : -n));
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            smoothScrollTo(selected() - 1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            smoothScrollTo(selected() + 1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (listener != null && selected() >= 0) listener.onItem(selected(), false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle b = new Bundle();
        b.putParcelable("super", super.onSaveInstanceState());
        b.putInt("selected", selected());
        return b;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle b = (Bundle) state;
            super.onRestoreInstanceState(b.getParcelable("super"));
            int s = b.getInt("selected", -1);
            if (s >= 0 && s < items.size()) {
                pos = s;
                invalidate();
            } else {
                pendingRestore = s;
            }
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    private int pendingRestore = -1;

    /** Index restored from saved state before items were set, or -1. */
    public int takePendingRestore() {
        int p = pendingRestore;
        pendingRestore = -1;
        return p;
    }
}
