package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * QVK 3.0 settings centre: large title, pinned search, appearance card, 9 categories with icon tiles, and
 * inside each category grouped cards ("Нечиталка", "Голосовые", …). Same preference keys as QVK 2.x.
 */
public final class Qvk3SettingsActivity extends Activity {
    static final String FAVORITES = "qvk3_settings_favorites";
    private Qvk3Ui.Palette p;
    private Qvk3SettingsIndex index;
    private LinearLayout content;
    private ScrollView scroll;
    private EditText search;
    private TextView title, subtitle;
    private View back;
    private int section = -1;
    private Object backCallback;

    private int dp(float v) {
        return Qvk3Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean dark = Qvk3Ui.night(this);
        int accent = Qvk3Ui.accent(this);
        p = dark ? new Qvk3Ui.Palette(true, 0xFF0E0F12, 0xFF1C1D21, 0xFF2A2B30, 0xFFF2F3F5, 0xFF8E9199, accent, Color.WHITE, 0xFF2C2D32)
                : new Qvk3Ui.Palette(false, 0xFFF2F3F7, Color.WHITE, 0xFFE9EAEE, 0xFF0B0B0D, 0xFF7C7F87, accent, Color.WHITE, 0xFFE4E5EA);
        Qvk3Ui.bars(this, p);
        index = buildIndex(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setFitsSystemWindows(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(6), dp(8), 0);
        back = Qvk3Ui.icon(this, Qvk3Icons.BACK, p.text, 48, 24, "Назад", v -> handleBack());
        head.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(8), 0, 0, 0);
        title = Qvk3Ui.line(this, "Настройки", 30, p.text, true);
        title.setAccessibilityHeading(true);
        subtitle = Qvk3Ui.line(this, version(), 13, p.text2, false);
        tt.addView(title);
        tt.addView(subtitle);
        head.addView(tt, Qvk3Ui.weight(1));
        head.addView(Qvk3Ui.icon(this, Qvk3Icons.UNDO, p.text, 48, 22, "Отменить последнее изменение", v -> undo()));
        root.addView(head, Qvk3Ui.match());

        FrameLayout searchBox = new FrameLayout(this);
        search = new EditText(this);
        search.setHint("Поиск настроек");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(p.text);
        search.setHintTextColor(p.text2);
        search.setBackground(Qvk3Ui.rounded(p.dark ? p.surface : 0xFFE5E6EB, dp(14)));
        search.setPadding(dp(44), dp(11), dp(16), dp(11));
        searchBox.addView(search, new FrameLayout.LayoutParams(-1, -2));
        android.widget.ImageView mag = new android.widget.ImageView(this);
        mag.setImageDrawable(Qvk3Icons.of(Qvk3Icons.SEARCH, p.text2));
        searchBox.addView(mag, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL | Gravity.START));
        ((FrameLayout.LayoutParams) mag.getLayoutParams()).leftMargin = dp(14);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                content.removeCallbacks(doSearch);
                content.postDelayed(doSearch, 150);
            }
        });
        LinearLayout.LayoutParams slp = Qvk3Ui.match();
        slp.setMargins(dp(16), dp(10), dp(16), dp(6));
        root.addView(searchBox, slp);

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(32));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (state != null) section = state.getInt("section", -1);
        if (state != null && !state.getString("query", "").isEmpty()) search.setText(state.getString("query"));
        else if (section >= 0) showSection(section);
        else showHome();
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback cb = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        }
    }

    private String version() {
        String n = Qvk3Updates.installedName(this);
        return "QVK " + (n == null || n.equals("?") ? "3.0" : n.replace("8.100-qvk-", "")) + " · на базе VK " + Qvk3UpdateManifest.VK_BASE;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("section", section);
        out.putString("query", search.getText().toString());
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (search.length() > 0) {
            search.setText("");
            return;
        }
        if (section >= 0) {
            showHome();
            return;
        }
        finish();
    }

    private void refresh() {
        if (search.length() > 0) doSearch.run();
        else if (section >= 0) showSection(section);
        else showHome();
    }

    // ------------------------------------------------------------------ data

    static JSONArray catalog(Activity a) {
        try (InputStream in = a.getAssets().open("qmods_features.json")) {
            byte[] b = new byte[1 << 20];
            int n = Qvk3Id3.readFully(in, b);
            return new JSONArray(new String(b, 0, n, "UTF-8"));
        } catch (Exception e) {
            String path = System.getProperty("qvk.features"); // JVM screenshot tests only
            if (path != null) {
                try {
                    return new JSONArray(new String(java.nio.file.Files.readAllBytes(new java.io.File(path).toPath()), "UTF-8"));
                } catch (Exception ignored) {
                }
            }
            RuntimeFaults.record("qvk3.settings.catalog", e);
            return new JSONArray();
        }
    }

    static Qvk3SettingsIndex buildIndex(Activity a) {
        Qvk3SettingsIndex idx = new Qvk3SettingsIndex();
        // QVK 3.0 items first so they lead their cards
        idx.addScreen("qvk3:styles", "Стили и конструктор", "8 стилей, цвета, форма, шрифт, плотность — с живым предпросмотром", 0, "QVK 3.0");
        idx.addScreen("qvk3:scenarios", "Сценарии", "Обычный, Работа, Ночь, Музыка, Фокус и свои", 0, "QVK 3.0");
        idx.addScreen("qvk3:resetLook", "Сбросить всё оформление", "Только темы и стили; данные не затрагиваются", 0, "QVK 3.0");
        idx.addScreen("qvk3:music", "Открыть QVK Music", "Собственный музыкальный раздел и плеер", 4, "QVK Music");
        idx.addScreen("qvk3:musicSettings", "Настройки QVK Music", "Воспроизведение, звук, плеер, офлайн, тексты", 4, "QVK Music");
        idx.addScreen("qvk3:push", "Диагностика Push", "Google/microG, транспорт, фоновые ограничения", 6, "Доставка уведомлений");
        idx.addScreen("qvk3:storage", "Место на устройстве", "Музыка, обложки, обновления", 7, "Загрузки и место");
        idx.addScreen("qvk3:perf", "Облегчённый режим", "Без размытия и прозрачности — для слабых устройств", 7, "Производительность и батарея");
        idx.addScreen("qvk3:battery", "Фоновая работа и батарея", "Оптимизация батареи и ограничения Android", 7, "Производительность и батарея");
        idx.addScreen("qvk3:faults", "Журнал ошибок", "Сбои модулей с указанием компонента", 7, "Диагностика");
        idx.addScreen("qvk3:updates", "Проверить обновления", "Загрузка с докачкой, проверка подписи", 8, "Обновления");
        idx.addScreen("qvk3:updateSources", "Серверы обновлений", "Свои адреса, зеркала и ключи подписи", 8, "Обновления");
        idx.addScreen("qvk3:about", "О QVK 3.0", "Версия, база VK 8.100, миграция", 8, "О QVK");
        idx.addScreen("qvk3:shell", "Открыть оболочку QVK", "Чаты и музыка в стиле iOS", 1, "Оболочка QVK");
        idx.addScreen("qvk3:shellToggle", "Запускать оболочку QVK при старте", "Вместо главного экрана ВКонтакте; выключите, чтобы вернуть штатный запуск", 1, "Оболочка QVK");
        idx.addScreen("qvk3:classic", "Классические настройки QVK", "Прежний экран настроек QVK 2.x", 8, "О QVK");
        idx.addScreen("qvk3:classicDefault", "Открывать классические настройки", "Вернуть прежний экран вместо этого центра", 8, "О QVK");
        idx.addCatalog(catalog(a));
        for (SettingsHub.Entry e : SettingsHub.ENTRIES) idx.addHubScreen(e.group, e.title, e.detail, e.target);
        return idx;
    }

    private Set<String> favorites() {
        return new HashSet<>(Qvk3MusicSettings.prefs(this).getStringSet(FAVORITES, new HashSet<>()));
    }

    private void toggleFavorite(Qvk3SettingsIndex.Item it) {
        Set<String> f = favorites();
        boolean add = !f.remove(it.id);
        if (add) f.add(it.id);
        Qvk3MusicSettings.prefs(this).edit().putStringSet(FAVORITES, f).apply();
        Qvk3Ui.toast(this, add ? "Добавлено в избранное" : "Убрано из избранного");
        refresh();
    }

    private boolean value(Qvk3SettingsIndex.Item it) {
        try {
            FeatureCatalog.Entry e = FeatureCatalog.get(this).find(it.key);
            if (e != null) return e.value();
        } catch (Throwable ignored) {
            // fall back to the raw preference
        }
        return Qvk3MusicSettings.prefs(this).getBoolean(it.key, it.def);
    }

    // ------------------------------------------------------------------ building blocks

    private void header(String text) {
        TextView t = Qvk3Ui.text(this, text.toUpperCase(java.util.Locale.ROOT), 12, p.text2, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(16), dp(22), dp(16), dp(8));
        t.setAccessibilityHeading(true);
        content.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = Qvk3Ui.card(this, p);
        content.addView(c, Qvk3Ui.match());
        return c;
    }

    private void divider(LinearLayout card, int inset) {
        if (card.getChildCount() == 0) return;
        View d = new View(this);
        d.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.6f)));
        lp.leftMargin = dp(inset);
        card.addView(d, lp);
    }

    /** One row: optional tile, title, summary, trailing chevron/switch/value. */
    private LinearLayout row(LinearLayout card, String icon, int tileColor, String titleText, String summary, View trailing, Runnable click, Runnable longClick) {
        divider(card, icon != null ? 64 : 16);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(56));
        r.setPadding(dp(16), dp(10), dp(12), dp(10));
        r.setBackground(Qvk3Ui.ripple(this, 0, p.dark ? 0x22FFFFFF : 0x14000000, 0));
        if (icon != null) {
            View tile = Qvk3Ui.tile(this, icon, tileColor, 32);
            LinearLayout.LayoutParams tl = (LinearLayout.LayoutParams) tile.getLayoutParams();
            tl.rightMargin = dp(16);
            r.addView(tile, tl);
        }
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Qvk3Ui.text(this, titleText, 16, p.text, false));
        if (summary != null && !summary.isEmpty()) {
            TextView s = Qvk3Ui.text(this, summary, 13, p.text2, false);
            s.setMaxLines(2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(s);
        }
        r.addView(texts, Qvk3Ui.weight(1));
        if (trailing == null) {
            android.widget.ImageView chev = new android.widget.ImageView(this);
            chev.setImageDrawable(Qvk3Icons.of(Qvk3Icons.RIGHT, p.dark ? 0xFF5C5F66 : 0xFFC4C6CC));
            trailing = chev;
            chev.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
        }
        r.addView(trailing);
        if (click != null) r.setOnClickListener(v -> click.run());
        if (longClick != null) r.setOnLongClickListener(v -> {
            longClick.run();
            return true;
        });
        r.setContentDescription(titleText + (summary == null ? "" : ". " + summary));
        card.addView(r, Qvk3Ui.match());
        return r;
    }

    private Switch toggle(boolean checked) {
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setClickable(false);
        s.setFocusable(false);
        s.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{Color.WHITE, p.dark ? 0xFFB9BBC2 : Color.WHITE}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0xFF34C759, p.dark ? 0xFF3A3B40 : 0xFFD9DAE0}));
        return s;
    }

    private void itemRow(LinearLayout card, Qvk3SettingsIndex.Item it, boolean showSection) {
        boolean fav = favorites().contains(it.id);
        String sum = it.summary;
        if (showSection) sum = Qvk3SettingsIndex.SECTIONS[it.section] + " › " + it.group + (sum.isEmpty() ? "" : " · " + sum);
        String t = (fav ? "★ " : "") + it.title;
        if (it.type == Qvk3SettingsIndex.SWITCH) {
            boolean on = value(it);
            LinearLayout r = row(card, null, 0, t, sum, toggle(on), () -> flip(it), () -> toggleFavorite(it));
            r.setContentDescription(it.title + ". " + (on ? "Включено" : "Выключено") + ". " + it.summary);
        } else {
            row(card, null, 0, t, sum, null, () -> open(it), () -> toggleFavorite(it));
        }
    }

    private void begin(String heading, String sub, boolean home) {
        content.removeAllViews();
        title.setText(heading);
        subtitle.setText(sub);
        back.setVisibility(home ? View.GONE : View.VISIBLE);
        scroll.scrollTo(0, 0);
    }

    // ------------------------------------------------------------------ screens

    private final Runnable doSearch = () -> {
        String q = search.getText().toString().trim();
        if (q.isEmpty()) {
            if (section >= 0) showSection(section);
            else showHome();
            return;
        }
        List<Qvk3SettingsIndex.Item> found = index.search(q, 60);
        begin("Поиск", found.isEmpty() ? "Ничего не найдено" : "Найдено: " + found.size(), false);
        if (found.isEmpty()) return;
        LinearLayout c = card();
        for (Qvk3SettingsIndex.Item it : found) itemRow(c, it, true);
    };

    private void showHome() {
        section = -1;
        if (search.length() > 0) search.setText("");
        begin("Настройки", version(), true);

        // appearance card
        Qvk3DesignTokens t = Qvk3Appearance.current(this);
        LinearLayout look = card();
        look.setPadding(dp(16), dp(16), dp(16), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout sw = new LinearLayout(this);
        for (int col : new int[]{t.accent, Qvk3DesignTokens.over(t.page, 0xFF000000), Qvk3DesignTokens.over(t.content, 0xFF000000), Qvk3DesignTokens.over(t.outgoing, 0xFF000000)}) {
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(col);
            g.setStroke(dp(2), p.surface);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.leftMargin = sw.getChildCount() == 0 ? 0 : -dp(9);
            sw.addView(dot, lp);
        }
        top.addView(sw);
        LinearLayout lt = new LinearLayout(this);
        lt.setOrientation(LinearLayout.VERTICAL);
        lt.setPadding(dp(14), 0, 0, 0);
        lt.addView(Qvk3Ui.text(this, "Оформление", 13, p.text2, false));
        lt.addView(Qvk3Ui.line(this, Qvk3Appearance.currentTitle(this), 18, p.text, true));
        top.addView(lt, Qvk3Ui.weight(1));
        look.addView(top);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(14), 0, 0);
        chips.addView(chip("Стили", true, this::styles), Qvk3Ui.weight(1));
        chips.addView(gap());
        chips.addView(chip("Сценарии", false, this::scenarios), Qvk3Ui.weight(1));
        chips.addView(gap());
        chips.addView(chip(t.dark ? "Светлая" : "Тёмная", false, this::quickTheme), Qvk3Ui.weight(1));
        look.addView(chips);

        // scenario
        Qvk3Scenarios.Scenario sc = Qvk3Scenarios.find(this, Qvk3Scenarios.active(this));
        LinearLayout scCard = card();
        ((LinearLayout.LayoutParams) scCard.getLayoutParams()).topMargin = dp(12);
        row(scCard, Qvk3Icons.WAND, 0xFF5AC8FA, "Сценарий: " + (sc == null ? "не выбран" : sc.title),
                sc == null ? "Обычный, Работа, Ночь, Музыка, Фокус" : sc.description, null, this::scenarios, null);

        // favourites
        Set<String> fav = favorites();
        if (!fav.isEmpty()) {
            header("Избранное");
            LinearLayout c = card();
            for (Qvk3SettingsIndex.Item it : index.all()) if (fav.contains(it.id)) itemRow(c, it, false);
        }

        // recent changes
        List<Qvk3SettingsHistory.Entry> hist = Qvk3SettingsHistory.list(this);
        if (!hist.isEmpty()) {
            // header row with a compact "undo" chip instead of a full-width row
            LinearLayout hr = new LinearLayout(this);
            hr.setGravity(Gravity.CENTER_VERTICAL);
            hr.setPadding(dp(16), dp(22), dp(4), dp(8));
            TextView ht = Qvk3Ui.text(this, "НЕДАВНИЕ ИЗМЕНЕНИЯ", 12, p.text2, true);
            ht.setLetterSpacing(0.06f);
            ht.setAccessibilityHeading(true);
            hr.addView(ht, Qvk3Ui.weight(1));
            TextView undoChip = Qvk3Ui.text(this, "Отменить", 13, p.accent, true);
            undoChip.setPadding(dp(12), dp(6), dp(12), dp(6));
            undoChip.setBackground(Qvk3Ui.ripple(this, p.dark ? 0xFF2A2C33 : 0xFFE4EAF5, 0x33888888, dp(14)));
            undoChip.setContentDescription("Отменить последнее изменение");
            undoChip.setOnClickListener(v -> undo());
            hr.addView(undoChip);
            content.addView(hr);
            LinearLayout c = card();
            java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
            for (int i = 0; i < Math.min(3, hist.size()); i++) {
                Qvk3SettingsHistory.Entry e = hist.get(i);
                Qvk3SettingsIndex.Item it = index.get(e.key);
                TextView when = Qvk3Ui.text(this, df.format(new java.util.Date(e.time)), 12, p.text2, false);
                row(c, null, 0, e.title, null, when, it == null ? null : () -> showSection(it.section), null);
            }

        }

        // categories
        int[][] groups = {{0, 1}, {2, 3, 6}, {4, 5}, {7, 8}};
        for (int[] g : groups) {
            LinearLayout c = card();
            ((LinearLayout.LayoutParams) c.getLayoutParams()).topMargin = dp(18);
            for (int s : g) {
                final int sec = s;
                row(c, Qvk3SettingsIndex.SECTION_ICONS[s], Qvk3SettingsIndex.SECTION_COLORS[s], Qvk3SettingsIndex.SECTIONS[s],
                        Qvk3SettingsIndex.SECTION_HINTS[s], null, () -> showSection(sec), null);
            }
        }
        TextView hint = Qvk3Ui.text(this, "Удерживайте настройку, чтобы добавить её в избранное.", 13, p.text2, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(16), dp(18), dp(16), 0);
        content.addView(hint);
    }

    private View gap() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        return v;
    }

    private TextView chip(String label, boolean primary, Runnable r) {
        TextView b = Qvk3Ui.text(this, label, 14, primary ? Color.WHITE : p.accent, true);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        b.setMinHeight(dp(44));
        b.setBackground(Qvk3Ui.ripple(this, primary ? p.accent : (p.dark ? 0xFF2A2C33 : 0xFFEDF1F8), 0x33888888, dp(12)));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void showSection(int s) {
        section = s;
        begin(Qvk3SettingsIndex.SECTIONS[s], Qvk3SettingsIndex.SECTION_HINTS[s], false);
        Qvk3SettingsIndex.Item first = null;
        for (String g : index.groups(s)) {
            header(g);
            LinearLayout c = card();
            List<Qvk3SettingsIndex.Item> screens = new ArrayList<>(), switches = new ArrayList<>();
            for (Qvk3SettingsIndex.Item it : index.section(s)) {
                if (!it.group.equals(g)) continue;
                (it.type == Qvk3SettingsIndex.SWITCH ? switches : screens).add(it);
            }
            for (Qvk3SettingsIndex.Item it : screens) itemRow(c, it, false);
            for (Qvk3SettingsIndex.Item it : switches) itemRow(c, it, false);
            if (first == null && !switches.isEmpty()) first = switches.get(0);
        }
        if (first != null) {
            LinearLayout c = card();
            ((LinearLayout.LayoutParams) c.getLayoutParams()).topMargin = dp(24);
            row(c, null, 0, "Сбросить переключатели раздела", "Вернуть значения по умолчанию; PIN не сбрасывается", new View(this), () -> resetSection(s), null);
        }
    }

    // ------------------------------------------------------------------ actions

    private void flip(Qvk3SettingsIndex.Item it) {
        boolean now = !value(it);
        if (it.key.equals("enablePin") && now && !Qvk3MusicSettings.prefs(this).contains("qmods_pin_hash")) {
            SettingsEditorActivity.open(this, "pin");
            return;
        }
        Qvk3SettingsHistory.before(this, it.key, (now ? "Включено: " : "Выключено: ") + it.title);
        try {
            SettingsRuntime.toggle(it.key, now);
        } catch (Throwable e) {
            Qvk3MusicSettings.prefs(this).edit().putBoolean(it.key, now).apply();
        }
        int y = scroll.getScrollY();
        refresh();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void open(Qvk3SettingsIndex.Item it) {
        String t = it.target;
        try {
            if (t.startsWith("qvk3:")) qvk3(t.substring(5));
            else if (it.type == Qvk3SettingsIndex.SCREEN) SettingsHub.open(this, t);
            else Qvk3SettingsBridge.runClassicAction(this, t);
        } catch (Throwable e) {
            RuntimeFaults.record("qvk3.settings.open", e);
            Qvk3Ui.toast(this, "Не удалось открыть: " + Qvk3Jobs.message(e));
        }
    }

    private void qvk3(String what) throws Exception {
        switch (what) {
            case "styles":
            case "editor":
                styles();
                break;
            case "resetLook":
                Qvk3Ui.dialog(this).setTitle("Сбросить всё оформление?")
                        .setMessage("Будут сброшены тема, стили iOS/Material и стиль QVK 3.0. Остальные настройки и данные не изменятся.")
                        .setPositiveButton("Сбросить", (d, w) -> Qvk3Appearance.reset(this)).setNegativeButton("Отмена", null).show();
                break;
            case "scenarios":
                scenarios();
                break;
            case "music":
                startActivity(new Intent(this, Qvk3MusicActivity.class));
                break;
            case "musicSettings":
                Qvk3MusicSettingsScreen.show(this);
                break;
            case "push":
                Qvk3InfoScreens.push(this);
                break;
            case "perf": {
                SharedPreferences pr = Qvk3MusicSettings.prefs(this);
                boolean on = !pr.getBoolean("qvk3_perf_light", Qvk3Compat.lowRam(this));
                Qvk3SettingsHistory.before(this, "qvk3_perf_light", on ? "Облегчённый режим включён" : "Облегчённый режим выключен");
                SharedPreferences.Editor e = pr.edit().putBoolean("qvk3_perf_light", on);
                if (on) e.putBoolean("qvk_motion_blur", false);
                e.apply();
                Qvk3Ui.toast(this, on ? "Облегчённый режим включён" : "Облегчённый режим выключен");
                break;
            }
            case "battery":
            case "storage":
            case "about":
            case "faults":
                Qvk3InfoScreens.show(this, what);
                break;
            case "updates":
                Qvk3InfoScreens.updates(this);
                break;
            case "updateSources":
                Qvk3InfoScreens.updateSources(this);
                break;
            case "shell":
                startActivity(new Intent(this, Qvk3ShellActivity.class));
                break;
            case "shellToggle": {
                SharedPreferences pr = Qvk3MusicSettings.prefs(this);
                boolean on = !pr.getBoolean(Qvk3ShellActivity.ENABLED, true);
                Qvk3SettingsHistory.before(this, Qvk3ShellActivity.ENABLED, on ? "Оболочка QVK при старте включена" : "Оболочка QVK при старте выключена");
                pr.edit().putBoolean(Qvk3ShellActivity.ENABLED, on).apply();
                Qvk3Ui.toast(this, on ? "При запуске будет открываться оболочка QVK" : "При запуске будет открываться штатный ВКонтакте");
                break;
            }
            case "classic":
                startActivity(new Intent(this, QModsActivity.class));
                break;
            case "classicDefault": {
                SharedPreferences pr = Qvk3MusicSettings.prefs(this);
                boolean on = !pr.getBoolean(Qvk3SettingsBridge.CLASSIC, false);
                Qvk3SettingsHistory.before(this, Qvk3SettingsBridge.CLASSIC, on ? "Классические настройки по умолчанию" : "Центр настроек QVK 3.0 по умолчанию");
                pr.edit().putBoolean(Qvk3SettingsBridge.CLASSIC, on).apply();
                Qvk3Ui.toast(this, on ? "Меню будет открывать классические настройки. Этот центр — по ярлыку «Настройки QVK»" : "Меню будет открывать этот центр");
                break;
            }
            default:
                throw new IllegalArgumentException(what);
        }
    }

    private void styles() {
        startActivity(new Intent(this, Qvk3StylesActivity.class));
    }

    private void quickTheme() {
        try {
            Qvk3Appearance.apply(this, Qvk3DesignTokens.PRESETS.get(Qvk3Appearance.current(this).dark ? "classic" : "amoled"));
        } catch (Exception e) {
            Qvk3Ui.toast(this, "Не удалось применить: " + Qvk3Jobs.message(e));
        }
    }

    private void scenarios() {
        List<Qvk3Scenarios.Scenario> all = Qvk3Scenarios.all(this);
        List<String> labels = new ArrayList<>();
        String active = Qvk3Scenarios.active(this);
        for (Qvk3Scenarios.Scenario s : all) labels.add((s.id.equals(active) ? "✓ " : "") + s.title + " — " + s.description);
        labels.add("＋ Сохранить текущие настройки как сценарий");
        if (Qvk3Scenarios.canUndo(this)) labels.add("↶ Вернуть как было до сценария");
        Qvk3Ui.dialog(this).setTitle("Сценарии").setItems(labels.toArray(new String[0]), (d, i) -> {
            try {
                if (i < all.size()) {
                    Qvk3Scenarios.Scenario s = all.get(i);
                    StringBuilder what = new StringBuilder();
                    for (String k : s.values.keySet()) {
                        Qvk3SettingsIndex.Item it = index.get(k);
                        Object v = s.values.get(k);
                        what.append("• ").append(it != null ? it.title : k).append(": ")
                                .append(v instanceof Boolean ? ((Boolean) v ? "вкл" : "выкл") : v).append('\n');
                    }
                    if (!s.style.isEmpty() && Qvk3DesignTokens.PRESETS.containsKey(s.style)) what.append("• Стиль: ").append(Qvk3DesignTokens.PRESETS.get(s.style).title);
                    android.app.AlertDialog.Builder b = Qvk3Ui.dialog(this).setTitle(s.title).setMessage(s.description + "\n\nБудет изменено:\n" + what)
                            .setPositiveButton("Применить", (d2, w) -> {
                                try {
                                    Qvk3Scenarios.apply(this, s);
                                } catch (Exception e) {
                                    Qvk3Ui.toast(this, Qvk3Jobs.message(e));
                                }
                            }).setNegativeButton("Отмена", null);
                    if (!s.builtin) b.setNeutralButton("Удалить", (d2, w) -> {
                        try {
                            Qvk3Scenarios.delete(this, s.id);
                        } catch (Exception e) {
                            Qvk3Ui.toast(this, Qvk3Jobs.message(e));
                        }
                    });
                    b.show();
                } else if (i == all.size()) {
                    EditText in = new EditText(this);
                    in.setInputType(InputType.TYPE_CLASS_TEXT);
                    in.setHint("Название сценария");
                    Qvk3Ui.dialog(this).setTitle("Новый сценарий").setView(in).setPositiveButton("Сохранить", (d2, w) -> {
                        List<String> keys = new ArrayList<>();
                        for (Qvk3SettingsIndex.Item it : index.all()) if (it.type == Qvk3SettingsIndex.SWITCH) keys.add(it.key);
                        keys.add(Qvk3MusicSettings.LAYOUT);
                        try {
                            String name = in.getText().toString().trim();
                            Qvk3Scenarios.saveCurrent(this, name.isEmpty() ? "Мой сценарий" : name, keys);
                            Qvk3Ui.toast(this, "Сценарий сохранён");
                        } catch (Exception e) {
                            Qvk3Ui.toast(this, Qvk3Jobs.message(e));
                        }
                    }).setNegativeButton("Отмена", null).show();
                } else {
                    Qvk3Scenarios.undo(this);
                }
            } catch (Exception e) {
                Qvk3Ui.toast(this, Qvk3Jobs.message(e));
            }
        }).show();
    }

    private void resetSection(int s) {
        Qvk3Ui.dialog(this).setTitle("Сбросить «" + Qvk3SettingsIndex.SECTIONS[s] + "»?")
                .setMessage("Переключатели раздела вернутся к значениям по умолчанию. Другие разделы и данные не изменятся. Каждое изменение можно отменить.")
                .setPositiveButton("Сбросить", (d, w) -> {
                    SharedPreferences.Editor e = Qvk3MusicSettings.prefs(this).edit();
                    for (Qvk3SettingsIndex.Item it : index.section(s)) {
                        if (it.type != Qvk3SettingsIndex.SWITCH || it.key.equals("enablePin")) continue;
                        Qvk3SettingsHistory.before(this, it.key, "Сброс: " + it.title);
                        e.remove(it.key);
                    }
                    e.apply();
                    showSection(s);
                }).setNegativeButton("Отмена", null).show();
    }

    private void undo() {
        String t = Qvk3SettingsHistory.undoLast(this);
        Qvk3Ui.toast(this, t == null ? "Нечего отменять" : "Отменено: " + t);
        refresh();
    }
}
