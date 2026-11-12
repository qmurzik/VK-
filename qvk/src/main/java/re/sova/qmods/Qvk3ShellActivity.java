package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * QVK shell: an iOS-messenger style home that replaces VK's main screen as the first thing the user sees.
 * Tabs: Чаты (own list and chat screen on VK's API), Музыка (QVK Music), Настройки, ВК (the stock client for
 * everything not rebuilt yet). Turned off with "qvk3_shell" → the app starts as plain QVK again.
 */
public final class Qvk3ShellActivity extends Activity {
    static final String ENABLED = "qvk3_shell";
    private Qvk3Ios s;
    private FrameLayout content;
    private LinearLayout tabBar;
    private int tab;
    private View chatsView;
    private Qvk3MusicScreen music;
    private final List<Qvk3Messages.Chat> all = new ArrayList<>(), shown = new ArrayList<>();
    private ChatsAdapter adapter;
    private TextView status;
    private EditText search;
    private boolean loading, end;
    private Object backCallback;
    static final String[] TABS = {"Чаты", "Музыка", "Ещё", "Настройки"};
    static final String[] ICONS = {Qvk3Icons.CHAT, Qvk3Icons.MUSIC, Qvk3Icons.GRID, Qvk3Icons.SLIDERS};

    private int dp(float v) {
        return Qvk3Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        s = new Qvk3Ios(this);
        s.bars(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(s.bg);
        root.setFitsSystemWindows(true);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        View line = new View(this);
        line.setBackgroundColor(s.sep);
        root.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f))));
        tabBar = new LinearLayout(this);
        tabBar.setBackgroundColor(s.bar);
        tabBar.setPadding(0, dp(6), 0, dp(4));
        root.addView(tabBar, Qvk3Ui.match());
        setContentView(root);
        select(state == null ? 0 : state.getInt("tab", 0));
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback cb = this::back;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tab == 0 && chatsView != null) reload();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private void back() {
        if (tab == 1 && music != null && music.handleBack()) return;
        if (tab != 0) {
            select(0);
            return;
        }
        moveTaskToBack(true); // like a home screen: leave the app, keep VK underneath intact
    }

    private void renderTabs() {
        tabBar.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            boolean sel = i == tab;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(Qvk3Icons.of(ICONS[i], sel ? s.tint : s.text2));
            item.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView t = Qvk3Ui.line(this, TABS[i], 10, sel ? s.tint : s.text2, sel);
            t.setGravity(Gravity.CENTER);
            item.addView(t);
            item.setMinimumHeight(dp(48));
            item.setContentDescription(TABS[i] + (sel ? ", выбрано" : ""));
            item.setOnClickListener(v -> select(idx));
            tabBar.addView(item, Qvk3Ui.weight(1));
        }
    }

    private void select(int i) {
        if (i == 3) {
            startActivity(new Intent(this, Qvk3SettingsActivity.class));
            return;
        }
        tab = i;
        renderTabs();
        content.removeAllViews();
        if (i == 0) {
            if (chatsView == null) chatsView = buildChats();
            content.addView(chatsView);
            reload();
        } else if (i == 2) {
            content.addView(buildMore());
        } else {
            if (music == null) music = new Qvk3MusicScreen(this, true, -1, false);
            content.addView(music);
        }
    }

    /** The stock client lives underneath this activity: closing the shell reveals it. */
    private void moveShellAside() {
        Qvk3Ui.toast(this, "Штатный ВКонтакте. Вернуться в QVK: ярлык «QVK» на иконке приложения или Настройки QVK");
        finish();
    }

    // ------------------------------------------------------------------ more: every stock VK section

    /** Opens a stock VK screen: QVK's own router first, then the vk.com link router of the app. */
    private void openStock(String route, String url) {
        try {
            if (route != null && RuntimeBridge.navigateChecked(this, route)) return;
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).setPackage(getPackageName()));
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.shell.stock", t);
            Qvk3Ui.toast(this, "Не удалось открыть раздел");
        }
    }

    private View buildMore() {
        long me = RuntimeBridge.account();
        String id = me > 0 ? "id" + me : "feed";
        Object[][][] groups = {
                {{"Профиль", Qvk3Icons.USER, 0xFF5856D6, null, "https://vk.com/" + id}, {"Новости", Qvk3Icons.HOME, 0xFF007AFF, null, "https://vk.com/feed"},
                        {"Уведомления", Qvk3Icons.BELL, 0xFFFF3B30, "notifications", "https://vk.com/feed?section=notifications"}},
                {{"Друзья", Qvk3Icons.USER, 0xFF34C759, "friends", "https://vk.com/friends"}, {"Сообщества", Qvk3Icons.GRID, 0xFFFF9500, "groups", "https://vk.com/groups"},
                        {"Поиск", Qvk3Icons.SEARCH, 0xFF8E8E93, "search", "https://vk.com/search"}},
                {{"Видео", Qvk3Icons.PLAY, 0xFFFF2D55, null, "https://vk.com/video"}, {"Клипы", Qvk3Icons.LAYERS, 0xFFAF52DE, null, "https://vk.com/clips"},
                        {"Фотографии", Qvk3Icons.IMAGE, 0xFF5AC8FA, null, "https://vk.com/albums" + me}, {"Закладки", Qvk3Icons.STAR, 0xFFFFCC00, null, "https://vk.com/fave"},
                        {"Документы", Qvk3Icons.FOLDER, 0xFF8E8E93, null, "https://vk.com/docs"}},
                {{"Сервисы", Qvk3Icons.GRID, 0xFF30B0C7, "services", "https://vk.com/apps"}, {"Игры", Qvk3Icons.WAND, 0xFF34C759, null, "https://vk.com/games"},
                        {"Маркет", Qvk3Icons.LAYERS, 0xFFFF9500, null, "https://vk.com/market"}}};
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, dp(16), dp(24));
        sv.addView(col);
        TextView title = Qvk3Ui.text(this, "Ещё", 24, s.text, true);
        title.setAccessibilityHeading(true);
        title.setPadding(dp(0), dp(10), 0, dp(10));
        col.addView(title);
        for (Object[][] g : groups) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Qvk3Ui.rounded(s.field, dp(12)));
            card.setClipToOutline(true);
            for (Object[] it : g) {
                if (card.getChildCount() > 0) {
                    View d = new View(this);
                    d.setBackgroundColor(s.sep);
                    LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f)));
                    dl.leftMargin = dp(56);
                    card.addView(d, dl);
                }
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(52));
                row.setPadding(dp(14), dp(6), dp(12), dp(6));
                View tile = Qvk3Ui.tile(this, (String) it[1], (Integer) it[2], 30);
                LinearLayout.LayoutParams tl = (LinearLayout.LayoutParams) tile.getLayoutParams();
                tl.rightMargin = dp(14);
                row.addView(tile, tl);
                row.addView(Qvk3Ui.text(this, (String) it[0], 17, s.text, false), Qvk3Ui.weight(1));
                ImageView chev = new ImageView(this);
                chev.setImageDrawable(Qvk3Icons.of(Qvk3Icons.RIGHT, s.text2));
                row.addView(chev, new LinearLayout.LayoutParams(dp(18), dp(18)));
                row.setBackground(Qvk3Ui.ripple(this, 0, s.dark ? 0x22FFFFFF : 0x14000000, 0));
                row.setContentDescription((String) it[0]);
                final String route = (String) it[3], url = (String) it[4];
                row.setOnClickListener(v -> openStock(route, url));
                card.addView(row, Qvk3Ui.match());
            }
            LinearLayout.LayoutParams cl = Qvk3Ui.match();
            cl.bottomMargin = dp(18);
            col.addView(card, cl);
        }
        LinearLayout stock = new LinearLayout(this);
        stock.setBackground(Qvk3Ui.rounded(s.field, dp(12)));
        stock.setGravity(Gravity.CENTER_VERTICAL);
        stock.setPadding(dp(16), dp(14), dp(16), dp(14));
        stock.addView(Qvk3Ui.text(this, "Открыть штатный ВКонтакте целиком", 17, s.tint, false), Qvk3Ui.weight(1));
        stock.setOnClickListener(v -> moveShellAside());
        col.addView(stock, Qvk3Ui.match());
        TextView note = Qvk3Ui.text(this, "Все разделы оригинального клиента остаются доступны: они открываются штатными экранами ВК, а оболочка QVK заменяет только чаты и музыку.", 13, s.text2, false);
        note.setPadding(dp(8), dp(12), dp(8), 0);
        col.addView(note);
        return sv;
    }

    // ------------------------------------------------------------------ chats

    private View buildChats() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(dp(16), dp(10), dp(8), dp(4));
        TextView title = Qvk3Ui.text(this, "Чаты", 24, s.text, true);
        title.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        title.setAccessibilityHeading(true);
        head.addView(title, Qvk3Ui.weight(1));
        col.addView(head);
        search = new EditText(this);
        search.setHint("Поиск");
        search.setSingleLine(true);
        search.setTextSize(17);
        search.setTextColor(s.text);
        search.setHintTextColor(s.text2);
        search.setBackground(Qvk3Ui.rounded(s.field, dp(10)));
        search.setPadding(dp(36), dp(8), dp(12), dp(8));
        FrameLayout sb = new FrameLayout(this);
        sb.addView(search);
        ImageView mag = new ImageView(this);
        mag.setImageDrawable(Qvk3Icons.of(Qvk3Icons.SEARCH, s.text2));
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER_VERTICAL);
        ml.leftMargin = dp(10);
        sb.addView(mag, ml);
        LinearLayout.LayoutParams sl = Qvk3Ui.match();
        sl.setMargins(dp(16), dp(2), dp(16), dp(8));
        col.addView(sb, sl);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence c, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                filter();
            }
        });
        status = Qvk3Ui.text(this, "", 15, s.text2, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), dp(40), dp(24), dp(16));
        status.setOnClickListener(v -> reload());
        col.addView(status);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(s.dark ? 0x22FFFFFF : 0x14000000));
        adapter = new ChatsAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> {
            Qvk3Messages.Chat c = shown.get(i);
            startActivity(new Intent(this, Qvk3ChatActivity.class).putExtra("peer", c.peer).putExtra("title", c.title)
                    .putExtra("photo", c.photo).putExtra("group", c.group));
        });
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        return col;
    }

    private void filter() {
        String q = Qvk3Lyrics.normal(search == null ? "" : search.getText().toString());
        shown.clear();
        for (Qvk3Messages.Chat c : all) if (q.isEmpty() || Qvk3Lyrics.normal(c.title).contains(q)) shown.add(c);
        adapter.notifyDataSetChanged();
    }

    private void reload() {
        if (loading) return;
        loading = true;
        end = false;
        if (all.isEmpty()) {
            status.setVisibility(View.VISIBLE);
            status.setText("Загрузка чатов…");
        }
        Qvk3Jobs.io(() -> {
            List<Qvk3Messages.Chat> r = null;
            String err = null;
            try {
                r = Qvk3Messages.chats(0);
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            final List<Qvk3Messages.Chat> fr = r;
            final String fe = err;
            Qvk3Jobs.main(() -> {
                loading = false;
                if (isDestroyed()) return;
                if (fr != null) {
                    all.clear();
                    all.addAll(fr);
                    status.setVisibility(fr.isEmpty() ? View.VISIBLE : View.GONE);
                    status.setText("Чатов пока нет");
                    filter();
                } else if (all.isEmpty()) {
                    status.setVisibility(View.VISIBLE);
                    status.setText("Не удалось загрузить чаты: " + fe + "\n\nНажмите, чтобы повторить, или откройте вкладку «ВКонтакте».");
                }
            });
        });
    }

    private void more() {
        if (loading || end || all.isEmpty()) return;
        loading = true;
        final int offset = all.size();
        Qvk3Jobs.io(() -> {
            List<Qvk3Messages.Chat> r = new ArrayList<>();
            try {
                r = Qvk3Messages.chats(offset);
            } catch (Exception ignored) {
            }
            final List<Qvk3Messages.Chat> fr = r;
            Qvk3Jobs.main(() -> {
                loading = false;
                java.util.Set<Long> have = new java.util.HashSet<>();
                for (Qvk3Messages.Chat c : all) have.add(c.peer);
                int before = all.size();
                for (Qvk3Messages.Chat c : fr) if (have.add(c.peer)) all.add(c);
                if (all.size() == before) end = true; // nothing new: do not ask again
                filter();
            });
        });
    }

    final class ChatsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return shown.get(i).peer;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            if (i >= shown.size() - 8) more();
            Qvk3Messages.Chat c = shown.get(i);
            Activity a = Qvk3ShellActivity.this;
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, 0, 0);
            FrameLayout av = s.avatar(a, c.title, c.photo, 54, c.peer);
            FrameLayout avBox = new FrameLayout(a);
            avBox.addView(av, new FrameLayout.LayoutParams(dp(54), dp(54)));
            if (c.online) {
                View dot = new View(a);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(s.green);
                g.setStroke(dp(2), s.bg);
                dot.setBackground(g);
                avBox.addView(dot, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.BOTTOM | Gravity.END));
            }
            row.addView(avBox, new LinearLayout.LayoutParams(dp(54), dp(54)));
            LinearLayout right = new LinearLayout(a);
            right.setOrientation(LinearLayout.VERTICAL);
            LinearLayout in = new LinearLayout(a);
            in.setOrientation(LinearLayout.VERTICAL);
            in.setPadding(dp(12), dp(10), dp(16), dp(10));
            LinearLayout l1 = new LinearLayout(a);
            l1.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = Qvk3Ui.line(a, c.title, 17, s.text, true);
            l1.addView(name, Qvk3Ui.weight(1));
            l1.addView(Qvk3Ui.text(a, c.date > 0 ? Qvk3Messages.time(c.date) : "", 14, c.unread > 0 ? s.tint : s.text2, false));
            in.addView(l1);
            LinearLayout l2 = new LinearLayout(a);
            l2.setGravity(Gravity.TOP);
            TextView prev = Qvk3Ui.text(a, (c.out ? "Вы: " : "") + c.preview, 15, s.text2, false);
            prev.setMaxLines(2);
            prev.setMinLines(2);
            prev.setEllipsize(TextUtils.TruncateAt.END);
            l2.addView(prev, Qvk3Ui.weight(1));
            if (c.unread > 0) {
                TextView badge = Qvk3Ui.text(a, c.unread > 999 ? "999+" : String.valueOf(c.unread), 13, Color.WHITE, true);
                badge.setGravity(Gravity.CENTER);
                badge.setMinWidth(dp(22));
                badge.setMinHeight(dp(22));
                badge.setPadding(dp(6), 0, dp(6), 0);
                badge.setBackground(Qvk3Ui.rounded(s.tint, dp(11)));
                LinearLayout.LayoutParams bl = Qvk3Ui.wrap();
                bl.leftMargin = dp(8);
                bl.topMargin = dp(2);
                l2.addView(badge, bl);
            }
            in.addView(l2);
            right.addView(in);
            View sep = new View(a);
            sep.setBackgroundColor(s.sep);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f)));
            sl.leftMargin = dp(12);
            right.addView(sep, sl);
            row.addView(right, Qvk3Ui.weight(1));
            row.setContentDescription(c.title + ". " + c.preview + (c.unread > 0 ? ". Непрочитанных: " + c.unread : ""));
            return row;
        }
    }
}
