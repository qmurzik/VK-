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
    private TextView upNext, scrubBubble;
    private Qvk3AmbientView ambient;
    private Boolean ambientPlaying;
    private Boolean lastPlaying;          // cover «breathing»: smaller on pause
    private int pendingDir;               // +1 next, -1 previous: where the new cover slides in from
    private boolean coverDragging;
    static final String REMAINING = "qvk3_player_remaining";

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
        lastPlaying = null;
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
        ambient = null;
        ambientPlaying = null;
        if (layout.equals("modern") && Qvk3MusicSettings.prefs(a).getBoolean(Qvk3MusicSettings.AMBIENT, true)) {
            // «Живой фон»: cover colours drifting (and breathing with the bass when possible)
            ambient = new Qvk3AmbientView(a);
            addView(ambient, new LayoutParams(-1, -1));
            View shade = new View(a);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x22000000, 0x11000000, 0x99000000}));
            addView(shade, new LayoutParams(-1, -1));
        }
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
            // tap: lyrics · double tap: favourite (with a heart burst) · long press: queue
            android.view.GestureDetector coverGestures = new android.view.GestureDetector(a, new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(android.view.MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                    toggleLyrics();
                    return true;
                }

                @Override
                public boolean onDoubleTap(android.view.MotionEvent e) {
                    Qvk3Track t = engine.current();
                    if (t != null && !Qvk3MusicLibrary.get(a).favorite(t.key())) toggleFavorite();
                    heartBurst(stage);
                    return true;
                }

                @Override
                public void onLongPress(android.view.MotionEvent e) {
                    cover.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    Qvk3QueueDialog.show(a);
                }
            });
            // the cover follows the finger sideways; let go far enough and it flies off — next / previous track
            final float[] down = new float[2];
            final int slop = android.view.ViewConfiguration.get(a).getScaledTouchSlop() * 2;
            final android.view.VelocityTracker[] vt = new android.view.VelocityTracker[1];
            cover.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        down[0] = ev.getRawX();
                        down[1] = ev.getRawY();
                        coverDragging = false;
                        if (vt[0] != null) vt[0].recycle();
                        vt[0] = android.view.VelocityTracker.obtain();
                        vt[0].addMovement(ev);
                        v.animate().cancel();
                        break;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        if (vt[0] != null) vt[0].addMovement(ev);
                        float dx = ev.getRawX() - down[0], dy = ev.getRawY() - down[1];
                        if (!coverDragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.4f && !lyricsMode) {
                            coverDragging = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            android.view.MotionEvent c = android.view.MotionEvent.obtain(ev);
                            c.setAction(android.view.MotionEvent.ACTION_CANCEL);
                            coverGestures.onTouchEvent(c); // no tap / long press after a drag
                            c.recycle();
                        }
                        if (coverDragging) {
                            float cw = Math.max(1, v.getWidth());
                            v.setTranslationX(dx);
                            v.setRotation(dx / cw * 7f);
                            v.setAlpha(1f - Math.min(0.5f, Math.abs(dx) / cw * 0.6f));
                            return true;
                        }
                        break;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: {
                        if (!coverDragging) break;
                        coverDragging = false;
                        float dx = ev.getRawX() - down[0], cw = Math.max(1, v.getWidth());
                        float vx = 0;
                        if (vt[0] != null) {
                            vt[0].addMovement(ev);
                            vt[0].computeCurrentVelocity(1000);
                            vx = vt[0].getXVelocity();
                            vt[0].recycle();
                            vt[0] = null;
                        }
                        boolean go = ev.getActionMasked() == android.view.MotionEvent.ACTION_UP
                                && (Math.abs(dx) > cw * 0.28f || (Math.abs(vx) > 1200 && Math.signum(vx) == Math.signum(dx) && Math.abs(dx) > slop));
                        if (go) {
                            int dir = dx < 0 ? 1 : -1;
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                            v.animate().translationX(-dir * cw * 1.25f).rotation(-dir * 10f).alpha(0f).setDuration(170)
                                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
                                        pendingDir = dir;
                                        if (dir > 0) engine.next();
                                        else engine.previous();
                                        // same track again (end of queue, restart of the track): bring the cover back
                                        v.postDelayed(() -> {
                                            if (v.getAlpha() < 0.05f && !coverDragging) slideIn(dir);
                                        }, 450);
                                    }).start();
                        } else {
                            v.animate().translationX(0).rotation(0).alpha(1f).setDuration(320)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
                        }
                        return true;
                    }
                    default:
                        break;
                }
                return coverGestures.onTouchEvent(ev);
            });
            cover.setClickable(true);
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
            if (lyrics != null && lyrics.synced && position < lyrics.lines.size()) {
                // synced text: a tap on a line jumps there
                engine.seekTo(Math.max(0, lyrics.lines.get(position).time - lyricsOffset()));
                manualScrollUntil = 0;
                lastLine = -2;
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                return;
            }
            showLyrics(false); // plain text: a tap hides it
        });
        lyricsList.setOnItemLongClickListener((parent, view, position, id) -> {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            lyricsMenu();
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
                if (!fromUser) return;
                pos.setText(Qvk3Track.time(progress));
                updateDur(progress, s.getMax());
                placeBubble(s, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                userSeeking = true;
                s.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                placeBubble(s, s.getProgress());
                scrubBubble.animate().cancel();
                scrubBubble.setVisibility(VISIBLE);
                scrubBubble.setScaleX(0.6f);
                scrubBubble.setScaleY(0.6f);
                scrubBubble.setAlpha(0f);
                scrubBubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                engine.seekTo(s.getProgress());
                scrubBubble.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(160)
                        .withEndAction(() -> scrubBubble.setVisibility(INVISIBLE)).start();
            }
        });
        // while dragging, the time floats above the thumb in a large bubble
        FrameLayout seekBox = new FrameLayout(a);
        seekBox.setClipChildren(false);
        seekBox.setClipToPadding(false);
        seekBox.addView(seek, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        scrubBubble = Qvk3Ui.text(a, "0:00", 15, onDark ? 0xFF111318 : Color.WHITE, true);
        scrubBubble.setPadding(dp(12), dp(5), dp(12), dp(6));
        scrubBubble.setBackground(Qvk3Ui.rounded(onDark ? Color.WHITE : accent, dp(14)));
        scrubBubble.setElevation(dp(4));
        scrubBubble.setVisibility(INVISIBLE);
        seekBox.addView(scrubBubble, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP));
        col.setClipChildren(false);
        col.addView(seekBox, Qvk3Ui.match());
        LinearLayout times = new LinearLayout(a);
        times.setPadding(dp(8), 0, dp(8), 0);
        pos = Qvk3Ui.text(a, "0:00", 12, fg2, false);
        dur = Qvk3Ui.text(a, "0:00", 12, fg2, false);
        dur.setGravity(Gravity.END);
        dur.setOnClickListener(v -> {
            boolean r = !Qvk3MusicSettings.prefs(a).getBoolean(REMAINING, false);
            Qvk3MusicSettings.prefs(a).edit().putBoolean(REMAINING, r).apply();
            updateDur(engine.position(), engine.duration());
        });
        dur.setContentDescription("Длительность. Нажмите, чтобы показывать, сколько осталось");
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
        controls.addView(Qvk3Ui.icon(a, Qvk3Icons.PREV, fg, 60, 32, "Предыдущий трек", v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            pendingDir = -1;
            engine.previous();
        }), weightBox(60));
        FrameLayout playBox = new FrameLayout(a);
        playBg = new View(a);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(onDark ? Color.WHITE : accent);
        playBg.setBackground(circle);
        playBg.setElevation(dp(6));
        int ps = land ? 52 : 72;
        playBox.addView(playBg, new FrameLayout.LayoutParams(dp(ps), dp(ps), Gravity.CENTER));
        play = Qvk3Ui.icon(a, Qvk3Icons.PLAY, onDark ? 0xFF111318 : Color.WHITE, ps, land ? 26 : 34, "Играть", v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            // a small spring of the round button
            for (View b : new View[]{playBg, play}) {
                b.animate().cancel();
                b.animate().scaleX(0.86f).scaleY(0.86f).setDuration(90).withEndAction(() ->
                        b.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start()).start();
            }
            engine.toggle();
        });
        play.setElevation(dp(7));
        playBox.addView(play, new FrameLayout.LayoutParams(dp(ps), dp(ps), Gravity.CENTER));
        controls.addView(playBox, weightBox(land ? 56 : 80));
        controls.addView(Qvk3Ui.icon(a, Qvk3Icons.NEXT, fg, 60, 32, "Следующий трек", v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            pendingDir = 1;
            engine.next();
        }), weightBox(60));
        controls.addView(repeat, weightBox(52));
        col.addView(controls, Qvk3Ui.match());
        upNext = Qvk3Ui.line(a, "", 13, fg2, false);
        upNext.setGravity(Gravity.CENTER);
        upNext.setPadding(dp(24), 0, dp(24), dp(6));
        upNext.setOnClickListener(v -> Qvk3QueueDialog.show(a));
        if (!land) col.addView(upNext, Qvk3Ui.match());

        // secondary actions
        extra = new LinearLayout(a);
        extra.setGravity(Gravity.CENTER);
        editing = false;
        fillExtra();
        if (!land) col.addView(extra, Qvk3Ui.match());

        gestures = new android.view.GestureDetector(a, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                boolean lyricsScrollable = lyricsMode && lyricsList != null && lyricsList.getVisibility() == VISIBLE && lyricsList.canScrollVertically(-1);
                if (dy > dp(70) && Math.abs(dy) > Math.abs(dx) * 1.6f && vy > 700 && e1.getY() < getHeight() * 0.72f && !lyricsScrollable) {
                    if (onClose != null) onClose.run(); // swipe down collapses the player
                    return true;
                }
                return false; // sideways on the cover: the cover's own drag handles it (follows the finger)
            }
        });
        if (lyricsMode) showLyrics(true);
        onState(engine);
    }

    // ------------------------------------------------------------------ editable action row (IDEA-0004)

    static final String ACTIONS = "qvk3_player_actions";
    private static final String[] DEFAULT_ACTIONS = {"add", "lyrics", "timer", "speed", "eq", "queue"};
    private LinearLayout extra;
    private boolean editing;
    private final java.util.List<android.animation.Animator> jiggles = new java.util.ArrayList<>();

    private java.util.List<String> actionOrder() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String k : Qvk3MusicSettings.prefs(a).getString(ACTIONS, "").split(",")) {
            if (java.util.Arrays.asList(DEFAULT_ACTIONS).contains(k) && !out.contains(k)) out.add(k);
        }
        for (String k : DEFAULT_ACTIONS) if (!out.contains(k)) out.add(k); // new actions appear at the end
        return out;
    }

    private View action(String key) {
        switch (key) {
            case "add":
                return Qvk3Ui.icon(a, Qvk3Icons.PLUS, fg2, 48, 24, "Добавить в мою музыку", v -> addToMine(v));
            case "lyrics":
                return Qvk3Ui.icon(a, Qvk3Icons.LYRICS, fg2, 48, 22, "Текст песни", v -> toggleLyrics());
            case "timer":
                return Qvk3Ui.icon(a, Qvk3Icons.TIMER, fg2, 48, 22, "Таймер сна", v -> sleepDialog());
            case "eq":
                return Qvk3Ui.icon(a, Qvk3Icons.EQ, fg2, 48, 22, "Звук и эквалайзер", v -> Qvk3SoundDialog.show(a));
            case "queue":
                return Qvk3Ui.icon(a, Qvk3Icons.QUEUE, fg2, 48, 22, "Очередь", v -> Qvk3QueueDialog.show(a));
            default:
                speedLabel = Qvk3Ui.text(a, speedLabel == null ? "1×" : speedLabel.getText(), 15, fg2, true);
                speedLabel.setGravity(Gravity.CENTER);
                speedLabel.setMinWidth(dp(48));
                speedLabel.setMinHeight(dp(48));
                speedLabel.setContentDescription("Скорость воспроизведения");
                speedLabel.setBackground(Qvk3Ui.ripple(a, 0, 0x33888888, dp(24)));
                speedLabel.setOnClickListener(v -> speedDialog());
                return speedLabel;
        }
    }

    /**
     * Holding any icon starts editing, as on a phone's home screen: the icons wiggle and can be dragged onto each
     * other's place; a tap on any icon finishes. The order is saved for every player layout.
     */
    private void fillExtra() {
        for (android.animation.Animator j : jiggles) j.cancel();
        jiggles.clear();
        extra.removeAllViews();
        int n = 0;
        for (String key : actionOrder()) {
            View v = action(key);
            v.setTag(key);
            if (editing) {
                v.setOnClickListener(x -> setEditing(false));
                v.setOnLongClickListener(x -> drag(x));
                android.animation.ObjectAnimator j = android.animation.ObjectAnimator.ofFloat(v, View.ROTATION, -2f, 2f);
                j.setDuration(130 + (n % 2) * 20);
                j.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                j.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                j.start();
                jiggles.add(j);
            } else {
                v.setOnLongClickListener(x -> {
                    setEditing(true);
                    return true;
                });
            }
            v.setOnDragListener((target, ev) -> {
                if (ev.getAction() == android.view.DragEvent.ACTION_DROP && ev.getLocalState() instanceof String) {
                    move((String) ev.getLocalState(), (String) target.getTag());
                }
                return true;
            });
            extra.addView(v, weightBox(48));
            n++;
        }
    }

    private void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        fillExtra();
        if (on) Qvk3Ui.toast(a, "Перетащите значки. Коснитесь любого, чтобы закончить");
    }

    private boolean drag(View v) {
        android.content.ClipData data = android.content.ClipData.newPlainText("qvk3-action", String.valueOf(v.getTag()));
        v.startDragAndDrop(data, new View.DragShadowBuilder(v), v.getTag(), 0);
        return true;
    }

    private void move(String from, String to) {
        if (from.equals(to)) return;
        java.util.List<String> order = actionOrder();
        int i = order.indexOf(from), j = order.indexOf(to);
        if (i < 0 || j < 0) return;
        order.remove(i);
        order.add(j, from);
        Qvk3MusicSettings.prefs(a).edit().putString(ACTIONS, android.text.TextUtils.join(",", order)).apply();
        fillExtra();
    }

    private float downX, downY;
    private boolean downValid;

    /**
     * Observes every touch of the player (BUG-0009: onInterceptTouchEvent stopped seeing the gesture when it began on an empty
     * area or a scrolling child took it) to detect swipe-down-to-collapse and swipe-on-cover track changes. A quick flick or a
     * slow drag of a quarter of the screen both collapse the player; nothing is consumed unless the player collapses.
     */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (gestures != null && gestures.onTouchEvent(ev) && ev.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
            downValid = false;
            cancelChildren(ev);
            return true;
        }
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                downValid = downY < getHeight() * 0.72f;
                break;
            case android.view.MotionEvent.ACTION_UP:
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                boolean lyricsScrollable = lyricsMode && lyricsList != null && lyricsList.getVisibility() == VISIBLE && lyricsList.canScrollVertically(-1);
                if (downValid && dy > getHeight() * 0.25f && dy > Math.abs(dx) * 2f && !lyricsScrollable && onClose != null) {
                    downValid = false;
                    cancelChildren(ev);
                    onClose.run(); // slow drag down collapses the player too
                    return true;
                }
                downValid = false;
                break;
            case android.view.MotionEvent.ACTION_POINTER_DOWN:
                downValid = false;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void cancelChildren(android.view.MotionEvent ev) {
        android.view.MotionEvent c = android.view.MotionEvent.obtain(ev);
        c.setAction(android.view.MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(c);
        c.recycle();
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

    /** BUG-0030: «+» adds the playing track to «Моя музыка» in VK. */
    private void addToMine(View button) {
        Qvk3Track t = engine.current();
        if (t == null) return;
        button.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        Qvk3Jobs.io(() -> {
            String m;
            boolean ok = false;
            try {
                Qvk3MusicSource.addToMine(t);
                m = "Добавлено в «Мою музыку»: " + t.title;
                ok = true;
            } catch (Exception e) {
                m = Qvk3Jobs.message(e);
            }
            final boolean done = ok;
            final String msg = m;
            Qvk3Jobs.main(() -> {
                Qvk3Ui.toast(a, msg);
                if (done && button instanceof android.widget.ImageView) {
                    Qvk3Ui.setIcon((android.widget.ImageView) button, Qvk3Icons.CHECK, fg2);
                    Qvk3Motion.pop(button);
                }
            });
        });
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
        breathe(playing);
        if (ambient != null && (ambientPlaying == null || ambientPlaying != playing)) {
            ambientPlaying = playing;
            ambient.setPlaying(playing, e.audioSessionId());
        }
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
        if (!userSeeking) updateDur(e.position(), e.duration());
        Qvk3Track nx = e.queue().peekNext();
        if (upNext != null) {
            upNext.setText(nx == null ? "" : "Далее: " + nx.title + (nx.artist.isEmpty() ? "" : " · " + nx.artist));
            upNext.setVisibility(nx == null || land ? GONE : VISIBLE);
            upNext.setContentDescription(nx == null ? null : "Далее: " + nx.label() + ". Нажмите, чтобы открыть очередь");
        }
        if (t != null && !t.key().equals(coverFor)) {
            boolean first = coverFor.isEmpty();
            coverFor = t.key();
            if (!first && cover != null && !coverDragging) slideIn(pendingDir);
            pendingDir = 0;
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
            int line = lyrics.lineAt(e.position() + lyricsOffset());
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

    /** New cover comes in from the side it was swiped to (or fades in when the track changed elsewhere). */
    private void slideIn(int dir) {
        if (cover == null) return;
        ImageView v = cover;
        v.animate().cancel();
        float w = Math.max(1, v.getWidth());
        v.setTranslationX(dir * w * 0.55f);
        v.setRotation(dir * 6f);
        v.setAlpha(0f);
        v.animate().translationX(0).rotation(0).alpha(1f).setDuration(dir == 0 ? 220 : 340)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** Apple-Music-like: the cover shrinks a little on pause and springs back on play. */
    private void breathe(boolean playing) {
        if (cover == null || (lastPlaying != null && lastPlaying == playing)) return;
        boolean firstTime = lastPlaying == null;
        lastPlaying = playing;
        float s = playing ? 1f : 0.86f;
        if (firstTime || Qvk3Appearance.weakDevice(a)) {
            cover.setScaleX(s);
            cover.setScaleY(s);
            return;
        }
        cover.animate().scaleX(s).scaleY(s).setDuration(playing ? 420 : 300)
                .setInterpolator(playing ? new android.view.animation.OvershootInterpolator(1.4f) : new android.view.animation.DecelerateInterpolator()).start();
    }

    private void updateDur(long position, long duration) {
        if (dur == null) return;
        boolean remaining = Qvk3MusicSettings.prefs(a).getBoolean(REMAINING, false);
        dur.setText(remaining && duration > 0 ? "−" + Qvk3Track.time(Math.max(0, duration - position)) : Qvk3Track.time(duration));
    }

    private void placeBubble(SeekBar s, int progress) {
        if (scrubBubble == null) return;
        scrubBubble.setText(Qvk3Track.time(progress));
        scrubBubble.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int track = s.getWidth() - s.getPaddingLeft() - s.getPaddingRight();
        float x = s.getPaddingLeft() + track * (progress / (float) Math.max(1, s.getMax())) - scrubBubble.getMeasuredWidth() / 2f;
        x = Math.max(0, Math.min(s.getWidth() - scrubBubble.getMeasuredWidth(), x));
        scrubBubble.setTranslationX(x);
        scrubBubble.setTranslationY(-scrubBubble.getMeasuredHeight() - dp(2));
    }

    private int lastBase = 0xFF3A3F4A;
    private android.animation.ValueAnimator tintAnim;

    /**
     * Colours from the cover. Modern / CoverFlow: the background gradient glides (600 ms) from the previous track's
     * colour to the new one instead of jumping. All dark layouts: the progress bar takes a vivid accent made from the
     * cover (saturation and brightness lifted so it stays visible on dark).
     */
    private void tint(Bitmap bmp) {
        int base = bmp == null ? 0xFF3A3F4A : Qvk3Ui.average(bmp);
        if (ambient != null) ambient.setCover(bmp);
        if (onDark && seek != null) Qvk3Ui.styleSeek(a, seek, vivid(base), 0x40FFFFFF);
        if (!(layout.equals("modern") || layout.equals("coverflow"))) return;
        final int from = lastBase, to = base;
        lastBase = base;
        if (tintAnim != null) tintAnim.cancel();
        if (Qvk3Appearance.weakDevice(a)) {
            gradient.setColors(new int[]{Qvk3Ui.deep(to, 0.42f), Qvk3Ui.deep(to, 0.16f), 0xFF0B0C10});
            return;
        }
        android.animation.ArgbEvaluator ev = new android.animation.ArgbEvaluator();
        tintAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        tintAnim.setDuration(600);
        tintAnim.addUpdateListener(an -> {
            int c = (int) ev.evaluate(an.getAnimatedFraction(), from, to);
            gradient.setColors(new int[]{Qvk3Ui.deep(c, 0.42f), Qvk3Ui.deep(c, 0.16f), 0xFF0B0C10});
        });
        tintAnim.start();
    }

    /** A bright, saturated version of a colour for accents on dark backgrounds. */
    static int vivid(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        if (hsv[1] < 0.12f) return 0xFFFFFFFF;           // grey covers: plain white
        hsv[1] = Math.max(hsv[1], 0.55f);
        hsv[2] = Math.max(hsv[2], 0.92f);
        return Color.HSVToColor(hsv);
    }

    /** A heart that pops up over the cover and fades (double tap = like). */
    private void heartBurst(FrameLayout stage) {
        if (stage == null) return;
        ImageView h = new ImageView(a);
        h.setImageDrawable(Qvk3Icons.of(Qvk3Icons.HEART_FILL, 0xFFFF4D6A));
        stage.addView(h, new LayoutParams(dp(96), dp(96), Gravity.CENTER));
        h.setScaleX(0.3f);
        h.setScaleY(0.3f);
        h.setAlpha(0f);
        h.animate().scaleX(1.15f).scaleY(1.15f).alpha(1f).setDuration(180).withEndAction(() ->
                h.animate().scaleX(1f).scaleY(1f).alpha(0f).setStartDelay(260).setDuration(320)
                        .withEndAction(() -> stage.removeView(h)).start()).start();
    }

    // ------------------------------------------------------------------ lyrics: sources and timing

    private String offsetKey() {
        Qvk3Track t = engine.current();
        return t == null ? null : "qvk3_lyr_off_" + t.key();
    }

    /** Per-track timing correction of synced text, ms (positive = text earlier). */
    private long lyricsOffset() {
        String k = offsetKey();
        return k == null ? 0 : Qvk3MusicSettings.prefs(a).getLong(k, 0);
    }

    private void shiftLyrics(long delta) {
        String k = offsetKey();
        if (k == null) return;
        long v = delta == 0 ? 0 : lyricsOffset() + delta;
        if (v == 0) Qvk3MusicSettings.prefs(a).edit().remove(k).apply();
        else Qvk3MusicSettings.prefs(a).edit().putLong(k, v).apply();
        lastLine = -2;
        if (lyricsAdapter != null) lyricsAdapter.notifyDataSetChanged();
        Qvk3Ui.toast(a, v == 0 ? "Текст без сдвига" : "Текст " + (v > 0 ? "раньше" : "позже") + " на " + String.format(java.util.Locale.ROOT, "%.1f", Math.abs(v) / 1000f).replace('.', ',') + " с");
    }

    private void lyricsMenu() {
        Qvk3Track t = engine.current();
        if (t == null) return;
        List<String> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (lyrics != null) {
            items.add("Скопировать текст");
            acts.add(() -> {
                android.content.ClipboardManager cm = a.getSystemService(android.content.ClipboardManager.class);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Текст песни", lyrics.plain()));
                Qvk3Ui.toast(a, "Текст скопирован");
            });
        }
        items.add("Другой текст\nПоискать во всех источниках и выбрать");
        acts.add(this::chooseLyrics);
        if (lyrics != null && lyrics.synced) {
            long off = lyricsOffset();
            items.add("Текст раньше на 0,5 с\nЕсли строки отстают от песни");
            acts.add(() -> shiftLyrics(500));
            items.add("Текст позже на 0,5 с\nЕсли строки спешат");
            acts.add(() -> shiftLyrics(-500));
            if (off != 0) {
                items.add("Убрать сдвиг\nСейчас " + (off > 0 ? "раньше" : "позже") + " на " + String.format(java.util.Locale.ROOT, "%.1f", Math.abs(off) / 1000f).replace('.', ',') + " с");
                acts.add(() -> shiftLyrics(0));
            }
        }
        Qvk3Ui.dialog(a).setTitle("Текст песни").setItems(items.toArray(new String[0]), (d, w) -> acts.get(w).run()).show();
    }

    /** Runs every source and lets the user pick; the choice is remembered for the track. */
    private void chooseLyrics() {
        Qvk3Track t = engine.current();
        if (t == null) return;
        if (!Qvk3MusicSettings.lyricsExternal(a)) Qvk3Ui.toast(a, "Поиск в интернете выключен в настройках QVK Music — ищем только во ВКонтакте и в файлах");
        android.app.AlertDialog wait = Qvk3Ui.dialog(a).setTitle("Ищем текст…").setMessage("ВКонтакте, LRCLIB, NetEase, lyrics.ovh и файлы рядом с треком").setNegativeButton("Отмена", null).create();
        wait.show();
        android.content.Context app = a.getApplicationContext();
        Qvk3Jobs.io(() -> {
            List<Qvk3Lyrics> found;
            if (Qvk3MusicSettings.lyricsExternal(app)) found = Qvk3LyricsManager.searchAll(app, t);
            else {
                found = new ArrayList<>();
                try {
                    Qvk3Lyrics vk = t.hasLyrics ? Qvk3MusicSource.lyrics(t) : null;
                    if (vk != null) found.add(vk);
                } catch (Exception ignored) {
                }
                Qvk3Lyrics side = Qvk3LyricsManager.sidecar(t);
                if (side != null) found.add(side);
            }
            final List<Qvk3Lyrics> res = found;
            Qvk3Jobs.main(() -> {
                if (!wait.isShowing()) return;
                wait.dismiss();
                if (!t.key().equals(coverFor)) return;
                if (res.isEmpty()) {
                    Qvk3Ui.toast(a, "Текст не найден ни в одном источнике");
                    return;
                }
                String[] labels = new String[res.size()];
                for (int i = 0; i < res.size(); i++) {
                    Qvk3Lyrics l = res.get(i);
                    String first = "";
                    for (Qvk3Lyrics.Line ln : l.lines) if (!ln.text.trim().isEmpty()) { first = ln.text.trim(); break; }
                    labels[i] = l.source + (l.synced ? " · синхронный" : " · обычный") + "\n" + (first.length() > 60 ? first.substring(0, 60) + "…" : first);
                }
                Qvk3Ui.dialog(a).setTitle("Выберите текст").setItems(labels, (d, w) -> {
                    Qvk3Lyrics l = res.get(w);
                    Qvk3Jobs.io(() -> Qvk3LyricsManager.choose(app, t, l));
                    List<String> lines = new ArrayList<>();
                    for (Qvk3Lyrics.Line ln : l.lines) lines.add(ln.text);
                    lines.add("");
                    lines.add("Источник: " + l.source);
                    lyricsFor = t.key();
                    setLyricsLines(lines, l);
                }).setNegativeButton("Отмена", null).show();
            });
        });
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
                    if (!Qvk3MusicSettings.lyricsExternal(a)) msg.add("Поиск в интернете выключен в настройках QVK Music → Тексты песен");
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
                boolean active = synced && position == lyrics.lineAt(engine.position() + lyricsOffset());
                // same text size for every line (no reflow jump); the current line glides in by scale and brightness
                tv.setTextColor(fg);
                tv.setTextSize(size);
                tv.setPivotX(0f);
                tv.setPivotY(tv.getHeight() / 2f);
                float scale = active ? 1.06f : 1f, alpha = !synced || active ? 1f : 0.42f;
                Object was = tv.getTag();
                boolean sameRow = was instanceof Integer && (Integer) was == position;
                tv.setTag(position);
                tv.animate().cancel();
                if (sameRow && !Qvk3Appearance.weakDevice(a)) {
                    tv.animate().scaleX(scale).scaleY(scale).alpha(alpha).setDuration(260)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                } else {
                    tv.setScaleX(scale);
                    tv.setScaleY(scale);
                    tv.setAlpha(alpha);
                }
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
