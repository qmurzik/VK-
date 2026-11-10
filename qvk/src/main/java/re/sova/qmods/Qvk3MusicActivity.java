package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * QVK Music — replaces VK 8.100's music section. Tabs: Главная, Моя музыка, Плейлисты, Поиск, Загрузки.
 *
 * Lists are virtualised (ListView) and paginated: VK pages are fetched on demand and written to the
 * offline index, so thousands of tracks never sit in memory at once and the section opens instantly offline.
 * The activity holds no playback state: everything lives in {@link Qvk3MusicEngine}, so rotation, process
 * death and switching away never interrupt music.
 */
public final class Qvk3MusicActivity extends Activity implements Qvk3MusicEngine.Listener {
    static final String EXTRA_TAB = "qvk3_tab";
    private static final int REQ_IMPORT = 41;
    static final String[] TABS = {"Главная", "Моя музыка", "Плейлисты", "Поиск", "Загрузки"};

    private Qvk3Ui.Palette p;
    private Qvk3MusicEngine engine;
    private FrameLayout root;
    private LinearLayout tabs;
    private TextView screenTitle;
    private EditText search;
    private ListView list;
    private Rows rows;
    private LinearLayout mini;
    private ImageView miniCover;
    private TextView miniTitle, miniArtist, miniPlay;
    private Qvk3PlayerView player;
    private int tab;
    private final Deque<Runnable> back = new ArrayDeque<>();
    private Runnable current;
    private Feed feed;
    private String miniCoverFor = "";
    private Object backCallback;

    // ------------------------------------------------------------------ rows

    static final class Row {
        static final int HEADER = 0, ITEM = 1, NOTE = 2;
        final int type;
        final String title, subtitle, right, cover;
        final Qvk3Track track;
        final Runnable click, longClick;
        final boolean saved;

        Row(int type, String title, String subtitle, String right, String cover, Qvk3Track track, boolean saved, Runnable click, Runnable longClick) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.right = right;
            this.cover = cover;
            this.track = track;
            this.saved = saved;
            this.click = click;
            this.longClick = longClick;
        }
    }

    final class Rows extends BaseAdapter {
        final List<Row> items = new ArrayList<>();

        void set(List<Row> r) {
            items.clear();
            items.addAll(r);
            notifyDataSetChanged();
        }

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
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int i) {
            return items.get(i).type;
        }

        @Override
        public boolean isEnabled(int i) {
            return items.get(i).click != null;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Row r = items.get(i);
            if (feed != null && i >= items.size() - 15) feed.more();
            if (r.type == Row.HEADER) {
                TextView t = convert instanceof TextView ? (TextView) convert : Qvk3Ui.text(Qvk3MusicActivity.this, "", 20, p.text, true);
                t.setText(r.title);
                t.setPadding(Qvk3Ui.dp(t.getContext(), 16), Qvk3Ui.dp(t.getContext(), 18), Qvk3Ui.dp(t.getContext(), 16), Qvk3Ui.dp(t.getContext(), 6));
                t.setAccessibilityHeading(true);
                return t;
            }
            if (r.type == Row.NOTE) {
                TextView t = convert instanceof TextView ? (TextView) convert : Qvk3Ui.text(Qvk3MusicActivity.this, "", 14, p.text2, false);
                t.setText(r.title);
                t.setTextColor(r.click != null ? p.accent : p.text2);
                t.setGravity(Gravity.CENTER);
                t.setPadding(Qvk3Ui.dp(t.getContext(), 16), Qvk3Ui.dp(t.getContext(), 14), Qvk3Ui.dp(t.getContext(), 16), Qvk3Ui.dp(t.getContext(), 14));
                return t;
            }
            ItemHolder h;
            if (convert != null && convert.getTag() instanceof ItemHolder) h = (ItemHolder) convert.getTag();
            else h = new ItemHolder();
            h.bind(r);
            return h.row;
        }
    }

    final class ItemHolder {
        final LinearLayout row = new LinearLayout(Qvk3MusicActivity.this);
        final ImageView cover = Qvk3Ui.cover(Qvk3MusicActivity.this, p, 48, 8);
        final TextView title = Qvk3Ui.line(Qvk3MusicActivity.this, "", 16, p.text, false);
        final TextView sub = Qvk3Ui.line(Qvk3MusicActivity.this, "", 13, p.text2, false);
        final TextView right = Qvk3Ui.text(Qvk3MusicActivity.this, "", 13, p.text2, false);
        String boundCover;

        ItemHolder() {
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = Qvk3Ui.dp(row.getContext(), 16);
            row.setPadding(pad, Qvk3Ui.dp(row.getContext(), 6), pad, Qvk3Ui.dp(row.getContext(), 6));
            row.setMinimumHeight(Qvk3Ui.dp(row.getContext(), 60));
            row.addView(cover);
            LinearLayout texts = new LinearLayout(row.getContext());
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Qvk3Ui.dp(row.getContext(), 12), 0, Qvk3Ui.dp(row.getContext(), 8), 0);
            texts.addView(title);
            texts.addView(sub);
            row.addView(texts, Qvk3Ui.weight(1));
            row.addView(right);
            row.setTag(this);
        }

        void bind(Row r) {
            Qvk3Track playing = engine.current();
            boolean now = r.track != null && playing != null && playing.key().equals(r.track.key());
            title.setText(r.title);
            title.setTextColor(now ? p.accent : r.track != null && !r.track.playable() ? p.text2 : p.text);
            sub.setText(r.subtitle);
            right.setText((r.saved ? "⬇ " : "") + (r.right == null ? "" : r.right));
            row.setContentDescription(r.title + ", " + r.subtitle + (r.saved ? ", сохранён на устройстве" : "")
                    + (r.right == null || r.right.isEmpty() ? "" : ", " + r.right) + (now ? ", сейчас играет" : ""));
            if (r.cover == null || !r.cover.equals(boundCover)) {
                boundCover = r.cover;
                cover.setImageBitmap(Qvk3Images.get(row.getContext()).cached(r.cover, 144));
                if (cover.getDrawable() == null && r.cover != null && !r.cover.isEmpty()) {
                    final String want = r.cover;
                    Qvk3Images.get(row.getContext()).load(want, 144, b -> {
                        if (want.equals(boundCover)) cover.setImageBitmap(b);
                    });
                }
            }
        }
    }

    // ------------------------------------------------------------------ paging

    interface Loader {
        Qvk3MusicSource.Page<Qvk3Track> load(int offset) throws Exception;
    }

    /** One paginated track list; online pages are mirrored to the offline index under {@code list}. */
    final class Feed {
        final String list;
        final Loader loader;
        final List<Qvk3Track> items = new ArrayList<>();
        final List<Row> header;
        int total = -1;
        boolean loading, offline;
        String error;
        final Runnable owner;

        Feed(String list, List<Row> header, Loader loader) {
            this.list = list;
            this.loader = loader;
            this.header = header;
            this.owner = current;
        }

        boolean done() {
            return total >= 0 && items.size() >= total;
        }

        void more() {
            if (loading || done() || error != null) return;
            loading = true;
            final int offset = items.size();
            Qvk3Jobs.io(() -> {
                Qvk3MusicSource.Page<Qvk3Track> page = null;
                String err = null;
                boolean off = false;
                try {
                    page = loader.load(offset);
                    if (list != null) Qvk3MusicLibrary.get(Qvk3MusicActivity.this).storePage(list, offset, page.items, page.total);
                    List<Qvk3Track> withLocal = new ArrayList<>();
                    for (Qvk3Track t : page.items) withLocal.add(Qvk3MusicLibrary.get(Qvk3MusicActivity.this).withDownload(t));
                    page = new Qvk3MusicSource.Page<>(withLocal, page.total);
                } catch (Exception e) {
                    if (list != null) {
                        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(Qvk3MusicActivity.this);
                        List<Qvk3Track> cached = lib.page(list, offset, Qvk3MusicSource.PAGE);
                        int t = lib.cachedTotal(list);
                        if (!cached.isEmpty() || offset > 0) {
                            page = new Qvk3MusicSource.Page<>(cached, cached.size() < Qvk3MusicSource.PAGE ? offset + cached.size() : Math.max(t, offset + cached.size()));
                            off = true;
                        }
                    }
                    if (page == null) err = Qvk3Jobs.message(e);
                }
                final Qvk3MusicSource.Page<Qvk3Track> fp = page;
                final String fe = err;
                final boolean fo = off;
                Qvk3Jobs.main(() -> {
                    loading = false;
                    if (feed != this || isDestroyed()) return;
                    if (fp != null) {
                        items.addAll(fp.items);
                        total = fp.items.isEmpty() ? items.size() : fp.total;
                        offline |= fo;
                    } else {
                        error = fe;
                    }
                    render();
                });
            });
        }

        void render() {
            List<Row> r = new ArrayList<>(header);
            if (offline) r.add(note("Нет связи с ВК — показана сохранённая копия", null));
            for (int i = 0; i < items.size(); i++) r.add(trackRow(items, i));
            if (error != null) r.add(note("Не удалось загрузить: " + error + "\nНажмите, чтобы повторить", () -> {
                error = null;
                more();
                render();
            }));
            else if (!done()) r.add(note("Загрузка…", null));
            else if (items.isEmpty()) r.add(note("Здесь пока пусто", null));
            rows.set(r);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        engine = Qvk3MusicEngine.get(this);
        p = Qvk3Ui.palette(this, Qvk3MusicSettings.layout(this).equals("amoled") ? "amoled" : "modern");
        Qvk3Ui.bars(this, p);
        tab = state != null ? state.getInt(EXTRA_TAB, 0) : getIntent().getIntExtra(EXTRA_TAB,
                Qvk3MusicSettings.prefs(this).getInt(Qvk3MusicSettings.START_TAB, 0));
        build();
        boolean showPlayer = state != null && state.getBoolean("player");
        selectTab(tab);
        if (showPlayer) openPlayer();
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback cb = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        }
        Qvk3MusicDownloads.setListener((key, percent, status) -> {
            if (tab == 4 && back.isEmpty()) showDownloads();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_TAB, tab);
        out.putBoolean("player", player != null && player.getVisibility() == View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine.addListener(this);
        onState(engine);
    }

    @Override
    protected void onStop() {
        engine.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Qvk3MusicDownloads.setListener(null);
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (player != null && player.getVisibility() == View.VISIBLE) {
            if (!player.back()) closePlayer();
            return;
        }
        if (!back.isEmpty()) {
            Runnable r = back.pop();
            current = r;
            r.run();
            return;
        }
        finish();
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(p.bg);
        root.setFitsSystemWindows(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Qvk3Ui.dp(this, 4), Qvk3Ui.dp(this, 4), Qvk3Ui.dp(this, 4), 0);
        header.addView(Qvk3Ui.glyph(this, p, "←", "Назад", 22, v -> handleBack()));
        screenTitle = Qvk3Ui.line(this, "QVK Music", 22, p.text, true);
        screenTitle.setAccessibilityHeading(true);
        header.addView(screenTitle, Qvk3Ui.weight(1));
        header.addView(Qvk3Ui.glyph(this, p, "⚙", "Настройки QVK Music", 20, v -> Qvk3MusicSettingsScreen.show(this)));
        col.addView(header, Qvk3Ui.match());

        search = new EditText(this);
        search.setHint("Поиск музыки");
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setTextColor(p.text);
        search.setHintTextColor(p.text2);
        search.setBackground(Qvk3Ui.rounded(p.surface2, Qvk3Ui.dp(this, 20)));
        search.setPadding(Qvk3Ui.dp(this, 16), Qvk3Ui.dp(this, 10), Qvk3Ui.dp(this, 16), Qvk3Ui.dp(this, 10));
        search.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH || ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                runSearch(search.getText().toString(), false);
                return true;
            }
            return false;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (tab == 3 && s.length() >= 2) {
                    // debounce: offline matches update while typing, online search on submit
                    search.removeCallbacks(localSearch);
                    search.postDelayed(localSearch, 250);
                }
            }
        });
        LinearLayout.LayoutParams slp = Qvk3Ui.match();
        slp.setMargins(Qvk3Ui.dp(this, 16), Qvk3Ui.dp(this, 4), Qvk3Ui.dp(this, 16), Qvk3Ui.dp(this, 4));
        col.addView(search, slp);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(this);
        tabs.setPadding(Qvk3Ui.dp(this, 12), 0, Qvk3Ui.dp(this, 12), 0);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Qvk3Ui.button(this, p, TABS[i], false, v -> {
                back.clear();
                selectTab(idx);
            });
            LinearLayout.LayoutParams lp = Qvk3Ui.wrap();
            lp.rightMargin = Qvk3Ui.dp(this, 6);
            tabs.addView(t, lp);
        }
        hs.addView(tabs);
        col.addView(hs, Qvk3Ui.match());

        list = new ListView(this);
        list.setDivider(null);
        list.setFastScrollEnabled(true);
        rows = new Rows();
        list.setAdapter(rows);
        list.setOnItemClickListener((parent, view, i, id) -> {
            Object o = parent.getItemAtPosition(i);
            if (o instanceof Row && ((Row) o).click != null) ((Row) o).click.run();
        });
        list.setOnItemLongClickListener((parent, view, i, id) -> {
            Object o = parent.getItemAtPosition(i);
            if (!(o instanceof Row) || ((Row) o).longClick == null) return false;
            ((Row) o).longClick.run();
            return true;
        });
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(Qvk3Ui.dp(this, 12), Qvk3Ui.dp(this, 6), Qvk3Ui.dp(this, 4), Qvk3Ui.dp(this, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.surface);
        bg.setCornerRadius(Qvk3Ui.dp(this, 16));
        mini.setBackground(bg);
        mini.setElevation(Qvk3Ui.dp(this, 6));
        miniCover = Qvk3Ui.cover(this, p, 44, 8);
        mini.addView(miniCover);
        LinearLayout mt = new LinearLayout(this);
        mt.setOrientation(LinearLayout.VERTICAL);
        mt.setPadding(Qvk3Ui.dp(this, 10), 0, 0, 0);
        miniTitle = Qvk3Ui.line(this, "", 15, p.text, true);
        miniArtist = Qvk3Ui.line(this, "", 13, p.text2, false);
        mt.addView(miniTitle);
        mt.addView(miniArtist);
        mini.addView(mt, Qvk3Ui.weight(1));
        miniPlay = Qvk3Ui.glyph(this, p, "▶", "Играть", 24, v -> engine.toggle());
        mini.addView(miniPlay);
        mini.addView(Qvk3Ui.glyph(this, p, "⏭", "Следующий трек", 22, v -> engine.next()));
        mini.setOnClickListener(v -> openPlayer());
        mini.setContentDescription("Мини-плеер. Нажмите, чтобы открыть плеер");
        LinearLayout.LayoutParams mlp = Qvk3Ui.match();
        mlp.setMargins(Qvk3Ui.dp(this, 8), 0, Qvk3Ui.dp(this, 8), Qvk3Ui.dp(this, 8));
        col.addView(mini, mlp);
        setContentView(root);
    }

    private final Runnable localSearch = () -> {
        String q = search.getText().toString();
        if (tab == 3 && q.length() >= 2) runSearch(q, true);
    };

    private void highlightTab() {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean sel = i == tab;
            t.setTextColor(sel ? p.onAccent : p.accent);
            t.setBackground(Qvk3Ui.ripple(this, sel ? p.accent : (p.dark ? 0x1FFFFFFF : 0x14000000), 0x33888888, Qvk3Ui.dp(this, 22)));
            t.setSelected(sel);
        }
    }

    void selectTab(int i) {
        tab = Math.max(0, Math.min(TABS.length - 1, i));
        highlightTab();
        search.setVisibility(tab == 3 || tab == 1 ? View.VISIBLE : View.GONE);
        switch (tab) {
            case 1:
                go(this::showMyMusic);
                break;
            case 2:
                go(this::showPlaylists);
                break;
            case 3:
                go(() -> {
                    screenTitle.setText("Поиск");
                    feed = null;
                    List<Row> r = new ArrayList<>();
                    r.add(note("Введите запрос: композиция, исполнитель или альбом. Сохранённые треки находятся и без сети.", null));
                    rows.set(r);
                    search.requestFocus();
                });
                break;
            case 4:
                go(this::showDownloads);
                break;
            default:
                go(this::showHome);
        }
    }

    /** Shows a screen; the previous one goes on the back stack when {@code push} is used. */
    private void go(Runnable screen) {
        current = screen;
        feed = null;
        if (coverflowHeader != null) {
            list.setAdapter(null);
            list.removeHeaderView(coverflowHeader);
            coverflowHeader = null;
            list.setAdapter(rows);
        }
        screen.run();
        list.setSelection(0);
    }

    private void push(Runnable screen) {
        if (current != null) back.push(current);
        go(screen);
    }

    // ------------------------------------------------------------------ screens

    Row note(String text, Runnable click) {
        return new Row(Row.NOTE, text, "", "", null, null, false, click, null);
    }

    Row header(String text) {
        return new Row(Row.HEADER, text, "", "", null, null, false, null, null);
    }

    Row trackRow(List<Qvk3Track> context, int index) {
        Qvk3Track t = context.get(index);
        String sub = t.artist + (t.album.isEmpty() ? "" : " · " + t.album);
        if (!t.playable()) sub = t.unplayableReason();
        return new Row(Row.ITEM, t.title, sub, Qvk3Track.time(t.duration * 1000L), t.cover, t, !t.local.isEmpty(),
                () -> play(context, index), () -> trackMenu(context, index));
    }

    private void play(List<Qvk3Track> context, int index) {
        Qvk3Track t = context.get(index);
        if (!t.playable() && t.local.isEmpty()) {
            Qvk3Ui.toast(this, t.unplayableReason());
            return;
        }
        engine.play(new ArrayList<>(context), index);
    }

    private void trackMenu(List<Qvk3Track> context, int index) {
        Qvk3Track t = context.get(index);
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(this);
        boolean fav = lib.favorite(t.key());
        String[] actions = {"Играть следующим", "Добавить в конец очереди", "Скачать", fav ? "Убрать из избранного" : "В избранное",
                "Все треки исполнителя", "Скопировать название"};
        Qvk3Ui.dialog(this).setTitle(t.label()).setItems(actions, (d, w) -> {
            switch (w) {
                case 0:
                    engine.editQueue(() -> engine.queue().addNext(t));
                    break;
                case 1:
                    engine.editQueue(() -> engine.queue().addLast(t));
                    break;
                case 2: {
                    List<Qvk3Track> one = new ArrayList<>();
                    one.add(t);
                    Qvk3Ui.toast(this, Qvk3MusicDownloads.enqueue(this, one));
                    break;
                }
                case 3:
                    lib.setFavorite(t, !fav);
                    break;
                case 4:
                    openArtist(t.artist);
                    break;
                default: {
                    android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Трек", t.label()));
                }
            }
        }).show();
    }

    private void showHome() {
        screenTitle.setText("QVK Music");
        List<Row> r = new ArrayList<>();
        Qvk3Track cur = engine.current();
        if (cur != null) {
            r.add(header("Продолжить"));
            List<Qvk3Track> one = new ArrayList<>();
            one.add(cur);
            r.add(new Row(Row.ITEM, cur.title, cur.artist + " · очередь: " + engine.queue().size(), "", cur.cover, cur, !cur.local.isEmpty(),
                    () -> {
                        engine.resume();
                        openPlayer();
                    }, null));
        }
        rows.set(r);
        Qvk3Jobs.io(() -> {
            Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(this);
            List<Qvk3Track> recent = lib.recent(8), favs = lib.favorites(0, 8), latest = lib.page("my", 0, 10);
            List<Qvk3MusicLibrary.LocalList> locals = lib.localPlaylists();
            Qvk3Jobs.main(() -> {
                if (tab != 0 || !back.isEmpty()) return;
                if (!recent.isEmpty()) {
                    r.add(header("Недавно прослушанное"));
                    for (int i = 0; i < recent.size(); i++) r.add(trackRow(recent, i));
                }
                if (!favs.isEmpty()) {
                    r.add(header("Избранное QVK"));
                    for (int i = 0; i < favs.size(); i++) r.add(trackRow(favs, i));
                    r.add(note("Всё избранное →", () -> push(this::showFavorites)));
                }
                r.add(header("Последние добавленные"));
                if (latest.isEmpty()) r.add(note("Откройте «Моя музыка», чтобы загрузить медиатеку", () -> selectTab(1)));
                for (int i = 0; i < latest.size(); i++) r.add(trackRow(latest, i));
                r.add(header("Плейлисты"));
                r.add(note("Мои плейлисты ВК →", () -> selectTab(2)));
                for (Qvk3MusicLibrary.LocalList l : locals) {
                    r.add(new Row(Row.ITEM, l.title == null ? "Плейлист QVK" : l.title, "Плейлист QVK · " + l.total + " треков", "", null, null, false,
                            () -> push(() -> showCachedList(l.list, l.title)), () -> confirmDeleteList(l)));
                }
                rows.set(r);
            });
        });
    }

    private void showMyMusic() {
        screenTitle.setText("Моя музыка");
        List<Row> head = new ArrayList<>();
        head.add(note("Альбомы · Исполнители · Избранное · История · Файлы", null));
        head.add(new Row(Row.ITEM, "Альбомы", Qvk3MusicSettings.layout(this).equals("coverflow") ? "CoverFlow" : "Сгруппировано по альбомам", "", null, null, false, () -> push(this::showAlbums), null));
        head.add(new Row(Row.ITEM, "Исполнители", "Сгруппировано по исполнителям", "", null, null, false, () -> push(this::showArtists), null));
        head.add(new Row(Row.ITEM, "Избранное QVK", "Локальный список, не меняет медиатеку ВК", "", null, null, false, () -> push(this::showFavorites), null));
        head.add(new Row(Row.ITEM, "История", "Недавно прослушанные", "", null, null, false, () -> Qvk3QueueDialog.history(this, p), null));
        head.add(new Row(Row.ITEM, "Файлы на устройстве", "Импортированные и ранее сохранённые QVK", "", null, null, false, () -> push(() -> showCachedList("local", "Файлы на устройстве")), null));
        head.add(new Row(Row.ITEM, "Перемешать всё", "Случайный порядок по всей медиатеке", "", null, null, false, this::shuffleAll, null));
        head.add(header("Все композиции"));
        feed = new Feed("my", head, Qvk3MusicSource::myTracks);
        feed.render();
        feed.more();
    }

    private void shuffleAll() {
        if (feed == null || feed.items.isEmpty()) {
            Qvk3Ui.toast(this, "Медиатека ещё загружается");
            return;
        }
        List<Qvk3Track> all = new ArrayList<>(feed.items);
        engine.play(all, (int) (Math.random() * all.size()));
        engine.setShuffle(true);
    }

    private void showFavorites() {
        screenTitle.setText("Избранное QVK");
        feed = new Feed(null, new ArrayList<>(), offset -> {
            List<Qvk3Track> page = Qvk3MusicLibrary.get(this).favorites(offset, Qvk3MusicSource.PAGE);
            return new Qvk3MusicSource.Page<>(page, page.size() < Qvk3MusicSource.PAGE ? offset + page.size() : Integer.MAX_VALUE);
        });
        feed.render();
        feed.more();
    }

    private void showCachedList(String listKey, String title) {
        screenTitle.setText(title == null ? "Плейлист" : title);
        feed = new Feed(null, new ArrayList<>(), offset -> {
            List<Qvk3Track> page = Qvk3MusicLibrary.get(this).page(listKey, offset, Qvk3MusicSource.PAGE);
            return new Qvk3MusicSource.Page<>(page, page.size() < Qvk3MusicSource.PAGE ? offset + page.size() : Integer.MAX_VALUE);
        });
        List<Row> h = feed.header;
        if (listKey.equals("local")) {
            h.add(new Row(Row.NOTE, "＋ Импортировать файлы с устройства", "", "", null, null, false, this::importFiles, null));
            h.add(new Row(Row.NOTE, "Найти музыку, ранее сохранённую QVK", "", "", null, null, false, () -> Qvk3Jobs.io(() -> {
                int n = Qvk3MusicDownloads.importExisting(this);
                Qvk3Ui.toast(this, n > 0 ? "Найдено файлов: " + n : "Сохранённых QVK файлов не найдено (нужен Android 10+)");
                Qvk3Jobs.main(() -> go(() -> showCachedList(listKey, title)));
            }), null));
        }
        feed.render();
        feed.more();
    }

    private void confirmDeleteList(Qvk3MusicLibrary.LocalList l) {
        Qvk3Ui.dialog(this).setTitle("Удалить плейлист QVK «" + l.title + "»?")
                .setMessage("Треки и загрузки не удаляются, только этот список.")
                .setPositiveButton("Удалить", (d, w) -> {
                    Qvk3MusicLibrary.get(this).deleteList(l.list);
                    go(this::showHome);
                }).setNegativeButton("Отмена", null).show();
    }

    private void showAlbums() {
        screenTitle.setText("Альбомы");
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(this);
        List<Qvk3MusicLibrary.Group> albums = lib.albums("my");
        int cached = lib.cachedCount("my"), total = lib.cachedTotal("my");
        List<Row> r = new ArrayList<>();
        if (total < 0 || cached < total) {
            r.add(note("В индексе " + cached + (total > 0 ? " из " + total : "") + " треков. Нажмите, чтобы загрузить всю медиатеку для группировки", this::syncAll));
        }
        if (Qvk3MusicSettings.layout(this).equals("coverflow") && !albums.isEmpty()) {
            Qvk3CoverFlowView cf = new Qvk3CoverFlowView(this);
            cf.setId(View.generateViewId());
            cf.setColors(p.text, p.text2, p.surface2);
            cf.setSpacingPercent(Qvk3MusicSettings.coverflowSpacing(this));
            cf.setCornerRadius(Qvk3Ui.dp(this, Qvk3MusicSettings.coverRadiusDp(this)));
            List<Qvk3CoverFlowView.Item> items = new ArrayList<>();
            for (Qvk3MusicLibrary.Group g : albums) items.add(new Qvk3CoverFlowView.Item(g.cover, g.name, g.artist + " · " + g.count));
            int saved = Qvk3MusicSettings.prefs(this).getInt("qvk3_music_coverflow_selected", 0);
            cf.setItems(items, Math.min(saved, items.size() - 1));
            cf.setListener((index, settled) -> {
                Qvk3MusicSettings.prefs(this).edit().putInt("qvk3_music_coverflow_selected", index).apply();
                if (!settled) albumTracks(albums.get(index), true);
                else albumTracks(albums.get(index), false);
            });
            boolean land = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            int h = Math.round(getResources().getDisplayMetrics().heightPixels * (land ? 0.62f : 0.42f));
            cf.setLayoutParams(new ListView.LayoutParams(-1, h));
            coverflowHeader = cf;
            list.setAdapter(null);
            list.addHeaderView(cf, null, false);
            list.setAdapter(rows);
            rows.set(r);
            albumTracks(albums.get(Math.min(saved, albums.size() - 1)), false);
            return;
        }
        for (Qvk3MusicLibrary.Group g : albums) {
            r.add(new Row(Row.ITEM, g.name, g.artist + " · " + g.count + " треков", "", g.cover, null, false, () -> push(() -> showGroup("album", g.name)), null));
        }
        if (albums.isEmpty() && cached > 0) r.add(note("У треков в медиатеке нет данных об альбомах", null));
        rows.set(r);
    }

    private View coverflowHeader;

    /** Track list under CoverFlow: duration on the right, ⬇ for saved tracks; play on tap. */
    private void albumTracks(Qvk3MusicLibrary.Group g, boolean playFirst) {
        List<Qvk3Track> tracks = Qvk3MusicLibrary.get(this).group("my", "album", g.name);
        List<Row> r = new ArrayList<>();
        r.add(header(g.name));
        for (int i = 0; i < tracks.size(); i++) r.add(trackRow(tracks, i));
        rows.set(r);
        if (playFirst && !tracks.isEmpty()) play(tracks, 0);
    }

    private void showArtists() {
        screenTitle.setText("Исполнители");
        List<Row> r = new ArrayList<>();
        for (Qvk3MusicLibrary.Group g : Qvk3MusicLibrary.get(this).artists("my")) {
            r.add(new Row(Row.ITEM, g.name, g.count + " треков в медиатеке", "", g.cover, null, false, () -> push(() -> showGroup("artist", g.name)), null));
        }
        if (r.isEmpty()) r.add(note("Загрузите медиатеку, чтобы увидеть исполнителей", this::syncAll));
        rows.set(r);
    }

    private void showGroup(String column, String value) {
        screenTitle.setText(value);
        List<Qvk3Track> tracks = Qvk3MusicLibrary.get(this).group("my", column, value);
        List<Row> r = new ArrayList<>();
        if (column.equals("artist")) r.add(note("Найти «" + value + "» во всём каталоге ВК →", () -> openArtist(value)));
        for (int i = 0; i < tracks.size(); i++) r.add(trackRow(tracks, i));
        rows.set(r);
    }

    /** Artist page: VK search limited to the performer (audio.search performer_only=1). */
    void openArtist(String name) {
        if (name == null || name.isEmpty()) return;
        push(() -> {
            screenTitle.setText(name);
            feed = new Feed(null, new ArrayList<>(), offset -> Qvk3MusicSource.search(name, true, offset));
            feed.header.add(note("Треки исполнителя из каталога ВК", null));
            feed.render();
            feed.more();
        });
    }

    private void syncAll() {
        Qvk3Ui.toast(this, "Загружаем всю медиатеку в индекс…");
        Qvk3Jobs.io(() -> {
            int offset = 0, total = Integer.MAX_VALUE;
            try {
                while (offset < total && offset < 20000) {
                    Qvk3MusicSource.Page<Qvk3Track> page = Qvk3MusicSource.myTracks(offset);
                    Qvk3MusicLibrary.get(this).storePage("my", offset, page.items, page.total);
                    total = page.total;
                    if (page.items.isEmpty()) break;
                    offset += page.items.size();
                    Thread.sleep(350); // stay well below VK's request rate limits
                }
                Qvk3Ui.toast(this, "Медиатека в индексе: " + offset + " треков");
            } catch (Exception e) {
                Qvk3Ui.toast(this, "Синхронизация остановлена: " + Qvk3Jobs.message(e));
            }
            Qvk3Jobs.main(() -> {
                if (!isDestroyed() && current != null) current.run();
            });
        });
    }

    private void showPlaylists() {
        screenTitle.setText("Плейлисты");
        List<Row> r = new ArrayList<>();
        r.add(note("Загрузка…", null));
        rows.set(r);
        Qvk3Jobs.io(() -> {
            List<Row> out = new ArrayList<>();
            try {
                int offset = 0;
                Qvk3MusicSource.Page<Qvk3MusicSource.Playlist> page;
                do {
                    page = Qvk3MusicSource.playlists(offset);
                    for (Qvk3MusicSource.Playlist pl : page.items) {
                        out.add(new Row(Row.ITEM, pl.title, (pl.subtitle.isEmpty() ? "" : pl.subtitle + " · ") + pl.count + " треков", "", pl.cover, null, false,
                                () -> push(() -> showPlaylist(pl)), () -> playlistMenu(pl)));
                    }
                    offset += page.items.size();
                } while (!page.items.isEmpty() && offset < page.total && offset < 500);
            } catch (Exception e) {
                out.add(note("Плейлисты ВК недоступны: " + Qvk3Jobs.message(e), () -> go(this::showPlaylists)));
            }
            List<Qvk3MusicLibrary.LocalList> locals = Qvk3MusicLibrary.get(this).localPlaylists();
            Qvk3Jobs.main(() -> {
                if (tab != 2 || !back.isEmpty()) return;
                List<Row> all = new ArrayList<>();
                if (!locals.isEmpty()) {
                    all.add(header("Плейлисты QVK"));
                    for (Qvk3MusicLibrary.LocalList l : locals) {
                        all.add(new Row(Row.ITEM, l.title == null ? "Плейлист QVK" : l.title, l.total + " треков · на устройстве", "", null, null, false,
                                () -> push(() -> showCachedList(l.list, l.title)), () -> confirmDeleteList(l)));
                    }
                }
                all.add(header("Плейлисты ВК"));
                all.addAll(out);
                if (out.isEmpty()) all.add(note("Плейлистов нет", null));
                rows.set(all);
            });
        });
    }

    private void playlistMenu(Qvk3MusicSource.Playlist pl) {
        Qvk3Ui.dialog(this).setTitle(pl.title).setItems(new String[]{"Слушать", "Скачать доступные треки"}, (d, w) -> {
            Qvk3Jobs.io(() -> {
                try {
                    List<Qvk3Track> all = new ArrayList<>();
                    int offset = 0;
                    Qvk3MusicSource.Page<Qvk3Track> page;
                    do {
                        page = Qvk3MusicSource.playlistTracks(pl, offset);
                        all.addAll(page.items);
                        offset += page.items.size();
                    } while (!page.items.isEmpty() && offset < page.total && offset < 5000);
                    if (w == 0) engine.play(all, 0);
                    else Qvk3Ui.toast(this, Qvk3MusicDownloads.enqueue(this, all));
                } catch (Exception e) {
                    Qvk3Ui.toast(this, Qvk3Jobs.message(e));
                }
            });
        }).show();
    }

    private void showPlaylist(Qvk3MusicSource.Playlist pl) {
        screenTitle.setText(pl.title);
        List<Row> head = new ArrayList<>();
        head.add(new Row(Row.NOTE, "▶ Слушать · ⬇ Скачать доступные", "", "", null, null, false, () -> playlistMenu(pl), null));
        feed = new Feed("pl:" + pl.key(), head, offset -> Qvk3MusicSource.playlistTracks(pl, offset));
        feed.render();
        feed.more();
    }

    private void runSearch(String q, boolean localOnly) {
        if (q.trim().length() < 2) return;
        if (tab != 3) {
            tab = 3;
            highlightTab();
        }
        String query = q.trim();
        current = () -> runSearch(query, localOnly);
        screenTitle.setText("Поиск: " + query);
        List<Qvk3Track> local = Qvk3MusicLibrary.get(this).searchLocal(query, 30);
        List<Row> head = new ArrayList<>();
        if (!local.isEmpty()) {
            head.add(header("В медиатеке и загрузках"));
            for (int i = 0; i < local.size(); i++) head.add(trackRow(local, i));
        }
        if (localOnly) {
            feed = null;
            head.add(note("Нажмите «Поиск» на клавиатуре, чтобы искать во всём ВК", () -> runSearch(query, false)));
            head.add(note("Только исполнитель «" + query + "» →", () -> openArtist(query)));
            rows.set(head);
            return;
        }
        head.add(header("Каталог ВК"));
        feed = new Feed(null, head, offset -> Qvk3MusicSource.search(query, false, offset));
        feed.render();
        feed.more();
    }

    private void showDownloads() {
        screenTitle.setText("Загрузки");
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(this);
        List<Row> r = new ArrayList<>();
        r.add(note(lib.stats() + (Qvk3MusicDownloads.status().isEmpty() ? "" : "\n" + Qvk3MusicDownloads.status()), null));
        r.add(new Row(Row.NOTE, Qvk3MusicDownloads.running() ? "Приостановить загрузки" : "Продолжить загрузки", "", "", null, null, false, () -> {
            if (Qvk3MusicDownloads.running()) Qvk3MusicDownloads.cancel();
            else Qvk3MusicDownloads.start(this);
            list.postDelayed(this::showDownloads, 400);
        }, null));
        r.add(new Row(Row.NOTE, "Файлы на устройстве и импорт →", "", "", null, null, false, () -> push(() -> showCachedList("local", "Файлы на устройстве")), null));
        List<Qvk3MusicLibrary.Download> dls = lib.downloads(0, 500);
        List<Qvk3Track> done = new ArrayList<>();
        for (Qvk3MusicLibrary.Download d : dls) if (d.state == Qvk3MusicLibrary.DL_DONE) done.add(d.track);
        if (!done.isEmpty()) {
            r.add(new Row(Row.NOTE, "Удалить все загрузки (" + done.size() + ")", "", "", null, null, false, () -> confirmDeleteAll(dls), null));
        }
        int doneIndex = 0;
        for (Qvk3MusicLibrary.Download d : dls) {
            String state;
            switch (d.state) {
                case Qvk3MusicLibrary.DL_DONE:
                    state = (d.size >> 20) + " МБ";
                    break;
                case Qvk3MusicLibrary.DL_RUNNING:
                    state = "загрузка";
                    break;
                case Qvk3MusicLibrary.DL_FAILED:
                    state = "ошибка";
                    break;
                default:
                    state = "в очереди";
            }
            final int di = d.state == Qvk3MusicLibrary.DL_DONE ? doneIndex++ : -1;
            String sub = d.state == Qvk3MusicLibrary.DL_FAILED && d.error != null ? d.error : d.track.artist;
            r.add(new Row(Row.ITEM, d.track.title, sub, state, d.track.cover, d.track, d.state == Qvk3MusicLibrary.DL_DONE,
                    () -> {
                        if (di >= 0) engine.play(done, di);
                        else if (d.state == Qvk3MusicLibrary.DL_FAILED) {
                            lib.retryDownload(d.track.key());
                            Qvk3MusicDownloads.start(this);
                            showDownloads();
                        }
                    },
                    () -> Qvk3Ui.dialog(this).setTitle(d.track.label()).setItems(new String[]{"Удалить загрузку"}, (dd, w) -> {
                        Qvk3MusicDownloads.delete(this, d);
                        showDownloads();
                    }).show()));
        }
        if (dls.isEmpty()) r.add(note("Скачанных треков нет. Скачать можно из меню трека (долгое нажатие) или плейлиста.", null));
        rows.set(r);
    }

    private void confirmDeleteAll(List<Qvk3MusicLibrary.Download> dls) {
        Qvk3Ui.dialog(this).setTitle("Удалить все загруженные треки?")
                .setMessage("Файлы будут удалены с устройства. Медиатека ВК не изменится.")
                .setPositiveButton("Удалить", (d, w) -> Qvk3Jobs.io(() -> {
                    for (Qvk3MusicLibrary.Download x : dls) if (x.state == Qvk3MusicLibrary.DL_DONE) Qvk3MusicDownloads.delete(this, x);
                    Qvk3Jobs.main(this::showDownloads);
                })).setNegativeButton("Отмена", null).show();
    }

    private void importFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            Qvk3Ui.toast(this, "Нет приложения для выбора файлов");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == Qvk3MusicSettingsScreen.REQ_LRC && res == RESULT_OK && data != null && data.getData() != null) {
            Qvk3Track t = engine.current();
            if (t != null) Qvk3Jobs.io(() -> {
                try {
                    Qvk3LyricsManager.importFile(this, t, data.getData());
                    Qvk3Ui.toast(this, "Текст сохранён для «" + t.title + "»");
                } catch (Exception e) {
                    Qvk3Ui.toast(this, Qvk3Jobs.message(e));
                }
            });
            return;
        }
        if (req != REQ_IMPORT || res != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        Qvk3Jobs.io(() -> {
            Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(this);
            List<Qvk3Track> existing = lib.page("local", 0, 100000);
            List<Qvk3Track> all = new ArrayList<>(existing);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Qvk3Track t : existing) seen.add(t.local);
            int added = 0;
            for (Uri u : uris) {
                try {
                    getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                    // some providers do not offer persistable grants; the file is still playable this session
                }
                if (seen.add(u.toString())) {
                    all.add(Qvk3MusicDownloads.fromDocument(this, u));
                    added++;
                }
            }
            lib.storePage("local", 0, all, all.size());
            Qvk3Ui.toast(this, "Импортировано: " + added);
            Qvk3Jobs.main(() -> {
                if (current != null) current.run();
            });
        });
    }

    // ------------------------------------------------------------------ player

    void openPlayer() {
        if (engine.current() == null) {
            Qvk3Ui.toast(this, "Выберите трек");
            return;
        }
        if (player == null) {
            player = new Qvk3PlayerView(this);
            player.setOnClose(this::closePlayer);
            root.addView(player, new FrameLayout.LayoutParams(-1, -1));
        }
        player.setVisibility(View.VISIBLE);
        player.setAlpha(0f);
        player.setTranslationY(Qvk3Ui.dp(this, 40));
        player.animate().alpha(1f).translationY(0).setDuration(220).start();
        player.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    void closePlayer() {
        if (player == null) return;
        player.animate().alpha(0f).translationY(Qvk3Ui.dp(this, 40)).setDuration(180).withEndAction(() -> player.setVisibility(View.GONE)).start();
    }

    /** Rebuilds the player after appearance settings change (playback continues). */
    void rebuildPlayer() {
        if (player != null) player.rebuild();
    }

    @Override
    public void onState(Qvk3MusicEngine e) {
        Qvk3Track t = e.current();
        mini.setVisibility(t == null ? View.GONE : View.VISIBLE);
        if (t == null) return;
        miniTitle.setText(t.title);
        miniArtist.setText(e.state() == Qvk3MusicEngine.ERROR ? e.error() : t.artist);
        miniArtist.setTextColor(e.state() == Qvk3MusicEngine.ERROR ? 0xFFE64646 : p.text2);
        boolean playing = e.isPlaying();
        miniPlay.setText(playing ? "⏸" : "▶");
        miniPlay.setContentDescription(playing ? "Пауза" : "Играть");
        if (!t.key().equals(miniCoverFor)) {
            miniCoverFor = t.key();
            miniCover.setImageBitmap(null);
            Qvk3Images.get(this).load(t.cover, 144, b -> {
                if (t.key().equals(miniCoverFor)) miniCover.setImageBitmap(b);
            });
            rows.notifyDataSetChanged(); // highlight the playing row
        }
    }
}
