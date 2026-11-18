package re.sova.qmods;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * IDEA-0030: pull the player up and the queue slides in from below. The cover shrinks to the top-left corner next to
 * the title; «+», shuffle and the seek bar stay on top, the current queue fills the rest. Tap a track to play it,
 * pull the handle down (or «Назад») to go back to the big cover.
 */
final class Qvk3QueuePanel extends FrameLayout implements Qvk3MusicEngine.Listener {
    private final Activity a;
    private final Qvk3MusicEngine engine;
    private final int fg, fg2, accent;
    private final ImageView cover = new ImageView(getContext());
    private final TextView title, artist, pos, dur;
    private final ImageView mine, shuffle;
    private final SeekBar seek;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private final Runnable onClosed;
    private String coverFor = "";
    private boolean dragging;
    private int dragFrom = -1, dragAt = -1;

    Qvk3QueuePanel(Activity a, int bg, int fg, int fg2, int accent, Runnable onClosed) {
        super(a);
        this.a = a;
        this.engine = Qvk3MusicEngine.get(a);
        this.fg = fg;
        this.fg2 = fg2;
        this.accent = accent;
        this.onClosed = onClosed;
        setClickable(true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        setBackground(g);
        setElevation(dp(12));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(-1, -1));

        // the handle: pull down to close
        View handle = new View(a);
        handle.setBackground(Qvk3Ui.rounded((fg2 & 0x00FFFFFF) | 0x66000000, dp(3)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(40), dp(5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.topMargin = dp(10);
        hl.bottomMargin = dp(6);
        col.addView(handle, hl);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(4), dp(8), dp(4));
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Qvk3Ui.rounded(0x33FFFFFF, dp(10)));
        cover.setClipToOutline(true);
        head.addView(cover, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(4), 0);
        title = Qvk3Ui.line(a, "", 17, fg, true);
        artist = Qvk3Ui.line(a, "", 14, fg2, false);
        texts.addView(title);
        texts.addView(artist);
        head.addView(texts, Qvk3Ui.weight(1));
        mine = Qvk3Ui.icon(a, Qvk3Icons.PLUS, fg, 44, 22, "В мою музыку", v -> {
            Qvk3Track t = engine.current();
            Qvk3Mine.toggle(a, t, (on, msg) -> {
                Qvk3Ui.toast(a, msg);
                onState(engine);
            });
        });
        head.addView(mine);
        shuffle = Qvk3Ui.icon(a, Qvk3Icons.SHUFFLE, fg, 44, 22, "Перемешать", v -> engine.setShuffle(!engine.queue().shuffle()));
        head.addView(shuffle);
        col.addView(head, Qvk3Ui.match());

        seek = new SeekBar(a);
        Qvk3Ui.styleSeek(a, seek, accent, (fg2 & 0x00FFFFFF) | 0x44000000);
        seek.setMax(1000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) pos.setText(Qvk3Track.time(engine.duration() * v / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                engine.seekTo(engine.duration() * s.getProgress() / 1000);
            }
        });
        LinearLayout.LayoutParams sl = Qvk3Ui.match();
        sl.setMargins(dp(4), dp(6), dp(4), 0);
        col.addView(seek, sl);
        LinearLayout times = new LinearLayout(a);
        times.setPadding(dp(20), 0, dp(20), dp(6));
        pos = Qvk3Ui.text(a, "0:00", 12, fg2, false);
        dur = Qvk3Ui.text(a, "0:00", 12, fg2, false);
        times.addView(pos, Qvk3Ui.weight(1));
        times.addView(dur);
        col.addView(times, Qvk3Ui.match());

        TextView up = Qvk3Ui.text(a, "Очередь · удерживайте трек, чтобы перетащить", 13, fg2, true);
        up.setPadding(dp(20), dp(6), dp(20), dp(4));
        col.addView(up);
        list = new ListView(a);
        list.setDivider(null);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, i, id) -> {
            if (dragAt < 0) engine.playIndex(i);
        });
        // QVK 3.1: hold a track and drag it to a new place in the queue
        list.setOnItemLongClickListener((parent, view, i, id) -> {
            dragFrom = dragAt = i;
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            adapter.notifyDataSetChanged();
            return true;
        });
        list.setOnTouchListener((v, e) -> {
            if (dragAt < 0) return false;
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_MOVE) {
                int to = list.pointToPosition((int) e.getX(), (int) e.getY());
                if (to != ListView.INVALID_POSITION && to != dragAt) {
                    Qvk3Track t = adapter.items.remove(dragAt);
                    adapter.items.add(to, t);
                    if (adapter.cur == dragAt) adapter.cur = to;
                    else if (dragAt < adapter.cur && to >= adapter.cur) adapter.cur--;
                    else if (dragAt > adapter.cur && to <= adapter.cur) adapter.cur++;
                    dragAt = to;
                    adapter.notifyDataSetChanged();
                    list.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
                // near the edges the list scrolls by itself
                int edge = dp(56);
                if (e.getY() < edge) list.smoothScrollBy(-dp(24), 40);
                else if (e.getY() > list.getHeight() - edge) list.smoothScrollBy(dp(24), 40);
                return true;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                final int from = dragFrom, to = dragAt;
                dragFrom = dragAt = -1;
                adapter.notifyDataSetChanged();
                if (from >= 0 && to >= 0 && from != to) engine.editQueue(() -> engine.queue().move(from, to));
                return true;
            }
            return true;
        });
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        // pull the top part down → close
        final float[] down = {0};
        OnTouchListener drag = (v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    setTranslationY(Math.max(0, e.getRawY() - down[0]));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (e.getRawY() - down[0] > dp(90)) close();
                    else animate().translationY(0).setDuration(160).start();
                    return true;
                default:
                    return false;
            }
        };
        handle.setOnTouchListener(drag);
        head.setOnTouchListener(drag);
    }

    private int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }

    void open(ViewGroup host) {
        host.addView(this, new LayoutParams(-1, -1));
        setTranslationY(host.getHeight() > 0 ? host.getHeight() : dp(600));
        animate().translationY(0).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        engine.addListener(this);
        onState(engine);
        int cur = engine.queue().currentIndex();
        if (cur > 2) list.setSelection(cur - 2);
    }

    void close() {
        engine.removeListener(this);
        animate().translationY(getHeight()).setDuration(200).withEndAction(() -> {
            if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
            if (onClosed != null) onClosed.run();
        }).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        engine.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onState(Qvk3MusicEngine e) {
        Qvk3Track t = e.current();
        Qvk3Ui.setTextIfChanged(title, t == null ? "Ничего не играет" : t.title);
        Qvk3Ui.setTextIfChanged(artist, t == null ? "" : t.artist);
        if (t != null && !t.cover.equals(coverFor)) {
            coverFor = t.cover;
            cover.setImageDrawable(null);
            Qvk3Images.get(a).load(t.cover, 200, b -> {
                if (t.cover.equals(coverFor) && b != null) cover.setImageDrawable(Qvk3Ui.roundedBitmap(b, dp(10)));
            });
        }
        boolean on = t != null && Qvk3Mine.mine(a, t);
        Qvk3Ui.setIcon(mine, on ? Qvk3Icons.CHECK : Qvk3Icons.PLUS, on ? accent : fg);
        Qvk3Ui.setIcon(shuffle, Qvk3Icons.SHUFFLE, e.queue().shuffle() ? accent : fg);
        if (!dragging && e.duration() > 0) {
            seek.setProgress((int) (e.position() * 1000 / e.duration()));
            Qvk3Ui.setTextIfChanged(pos, Qvk3Track.time(e.position()));
        }
        Qvk3Ui.setTextIfChanged(dur, Qvk3Track.time(e.duration()));
        List<Qvk3Track> q = e.queue().items();
        int cur = e.queue().currentIndex();
        if (dragAt < 0 && (adapter.cur != cur || adapter.items.size() != q.size() || !sameOrder(adapter.items, q))) {
            adapter.items = new ArrayList<>(q);
            adapter.cur = cur;
            adapter.notifyDataSetChanged();
        }
    }

    private static boolean sameOrder(List<Qvk3Track> a, List<Qvk3Track> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private final class Adapter extends BaseAdapter {
        List<Qvk3Track> items = new ArrayList<>();
        int cur = -1;

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int i) {
            return items.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row;
            TextView n, t, s, d;
            if (convert instanceof LinearLayout) {
                row = (LinearLayout) convert;
                n = (TextView) row.getChildAt(0);
                LinearLayout tx = (LinearLayout) row.getChildAt(1);
                t = (TextView) tx.getChildAt(0);
                s = (TextView) tx.getChildAt(1);
                d = (TextView) row.getChildAt(2);
            } else {
                row = new LinearLayout(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(8), dp(16), dp(8));
                n = Qvk3Ui.text(a, "", 13, fg2, false);
                n.setMinWidth(dp(28));
                row.addView(n);
                LinearLayout tx = new LinearLayout(a);
                tx.setOrientation(LinearLayout.VERTICAL);
                t = Qvk3Ui.line(a, "", 15, fg, false);
                s = Qvk3Ui.line(a, "", 13, fg2, false);
                tx.addView(t);
                tx.addView(s);
                row.addView(tx, Qvk3Ui.weight(1));
                d = Qvk3Ui.text(a, "", 12, fg2, false);
                row.addView(d);
            }
            Qvk3Track tr = items.get(i);
            boolean now = i == cur;
            n.setText(now ? "▶" : Integer.toString(i + 1));
            n.setTextColor(now ? accent : fg2);
            t.setText(tr.title);
            t.setTextColor(now ? accent : fg);
            s.setText(tr.artist);
            d.setText(Qvk3Track.time(tr.duration * 1000L));
            boolean held = i == dragAt;
            row.setBackgroundColor(held ? (accent & 0x00FFFFFF) | 0x40000000 : now ? (accent & 0x00FFFFFF) | 0x1F000000 : Color.TRANSPARENT);
            row.setElevation(held ? dp(6) : 0);
            return row;
        }
    }
}
