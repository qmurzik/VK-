package re.sova.qmods;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Album Cover Flow as on the iPhone/iPod: albums of the user's library on black, the album that is playing in the
 * centre (else the last one opened). Tapping the centre album flips it over to its track list; tapping a track
 * plays the album from that track; tapping the list header (or Back) flips it back. A play/pause button sits in the
 * bottom-left corner. Opened by turning the phone sideways anywhere in QVK Music, and from "Моя музыка → Альбомы"
 * when the Cover Flow layout is chosen.
 *
 * Track URLs from the library index may have expired, so an album is re-resolved with VK (audio.getById) right
 * before it starts playing (BUG-0002, point 13).
 */
final class Qvk3CoverFlowScreen extends FrameLayout implements Qvk3MusicEngine.Listener {
    static final String SELECTED = "qvk3_music_coverflow_selected";
    private static final int WHITE = Color.WHITE, GREY = 0xFF9A9A9F, ACCENT = 0xFF4DA3FF;

    final Activity a;
    final boolean byRotation;
    private final Qvk3MusicEngine engine;
    private final Qvk3CoverFlowView flow;
    private final ImageView play;
    private final TextView empty;
    private List<Qvk3MusicLibrary.Group> albums = new ArrayList<>();
    private Set<String> saved = new java.util.HashSet<>();
    private Runnable onClose, onSync;

    // back of the album (track list)
    private FrameLayout card;
    private TextView cardTitle, cardArtist;
    private Qvk3Ring ring;
    private Tracks adapter;
    private Qvk3MusicLibrary.Group open;
    private List<Qvk3Track> openTracks = new ArrayList<>();
    private boolean flipping, albumQueued;
    private ImageView flipCover;

    Qvk3CoverFlowScreen(Activity a, boolean byRotation) {
        super(a);
        this.a = a;
        this.byRotation = byRotation;
        this.engine = Qvk3MusicEngine.get(a);
        setBackgroundColor(Color.BLACK);
        setClickable(true);   // nothing below receives touches
        setFocusable(true);

        flow = new Qvk3CoverFlowView(a);
        flow.setColors(WHITE, GREY, 0xFF2A2A2D);
        flow.setSpacingPercent(Qvk3MusicSettings.coverflowSpacing(a));
        flow.setCornerRadius(0);
        flow.setListener((index, settled) -> {
            Qvk3MusicSettings.prefs(a).edit().putInt(SELECTED, index).apply();
            if (!settled) flipOpen(index);
        });
        LayoutParams fl = new LayoutParams(-1, -1);
        if (!byRotation) fl.topMargin = dp(52);
        fl.bottomMargin = dp(byRotation ? 8 : 64);
        addView(flow, fl);

        empty = Qvk3Ui.text(a, "", 15, GREY, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), 0, dp(32), 0);
        empty.setVisibility(GONE);
        addView(empty, new LayoutParams(-1, -1));

        if (!byRotation) {
            LinearLayout bar = new LinearLayout(a);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(4), dp(4), dp(4), 0);
            bar.addView(Qvk3Ui.icon(a, Qvk3Icons.BACK, WHITE, 48, 24, "Назад", v -> close()));
            TextView t = Qvk3Ui.line(a, "Альбомы", 20, WHITE, true);
            t.setPadding(dp(6), 0, 0, 0);
            bar.addView(t, Qvk3Ui.weight(1));
            addView(bar, new LayoutParams(-1, dp(52), Gravity.TOP));
        }

        // bottom corners: play/pause (left) and the track list of the centre album (right), like the iPod
        play = Qvk3Ui.icon(a, Qvk3Icons.PLAY, WHITE, 52, 26, "Играть", v -> {
            if (engine.current() == null) {
                int c = flow.selected();
                if (c >= 0) flipOpen(c);
            } else {
                engine.toggle();
            }
        });
        LayoutParams pl = new LayoutParams(dp(52), dp(52), Gravity.BOTTOM | Gravity.START);
        pl.leftMargin = dp(10);
        pl.bottomMargin = dp(8);
        addView(play, pl);
        ImageView list = Qvk3Ui.icon(a, Qvk3Icons.LIST, WHITE, 52, 24, "Треки альбома", v -> {
            int c = flow.selected();
            if (c >= 0) flipOpen(c);
        });
        LayoutParams ll = new LayoutParams(dp(52), dp(52), Gravity.BOTTOM | Gravity.END);
        ll.rightMargin = dp(10);
        ll.bottomMargin = dp(8);
        addView(list, ll);

        load();
    }

    private int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }

    void setOnClose(Runnable r) {
        onClose = r;
    }

    /** Called with "load the whole library" when the index has no albums yet. */
    void setOnSync(Runnable r) {
        onSync = r;
    }

    /** (Re)reads albums from the library index; keeps the playing / last opened album centred. */
    void load() {
        Qvk3Jobs.io(() -> {
            Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
            List<Qvk3MusicLibrary.Group> list = lib.albums("my");
            Set<String> done = lib.albumsSaved("my");
            Qvk3Jobs.main(() -> show(list, done));
        });
    }

    private void show(List<Qvk3MusicLibrary.Group> list, Set<String> done) {
        albums = list;
        saved = done;
        if (albums.isEmpty()) {
            flow.setVisibility(GONE);
            empty.setVisibility(VISIBLE);
            empty.setText("Альбомов пока нет: Cover Flow строится по медиатеке ВК.\n\nНажмите, чтобы загрузить медиатеку.");
            empty.setOnClickListener(v -> {
                if (onSync != null) onSync.run();
            });
            return;
        }
        flow.setVisibility(VISIBLE);
        empty.setVisibility(GONE);
        List<Qvk3CoverFlowView.Item> items = new ArrayList<>();
        for (Qvk3MusicLibrary.Group g : albums) items.add(new Qvk3CoverFlowView.Item(g.cover, g.name, label(g)));
        int sel = Qvk3MusicSettings.prefs(a).getInt(SELECTED, 0);
        int playing = playingAlbum();
        if (playing >= 0) sel = playing;
        flow.setItems(items, Math.max(0, Math.min(sel, items.size() - 1)));
    }

    private String label(Qvk3MusicLibrary.Group g) {
        return g.artist + (saved.contains(g.name) ? "   ⬇ на устройстве" : "");
    }

    private int playingAlbum() {
        Qvk3Track t = engine.current();
        if (t == null || t.album.isEmpty()) return -1;
        for (int i = 0; i < albums.size(); i++) if (albums.get(i).name.equals(t.album)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ flip to the track list

    private void flipOpen(int index) {
        if (flipping || card != null || index < 0 || index >= albums.size()) return;
        open = albums.get(index);
        flipping = true;
        Qvk3Jobs.io(() -> {
            List<Qvk3Track> tracks = Qvk3MusicLibrary.get(a).group("my", "album", open.name);
            Qvk3Jobs.main(() -> {
                openTracks = tracks;
                buildCard();
                animateOpen();
            });
        });
    }

    private void buildCard() {
        boolean land = getWidth() > getHeight();
        int w = getWidth(), h = getHeight();
        int cw = land ? Math.round(Math.min(w * 0.62f, h * 1.15f)) : Math.round(w * 0.9f);
        int maxH = land ? Math.round(h * 0.86f) : Math.round(h * 0.72f);
        int ch = Math.min(maxH, dp(70) + openTracks.size() * dp(45) + dp(8)); // short albums get a short card
        card = new FrameLayout(a);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2E2E31, 0xFF111113});
        bg.setCornerRadius(dp(6));
        bg.setStroke(Math.max(1, dp(1)), 0x40FFFFFF);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(dp(12));
        card.setCameraDistance(dp(1200) * getResources().getDisplayMetrics().density);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(8), dp(10));
        head.setBackground(Qvk3Ui.ripple(a, 0x14FFFFFF, 0x33FFFFFF, 0));
        head.setContentDescription("Перевернуть обратно к обложкам");
        head.setOnClickListener(v -> flipClose());
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        cardTitle = Qvk3Ui.line(a, open.name, 17, WHITE, true);
        cardTitle.setGravity(Gravity.CENTER);
        cardArtist = Qvk3Ui.line(a, open.artist + " · " + openTracks.size() + " " + tracksWord(openTracks.size()), 13, GREY, false);
        cardArtist.setGravity(Gravity.CENTER);
        texts.addView(cardTitle, Qvk3Ui.match());
        texts.addView(cardArtist, Qvk3Ui.match());
        head.addView(texts, Qvk3Ui.weight(1));
        ring = new Qvk3Ring(a, WHITE, ACCENT);
        ring.setOnClickListener(v -> downloadAlbum());
        head.addView(ring, new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(head, Qvk3Ui.match());
        View line = new View(a);
        line.setBackgroundColor(0x33FFFFFF);
        col.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.7f))));

        ListView lv = new ListView(a);
        lv.setDivider(new android.graphics.drawable.ColorDrawable(0x1FFFFFFF));
        lv.setDividerHeight(Math.max(1, dp(0.5f)));
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0x33FFFFFF));
        lv.setVerticalFadingEdgeEnabled(true);
        adapter = new Tracks();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) -> playAlbum(pos));
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        card.addView(col, new LayoutParams(-1, -1));

        LayoutParams cl = new LayoutParams(cw, ch, Gravity.CENTER);
        if (!byRotation) cl.topMargin = dp(26);
        addView(card, cl);
        card.setVisibility(INVISIBLE);
        refreshRing();
    }

    private static String tracksWord(int n) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return "трек";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "трека";
        return "треков";
    }

    /** The centre cover turns 90° (and grows towards the card), then the card turns in from the other side. */
    private void animateOpen() {
        RectF r = new RectF();
        flow.centerCoverRect(r);
        r.offset(flow.getLeft(), flow.getTop());
        flipCover = new ImageView(a);
        flipCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Qvk3CoverFlowView.Item it = flow.item(flow.selected());
        Bitmap b = it == null ? null : Qvk3Images.get(a).cached(it.cover, 600);
        if (b == null && it != null) b = Qvk3Images.get(a).cached(it.cover, 300);
        if (b != null) flipCover.setImageBitmap(b);
        else flipCover.setBackgroundColor(0xFF2A2A2D);
        addView(flipCover, new LayoutParams(Math.round(r.width()), Math.round(r.height())));
        flipCover.setX(r.left);
        flipCover.setY(r.top);
        flipCover.setCameraDistance(card.getCameraDistance());
        card.post(() -> {
            float sx = card.getWidth() / Math.max(1f, r.width()), sy = card.getHeight() / Math.max(1f, r.height());
            float tx = card.getX() + card.getWidth() / 2f - (r.left + r.width() / 2f);
            float ty = card.getY() + card.getHeight() / 2f - (r.top + r.height() / 2f);
            flow.animate().alpha(0.25f).setDuration(320).start();
            flipCover.animate().rotationY(90f).scaleX(sx * 0.5f + 0.5f).scaleY(sy * 0.5f + 0.5f).translationXBy(tx / 2f).translationYBy(ty / 2f)
                    .setDuration(170).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
                        removeView(flipCover);
                        card.setRotationY(-90f);
                        card.setVisibility(VISIBLE);
                        card.animate().rotationY(0f).setDuration(190).setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .withEndAction(() -> flipping = false).start();
                    }).start();
        });
    }

    void flipClose() {
        if (card == null || flipping) return;
        flipping = true;
        final FrameLayout c = card;
        c.animate().rotationY(90f).setDuration(170).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
            removeView(c);
            card = null;
            adapter = null;
            ring = null;
            flow.animate().alpha(1f).setDuration(200).start();
            flipping = false;
        }).start();
    }

    /** Back: flip the card back first; returns false when this screen should close. */
    boolean back() {
        if (card != null) {
            flipClose();
            return true;
        }
        return false;
    }

    void close() {
        if (onClose != null) onClose.run();
    }

    // ------------------------------------------------------------------ playback

    private void playAlbum(int index) {
        if (index < 0 || index >= openTracks.size()) return;
        Qvk3Track t = openTracks.get(index);
        if (t.restricted != 0 && t.local.isEmpty()) {
            Qvk3Ui.toast(a, t.unplayableReason());
            return;
        }
        final List<Qvk3Track> stored = new ArrayList<>(openTracks);
        Qvk3Jobs.io(() -> {
            List<Qvk3Track> list = fresh(stored);
            int start = 0;
            for (int i = 0; i < list.size(); i++) if (list.get(i).key().equals(t.key())) start = i;
            final int s = start;
            Qvk3Jobs.main(() -> {
                if (list.isEmpty()) {
                    Qvk3Ui.toast(a, "Треки альбома недоступны");
                    return;
                }
                engine.play(list, s);
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        });
    }

    /** Index URLs expire: ask VK for current ones (50 per request), keep saved files, drop tracks VK no longer gives. */
    private List<Qvk3Track> fresh(List<Qvk3Track> stored) {
        Map<String, Qvk3Track> byKey = new HashMap<>();
        try {
            for (int i = 0; i < stored.size(); i += 50) {
                List<Qvk3Track> part = new ArrayList<>();
                for (Qvk3Track x : stored.subList(i, Math.min(stored.size(), i + 50))) if (x.id != 0) part.add(x);
                for (Qvk3Track f : Qvk3MusicSource.byIds(part)) byKey.put(f.key(), f);
            }
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.coverflow.fresh", e);
            return stored; // offline: the engine refreshes a link itself if it fails
        }
        List<Qvk3Track> out = new ArrayList<>();
        for (Qvk3Track x : stored) {
            Qvk3Track f = byKey.get(x.key());
            if (f == null) {
                if (!x.local.isEmpty()) out.add(x);
                continue;
            }
            out.add(x.local.isEmpty() ? f : f.withLocal(x.local));
        }
        return out;
    }

    private void downloadAlbum() {
        if (openTracks.isEmpty()) return;
        albumQueued = true;
        Qvk3Ui.toast(a, Qvk3MusicDownloads.enqueue(a, new ArrayList<>(openTracks)));
        if (ring != null) ring.set(0.02f, true);
        refreshRing();
    }

    /** Ring of the opened album: share of its available tracks saved on the device. */
    void refreshRing() {
        if (ring == null || open == null) return;
        final List<Qvk3Track> tracks = openTracks;
        Qvk3Jobs.io(() -> {
            Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
            int total = 0, done = 0;
            for (Qvk3Track t : tracks) {
                if (t.id == 0 || t.restricted != 0) continue;
                total++;
                if (lib.downloadedLocation(t.key()) != null) done++;
            }
            final float pr = total == 0 ? 0f : done / (float) total;
            final boolean active = Qvk3MusicDownloads.running() && pr < 1f && albumQueued;
            Qvk3Jobs.main(() -> {
                if (ring != null && tracks == openTracks) ring.set(pr, active);
                if (pr >= 1f && open != null && !saved.contains(open.name)) load();
            });
        });
    }

    // ------------------------------------------------------------------ engine

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        engine.addListener(this);
        onState(engine);
    }

    @Override
    protected void onDetachedFromWindow() {
        engine.removeListener(this);
        super.onDetachedFromWindow();
    }

    private String lastPlaying = "";

    @Override
    public void onState(Qvk3MusicEngine e) {
        boolean playing = e.isPlaying();
        Qvk3Ui.setIcon(play, playing ? Qvk3Icons.PAUSE : Qvk3Icons.PLAY, WHITE);
        play.setContentDescription(playing ? "Пауза" : "Играть");
        Qvk3Track t = e.current();
        String k = t == null ? "" : t.key();
        if (!k.equals(lastPlaying)) {
            lastPlaying = k;
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    }

    // ------------------------------------------------------------------ track rows

    private final class Tracks extends BaseAdapter {
        @Override
        public int getCount() {
            return openTracks.size();
        }

        @Override
        public Object getItem(int i) {
            return openTracks.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            LinearLayout row;
            TextView num, title, dur;
            if (v == null) {
                row = new LinearLayout(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(44));
                row.setPadding(dp(12), dp(6), dp(14), dp(6));
                num = Qvk3Ui.text(a, "", 14, GREY, false);
                num.setGravity(Gravity.END);
                num.setMinWidth(dp(26));
                title = Qvk3Ui.text(a, "", 15, WHITE, false);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                title.setPadding(dp(10), 0, dp(10), 0);
                dur = Qvk3Ui.text(a, "", 14, GREY, false);
                dur.setGravity(Gravity.END);   // durations always aligned to the right edge
                dur.setMinWidth(dp(56));
                row.addView(num);
                row.addView(title, Qvk3Ui.weight(1));
                row.addView(dur);
                row.setTag(new TextView[]{num, title, dur});
            } else {
                row = (LinearLayout) v;
            }
            TextView[] tv = (TextView[]) row.getTag();
            Qvk3Track t = openTracks.get(i);
            Qvk3Track cur = engine.current();
            boolean now = cur != null && cur.key().equals(t.key());
            tv[0].setText(now ? "▶" : String.valueOf(i + 1));
            tv[0].setTextColor(now ? ACCENT : GREY);
            tv[1].setText(t.title);
            tv[1].setTextColor(t.playable() || t.restricted == 0 ? WHITE : 0x66FFFFFF);
            tv[2].setText((t.local.isEmpty() ? "" : "⬇  ") + Qvk3Track.time(t.duration * 1000L));
            row.setContentDescription((i + 1) + ". " + t.title + ", " + Qvk3Track.time(t.duration * 1000L) + (t.local.isEmpty() ? "" : ", на устройстве"));
            return row;
        }
    }
}
