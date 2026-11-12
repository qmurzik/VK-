package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
public final class Qvk3MusicScreen extends FrameLayout implements Qvk3MusicEngine.Listener {
    private static final java.util.WeakHashMap<Activity, Qvk3MusicScreen> screens = new java.util.WeakHashMap<>();

    /** The music screen hosted by this activity (standalone or embedded in VK's music tab), or null. */
    public static Qvk3MusicScreen of(Activity activity) {
        return screens.get(activity);
    }

    final Activity a;
    /** True when shown inside VK's own music tab instead of Qvk3MusicActivity. */
    final boolean embedded;
    private static final int REQ_IMPORT = 41;
    static final String[] TABS = Qvk3MusicActivity.TABS;

    private Qvk3Ui.Palette p;
    private Qvk3MusicEngine engine;
    private FrameLayout root;
    private LinearLayout tabs;
    private TextView screenTitle;
    private EditText search;
    private ListView list;
    private Rows rows;
    private LinearLayout mini;
    private View miniBox;
    private ImageView miniCover;
    private TextView miniTitle, miniArtist;
    private ImageView miniPlay;
    private Qvk3PlayerView player;
    private int tab;
    private View searchRow;
    private android.widget.ProgressBar miniProgress;
    private final Deque<Runnable> back = new ArrayDeque<>();
    private Runnable current;
    private Feed feed;
    private String miniCoverFor = "";
    private Qvk3CoverFlowScreen coverFlow;
    private Qvk3NowButton nowButton;
    private boolean imeShown;
    private String miniHiddenFor = "";
    /** Called when the standalone player-only host should close. */
    Runnable onPlayerClosed;

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
                TextView t = convert instanceof TextView ? (TextView) convert : Qvk3Ui.text(a, "", 21, p.text, true);
                t.setText(r.title);
                t.setPadding(Qvk3Ui.dp(t.getContext(), 16), Qvk3Ui.dp(t.getContext(), 22), Qvk3Ui.dp(t.getContext(), 16), Qvk3Ui.dp(t.getContext(), 8));
                t.setAccessibilityHeading(true);
                return t;
            }
            if (r.type == Row.NOTE) {
                TextView t = convert instanceof TextView ? (TextView) convert : Qvk3Ui.text(a, "", 14, p.text2, false);
                int d16 = Qvk3Ui.dp(t.getContext(), 16);
                if (r.click != null) {
                    // an action row: soft tonal card with a chevron instead of a blue «link →»
                    t.setText(Qvk3Ui.plain(r.title));
                    t.setTextSize(15);
                    t.setTextColor(p.text);
                    t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    t.setMinHeight(Qvk3Ui.dp(t.getContext(), 52));
                    t.setBackground(new android.graphics.drawable.InsetDrawable(Qvk3Ui.ripple(a, p.dark ? 0x12FFFFFF : 0x0A000000,
                            p.dark ? 0x22FFFFFF : 0x14000000, Qvk3Ui.dp(t.getContext(), 14)), d16, Qvk3Ui.dp(t.getContext(), 4), d16, Qvk3Ui.dp(t.getContext(), 4)));
                    android.graphics.drawable.Drawable chev = Qvk3Icons.of(Qvk3Icons.RIGHT, (p.text2 & 0x00FFFFFF) | 0x99000000);
                    chev.setBounds(0, 0, d16, d16);
                    t.setCompoundDrawablesRelative(null, null, chev, null);
                    t.setPadding(d16 * 2, Qvk3Ui.dp(t.getContext(), 14), d16 * 2, Qvk3Ui.dp(t.getContext(), 14));
                } else {
                    t.setText(r.title);
                    t.setTextSize(14);
                    t.setTextColor(p.text2);
                    t.setGravity(Gravity.CENTER);
                    t.setMinHeight(0);
                    t.setBackground(null);
                    t.setCompoundDrawablesRelative(null, null, null, null);
                    t.setPadding(d16, Qvk3Ui.dp(t.getContext(), 14), d16, Qvk3Ui.dp(t.getContext(), 14));
                }
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
        final LinearLayout row = new LinearLayout(a);
        final ImageView cover = Qvk3Ui.cover(a, p, 52, 10);
        final TextView title = Qvk3Ui.line(a, "", 16, p.text, false);
        final TextView sub = Qvk3Ui.line(a, "", 13, p.text2, false);
        final TextView right = Qvk3Ui.text(a, "", 13, p.text2, false);
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
            row.setTag(a);
        }

        void bind(Row r) {
            Qvk3Track playing = engine.current();
            boolean now = r.track != null && playing != null && playing.key().equals(r.track.key());
            title.setText(r.title);
            title.setTextColor(now ? p.accent : r.track != null && !r.track.playable() ? p.text2 : p.text);
            sub.setText(r.subtitle);
            right.setText(r.right == null ? "" : r.right);
            right.setTextColor(p.text2);
            if (r.saved) {
                android.graphics.drawable.Drawable dl = Qvk3Icons.of(Qvk3Icons.DOWNLOAD, p.accent);
                int sz = Qvk3Ui.dp(a, 14);
                dl.setBounds(0, 0, sz, sz);
                right.setCompoundDrawablesRelative(dl, null, null, null);
                right.setCompoundDrawablePadding(Qvk3Ui.dp(a, 6));
            } else {
                right.setCompoundDrawablesRelative(null, null, null, null);
            }
            row.setContentDescription(r.title + ", " + r.subtitle + (r.saved ? ", сохранён на устройстве" : "")
                    + (r.right == null || r.right.isEmpty() ? "" : ", " + r.right) + (now ? ", сейчас играет" : ""));
            if (r.cover == null || !r.cover.equals(boundCover)) {
                boundCover = r.cover;
                android.graphics.Bitmap hit = Qvk3Images.get(row.getContext()).cached(r.cover, 144);
                setRounded(cover, hit, 10);
                if (hit == null && r.cover != null && !r.cover.isEmpty()) {
                    final String want = r.cover;
                    Qvk3Images.get(row.getContext()).load(want, 144, b -> {
                        if (want.equals(boundCover)) setRounded(cover, b, 10);
                    });
                }
            }
        }
    }

    void setRounded(ImageView v, android.graphics.Bitmap b, int radiusDp) {
        if (b == null) {
            v.setImageDrawable(Qvk3Icons.of(Qvk3Icons.MUSIC, p.text2));
            int pad = Qvk3Ui.dp(a, 14);
            v.setPadding(pad, pad, pad, pad);
        } else {
            v.setPadding(0, 0, 0, 0);
            v.setImageDrawable(Qvk3Ui.roundedBitmap(b, Qvk3Ui.dp(a, radiusDp)));
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
                    if (list != null) Qvk3MusicLibrary.get(a).storePage(list, offset, page.items, page.total);
                    List<Qvk3Track> withLocal = new ArrayList<>();
                    for (Qvk3Track t : page.items) withLocal.add(Qvk3MusicLibrary.get(a).withDownload(t));
                    page = new Qvk3MusicSource.Page<>(withLocal, page.total);
                } catch (Exception e) {
                    if (list != null) {
                        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
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
                    if (feed != this || a.isDestroyed()) return;
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

    public Qvk3MusicScreen(Activity activity, boolean embedded, int startTab, boolean showPlayer) {
        super(activity);
        this.a = activity;
        this.embedded = embedded;
        screens.put(activity, this);
        engine = Qvk3MusicEngine.get(a);
        p = Qvk3Ui.palette(a, Qvk3MusicSettings.layout(a).equals("amoled") ? "amoled" : "modern");
        if (!embedded) Qvk3Ui.bars(a, p);
        tab = startTab >= 0 ? startTab : Qvk3MusicSettings.prefs(a).getInt(Qvk3MusicSettings.START_TAB, 0);
        build();
        selectTab(tab);
        if (showPlayer) openPlayer();
    }

    public int tab() {
        return tab;
    }

    public boolean playerShown() {
        return player != null && player.getVisibility() == View.VISIBLE;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        screens.put(a, this);
        engine.addListener(this);
        Qvk3MusicDownloads.setListener((key, percent, status) -> {
            if (tab == 4 && back.isEmpty()) showDownloads();
            else if (coverFlow != null) coverFlow.refreshRing();
        });
        onState(engine);
        post(() -> orientation(getResources().getConfiguration()));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            setOnApplyWindowInsetsListener((v, in) -> {
                boolean ime = in.isVisible(android.view.WindowInsets.Type.ime());
                if (ime != imeShown) {
                    imeShown = ime;
                    onState(engine);
                }
                return v.onApplyWindowInsets(in);
            });
            requestApplyInsets();
        }
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        orientation(c);
    }

    /** Turning the phone sideways opens the album Cover Flow (as on the iPhone); turning it back closes it. */
    private void orientation(android.content.res.Configuration c) {
        boolean land = c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (land && Qvk3MusicSettings.rotateCoverflow(a)) showCoverFlow(true);
        else if (!land && coverFlow != null && coverFlow.byRotation) hideCoverFlow();
    }

    /**
     * Keyboard detection that works whether or not the window passes IME insets down (VK's own window does not):
     * the root insets, or on old Android the visible frame. Called whenever this screen is resized.
     */
    private void checkIme() {
        boolean ime = false;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsets in = getRootWindowInsets();
            if (in != null) ime = in.isVisible(android.view.WindowInsets.Type.ime());
        }
        if (!ime) {
            android.graphics.Rect r = new android.graphics.Rect();
            getWindowVisibleDisplayFrame(r);
            int h = getRootView().getHeight();
            ime = h > 0 && h - r.bottom > h * 0.15f;
        }
        if (ime != imeShown) {
            imeShown = ime;
            onState(engine);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        post(this::checkIme);
    }

    void showCoverFlow(boolean byRotation) {
        if (coverFlow != null) return;
        coverFlow = new Qvk3CoverFlowScreen(a, byRotation);
        coverFlow.setOnClose(this::hideCoverFlow);
        coverFlow.setOnSync(this::syncAll);
        root.addView(coverFlow, new FrameLayout.LayoutParams(-1, -1));
        coverFlow.setAlpha(0f);
        coverFlow.animate().alpha(1f).setDuration(200).start();
    }

    void hideCoverFlow() {
        final Qvk3CoverFlowScreen c = coverFlow;
        if (c == null) return;
        coverFlow = null;
        c.animate().alpha(0f).setDuration(160).withEndAction(() -> root.removeView(c)).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        engine.removeListener(this);
        Qvk3MusicDownloads.setListener(null);
        super.onDetachedFromWindow();
    }

    /** Returns true when the back press was consumed (player closed / inner screen popped). */
    public boolean handleBack() {
        if (coverFlow != null) {
            if (!coverFlow.back()) hideCoverFlow();
            return true;
        }
        if (playerShown()) {
            if (!player.back()) closePlayer();
            return true;
        }
        if (!back.isEmpty()) {
            Runnable r = back.pop();
            current = r;
            r.run();
            return true;
        }
        return false;
    }

    private void backOrFinish() {
        if (!handleBack() && !embedded) a.finish();
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        root = new FrameLayout(a);
        root.setBackgroundColor(p.bg);
        root.setFitsSystemWindows(!embedded);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(a);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 4), 0);
        header.addView(Qvk3Ui.icon(a, Qvk3Icons.BACK, p.text, 48, 24, "Назад", v -> backOrFinish()));
        screenTitle = Qvk3Ui.line(a, "Музыка", 28, p.text, true);
        screenTitle.setAccessibilityHeading(true);
        screenTitle.setPadding(Qvk3Ui.dp(a, 6), 0, 0, 0);
        header.addView(screenTitle, Qvk3Ui.weight(1));
        // IDEA-0005: while the keyboard is open the mini player steps aside and this button opens the player instead
        nowButton = new Qvk3NowButton(a, p.text, p.accent);
        nowButton.setOnClickListener(v -> engine.toggle());
        nowButton.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            openPlayer();
            return true;
        });
        nowButton.setVisibility(View.GONE);
        header.addView(nowButton, new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 48), Qvk3Ui.dp(a, 48)));
        header.addView(Qvk3Ui.icon(a, Qvk3Icons.TUNE, p.text, 48, 22, "Настройки QVK Music", v -> Qvk3MusicSettingsScreen.show(a)));
        col.addView(header, Qvk3Ui.match());

        search = new EditText(a);
        search.setHint("Поиск музыки");
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setTextColor(p.text);
        search.setHintTextColor(p.text2);
        search.setBackground(Qvk3Ui.rounded(p.surface2, Qvk3Ui.dp(a, 14)));
        search.setPadding(Qvk3Ui.dp(a, 44), Qvk3Ui.dp(a, 11), Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 11));
        search.setTextSize(16);
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
        FrameLayout searchBox = new FrameLayout(a);
        searchBox.addView(search, new FrameLayout.LayoutParams(-1, -2));
        ImageView mag = new ImageView(a);
        mag.setImageDrawable(Qvk3Icons.of(Qvk3Icons.SEARCH, p.text2));
        FrameLayout.LayoutParams mlp0 = new FrameLayout.LayoutParams(Qvk3Ui.dp(a, 20), Qvk3Ui.dp(a, 20), Gravity.CENTER_VERTICAL | Gravity.START);
        mlp0.leftMargin = Qvk3Ui.dp(a, 14);
        searchBox.addView(mag, mlp0);
        searchBox.setTag("search");
        LinearLayout.LayoutParams slp = Qvk3Ui.match();
        slp.setMargins(Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 6));
        col.addView(searchBox, slp);
        searchRow = searchBox;

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(a);
        tabs.setPadding(Qvk3Ui.dp(a, 12), 0, Qvk3Ui.dp(a, 12), 0);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Qvk3Ui.button(a, p, TABS[i], false, v -> {
                back.clear();
                selectTab(idx);
            });
            t.setTextSize(14);
            t.setPadding(Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 16), Qvk3Ui.dp(a, 8));
            t.setMinHeight(Qvk3Ui.dp(a, 40));
            LinearLayout.LayoutParams lp = Qvk3Ui.wrap();
            lp.rightMargin = Qvk3Ui.dp(a, 6);
            tabs.addView(t, lp);
        }
        hs.addView(tabs);
        col.addView(hs, Qvk3Ui.match());

        list = new ListView(a);
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
        if (embedded) {
            list.setClipToPadding(false);
            list.setPadding(0, 0, 0, Qvk3Ui.dp(a, 8)); // room for the floating mini-player and VK's bottom bar
        }
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout miniWrap = new LinearLayout(a);
        miniWrap.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF232530, 0xFF16181F});
        bg.setCornerRadius(Qvk3Ui.dp(a, 18));
        miniWrap.setBackground(bg);
        miniWrap.setElevation(Qvk3Ui.dp(a, 8));
        miniWrap.setClipToOutline(true);
        mini = new LinearLayout(a);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 6));
        miniCover = Qvk3Ui.cover(a, p, 44, 10);
        miniCover.setBackground(Qvk3Ui.rounded(0x33FFFFFF, Qvk3Ui.dp(a, 10)));
        mini.addView(miniCover);
        LinearLayout mt = new LinearLayout(a);
        mt.setOrientation(LinearLayout.VERTICAL);
        mt.setPadding(Qvk3Ui.dp(a, 12), 0, 0, 0);
        miniTitle = Qvk3Ui.line(a, "", 15, Color.WHITE, true);
        miniArtist = Qvk3Ui.line(a, "", 13, 0xB3FFFFFF, false);
        mt.addView(miniTitle);
        mt.addView(miniArtist);
        mini.addView(mt, Qvk3Ui.weight(1));
        mini.addView(Qvk3Ui.icon(a, Qvk3Icons.NEXT, Color.WHITE, 44, 20, "Следующий трек", v -> engine.next()));
        miniPlay = Qvk3Ui.icon(a, Qvk3Icons.PLAY, Color.WHITE, 48, 24, "Играть", v -> engine.toggle());
        mini.addView(miniPlay);   // play/pause at the very right (BUG-0022)
        miniWrap.setOnClickListener(v -> openPlayer());
        Qvk3MiniSwipe.attach(miniWrap, () -> {
            Qvk3Track cur = engine.current();
            miniHiddenFor = cur == null ? "" : cur.key();
            if (Qvk3MiniSwipe.stops(a)) engine.stop();
            onState(engine);
        });
        miniWrap.setContentDescription("Мини-плеер. Нажмите, чтобы открыть плеер");
        miniWrap.addView(mini, Qvk3Ui.match());
        miniProgress = new android.widget.ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        miniProgress.setMax(1000);
        miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        miniProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        miniWrap.addView(miniProgress, new LinearLayout.LayoutParams(-1, Qvk3Ui.dp(a, 3)));
        LinearLayout.LayoutParams mlp = Qvk3Ui.match();
        mlp.setMargins(Qvk3Ui.dp(a, 10), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 10), Qvk3Ui.dp(a, 10));
        col.addView(miniWrap, mlp);
        miniBox = miniWrap;
        addView(root, new FrameLayout.LayoutParams(-1, -1));
    }

    private final Runnable localSearch = () -> {
        String q = search.getText().toString();
        if (tab == 3 && q.length() >= 2) runSearch(q, true);
    };

    private void highlightTab() {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean sel = i == tab;
            t.setTextColor(sel ? (p.dark ? 0xFF111214 : Color.WHITE) : p.text);
            t.setBackground(Qvk3Ui.ripple(a, sel ? (p.dark ? Color.WHITE : 0xFF111214) : p.surface2, 0x33888888, Qvk3Ui.dp(a, 20)));
            t.setSelected(sel);
        }
    }

    void selectTab(int i) {
        tab = Math.max(0, Math.min(TABS.length - 1, i));
        highlightTab();
        searchRow.setVisibility(tab == 3 || tab == 1 ? View.VISIBLE : View.GONE);
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
            case 5:
                go(this::showVkHome);
                break;
            default:
                go(this::showHome);
        }
    }

    /** Shows a screen; the previous one goes on the back stack when {@code push} is used. */
    private void go(Runnable screen) {
        current = screen;
        feed = null;
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
            Qvk3Ui.toast(a, t.unplayableReason());
            return;
        }
        engine.play(new ArrayList<>(context), index);
    }

    private void trackMenu(List<Qvk3Track> context, int index) {
        Qvk3Track t = context.get(index);
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
        boolean fav = lib.favorite(t.key());
        String[] actions = {"Играть следующим", "Добавить в конец очереди", "Скачать", fav ? "Убрать из избранного" : "В избранное",
                "Все треки исполнителя", "Скопировать название"};
        Qvk3Ui.dialog(a).setTitle(t.label()).setItems(actions, (d, w) -> {
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
                    Qvk3Ui.toast(a, Qvk3MusicDownloads.enqueue(a, one));
                    break;
                }
                case 3:
                    lib.setFavorite(t, !fav);
                    break;
                case 4:
                    openArtist(t.artist);
                    break;
                default: {
                    android.content.ClipboardManager cm = a.getSystemService(android.content.ClipboardManager.class);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Трек", t.label()));
                }
            }
        }).show();
    }

    private void showHome() {
        screenTitle.setText("Музыка");
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
            Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
            List<Qvk3Track> recent = lib.recent(8), favs = lib.favorites(0, 8), latest = lib.page("my", 0, 10);
            List<Qvk3MusicLibrary.LocalList> locals = lib.localPlaylists();
            // suggestions come from VK; when the account has none (or offline) the sections are simply absent
            List<Qvk3Track> reco = new ArrayList<>(), popular = new ArrayList<>();
            try {
                reco = Qvk3MusicSource.recommendations(15);
            } catch (Exception ignored) {
            }
            try {
                popular = Qvk3MusicSource.popular(15);
            } catch (Exception ignored) {
            }
            // BUG-0010: VK's own music home (джемы, подборки, плейлисты дня) right on «Главная», not only on «Музыка ВК»
            List<Qvk3MusicSource.Block> vkBlocks = new ArrayList<>();
            try {
                List<Qvk3MusicSource.Block> all = Qvk3MusicSource.vkHome();
                for (int i = 0; i < all.size() && vkBlocks.size() < 5; i++) vkBlocks.add(all.get(i));
            } catch (Exception e) {
                android.util.Log.i("QVK3Music", "home: VK blocks unavailable: " + Qvk3Jobs.message(e) + " · " + Qvk3MusicSource.lastHomeTrace);
            }
            final List<Qvk3MusicSource.Block> fBlocks = vkBlocks;
            final List<Qvk3Track> fReco = reco, fPopular = popular;
            Qvk3Jobs.main(() -> {
                List<Qvk3Track> reco2 = fReco, popular2 = fPopular;
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
                for (Qvk3MusicSource.Block b : fBlocks) {
                    r.add(header(b.title));
                    for (Qvk3MusicSource.Playlist pl : b.playlists) {
                        r.add(new Row(Row.ITEM, pl.title, (pl.subtitle.isEmpty() ? "Плейлист" : pl.subtitle) + (pl.count > 0 ? " · " + pl.count + " треков" : ""),
                                "", pl.cover, null, false, () -> push(() -> showPlaylist(pl)), null));
                    }
                    for (int i = 0; i < Math.min(5, b.tracks.size()); i++) r.add(trackRow(b.tracks, i));
                }
                if (!fBlocks.isEmpty()) r.add(note("Вся главная музыки ВК →", () -> selectTab(5)));
                if (!reco2.isEmpty()) {
                    r.add(header("Для вас"));
                    for (int i = 0; i < reco2.size(); i++) r.add(trackRow(reco2, i));
                }
                if (!popular2.isEmpty()) {
                    r.add(header("Популярное"));
                    for (int i = 0; i < popular2.size(); i++) r.add(trackRow(popular2, i));
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

    /**
     * BUG-0010 / IDEA-0008: «Музыка ВК» — VK's own music home (джемы, подборки, плейлисты дня, чарты) inside QVK Music, with
     * the QVK player and mini-player. Opening the whole stock section hid the QVK player and brought back the old interface.
     */
    private void showVkHome() {
        screenTitle.setText("Музыка ВК");
        List<Row> r = new ArrayList<>();
        r.add(note("Загружаем главную музыки ВК…", null));
        rows.set(r);
        Qvk3Jobs.io(() -> {
            List<Qvk3MusicSource.Block> blocks = null;
            String err = null;
            try {
                blocks = Qvk3MusicSource.vkHome();
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            android.util.Log.i("QVK3Music", "VK home: " + (err != null ? "error " + err : (blocks == null ? 0 : blocks.size()) + " blocks") + " · " + Qvk3MusicSource.lastHomeTrace);
            if (blocks == null || blocks.isEmpty()) {
                // BUG-0010: never an empty tab — VK's own suggestions and charts from the audio API instead
                List<Qvk3MusicSource.Block> fb = new ArrayList<>();
                try {
                    List<Qvk3Track> reco = Qvk3MusicSource.recommendations(30);
                    if (!reco.isEmpty()) fb.add(new Qvk3MusicSource.Block("Для вас", reco, new ArrayList<>()));
                } catch (Exception ignored) {
                }
                try {
                    List<Qvk3Track> pop = Qvk3MusicSource.popular(30);
                    if (!pop.isEmpty()) fb.add(new Qvk3MusicSource.Block("Популярное", pop, new ArrayList<>()));
                } catch (Exception ignored) {
                }
                if (!fb.isEmpty()) blocks = fb;
            }
            final List<Qvk3MusicSource.Block> bl = blocks;
            final String e = err;
            Qvk3Jobs.main(() -> {
                if (tab != 5 || !back.isEmpty()) return;
                List<Row> out = new ArrayList<>();
                if (bl == null || bl.isEmpty()) out.add(note(e != null ? "Не удалось загрузить: " + e : "ВК не прислал подборок для этого аккаунта", this::showVkHome));
                if (bl != null) for (Qvk3MusicSource.Block b : bl) {
                    out.add(header(b.title));
                    for (Qvk3MusicSource.Playlist pl : b.playlists) {
                        out.add(new Row(Row.ITEM, pl.title, (pl.subtitle.isEmpty() ? "Плейлист" : pl.subtitle) + (pl.count > 0 ? " · " + pl.count + " треков" : ""),
                                "", pl.cover, null, false, () -> push(() -> showPlaylist(pl)), null));
                    }
                    for (int i = 0; i < b.tracks.size(); i++) out.add(trackRow(b.tracks, i));
                }
                out.add(note("Открыть штатную музыку ВК целиком →", () -> Qvk3MusicEntry.openClassic(a)));
                rows.set(out);
            });
        });
    }

    private void showMyMusic() {
        screenTitle.setText("Моя музыка");
        List<Row> head = new ArrayList<>();
        head.add(note("Альбомы · Исполнители · Избранное · История · Файлы", null));
        head.add(new Row(Row.ITEM, "Альбомы", Qvk3MusicSettings.layout(a).equals("coverflow") ? "CoverFlow" : "Сгруппировано по альбомам", "", null, null, false, () -> push(this::showAlbums), null));
        head.add(new Row(Row.ITEM, "Cover Flow", "Альбомы на чёрном фоне, как на iPhone. Открывается и поворотом телефона", "", null, null, false, () -> showCoverFlow(false), null));
        head.add(new Row(Row.ITEM, "Исполнители", "Сгруппировано по исполнителям", "", null, null, false, () -> push(this::showArtists), null));
        head.add(new Row(Row.ITEM, "Избранное QVK", "Локальный список, не меняет медиатеку ВК", "", null, null, false, () -> push(this::showFavorites), null));
        head.add(new Row(Row.ITEM, "История", "Недавно прослушанные", "", null, null, false, () -> Qvk3QueueDialog.history(a, p), null));
        head.add(new Row(Row.ITEM, "Файлы на устройстве", "Импортированные и ранее сохранённые QVK", "", null, null, false, () -> push(() -> showCachedList("local", "Файлы на устройстве")), null));
        head.add(new Row(Row.ITEM, "Перемешать всё", "Случайный порядок по всей медиатеке", "", null, null, false, this::shuffleAll, null));
        head.add(header("Все композиции"));
        feed = new Feed("my", head, Qvk3MusicSource::myTracks);
        feed.render();
        feed.more();
    }

    private void shuffleAll() {
        if (feed == null || feed.items.isEmpty()) {
            Qvk3Ui.toast(a, "Медиатека ещё загружается");
            return;
        }
        List<Qvk3Track> all = new ArrayList<>(feed.items);
        engine.play(all, (int) (Math.random() * all.size()));
        engine.setShuffle(true);
    }

    private void showFavorites() {
        screenTitle.setText("Избранное QVK");
        feed = new Feed(null, new ArrayList<>(), offset -> {
            List<Qvk3Track> page = Qvk3MusicLibrary.get(a).favorites(offset, Qvk3MusicSource.PAGE);
            return new Qvk3MusicSource.Page<>(page, page.size() < Qvk3MusicSource.PAGE ? offset + page.size() : Integer.MAX_VALUE);
        });
        feed.render();
        feed.more();
    }

    private void showCachedList(String listKey, String title) {
        screenTitle.setText(title == null ? "Плейлист" : title);
        feed = new Feed(null, new ArrayList<>(), offset -> {
            List<Qvk3Track> page = Qvk3MusicLibrary.get(a).page(listKey, offset, Qvk3MusicSource.PAGE);
            return new Qvk3MusicSource.Page<>(page, page.size() < Qvk3MusicSource.PAGE ? offset + page.size() : Integer.MAX_VALUE);
        });
        List<Row> h = feed.header;
        if (listKey.equals("local")) {
            h.add(new Row(Row.NOTE, "Импортировать файлы с устройства", "", "", null, null, false, this::importFiles, null));
            h.add(new Row(Row.NOTE, "Найти музыку, ранее сохранённую QVK", "", "", null, null, false, () -> Qvk3Jobs.io(() -> {
                int n = Qvk3MusicDownloads.importExisting(a);
                Qvk3Ui.toast(a, n > 0 ? "Найдено файлов: " + n : "Сохранённых QVK файлов не найдено (нужен Android 10+)");
                Qvk3Jobs.main(() -> go(() -> showCachedList(listKey, title)));
            }), null));
        }
        feed.render();
        feed.more();
    }

    private void confirmDeleteList(Qvk3MusicLibrary.LocalList l) {
        Qvk3Ui.dialog(a).setTitle("Удалить плейлист QVK «" + l.title + "»?")
                .setMessage("Треки и загрузки не удаляются, только этот список.")
                .setPositiveButton("Удалить", (d, w) -> {
                    Qvk3MusicLibrary.get(a).deleteList(l.list);
                    go(this::showHome);
                }).setNegativeButton("Отмена", null).show();
    }

    private void showAlbums() {
        screenTitle.setText("Альбомы");
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
        List<Qvk3MusicLibrary.Group> albums = lib.albums("my");
        int cached = lib.cachedCount("my"), total = lib.cachedTotal("my");
        List<Row> r = new ArrayList<>();
        if (total < 0 || cached < total) {
            r.add(note("В индексе " + cached + (total > 0 ? " из " + total : "") + " треков. Нажмите, чтобы загрузить всю медиатеку для группировки", this::syncAll));
        }
        if (Qvk3MusicSettings.layout(a).equals("coverflow")) showCoverFlow(false); // the list below stays as the fallback
        for (Qvk3MusicLibrary.Group g : albums) {
            r.add(new Row(Row.ITEM, g.name, g.artist + " · " + g.count + " треков", "", g.cover, null, false, () -> push(() -> showGroup("album", g.name)), null));
        }
        if (albums.isEmpty() && cached > 0) r.add(note("У треков в медиатеке нет данных об альбомах", null));
        rows.set(r);
    }

    private void showArtists() {
        screenTitle.setText("Исполнители");
        List<Row> r = new ArrayList<>();
        for (Qvk3MusicLibrary.Group g : Qvk3MusicLibrary.get(a).artists("my")) {
            r.add(new Row(Row.ITEM, g.name, g.count + " треков в медиатеке", "", g.cover, null, false, () -> push(() -> showGroup("artist", g.name)), null));
        }
        if (r.isEmpty()) r.add(note("Загрузите медиатеку, чтобы увидеть исполнителей", this::syncAll));
        rows.set(r);
    }

    private void showGroup(String column, String value) {
        screenTitle.setText(value);
        List<Qvk3Track> tracks = Qvk3MusicLibrary.get(a).group("my", column, value);
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
        Qvk3Ui.toast(a, "Загружаем всю медиатеку в индекс…");
        Qvk3Jobs.io(() -> {
            int offset = 0, total = Integer.MAX_VALUE;
            try {
                while (offset < total && offset < 20000) {
                    Qvk3MusicSource.Page<Qvk3Track> page = Qvk3MusicSource.myTracks(offset);
                    Qvk3MusicLibrary.get(a).storePage("my", offset, page.items, page.total);
                    total = page.total;
                    if (page.items.isEmpty()) break;
                    offset += page.items.size();
                    Thread.sleep(350); // stay well below VK's request rate limits
                }
                Qvk3Ui.toast(a, "Медиатека в индексе: " + offset + " треков");
            } catch (Exception e) {
                Qvk3Ui.toast(a, "Синхронизация остановлена: " + Qvk3Jobs.message(e));
            }
            Qvk3Jobs.main(() -> {
                if (!a.isDestroyed() && current != null) current.run();
                if (coverFlow != null) coverFlow.load();
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
            List<Qvk3MusicLibrary.LocalList> locals = Qvk3MusicLibrary.get(a).localPlaylists();
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
        Qvk3Ui.dialog(a).setTitle(pl.title).setItems(new String[]{"Слушать", "Скачать доступные треки"}, (d, w) -> {
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
                    else Qvk3Ui.toast(a, Qvk3MusicDownloads.enqueue(a, all));
                } catch (Exception e) {
                    Qvk3Ui.toast(a, Qvk3Jobs.message(e));
                }
            });
        }).show();
    }

    private void showPlaylist(Qvk3MusicSource.Playlist pl) {
        screenTitle.setText(pl.title);
        List<Row> head = new ArrayList<>();
        head.add(new Row(Row.NOTE, "Слушать или скачать", "", "", null, null, false, () -> playlistMenu(pl), null));
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
        List<Qvk3Track> local = Qvk3MusicLibrary.get(a).searchLocal(query, 30);
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
        Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
        List<Row> r = new ArrayList<>();
        r.add(note(lib.stats() + (Qvk3MusicDownloads.status().isEmpty() ? "" : "\n" + Qvk3MusicDownloads.status()), null));
        r.add(new Row(Row.NOTE, Qvk3MusicDownloads.running() ? "Приостановить загрузки" : "Продолжить загрузки", "", "", null, null, false, () -> {
            if (Qvk3MusicDownloads.running()) Qvk3MusicDownloads.cancel();
            else Qvk3MusicDownloads.start(a);
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
                            Qvk3MusicDownloads.start(a);
                            showDownloads();
                        }
                    },
                    () -> Qvk3Ui.dialog(a).setTitle(d.track.label()).setItems(new String[]{"Удалить загрузку"}, (dd, w) -> {
                        Qvk3MusicDownloads.delete(a, d);
                        showDownloads();
                    }).show()));
        }
        if (dls.isEmpty()) r.add(note("Скачанных треков нет. Скачать можно из меню трека (долгое нажатие) или плейлиста.", null));
        rows.set(r);
    }

    private void confirmDeleteAll(List<Qvk3MusicLibrary.Download> dls) {
        Qvk3Ui.dialog(a).setTitle("Удалить все загруженные треки?")
                .setMessage("Файлы будут удалены с устройства. Медиатека ВК не изменится.")
                .setPositiveButton("Удалить", (d, w) -> Qvk3Jobs.io(() -> {
                    for (Qvk3MusicLibrary.Download x : dls) if (x.state == Qvk3MusicLibrary.DL_DONE) Qvk3MusicDownloads.delete(a, x);
                    Qvk3Jobs.main(this::showDownloads);
                })).setNegativeButton("Отмена", null).show();
    }

    void importFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            if (embedded) {
                // results cannot be delivered to a view inside VK's activity: run the picker in our own activity
                a.startActivity(new Intent(a, Qvk3MusicActivity.class).putExtra(Qvk3MusicActivity.EXTRA_IMPORT, true));
                return;
            }
            a.startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Нет приложения для выбора файлов");
        }
    }

    /** Activity results forwarded by the hosting Qvk3MusicActivity. */
    public void result(int req, int res, Intent data) {
        if (req == Qvk3MusicSettingsScreen.REQ_LRC && res == Activity.RESULT_OK && data != null && data.getData() != null) {
            Qvk3Track t = engine.current();
            if (t != null) Qvk3Jobs.io(() -> {
                try {
                    Qvk3LyricsManager.importFile(a, t, data.getData());
                    Qvk3Ui.toast(a, "Текст сохранён для «" + t.title + "»");
                } catch (Exception e) {
                    Qvk3Ui.toast(a, Qvk3Jobs.message(e));
                }
            });
            return;
        }
        if (req != REQ_IMPORT || res != Activity.RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        Qvk3Jobs.io(() -> {
            Qvk3MusicLibrary lib = Qvk3MusicLibrary.get(a);
            List<Qvk3Track> existing = lib.page("local", 0, 100000);
            List<Qvk3Track> all = new ArrayList<>(existing);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Qvk3Track t : existing) seen.add(t.local);
            int added = 0;
            for (Uri u : uris) {
                try {
                    a.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                    // some providers do not offer persistable grants; the file is still playable this session
                }
                if (seen.add(u.toString())) {
                    all.add(Qvk3MusicDownloads.fromDocument(a, u));
                    added++;
                }
            }
            lib.storePage("local", 0, all, all.size());
            Qvk3Ui.toast(a, "Импортировано: " + added);
            Qvk3Jobs.main(() -> {
                if (current != null) current.run();
            });
        });
    }

    // ------------------------------------------------------------------ player

    void openPlayer() {
        if (engine.current() == null) {
            Qvk3Ui.toast(a, "Выберите трек");
            return;
        }
        if (embedded) {
            // full-screen player above VK's bottom bar
            a.startActivity(new Intent(a, Qvk3MusicActivity.class).putExtra(Qvk3MusicActivity.EXTRA_PLAYER_ONLY, true));
            return;
        }
        if (player == null) {
            player = new Qvk3PlayerView(a);
            player.setOnClose(this::closePlayer);
            root.addView(player, new FrameLayout.LayoutParams(-1, -1));
        }
        player.setVisibility(View.VISIBLE);
        player.setAlpha(0f);
        player.setTranslationY(Qvk3Ui.dp(a, 40));
        player.animate().alpha(1f).translationY(0).setDuration(220).start();
        player.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    void closePlayer() {
        if (player == null) return;
        if (onPlayerClosed != null) {
            onPlayerClosed.run();
            return;
        }
        player.animate().alpha(0f).translationY(Qvk3Ui.dp(a, 40)).setDuration(180).withEndAction(() -> player.setVisibility(View.GONE)).start();
    }

    /** Rebuilds the player after appearance settings change (playback continues). */
    void rebuildPlayer() {
        if (player != null) player.rebuild();
    }

    @Override
    public void onState(Qvk3MusicEngine e) {
        Qvk3Track t = e.current();
        // inside VK's tab the floating QVK mini-player (Qvk3MiniBar) is shown instead of this one
        if (t != null && !t.key().equals(miniHiddenFor)) miniHiddenFor = "";
        boolean swiped = t != null && t.key().equals(miniHiddenFor);
        miniBox.setVisibility(t == null || imeShown || swiped ? View.GONE : View.VISIBLE);
        nowButton.setVisibility(t != null && imeShown ? View.VISIBLE : View.GONE);
        if (t != null && imeShown) nowButton.set(e.duration() > 0 ? e.position() / (float) e.duration() : 0f, e.isPlaying());
        if (t == null) return;
        miniProgress.setProgress(e.duration() > 0 ? (int) (e.position() * 1000 / e.duration()) : 0);
        miniTitle.setText(t.title);
        miniArtist.setText(e.state() == Qvk3MusicEngine.ERROR ? e.error() : t.artist);
        miniArtist.setTextColor(e.state() == Qvk3MusicEngine.ERROR ? 0xFFFF6B6B : 0xB3FFFFFF);
        boolean playing = e.isPlaying();
        Qvk3Ui.setIcon(miniPlay, playing ? Qvk3Icons.PAUSE : Qvk3Icons.PLAY, Color.WHITE);
        miniPlay.setContentDescription(playing ? "Пауза" : "Играть");
        if (!t.key().equals(miniCoverFor)) {
            miniCoverFor = t.key();
            miniCover.setImageBitmap(null);
            Qvk3Images.get(a).load(t.cover, 144, b -> {
                if (t.key().equals(miniCoverFor)) setRounded(miniCover, b, 10);
            });
            rows.notifyDataSetChanged(); // highlight the playing row
        }
    }
}
