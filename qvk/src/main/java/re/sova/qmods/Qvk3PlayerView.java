package re.sova.qmods;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen player of QVK Music. One view, five layouts (Modern, Classic, AMOLED, Glass, CoverFlow) on the
 * same engine. Modern/Glass tint the background with the cover colour; all controls are vector icons.
 * Changing the layout rebuilds only this view — playback is not restarted.
 */
public final class Qvk3PlayerView extends FrameLayout implements Qvk3MusicEngine.Listener {
    private final Activity a;
    private final Qvk3MusicEngine engine;
    private String layout;
    private boolean onDark;
    private int fg, fg2, accent;
    private ImageView background, cover, play, shuffle, repeat, heart;
    private View playBg;
    private Qvk3CoverFlowView flow;
    private TextView title, artist, pos, dur, info, speedLabel, sub;
    private SeekBar seek;
    private ListView lyricsList;
    private ArrayAdapter<String> lyricsAdapter;
    private Qvk3Lyrics lyrics;
    private String lyricsFor = "";
    private boolean lyricsMode;
    private boolean userSeeking;
    private long manualScrollUntil;
    private int lastLine = -2;
    private String coverFor = "";
    private Runnable onClose;
    private GradientDrawable gradient;
    private boolean land, lastLand;
    private android.view.GestureDetector gestures;

    public Qvk3PlayerView(Activity a) {
        super(a);
        this.a = a;
        this.engine = Qvk3MusicEngine.get(a);
        setClickable(true);
        rebuild();
    }

    public void setOnClose(Runnable r) {
        onClose = r;
    }

    private int dp(float v) {
        return Qvk3Ui.dp(a, v);
    }

    public void rebuild() {
        removeAllViews();
        land = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        // Turning the phone shows the album Cover Flow above the player (Qvk3MusicScreen); with that option off the
        // player keeps the chosen layout in landscape instead of a stretched queue Cover Flow (BUG-0007).
        layout = Qvk3MusicSettings.layout(a);
        lastLand = land;
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, layout.equals("classic") ? "classic" : "modern");
        onDark = !layout.equals("classic") || p.dark;
        fg = onDark ? Color.WHITE : p.text;
        fg2 = onDark ? 0xB3FFFFFF : p.text2;
        accent = p.accent;
        coverFor = "";
        lastLine = -2;

        gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2B3140, 0xFF0D0F14});
        if (layout.equals("amoled") || layout.equals("coverflow")) setBackgroundColor(Color.BLACK); // Cover Flow sits on black
        else if (layout.equals("classic")) setBackgroundColor(p.bg);
        else setBackground(gradient);

        background = new ImageView(a);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        background.setVisibility(GONE);
        if (layout.equals("glass")) {
            background.setVisibility(VISIBLE);
            if (!Qvk3Appearance.weakDevice(a) && Build.VERSION.SDK_INT >= 31) {
                background.setRenderEffect(RenderEffect.createBlurEffect(80, 80, Shader.TileMode.CLAMP));
                background.setAlpha(0.6f);
            } else {
                background.setAlpha(0.22f);
            }
        }
        addView(background, new LayoutParams(-1, -1));
        if (layout.equals("glass")) {
            View shade = new View(a);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x33000000, 0xCC000000}));
            addView(shade, new LayoutParams(-1, -1));
        }

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(16));
        addView(col, new LayoutParams(-1, -1));

        // top bar
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Qvk3Ui.icon(a, Qvk3Icons.DOWN, fg, 48, 26, "Свернуть плеер", v -> {
            if (onClose != null) onClose.run();
        }));
        LinearLayout tt = new LinearLayout(a);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setGravity(Gravity.CENTER);
        TextView now = Qvk3Ui.line(a, "СЕЙЧАС ИГРАЕТ", 11, fg2, true);
        now.setLetterSpacing(0.12f);
        now.setGravity(Gravity.CENTER);
        sub = Qvk3Ui.line(a, "", 13, fg, true);
        sub.setGravity(Gravity.CENTER);
        tt.addView(now, Qvk3Ui.match());
        tt.addView(sub, Qvk3Ui.match());
        top.addView(tt, Qvk3Ui.weight(1));
        top.addView(Qvk3Ui.icon(a, Qvk3Icons.MORE, fg, 48, 24, "Ещё", v -> more()));
        if (!land) col.addView(top, Qvk3Ui.match());

        // stage: cover / coverflow / lyrics
        FrameLayout stage = new FrameLayout(a);
        if (layout.equals("coverflow")) {
            flow = new Qvk3CoverFlowView(a);
            flow.setId(View.generateViewId());
            flow.setColors(fg, fg2, 0x33FFFFFF);
            flow.setSpacingPercent(Qvk3MusicSettings.coverflowSpacing(a));
            flow.setCornerRadius(dp(Qvk3MusicSettings.coverRadiusDp(a)));
            flow.setListener((index, settled) -> {
                if (settled) {
                    // swiping the covers and letting go on another one switches to that track (BUG-0013)
                    if (flow.settledByUser() && index != engine.queue().currentIndex()) engine.playIndex(index);
                    return;
                }
                if (index != engine.queue().currentIndex()) engine.playIndex(index);
                else engine.toggle();
            });
            stage.addView(flow, new LayoutParams(-1, -1));
            cover = null;
        } else {
            flow = null;
            cover = new ImageView(a);
            cover.setScaleType(ImageView.ScaleType.FIT_XY);
            float r = dp(Qvk3MusicSettings.coverRadiusDp(a));
            GradientDrawable ph = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x33FFFFFF, 0x14FFFFFF});
            ph.setCornerRadius(r);
            cover.setBackground(onDark ? ph : Qvk3Ui.rounded(p.surface2, r));
            cover.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline o) {
                    o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                }
            });
            cover.setClipToOutline(true);
            cover.setElevation(dp(layout.equals("amoled") ? 0 : 18));
            cover.setContentDescription("Обложка. Нажмите, чтобы показать текст песни");
            cover.setImageDrawable(placeholderNote());
            int w = getResources().getDisplayMetrics().widthPixels - dp(48);
            int hLimit = (int) (getResources().getDisplayMetrics().heightPixels * (land ? 0.6f : 0.46f));
            int size = Math.round(Math.min(w, hLimit) * Qvk3MusicSettings.coverSize(a));
            stage.addView(cover, new LayoutParams(size, size, Gravity.CENTER));
            cover.setOnClickListener(v -> toggleLyrics());
        }
        lyricsList = new ListView(a);
        lyricsList.setDivider(null);
        lyricsList.setVerticalFadingEdgeEnabled(true);
        lyricsList.setFadingEdgeLength(dp(56));
        lyricsList.setSelector(new android.graphics.drawable.ColorDrawable(0));
        lyricsList.setVisibility(GONE);
        lyricsList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state == SCROLL_STATE_TOUCH_SCROLL) manualScrollUntil = System.currentTimeMillis() + 4000;
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
            }
        });
        lyricsList.setOnItemClickListener((parent, view, position, id) -> {
            showLyrics(false); // a tap on the text hides it
        });
        lyricsList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (lyrics == null) return false;
            android.content.ClipboardManager cm = a.getSystemService(android.content.ClipboardManager.class);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Текст песни", lyrics.plain()));
            Qvk3Ui.toast(a, "Текст скопирован");
            return true;
        });
        if (cover != null) {
            // the lyrics panel takes exactly the cover's frame: same size, same place, same corners
            LayoutParams cl = (LayoutParams) cover.getLayoutParams();
            float lr = dp(Qvk3MusicSettings.coverRadiusDp(a));
            lyricsList.setBackground(Qvk3Ui.rounded(onDark ? 0x66000000 : 0x14000000, lr));
            Qvk3Ui.clipRound(lyricsList, lr);
            lyricsList.setPadding(dp(8), dp(10), dp(8), dp(10));
            lyricsList.setClipToPadding(false);
            stage.addView(lyricsList, new LayoutParams(cl.width, cl.height, Gravity.CENTER));
        } else {
            stage.addView(lyricsList, new LayoutParams(-1, -1));
        }
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        if (land) stage.addView(top, new LayoutParams(-1, -2, Gravity.TOP)); // landscape: header floats over the Cover Flow

        // title + favourite
        LinearLayout meta = new LinearLayout(a);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(dp(8), dp(12), 0, 0);
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        title = Qvk3Ui.line(a, "", 22, fg, true);
        title.setEllipsize(TextUtils.TruncateAt.END); // no marquee: it made the text shake
        artist = Qvk3Ui.line(a, "", 16, fg2, false);
        artist.setOnClickListener(v -> {
            Qvk3Track t = engine.current();
            if (t != null && !t.artist.isEmpty() && Qvk3MusicScreen.of(a) != null) {
                if (onClose != null) onClose.run();
                Qvk3MusicScreen.of(a).openArtist(t.artist);
            }
        });
        texts.addView(title, Qvk3Ui.match());
        texts.addView(artist, Qvk3Ui.match());
        meta.addView(texts, Qvk3Ui.weight(1));
        heart = Qvk3Ui.icon(a, Qvk3Icons.HEART, fg, 48, 26, "В избранное", v -> toggleFavorite());
        meta.addView(heart);
        if (!land) col.addView(meta, Qvk3Ui.match());

        info = Qvk3Ui.line(a, "", 12, fg2, false);
        info.setPadding(dp(8), dp(2), dp(8), 0);
        if (!land) col.addView(info, Qvk3Ui.match());

        seek = new SeekBar(a);
        seek.setContentDescription("Позиция воспроизведения");
        seek.setPadding(dp(8), 0, dp(8), 0);
        Qvk3Ui.styleSeek(a, seek, fg, onDark ? 0x40FFFFFF : 0x22000000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) pos.setText(Qvk3Track.time(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                engine.seekTo(s.getProgress());
            }
        });
        col.addView(seek, Qvk3Ui.match());
        LinearLayout times = new LinearLayout(a);
        times.setPadding(dp(8), 0, dp(8), 0);
        pos = Qvk3Ui.text(a, "0:00", 12, fg2, false);
        dur = Qvk3Ui.text(a, "0:00", 12, fg2, false);
        dur.setGravity(Gravity.END);
        times.addView(pos, Qvk3Ui.weight(1));
        times.addView(dur, Qvk3Ui.weight(1));
        col.addView(times, Qvk3Ui.match());

        // transport
        LinearLayout controls = new LinearLayout(a);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(6), 0, dp(6));
        shuffle = Qvk3Ui.icon(a, Qvk3Icons.SHUFFLE, fg, 52, 22, "Перемешивание", v -> engine.setShuffle(!engine.queue().shuffle()));
        repeat = Qvk3Ui.icon(a, Qvk3Icons.REPEAT, fg, 52, 22, "Повтор", v -> engine.setRepeat((engine.queue().repeat() + 1) % 3));
        controls.addView(shuffle, weightBox(52));
        controls.addView(Qvk3Ui.icon(a, Qvk3Icons.PREV, fg, 60, 32, "Предыдущий трек", v -> engine.previous()), weightBox(60));
        FrameLayout playBox = new FrameLayout(a);
        playBg = new View(a);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(onDark ? Color.WHITE : accent);
        playBg.setBackground(circle);
        playBg.setElevation(dp(6));
        int ps = land ? 52 : 72;
        playBox.addView(playBg, new FrameLayout.LayoutParams(dp(ps), dp(ps), Gravity.CENTER));
        play = Qvk3Ui.icon(a, Qvk3Icons.PLAY, onDark ? 0xFF111318 : Color.WHITE, ps, land ? 26 : 34, "Играть", v -> engine.toggle());
        play.setElevation(dp(7));
        playBox.addView(play, new FrameLayout.LayoutParams(dp(ps), dp(ps), Gravity.CENTER));
        controls.addView(playBox, weightBox(land ? 56 : 80));
        controls.addView(Qvk3Ui.icon(a, Qvk3Icons.NEXT, fg, 60, 32, "Следующий трек", v -> engine.next()), weightBox(60));
        controls.addView(repeat, weightBox(52));
        col.addView(controls, Qvk3Ui.match());

        // secondary actions
        LinearLayout extra = new LinearLayout(a);
        extra.setGravity(Gravity.CENTER);
        extra.addView(Qvk3Ui.icon(a, Qvk3Icons.LYRICS, fg2, 48, 22, "Текст песни", v -> toggleLyrics()), weightBox(48));
        extra.addView(Qvk3Ui.icon(a, Qvk3Icons.TIMER, fg2, 48, 22, "Таймер сна", v -> sleepDialog()), weightBox(48));
        speedLabel = Qvk3Ui.text(a, "1×", 15, fg2, true);
        speedLabel.setGravity(Gravity.CENTER);
        speedLabel.setMinWidth(dp(48));
        speedLabel.setMinHeight(dp(48));
        speedLabel.setContentDescription("Скорость воспроизведения");
        speedLabel.setBackground(Qvk3Ui.ripple(a, 0, 0x33888888, dp(24)));
        speedLabel.setOnClickListener(v -> speedDialog());
        extra.addView(speedLabel, weightBox(48));
        extra.addView(Qvk3Ui.icon(a, Qvk3Icons.EQ, fg2, 48, 22, "Звук и эквалайзер", v -> Qvk3SoundDialog.show(a)), weightBox(48));
        extra.addView(Qvk3Ui.icon(a, Qvk3Icons.QUEUE, fg2, 48, 22, "Очередь", v -> Qvk3QueueDialog.show(a)), weightBox(48));
        if (!land) col.addView(extra, Qvk3Ui.match());

        gestures = new android.view.GestureDetector(a, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                boolean lyricsScrollable = lyricsMode && lyricsList != null && lyricsList.getVisibility() == VISIBLE && lyricsList.canScrollVertically(-1);
                if (dy > dp(90) && Math.abs(dy) > Math.abs(dx) * 1.6f && vy > 900 && e1.getY() < getHeight() * 0.72f && !lyricsScrollable) {
                    if (onClose != null) onClose.run(); // swipe down collapses the player
                    return true;
                }
                if (cover != null && Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) * 1.6f && Math.abs(vx) > 700) {
                    int[] loc = new int[2];
                    cover.getLocationOnScreen(loc);
                    float x = e1.getRawX(), y = e1.getRawY();
                    if (x >= loc[0] && x <= loc[0] + cover.getWidth() && y >= loc[1] && y <= loc[1] + cover.getHeight()) {
                        if (dx < 0) engine.next();
                        else engine.previous();
                        return true;
                    }
                }
                return false;
            }
        });
        if (lyricsMode) showLyrics(true);
        onState(engine);
    }

    /** Observes touches (never consumes them) to detect swipe-down-to-collapse and swipe-on-cover track changes. */
    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
        if (gestures != null) gestures.onTouchEvent(ev);
        return false;
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        boolean nowLand = c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (nowLand != lastLand) rebuild();
    }

    private LinearLayout.LayoutParams weightBox(int dpSize) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(dpSize), 1);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    private android.graphics.drawable.Drawable placeholderNote() {
        android.graphics.drawable.InsetDrawable d = new android.graphics.drawable.InsetDrawable(Qvk3Icons.of(Qvk3Icons.MUSIC, onDark ? 0x55FFFFFF : 0x33000000), dp(96));
        return d;
    }

    private void more() {
        Qvk3Track t = engine.current();
        long[] ab = engine.abRange();
        String[] items = {"Скачать трек", ab[0] >= 0 ? "Выключить повтор A–B" : "Повтор фрагмента A–B", "Оформление плеера", "Найти треки исполнителя", "Скопировать название"};
        Qvk3Ui.dialog(a).setItems(items, (d, w) -> {
            switch (w) {
                case 0:
                    if (t != null) {
                        List<Qvk3Track> one = new ArrayList<>();
                        one.add(t);
                        Qvk3Ui.toast(a, Qvk3MusicDownloads.enqueue(a, one));
                    }
                    break;
                case 1:
                    abAction();
                    break;
                case 2:
                    Qvk3MusicSettingsScreen.playerLook(a, this::rebuild);
                    break;
                case 3:
                    if (t != null && Qvk3MusicScreen.of(a) != null) {
                        if (onClose != null) onClose.run();
                        Qvk3MusicScreen.of(a).openArtist(t.artist);
                    }
                    break;
                default:
                    if (t != null) {
                        android.content.ClipboardManager cm = a.getSystemService(android.content.ClipboardManager.class);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Трек", t.label()));
                    }
            }
        }).show();
    }

    private void toggleFavorite() {
        Qvk3Track t = engine.current();
        if (t == null) return;
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
        boolean on = !lib.favorite(t.key());
        lib.setFavorite(t, on);
        updateHeart(t);
    }

    private void updateHeart(Qvk3Track t) {
        boolean fav = t != null && Qvk3MusicLibrary.get(a).favorite(t.key());
        Qvk3Ui.setIcon(heart, fav ? Qvk3Icons.HEART_FILL : Qvk3Icons.HEART, fav ? 0xFFFF4D6A : fg);
        heart.setContentDescription(fav ? "Убрать из избранного" : "В избранное");
    }

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

    @Override
    public void onState(Qvk3MusicEngine e) {
        Qvk3Track t = e.current();
        title.setText(t == null ? "Ничего не играет" : t.title);
        artist.setText(t == null ? "" : t.artist);
        sub.setText(t == null ? "QVK Music" : (t.album.isEmpty() ? "QVK Music" : t.album));
        boolean playing = e.isPlaying();
        Qvk3Ui.setIcon(play, playing ? Qvk3Icons.PAUSE : Qvk3Icons.PLAY, onDark ? 0xFF111318 : Color.WHITE);
        play.setContentDescription(playing ? "Пауза" : "Играть");
        boolean sh = e.queue().shuffle();
        Qvk3Ui.setIcon(shuffle, Qvk3Icons.SHUFFLE, sh ? accentOn() : fg);
        shuffle.setAlpha(sh ? 1f : 0.55f);
        shuffle.setContentDescription(sh ? "Перемешивание включено" : "Перемешивание выключено");
        int rep = e.queue().repeat();
        Qvk3Ui.setIcon(repeat, rep == Qvk3Queue.REPEAT_ONE ? Qvk3Icons.REPEAT_ONE : Qvk3Icons.REPEAT, rep == Qvk3Queue.REPEAT_OFF ? fg : accentOn());
        repeat.setAlpha(rep == Qvk3Queue.REPEAT_OFF ? 0.55f : 1f);
        repeat.setContentDescription(rep == Qvk3Queue.REPEAT_ONE ? "Повтор трека" : rep == Qvk3Queue.REPEAT_ALL ? "Повтор очереди" : "Повтор выключен");
        speedLabel.setText(e.speed() == 1f ? "1×" : (e.speed() + "×").replace(".0×", "×"));
        StringBuilder s = new StringBuilder();
        if (e.state() == Qvk3MusicEngine.ERROR) s.append(e.error());
        else if (e.state() == Qvk3MusicEngine.BUFFERING) s.append("Загрузка…");
        long[] ab = e.abRange();
        if (ab[0] >= 0) s.append(s.length() > 0 ? " · " : "").append("A–B ").append(Qvk3Track.time(ab[0])).append("–").append(Qvk3Track.time(ab[1]));
        if (e.sleepRemaining() > 0) s.append(s.length() > 0 ? " · " : "").append("Сон через ").append(Qvk3Track.time(e.sleepRemaining()));
        else if (e.sleepAfterTrack()) s.append(s.length() > 0 ? " · " : "").append("Сон после трека");
        if (t != null && !t.local.isEmpty()) s.append(s.length() > 0 ? " · " : "").append("Сохранён на устройстве");
        info.setText(s);
        info.setVisibility(s.length() == 0 ? GONE : VISIBLE);
        info.setTextColor(e.state() == Qvk3MusicEngine.ERROR ? 0xFFFF6B6B : fg2);
        if (!userSeeking) {
            seek.setMax((int) Math.max(1, e.duration()));
            seek.setProgress((int) e.position());
            pos.setText(Qvk3Track.time(e.position()));
        }
        dur.setText(Qvk3Track.time(e.duration()));
        if (t != null && !t.key().equals(coverFor)) {
            coverFor = t.key();
            updateHeart(t);
            lyrics = null;
            lyricsFor = "";
            if (lyricsMode) loadLyrics(t);
            if (flow != null) {
                List<Qvk3CoverFlowView.Item> items = new ArrayList<>();
                for (Qvk3Track q : e.queue().items()) items.add(new Qvk3CoverFlowView.Item(q.cover, q.title, q.artist));
                int restore = flow.takePendingRestore();
                flow.setItems(items, restore >= 0 ? restore : e.queue().currentIndex());
            }
            Qvk3Images.get(a).load(t.cover, 900, bmp -> {
                if (!t.key().equals(coverFor)) return;
                if (cover != null) cover.setImageDrawable(bmp != null ? Qvk3Ui.roundedBitmap(bmp, dp(Qvk3MusicSettings.coverRadiusDp(a))) : placeholderNote());
                if (background.getVisibility() == VISIBLE) background.setImageBitmap(bmp);
                tint(bmp);
            });
            Qvk3Track n = e.queue().peekNext();
            if (n != null) Qvk3Images.get(a).prefetch(n.cover, 900);
        } else if (flow != null && e.queue().currentIndex() >= 0 && flow.selected() != e.queue().currentIndex()) {
            flow.smoothScrollTo(e.queue().currentIndex());
        }
        if (lyricsMode && lyrics != null && lyrics.synced) {
            int line = lyrics.lineAt(e.position());
            if (line != lastLine) {
                lastLine = line;
                lyricsAdapter.notifyDataSetChanged();
                if (line >= 0 && System.currentTimeMillis() > manualScrollUntil) {
                    lyricsList.smoothScrollToPositionFromTop(line, lyricsList.getHeight() / 3, 300);
                }
            }
        }
    }

    private int accentOn() {
        return onDark ? 0xFF8EC5FF : accent;
    }

    /** Modern / CoverFlow: background gradient from the cover colour. */
    private void tint(Bitmap bmp) {
        if (!(layout.equals("modern") || layout.equals("coverflow"))) return;
        int base = bmp == null ? 0xFF3A3F4A : Qvk3Ui.average(bmp);
        gradient.setColors(new int[]{Qvk3Ui.deep(base, 0.42f), Qvk3Ui.deep(base, 0.16f), 0xFF0B0C10});
    }

    private void toggleLyrics() {
        showLyrics(!lyricsMode);
    }

    private void showLyrics(boolean on) {
        lyricsMode = on;
        lyricsList.setVisibility(on ? VISIBLE : GONE);
        boolean over = Qvk3MusicSettings.lyricsOverCover(a);
        // "over the cover": the dimmed cover stays visible under the text; otherwise the panel replaces it
        if (cover != null) cover.setVisibility(on && !over ? INVISIBLE : VISIBLE);
        if (cover != null) cover.setAlpha(on && over ? 0.45f : 1f);
        if (flow != null) flow.setVisibility(on ? INVISIBLE : VISIBLE);
        Qvk3Track t = engine.current();
        if (on && t != null) loadLyrics(t);
    }

    private void loadLyrics(Qvk3Track t) {
        if (t.key().equals(lyricsFor)) return;
        lyricsFor = t.key();
        setLyricsLines(java.util.Collections.singletonList("Ищем текст…"), null);
        Qvk3Jobs.io(() -> {
            Qvk3Lyrics l = null;
            String err = null;
            try {
                l = Qvk3LyricsManager.load(a, t);
            } catch (Exception ex) {
                err = Qvk3Jobs.message(ex);
            }
            final Qvk3Lyrics fl = l;
            final String fe = err;
            Qvk3Jobs.main(() -> {
                if (!t.key().equals(lyricsFor)) return;
                if (fl == null) {
                    List<String> msg = new ArrayList<>();
                    msg.add(fe != null ? "Текст недоступен: " + fe : "Для этого трека нет текста");
                    if (!Qvk3MusicSettings.lyricsExternal(a)) msg.add("Поиск в LRCLIB выключен в настройках QVK Music");
                    setLyricsLines(msg, null);
                } else {
                    List<String> lines = new ArrayList<>();
                    for (Qvk3Lyrics.Line ln : fl.lines) lines.add(ln.text);
                    lines.add("");
                    lines.add("Источник: " + fl.source);
                    setLyricsLines(lines, fl);
                }
            });
        });
    }

    private void setLyricsLines(List<String> lines, Qvk3Lyrics l) {
        lyrics = l;
        lastLine = -2;
        final int size = Qvk3MusicSettings.lyricsSizeSp(a);
        lyricsAdapter = new ArrayAdapter<String>(a, 0, lines) {
            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                TextView tv = convert instanceof TextView ? (TextView) convert : Qvk3Ui.text(a, "", size, fg, true);
                tv.setText(getItem(position));
                tv.setPadding(dp(12), dp(7), dp(12), dp(7));
                boolean synced = lyrics != null && lyrics.synced;
                boolean active = synced && position == lyrics.lineAt(engine.position());
                tv.setTextColor(active ? fg : synced ? (fg & 0x00FFFFFF) | 0x66000000 : fg);
                tv.setTextSize(size + (active ? 1 : 0));
                return tv;
            }
        };
        lyricsList.setAdapter(lyricsAdapter);
    }

    private void speedDialog() {
        final String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        final float[] values = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        Qvk3Ui.dialog(a).setTitle("Скорость").setItems(labels, (d, i) -> engine.setSpeed(values[i])).show();
    }

    private void sleepDialog() {
        final String[] labels = {"Выключить", "15 минут", "30 минут", "45 минут", "1 час", "После текущего трека", "Своё время…"};
        Qvk3Ui.dialog(a).setTitle("Таймер сна").setItems(labels, (d, i) -> {
            switch (i) {
                case 0:
                    engine.setSleep(0, false);
                    break;
                case 5:
                    engine.setSleep(0, true);
                    break;
                case 6: {
                    EditText in = new EditText(a);
                    in.setInputType(InputType.TYPE_CLASS_NUMBER);
                    in.setHint("Минуты");
                    Qvk3Ui.dialog(a).setTitle("Через сколько минут остановить").setView(in)
                            .setPositiveButton("OK", (d2, w) -> {
                                try {
                                    engine.setSleep(Math.max(1, Math.min(600, Integer.parseInt(in.getText().toString()))), false);
                                } catch (NumberFormatException ignored) {
                                    Qvk3Ui.toast(a, "Введите число минут");
                                }
                            }).setNegativeButton("Отмена", null).show();
                    break;
                }
                default:
                    engine.setSleep(new int[]{0, 15, 30, 45, 60}[i], false);
            }
        }).show();
    }

    private long abA = -1;

    private void abAction() {
        long[] ab = engine.abRange();
        if (ab[0] >= 0) {
            engine.setAb(-1, -1);
            abA = -1;
            Qvk3Ui.toast(a, "Повтор фрагмента выключен");
        } else if (abA < 0) {
            abA = engine.position();
            Qvk3Ui.toast(a, "Точка A: " + Qvk3Track.time(abA) + ". Откройте «Ещё» в точке B");
        } else {
            long b = engine.position();
            if (b <= abA + 500) {
                Qvk3Ui.toast(a, "Точка B должна быть позже точки A");
                return;
            }
            engine.setAb(abA, b);
            abA = -1;
        }
    }

    public boolean back() {
        if (lyricsMode) {
            showLyrics(false);
            return true;
        }
        return false;
    }
}
