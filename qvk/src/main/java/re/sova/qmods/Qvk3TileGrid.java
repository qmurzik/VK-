package re.sova.qmods;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * IDEA-0022: the sections of «Моя музыка» as two rows of gradient tiles that scroll sideways. Long press starts
 * edit mode: the tiles wiggle like iOS icons and can be dragged to a new place; a tap on any tile or «Назад» ends it
 * and the order is kept.
 */
final class Qvk3TileGrid extends HorizontalScrollView {
    static final String ORDER = "qvk3_music_tiles_order";

    static final class Tile {
        final String id, title, icon;
        final int c1, c2;
        final Runnable action;

        Tile(String id, String title, String icon, int c1, int c2, Runnable action) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.c1 = c1;
            this.c2 = c2;
            this.action = action;
        }
    }

    private final Activity a;
    private final FrameLayout box;
    private final List<Tile> tiles = new ArrayList<>();
    private final List<View> views = new ArrayList<>();
    private final int tileW, tileH, gap;
    private boolean editing;
    private View dragged;
    private float dragDx, dragDy;
    private int dragFrom = -1;

    Qvk3TileGrid(Activity a, List<Tile> all) {
        super(a);
        this.a = a;
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        int d16 = Qvk3Ui.dp(a, 16);
        setPadding(d16, Qvk3Ui.dp(a, 4), d16, Qvk3Ui.dp(a, 8));
        tileW = Qvk3Ui.dp(a, 150);
        tileH = Qvk3Ui.dp(a, 92);
        gap = Qvk3Ui.dp(a, 10);
        box = new FrameLayout(a);
        box.setClipChildren(false);
        addView(box, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tiles.addAll(ordered(all));
        for (Tile t : tiles) views.add(tileView(t));
        for (View v : views) box.addView(v, new FrameLayout.LayoutParams(tileW, tileH));
        int cols = (tiles.size() + 1) / 2;
        box.setMinimumWidth(cols * tileW + (cols - 1) * gap);
        box.setMinimumHeight(tileH * 2 + gap);
        place(false);
    }

    private List<Tile> ordered(List<Tile> all) {
        String saved = Qvk3MusicSettings.prefs(a).getString(ORDER, "");
        if (saved.isEmpty()) return all;
        List<String> ids = Arrays.asList(saved.split(","));
        List<Tile> out = new ArrayList<>();
        for (String id : ids) for (Tile t : all) if (t.id.equals(id) && !out.contains(t)) out.add(t);
        for (Tile t : all) if (!out.contains(t)) out.add(t);   // new sections go to the end
        return out;
    }

    private void save() {
        StringBuilder b = new StringBuilder();
        for (Tile t : tiles) {
            if (b.length() > 0) b.append(',');
            b.append(t.id);
        }
        SharedPreferences.Editor e = Qvk3MusicSettings.prefs(a).edit();
        e.putString(ORDER, b.toString()).apply();
    }

    private View tileView(Tile t) {
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.BOTTOM | Gravity.START);
        int p = Qvk3Ui.dp(a, 12);
        v.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.c1, t.c2});
        g.setCornerRadius(Qvk3Ui.dp(a, 18));
        v.setBackground(g);
        v.setElevation(Qvk3Ui.dp(a, 2));
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(Qvk3Icons.of(t.icon, 0xFFFFFFFF));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 26), Qvk3Ui.dp(a, 26));
        il.bottomMargin = Qvk3Ui.dp(a, 8);
        v.addView(ic, il);
        TextView tv = Qvk3Ui.line(a, t.title, 15, 0xFFFFFFFF, true);
        v.addView(tv);
        v.setContentDescription(t.title + ". Удерживайте, чтобы переставить разделы");
        v.setOnClickListener(x -> {
            if (editing) {
                stopEdit();
                return;
            }
            if (t.action != null) t.action.run();
        });
        v.setOnLongClickListener(x -> {
            if (!editing) startEdit();
            beginDrag(x);
            return true;
        });
        return v;
    }

    /** Column-major: tile i goes to column i/2, row i%2 — two rows that grow sideways. */
    private void place(boolean animate) {
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            if (v == dragged) continue;
            float x = (i / 2) * (tileW + gap), y = (i % 2) * (tileH + gap);
            if (animate) v.animate().x(x).y(y).setDuration(160).start();
            else {
                v.setX(x);
                v.setY(y);
            }
        }
    }

    boolean editing() {
        return editing;
    }

    void startEdit() {
        editing = true;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        for (int i = 0; i < views.size(); i++) wiggle(views.get(i), i);
    }

    void stopEdit() {
        editing = false;
        for (View v : views) {
            v.animate().cancel();
            Object an = v.getTag();
            if (an instanceof android.animation.Animator) ((android.animation.Animator) an).cancel();
            v.setRotation(0f);
            v.setScaleX(1f);
            v.setScaleY(1f);
        }
        place(false);
        save();
    }

    private void wiggle(View v, int i) {
        android.animation.ObjectAnimator r = android.animation.ObjectAnimator.ofFloat(v, View.ROTATION, -1.6f, 1.6f);
        r.setDuration(130 + (i % 3) * 15L);
        r.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        r.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        r.setStartDelay((i * 37L) % 90);
        r.start();
        v.setTag(r);
    }

    private void beginDrag(View v) {
        dragged = v;
        dragFrom = views.indexOf(v);
        v.bringToFront();
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
        getParent().requestDisallowInterceptTouchEvent(true);
        dragDx = Float.NaN;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (dragged != null) return true;
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (dragged == null) return super.onTouchEvent(ev);
        float bx = ev.getX() + getScrollX() - getPaddingLeft(), by = ev.getY() - getPaddingTop();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (Float.isNaN(dragDx)) {
                    dragDx = bx - dragged.getX();
                    dragDy = by - dragged.getY();
                }
                dragged.setX(bx - dragDx);
                dragged.setY(by - dragDy);
                int to = slotAt(bx, by);
                int from = views.indexOf(dragged);
                if (to >= 0 && to != from) {
                    views.remove(from);
                    views.add(to, dragged);
                    Tile t = tiles.remove(from);
                    tiles.add(to, t);
                    place(true);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                View d = dragged;
                dragged = null;
                int i = views.indexOf(d);
                d.animate().x((i / 2) * (tileW + gap)).y((i % 2) * (tileH + gap)).scaleX(1f).scaleY(1f).setDuration(160).start();
                getParent().requestDisallowInterceptTouchEvent(false);
                if (dragFrom != i) save();
                return true;
            default:
                return true;
        }
    }

    private int slotAt(float x, float y) {
        int col = (int) Math.floor(x / (tileW + gap)), row = y < tileH + gap / 2f ? 0 : 1;
        if (col < 0) col = 0;
        int i = col * 2 + row;
        return i >= 0 && i < views.size() ? i : -1;
    }
}
