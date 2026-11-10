package re.sova.qmods;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * QVK 3.0 settings centre. Header with version, pinned search, current style, light/dark switch and the
 * active scenario; 15 sections; favourites; recent changes with undo; per-section reset.
 *
 * Settings are the same keys QVK 2.x uses (FeatureCatalog / SettingsRuntime.toggle), so nothing has to be
 * migrated and the classic screen ("Классические настройки QVK") keeps working side by side.
 */
public final class Qvk3SettingsActivity extends Activity {
    static final String FAVORITES = "qvk3_settings_favorites";
    private Qvk3Ui.Palette p;
    private Qvk3SettingsIndex index;
    private ListView list;
    private Adapter adapter;
    private EditText search;
    private TextView title, home;
    private int section = -1;
    private Object backCallback;

    interface RowAction {
        void run();
    }

    static final class Row {
        final int kind; // 0 header, 1 link, 2 switch, 3 note
        final String title, summary;
        final Qvk3SettingsIndex.Item item;
        final RowAction click;

        Row(int kind, String title, String summary, Qvk3SettingsIndex.Item item, RowAction click) {
            this.kind = kind;
            this.title = title;
            this.summary = summary;
            this.item = item;
            this.click = click;
        }
    }

    final class Adapter extends BaseAdapter {
        final List<Row> rows = new ArrayList<>();

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int i) {
            return rows.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public int getItemViewType(int i) {
            return rows.get(i).kind;
        }

        @Override
        public boolean isEnabled(int i) {
            return rows.get(i).kind != 0 && (rows.get(i).click != null || rows.get(i).kind == 2);
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Row r = rows.get(i);
            Activity a = Qvk3SettingsActivity.this;
            int pad = Qvk3Ui.dp(a, 16);
            if (r.kind == 0) {
                TextView t = Qvk3Ui.text(a, r.title, 14, p.accent, true);
                t.setPadding(pad, Qvk3Ui.dp(a, 20), pad, Qvk3Ui.dp(a, 6));
                t.setAccessibilityHeading(true);
                return t;
            }
            if (r.kind == 3) {
                TextView t = Qvk3Ui.text(a, r.title, 13, p.text2, false);
                t.setPadding(pad, Qvk3Ui.dp(a, 6), pad, Qvk3Ui.dp(a, 6));
                return t;
            }
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, Qvk3Ui.dp(a, 10), pad, Qvk3Ui.dp(a, 10));
            row.setMinimumHeight(Qvk3Ui.dp(a, 56));
            LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(LinearLayout.VERTICAL);
            boolean fav = r.item != null && favorites().contains(r.item.id);
            texts.addView(Qvk3Ui.text(a, (fav ? "★ " : "") + r.title, 16, p.text, false));
            if (r.summary != null && !r.summary.isEmpty()) texts.addView(Qvk3Ui.text(a, r.summary, 13, p.text2, false));
            row.addView(texts, Qvk3Ui.weight(1));
            if (r.kind == 2) {
                Switch s = new Switch(a);
                s.setChecked(value(r.item));
                s.setClickable(false);
                s.setFocusable(false);
                s.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(s);
                row.setContentDescription(r.title + ". " + (value(r.item) ? "Включено" : "Выключено") + ". " + r.summary);
            } else {
                row.addView(Qvk3Ui.text(a, "›", 22, p.text2, false));
                row.setContentDescription(r.title + ". " + (r.summary == null ? "" : r.summary));
            }
            return row;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        p = Qvk3Ui.palette(this, Qvk3Appearance.current(this).dark ? "amoled" : "modern");
        Qvk3Ui.bars(this, p);
        index = buildIndex();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setFitsSystemWindows(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Qvk3Ui.dp(this, 4), Qvk3Ui.dp(this, 4), Qvk3Ui.dp(this, 8), 0);
        home = Qvk3Ui.glyph(this, p, "⌂", "На главный экран настроек", 22, v -> showHome());
        head.addView(home);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        title = Qvk3Ui.line(this, "QVK", 24, p.text, true);
        title.setAccessibilityHeading(true);
        tt.addView(title);
        tt.addView(Qvk3Ui.line(this, Qvk3Updates.installedName(this) + " · на базе VK " + Qvk3UpdateManifest.VK_BASE, 12, p.text2, false));
        head.addView(tt, Qvk3Ui.weight(1));
        head.addView(Qvk3Ui.glyph(this, p, "↶", "Отменить последнее изменение", 22, v -> undo()));
        root.addView(head, Qvk3Ui.match());

        search = new EditText(this);
        search.setHint("Поиск по всем настройкам");
        search.setSingleLine(true);
        search.setTextColor(p.text);
        search.setHintTextColor(p.text2);
        search.setBackground(Qvk3Ui.rounded(p.surface2, Qvk3Ui.dp(this, 20)));
        search.setPadding(Qvk3Ui.dp(this, 16), Qvk3Ui.dp(this, 10), Qvk3Ui.dp(this, 16), Qvk3Ui.dp(this, 10));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                list.removeCallbacks(doSearch);
                list.postDelayed(doSearch, 150);
            }
        });
        LinearLayout.LayoutParams slp = Qvk3Ui.match();
        slp.setMargins(Qvk3Ui.dp(this, 16), Qvk3Ui.dp(this, 6), Qvk3Ui.dp(this, 16), Qvk3Ui.dp(this, 6));
        root.addView(search, slp);

        list = new ListView(this);
        list.setDivider(null);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, i, id) -> click(adapter.rows.get(i)));
        list.setOnItemLongClickListener((parent, view, i, id) -> {
            Row r = adapter.rows.get(i);
            if (r.item == null) return false;
            toggleFavorite(r.item);
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (state != null) section = state.getInt("section", -1);
        if (state != null && !state.getString("query", "").isEmpty()) {
            search.setText(state.getString("query"));
        } else if (section >= 0) {
            showSection(section);
        } else {
            showHome();
        }
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback cb = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged(); // values may have changed in a QVK 2.x screen
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

    // ------------------------------------------------------------------ data

    private Qvk3SettingsIndex buildIndex() {
        Qvk3SettingsIndex idx = new Qvk3SettingsIndex();
        try (InputStream in = getAssets().open("qmods_features.json")) {
            byte[] b = new byte[1 << 20];
            int n = Qvk3Id3.readFully(in, b);
            idx.addCatalog(new JSONArray(new String(b, 0, n, "UTF-8")));
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.settings.catalog", e);
        }
        for (SettingsHub.Entry e : SettingsHub.ENTRIES) idx.addHubScreen(e.group, e.title, e.detail, e.target);
        idx.addScreen("qvk3:styles", "Стили QVK 3.0", "Classic, Modern, Glass, AMOLED, Compact, Cozy, Neon, iOS", 1);
        idx.addScreen("qvk3:editor", "Конструктор оформления", "Цвета, фон, карточки, радиусы, шрифт, плотность — с предпросмотром", 1);
        idx.addScreen("qvk3:resetLook", "Сбросить всё оформление", "Темы и стили; остальные настройки и данные не затрагиваются", 1);
        idx.addScreen("qvk3:scenarios", "Сценарии", "Обычный, Работа, Ночь, Музыка, Фокус и свои", 1);
        idx.addScreen("qvk3:music", "QVK Music", "Собственный музыкальный раздел и плеер", 5);
        idx.addScreen("qvk3:musicSettings", "Настройки QVK Music", "Воспроизведение, звук, плеер, офлайн, тексты, CoverFlow", 5);
        idx.addScreen("qvk3:push", "Диагностический центр Push", "Сервисы Google/microG, транспорт, фоновые ограничения", 7);
        idx.addScreen("qvk3:perf", "Облегчённый режим", "Отключить тяжёлые эффекты (размытие, прозрачность)", 9);
        idx.addScreen("qvk3:battery", "Фоновая работа и батарея", "Оптимизация батареи, ограничения Android", 9);
        idx.addScreen("qvk3:storage", "Место на устройстве", "Музыка, обложки, обновления", 8);
        idx.addScreen("qvk3:updates", "Обновления QVK", "Проверка, загрузка с докачкой, история версий", 12);
        idx.addScreen("qvk3:updateSources", "Серверы обновлений", "Свои адреса манифеста и зеркала, ключи подписи", 12);
        idx.addScreen("qvk3:faults", "Журнал ошибок QVK 3.0", "Последние сбои модулей с указанием компонента", 13);
        idx.addScreen("qvk3:classic", "Классические настройки QVK", "Прежний экран настроек QVK 2.x", 14);
        idx.addScreen("qvk3:about", "О QVK 3.0", "Версия, база VK 8.100, миграция", 14);
        idx.addScreen("qvk3:classicDefault", "Открывать классические настройки по умолчанию", "Флаг возврата к прежнему интерфейсу настроек QVK 2.x", 14);
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
        Qvk3Ui.toast(this, add ? "Добавлено в избранные настройки" : "Убрано из избранных");
        adapter.notifyDataSetChanged();
    }

    private boolean value(Qvk3SettingsIndex.Item it) {
        SharedPreferences prefs = Qvk3MusicSettings.prefs(this);
        try {
            FeatureCatalog.Entry e = FeatureCatalog.get(this).find(it.key);
            if (e != null) return e.value();
        } catch (Exception ignored) {
            // fall back to the raw preference
        }
        return prefs.getBoolean(it.key, it.def);
    }

    // ------------------------------------------------------------------ screens

    private final Runnable doSearch = () -> {
        String q = search.getText().toString().trim();
        if (q.isEmpty()) {
            if (section >= 0) showSection(section);
            else showHome();
            return;
        }
        List<Row> rows = new ArrayList<>();
        List<Qvk3SettingsIndex.Item> found = index.search(q, 80);
        rows.add(new Row(3, found.isEmpty() ? "Ничего не найдено" : "Найдено: " + found.size(), null, null, null));
        for (Qvk3SettingsIndex.Item it : found) rows.add(row(it, true));
        set(rows, "Поиск");
    };

    private void set(List<Row> rows, String heading) {
        adapter.rows.clear();
        adapter.rows.addAll(rows);
        adapter.notifyDataSetChanged();
        title.setText(heading);
        home.setVisibility(section >= 0 || search.length() > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    private Row row(Qvk3SettingsIndex.Item it, boolean withSection) {
        String sum = it.summary + (withSection ? (it.summary.isEmpty() ? "" : " · ") + Qvk3SettingsIndex.SECTIONS[it.section] : "");
        if (it.type == Qvk3SettingsIndex.SWITCH) return new Row(2, it.title, sum, it, () -> flip(it));
        return new Row(1, it.title, sum, it, () -> open(it));
    }

    private void showHome() {
        section = -1;
        if (search.length() > 0) search.setText("");
        List<Row> rows = new ArrayList<>();
        Qvk3DesignTokens t = Qvk3Appearance.current(this);
        rows.add(new Row(1, "Стиль: " + Qvk3Appearance.currentTitle(this), "Нажмите, чтобы выбрать другой стиль", null, this::styles));
        rows.add(new Row(1, t.dark ? "☀ Переключить на светлое оформление" : "☾ Переключить на тёмное оформление",
                t.dark ? "Применит QVK Classic" : "Применит QVK AMOLED", null, this::quickTheme));
        Qvk3Scenarios.Scenario sc = Qvk3Scenarios.find(this, Qvk3Scenarios.active(this));
        rows.add(new Row(1, "Сценарий: " + (sc == null ? "не выбран" : sc.title), sc == null ? "Профили поведения приложения в одно касание" : sc.description, null, this::scenarios));
        Set<String> fav = favorites();
        if (!fav.isEmpty()) {
            rows.add(new Row(0, "Избранные настройки", null, null, null));
            for (Qvk3SettingsIndex.Item it : index.all()) if (fav.contains(it.id)) rows.add(row(it, true));
        }
        List<Qvk3SettingsHistory.Entry> hist = Qvk3SettingsHistory.list(this);
        if (!hist.isEmpty()) {
            rows.add(new Row(0, "Недавно изменённые", null, null, null));
            java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
            for (int i = 0; i < Math.min(5, hist.size()); i++) {
                Qvk3SettingsHistory.Entry e = hist.get(i);
                Qvk3SettingsIndex.Item it = index.get(e.key);
                rows.add(new Row(it != null ? 1 : 3, e.title, df.format(new java.util.Date(e.time)), it, it == null ? null : () -> showSection(it.section)));
            }
            rows.add(new Row(1, "↶ Отменить последнее изменение", null, null, this::undo));
        }
        rows.add(new Row(0, "Разделы", null, null, null));
        for (int s = 0; s < Qvk3SettingsIndex.SECTIONS.length; s++) {
            final int sec = s;
            int n = index.section(s).size();
            rows.add(new Row(1, Qvk3SettingsIndex.SECTIONS[s], Qvk3SettingsIndex.SECTION_HINTS[s] + " · " + n, null, () -> showSection(sec)));
        }
        rows.add(new Row(3, "Долгое нажатие на настройку добавляет её в избранное.", null, null, null));
        set(rows, "QVK");
        list.setSelection(0);
    }

    private void showSection(int s) {
        section = s;
        List<Row> rows = new ArrayList<>();
        List<Qvk3SettingsIndex.Item> items = index.section(s);
        List<Row> switches = new ArrayList<>(), links = new ArrayList<>();
        for (Qvk3SettingsIndex.Item it : items) (it.type == Qvk3SettingsIndex.SWITCH ? switches : links).add(row(it, false));
        if (!links.isEmpty()) {
            rows.add(new Row(0, "Экраны и действия", null, null, null));
            rows.addAll(links);
        }
        if (!switches.isEmpty()) {
            rows.add(new Row(0, "Переключатели", null, null, null));
            rows.addAll(switches);
            rows.add(new Row(1, "Сбросить раздел «" + Qvk3SettingsIndex.SECTIONS[s] + "»", "Вернуть переключатели раздела к значениям по умолчанию", null, () -> resetSection(s)));
        }
        if (rows.isEmpty()) rows.add(new Row(3, "В этом разделе пока нет настроек", null, null, null));
        set(rows, Qvk3SettingsIndex.SECTIONS[s]);
        list.setSelection(0);
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
        } catch (Exception e) {
            Qvk3MusicSettings.prefs(this).edit().putBoolean(it.key, now).apply();
        }
        adapter.notifyDataSetChanged();
    }

    private void open(Qvk3SettingsIndex.Item it) {
        String t = it.target;
        try {
            if (t.startsWith("qvk3:")) {
                qvk3(t.substring(5));
            } else if (it.type == Qvk3SettingsIndex.SCREEN) {
                SettingsHub.open(this, t);
            } else {
                // Catalog action: run through the classic QVK screen, which owns these actions.
                Qvk3SettingsBridge.runClassicAction(this, t);
            }
        } catch (Throwable e) {
            RuntimeFaults.record("qvk3.settings.open", e);
            Qvk3Ui.toast(this, "Не удалось открыть: " + Qvk3Jobs.message(e));
        }
    }

    private void qvk3(String what) throws Exception {
        switch (what) {
            case "styles":
                styles();
                break;
            case "editor":
                Qvk3StyleEditor.show(this);
                break;
            case "resetLook":
                Qvk3Ui.dialog(this).setTitle("Сбросить всё оформление?")
                        .setMessage("Будут сброшены тема, стили iOS/Material и стиль QVK 3.0. Остальные настройки, переписки и данные не изменятся. Отменить можно кнопкой ↶.")
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
                pr.edit().putBoolean("qvk3_perf_light", on).putBoolean("qvk_motion_blur", on ? false : pr.getBoolean("qvk_motion_blur", false)).apply();
                Qvk3Ui.toast(this, on ? "Облегчённый режим включён: размытие и прозрачность отключены" : "Облегчённый режим выключен");
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
            case "classic":
                startActivity(new Intent(this, QModsActivity.class));
                break;
            case "classicDefault": {
                SharedPreferences pr = Qvk3MusicSettings.prefs(this);
                boolean on = !pr.getBoolean(Qvk3SettingsBridge.CLASSIC, false);
                Qvk3SettingsHistory.before(this, Qvk3SettingsBridge.CLASSIC, on ? "Классические настройки по умолчанию" : "Центр настроек QVK 3.0 по умолчанию");
                pr.edit().putBoolean(Qvk3SettingsBridge.CLASSIC, on).apply();
                Qvk3Ui.toast(this, on ? "Меню будет открывать классические настройки. Центр 3.0 доступен по ярлыку «Настройки QVK»"
                        : "Меню будет открывать центр настроек QVK 3.0");
                break;
            }
            default:
                throw new IllegalArgumentException(what);
        }
    }

    private void styles() {
        List<Qvk3DesignTokens> all = new ArrayList<>(Qvk3DesignTokens.PRESETS.values());
        String[] labels = new String[all.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = all.get(i).title + " — " + all.get(i).description;
        Qvk3Ui.dialog(this).setTitle("Стиль оформления").setItems(labels, (d, i) -> Qvk3StyleEditor.preview(this, all.get(i))).show();
    }

    private void quickTheme() {
        Qvk3DesignTokens target = Qvk3DesignTokens.PRESETS.get(Qvk3Appearance.current(this).dark ? "classic" : "amoled");
        try {
            Qvk3Appearance.apply(this, target);
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
                        what.append("• ").append(it != null ? it.title : k).append(": ").append(s.values.get(k)).append('\n');
                    }
                    if (!s.style.isEmpty()) what.append("• Стиль: ").append(Qvk3DesignTokens.PRESETS.containsKey(s.style) ? Qvk3DesignTokens.PRESETS.get(s.style).title : s.style);
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
                .setMessage("Переключатели раздела вернутся к значениям по умолчанию. Другие разделы и данные не изменятся.")
                .setPositiveButton("Сбросить", (d, w) -> {
                    SharedPreferences.Editor e = Qvk3MusicSettings.prefs(this).edit();
                    for (Qvk3SettingsIndex.Item it : index.section(s)) {
                        if (it.type != Qvk3SettingsIndex.SWITCH || it.key.equals("enablePin")) continue; // PIN is never reset silently
                        Qvk3SettingsHistory.before(this, it.key, "Сброс: " + it.title); // each key stays undoable
                        e.remove(it.key);
                    }
                    e.apply();
                    Qvk3SettingsHistory.record(this, "section:" + s, "Сброшен раздел «" + Qvk3SettingsIndex.SECTIONS[s] + "»");
                    showSection(s);
                }).setNegativeButton("Отмена", null).show();
    }

    private void undo() {
        String t = Qvk3SettingsHistory.undoLast(this);
        Qvk3Ui.toast(this, t == null ? "Нечего отменять" : "Отменено: " + t);
        if (section >= 0) showSection(section);
        else showHome();
    }

    private void click(Row r) {
        if (r.click != null) r.click.run();
    }
}
