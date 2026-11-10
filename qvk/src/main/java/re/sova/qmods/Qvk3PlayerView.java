package re.sova.qmods;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen player of QVK Music. One view, five layouts (Modern, Classic, AMOLED, Glass, CoverFlow) on
 * the same engine; switching layout or cover/lyrics options rebuilds only this view — playback is not
 * restarted. Lyrics (synced or plain) can be shown over the cover or full screen; manual scrolling pauses
 * auto-scroll for a few seconds.
 */
public final class Qvk3PlayerView extends FrameLayout implements Qvk3MusicEngine.Listener {
    private final Activity a;
    private final Qvk3MusicEngine engine;
    private Qvk3Ui.Palette p;
    private String layout;
    private ImageView background, cover;
    private Qvk3CoverFlowView flow;
    private TextView title, artist, pos, dur, play, shuffle, repeat, info;
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

    /** Rebuilds the layout from current settings (called after the user changes player appearance). */
    public void rebuild() {
        removeAllViews();
        layout = Qvk3MusicSettings.layout(a);
        p = Qvk3Ui.palette(a, layout);
        coverFor = "";
        lastLine = -2;
        setBackgroundColor(p.bg);
        background = new ImageView(a);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(background, new LayoutParams(-1, -1));
        if (layout.equals("glass") && !Qvk3Appearance.weakDevice(a) && Build.VERSION.SDK_INT >= 31) {
            background.setRenderEffect(RenderEffect.createBlurEffect(60, 60, Shader.TileMode.CLAMP));
            background.setAlpha(0.55f);
        } else if (layout.equals("glass")) {
            background.setAlpha(0.25f); // weak device or Android < 12: dimmed cover instead of blur
        } else {
            background.setVisibility(GONE);
        }

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 20);
        col.setPadding(pad, Qvk3Ui.dp(a, 8), pad, Qvk3Ui.dp(a, 16));
        addView(col, new LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Qvk3Ui.glyph(a, p, "⌄", "Свернуть плеер", 26, v -> {
            if (onClose != null) onClose.run();
        }));
        TextView heading = Qvk3Ui.line(a, Qvk3MusicSettings.LAYOUT_TITLES[indexOf(layout)], 14, p.text2, true);
        heading.setGravity(Gravity.CENTER);
        top.addView(heading, Qvk3Ui.weight(1));
        top.addView(Qvk3Ui.glyph(a, p, "☰", "Очередь", 20, v -> Qvk3QueueDialog.show(a)));
        col.addView(top, Qvk3Ui.match());

        FrameLayout stage = new FrameLayout(a);
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (layout.equals("coverflow")) {
            flow = new Qvk3CoverFlowView(a);
            flow.setId(View.generateViewId());
            flow.setColors(p.text, p.text2, p.surface2);
            flow.setSpacingPercent(Qvk3MusicSettings.coverflowSpacing(a));
            flow.setCornerRadius(Qvk3Ui.dp(a, Qvk3MusicSettings.coverRadiusDp(a)));
            // Selecting a track in CoverFlow starts playback (QVK 2.x only selected it and stayed paused).
            flow.setListener((index, settled) -> {
                if (!settled && index != engine.queue().currentIndex()) engine.playIndex(index);
                else if (!settled) engine.toggle();
            });
            stage.addView(flow, new LayoutParams(-1, -1));
        } else {
            flow = null;
            cover = new ImageView(a);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            float r = Qvk3Ui.dp(a, Qvk3MusicSettings.coverRadiusDp(a));
            cover.setBackground(Qvk3Ui.rounded(p.surface2, r));
            Qvk3Ui.clipRound(cover, r);
            cover.setContentDescription("Обложка");
            int size = Math.round(Math.min(getResources().getDisplayMetrics().widthPixels - pad * 2,
                    getResources().getDisplayMetrics().heightPixels * (landscape ? 0.55f : 0.45f)) * Qvk3MusicSettings.coverSize(a));
            LayoutParams lp = new LayoutParams(size, size, Gravity.CENTER);
            stage.addView(cover, lp);
            cover.setOnClickListener(v -> toggleLyrics());
        }
        lyricsList = new ListView(a);
        lyricsList.setDivider(null);
        lyricsList.setVerticalFadingEdgeEnabled(true);
        lyricsList.setFadingEdgeLength(Qvk3Ui.dp(a, 48));
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
            if (lyrics != null && lyrics.synced && position < lyrics.lines.size()) engine.seekTo(lyrics.lines.get(position).time);
        });
        lyricsList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (lyrics == null) return false;
            android.content.ClipboardManager cm = a.getSystemService(android.content.ClipboardManager.class);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Текст песни", lyrics.plain()));
            Qvk3Ui.toast(a, "Текст скопирован");
            return true;
        });
        if (Qvk3MusicSettings.lyricsOverCover(a) && layout.equals("coverflow") == false) {
            GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xAA000000, 0xAA000000});
            lyricsList.setBackground(shade);
        }
        stage.addView(lyricsList, new LayoutParams(-1, -1));
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        title = Qvk3Ui.line(a, "", 21, p.text, true);
        artist = Qvk3Ui.line(a, "", 16, p.text2, false);
        artist.setOnClickListener(v -> {
            Qvk3Track t = engine.current();
            if (t != null && !t.artist.isEmpty() && a instanceof Qvk3MusicActivity) {
                if (onClose != null) onClose.run();
                ((Qvk3MusicActivity) a).openArtist(t.artist);
            }
        });
        col.addView(title, Qvk3Ui.match());
        col.addView(artist, Qvk3Ui.match());
        info = Qvk3Ui.line(a, "", 13, p.text2, false);
        col.addView(info, Qvk3Ui.match());

        seek = new SeekBar(a);
        seek.setContentDescription("Позиция воспроизведения");
        seek.getProgressDrawable().setTint(p.accent);
        seek.getThumb().setTint(p.accent);
        if ("thick".equals(Qvk3MusicSettings.progressStyle(a))) seek.setScaleY(1.6f);
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
        pos = Qvk3Ui.text(a, "0:00", 12, p.text2, false);
        dur = Qvk3Ui.text(a, "0:00", 12, p.text2, false);
        dur.setGravity(Gravity.END);
        times.addView(pos, Qvk3Ui.weight(1));
        times.addView(dur, Qvk3Ui.weight(1));
        col.addView(times, Qvk3Ui.match());

        LinearLayout controls = new LinearLayout(a);
        controls.setGravity(Gravity.CENTER);
        shuffle = Qvk3Ui.glyph(a, p, "⤮", "Перемешивание", 22, v -> engine.setShuffle(!engine.queue().shuffle()));
        repeat = Qvk3Ui.glyph(a, p, "↻", "Повтор", 22, v -> engine.setRepeat((engine.queue().repeat() + 1) % 3));
        play = Qvk3Ui.glyph(a, p, "▶", "Играть", 34, v -> engine.toggle());
        play.setTextColor(p.onAccent);
        play.setBackground(Qvk3Ui.ripple(a, p.accent, 0x33FFFFFF, Qvk3Ui.dp(a, 36)));
        int big = Qvk3Ui.dp(a, 72);
        controls.addView(shuffle);
        controls.addView(Qvk3Ui.glyph(a, p, "⏮", "Предыдущий трек", 28, v -> engine.previous()));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(big, big);
        plp.leftMargin = plp.rightMargin = Qvk3Ui.dp(a, 12);
        controls.addView(play, plp);
        controls.addView(Qvk3Ui.glyph(a, p, "⏭", "Следующий трек", 28, v -> engine.next()));
        controls.addView(repeat);
        col.addView(controls, Qvk3Ui.match());

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(a);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        hs.addView(actions);
        actions.addView(chip("Текст", v -> toggleLyrics()));
        actions.addView(chip("Скорость", v -> speedDialog()));
        actions.addView(chip("Таймер сна", v -> sleepDialog()));
        actions.addView(chip("A–B", v -> abAction()));
        actions.addView(chip("Звук", v -> Qvk3SoundDialog.show(a)));
        actions.addView(chip("Скачать", v -> {
            Qvk3Track t = engine.current();
            if (t != null) {
                List<Qvk3Track> one = new ArrayList<>();
                one.add(t);
                Qvk3Ui.toast(a, Qvk3MusicDownloads.enqueue(a, one));
            }
        }));
        actions.addView(chip("★ Избранное", v -> {
            Qvk3Track t = engine.current();
            if (t == null) return;
            Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
            boolean on = !lib.favorite(t.key());
            lib.setFavorite(t, on);
            Qvk3Ui.toast(a, on ? "Добавлено в избранное QVK" : "Убрано из избранного QVK");
        }));
        actions.addView(chip("Оформление", v -> Qvk3MusicSettingsScreen.playerLook(a, this::rebuild)));
        col.addView(hs, Qvk3Ui.match());
        if (lyricsMode) showLyrics(true);
        onState(engine);
    }

    private static int indexOf(String layout) {
        for (int i = 0; i < Qvk3MusicSettings.LAYOUTS.length; i++) if (Qvk3MusicSettings.LAYOUTS[i].equals(layout)) return i;
        return 0;
    }

    private TextView chip(String label, View.OnClickListener l) {
        TextView t = Qvk3Ui.button(a, p, label, false, l);
        LinearLayout.LayoutParams lp = Qvk3Ui.wrap();
        lp.rightMargin = Qvk3Ui.dp(a, 8);
        lp.topMargin = Qvk3Ui.dp(a, 8);
        t.setLayoutParams(lp);
        return t;
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
        boolean playing = e.isPlaying();
        play.setText(playing ? "⏸" : "▶");
        play.setContentDescription(playing ? "Пауза" : "Играть");
        shuffle.setAlpha(e.queue().shuffle() ? 1f : 0.45f);
        shuffle.setTextColor(e.queue().shuffle() ? p.accent : p.text);
        int rep = e.queue().repeat();
        repeat.setText(rep == Qvk3Queue.REPEAT_ONE ? "🔂" : "↻");
        repeat.setAlpha(rep == Qvk3Queue.REPEAT_OFF ? 0.45f : 1f);
        repeat.setTextColor(rep == Qvk3Queue.REPEAT_OFF ? p.text : p.accent);
        repeat.setContentDescription(rep == Qvk3Queue.REPEAT_ONE ? "Повтор трека" : rep == Qvk3Queue.REPEAT_ALL ? "Повтор очереди" : "Повтор выключен");
        StringBuilder s = new StringBuilder();
        if (e.state() == Qvk3MusicEngine.ERROR) s.append(e.error());
        else if (e.state() == Qvk3MusicEngine.BUFFERING) s.append("Загрузка…");
        if (e.speed() != 1f) s.append(s.length() > 0 ? " · " : "").append("×").append(e.speed());
        long[] ab = e.abRange();
        if (ab[0] >= 0) s.append(s.length() > 0 ? " · " : "").append("A–B ").append(Qvk3Track.time(ab[0])).append("–").append(Qvk3Track.time(ab[1]));
        if (e.sleepRemaining() > 0) s.append(s.length() > 0 ? " · " : "").append("сон через ").append(Qvk3Track.time(e.sleepRemaining()));
        else if (e.sleepAfterTrack()) s.append(s.length() > 0 ? " · " : "").append("сон после трека");
        if (t != null && !t.local.isEmpty()) s.append(s.length() > 0 ? " · " : "").append("сохранён на устройстве");
        info.setText(s);
        info.setTextColor(e.state() == Qvk3MusicEngine.ERROR ? 0xFFE64646 : p.text2);
        if (!userSeeking) {
            seek.setMax((int) Math.max(1, e.duration()));
            seek.setProgress((int) e.position());
            pos.setText(Qvk3Track.time(e.position()));
        }
        dur.setText(Qvk3Track.time(e.duration()));
        if (t != null && !t.key().equals(coverFor)) {
            coverFor = t.key();
            lyrics = null;
            lyricsFor = "";
            if (lyricsMode) loadLyrics(t);
            if (flow != null) {
                List<Qvk3CoverFlowView.Item> items = new ArrayList<>();
                for (Qvk3Track q : e.queue().items()) items.add(new Qvk3CoverFlowView.Item(q.cover, q.title, q.artist));
                int restore = flow.takePendingRestore();
                flow.setItems(items, restore >= 0 ? restore : e.queue().currentIndex());
            } else if (cover != null) {
                cover.setImageBitmap(null);
                Qvk3Images.get(a).load(t.cover, 900, bmp -> {
                    if (t.key().equals(coverFor)) {
                        cover.setImageBitmap(bmp);
                        if (background.getVisibility() == VISIBLE) background.setImageBitmap(bmp);
                    }
                });
                Qvk3Track n = e.queue().peekNext();
                if (n != null) Qvk3Images.get(a).prefetch(n.cover, 900);
            }
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

    private void toggleLyrics() {
        showLyrics(!lyricsMode);
    }

    private void showLyrics(boolean on) {
        lyricsMode = on;
        lyricsList.setVisibility(on ? VISIBLE : GONE);
        boolean over = Qvk3MusicSettings.lyricsOverCover(a);
        if (cover != null) cover.setVisibility(on && !over ? INVISIBLE : VISIBLE);
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
                    if (!Qvk3MusicSettings.lyricsExternal(a)) msg.add("Внешний поиск (LRCLIB) выключен в настройках QVK Music");
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
        final boolean over = Qvk3MusicSettings.lyricsOverCover(a) && cover != null;
        lyricsAdapter = new ArrayAdapter<String>(a, 0, lines) {
            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                TextView tv = convert instanceof TextView ? (TextView) convert : Qvk3Ui.text(a, "", size, p.text, false);
                tv.setText(getItem(position));
                tv.setTextIsSelectable(false);
                tv.setPadding(Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 6), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 6));
                boolean active = lyrics != null && lyrics.synced && position == lyrics.lineAt(engine.position());
                int base = over ? Color.WHITE : p.text;
                tv.setTextColor(active ? (over ? Color.WHITE : p.accent) : (lyrics != null && lyrics.synced ? (base & 0x00FFFFFF) | 0x99000000 : base));
                tv.setTextSize(size + (active ? 2 : 0));
                return tv;
            }
        };
        lyricsList.setAdapter(lyricsAdapter);
    }

    private void speedDialog() {
        final String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        final float[] values = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        Qvk3Ui.dialog(a).setTitle("Скорость воспроизведения")
                .setItems(labels, (d, i) -> engine.setSpeed(values[i])).show();
    }

    private void sleepDialog() {
        final String[] labels = {"Выключить таймер", "15 минут", "30 минут", "45 минут", "1 час", "После текущего трека", "Своё время…"};
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
            Qvk3Ui.toast(a, "Точка A: " + Qvk3Track.time(abA) + ". Нажмите ещё раз в точке B");
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

    /** For the activity: whether a back press should first leave lyrics mode. */
    public boolean back() {
        if (lyricsMode) {
            showLyrics(false);
            return true;
        }
        return false;
    }

    public Bitmap currentCover() {
        return null;
    }
}
