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
    /** Opens a qvk3 route on start, e.g. from a portal notification: "myTickets", "ticket:BUG-0021". */
    static final String EXTRA_ROUTE = "qvk3_route";
    private Qvk3Ui.Palette p;
    private Qvk3SettingsIndex index;
    private LinearLayout content;
    private ScrollView scroll;
    private EditText search;
    private TextView title, subtitle;
    private View back;
    /** Animation for the next screen built: 1 forward (slide from the right), -1 back, 2 light (search), 0 none (refresh). */
    private int pendingAnim = 2;
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
        Qvk3Ui.Palette themed = null;
        try {
            // same colours as the older screens (MenuUi): VK's current theme, so styles and themes apply everywhere
            MenuUi mu = new MenuUi(this);
            themed = new Qvk3Ui.Palette(mu.dark, mu.background, mu.surface, mu.soft, mu.text, mu.secondary, mu.accent, Color.WHITE, mu.line);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.settings.palette", t);
        }
        p = themed != null ? themed : dark ? new Qvk3Ui.Palette(true, 0xFF0E0F12, 0xFF1C1D21, 0xFF2A2B30, 0xFFF2F3F5, 0xFF8E9199, accent, Color.WHITE, 0xFF2C2D32)
                : new Qvk3Ui.Palette(false, 0xFFF2F3F7, Color.WHITE, 0xFFE9EAEE, 0xFF0B0B0D, 0xFF7C7F87, accent, Color.WHITE, 0xFFE4E5EA);
        Qvk3Ui.bars(this, p);
        index = buildIndex(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        // BUG-0029: no fitsSystemWindows — on HyperOS the keyboard inset could stay on the whole screen after the keyboard
        // closed, leaving an empty screen under the last item. Status/navigation bars pad the root; the keyboard only the list.
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int top = in.getSystemWindowInsetTop(), bottomBars = in.getStableInsetBottom();
            int ime = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = in.getInsets(android.view.WindowInsets.Type.systemBars());
                top = bars.top;
                bottomBars = bars.bottom;
                ime = in.isVisible(android.view.WindowInsets.Type.ime()) ? Math.max(0, in.getInsets(android.view.WindowInsets.Type.ime()).bottom - bars.bottom) : 0;
            } else {
                ime = Math.max(0, in.getSystemWindowInsetBottom() - bottomBars);
            }
            v.setPadding(in.getSystemWindowInsetLeft(), top, in.getSystemWindowInsetRight(), bottomBars);
            if (scroll != null) scroll.setPadding(0, 0, 0, ime);
            return in;
        });
        root.setFocusableInTouchMode(true); // nothing (the search box) takes focus — and the keyboard — on opening
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(6), dp(8), 0);
        back = Qvk3Ui.icon(this, Qvk3Icons.BACK, p.text, 48, 24, "Назад", v -> handleBack());
        head.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(8), 0, 0, 0);
        title = Qvk3Ui.line(this, "Настройки", 24, p.text, true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        // large title that shrinks as the list scrolls (iOS-like), subtitle fades out, header gets a hairline
        View hairline = new View(this);
        hairline.setBackgroundColor(p.divider);
        hairline.setAlpha(0f);
        root.addView(hairline, root.indexOfChild(scroll), new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.6f))));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            float f = Math.max(0f, Math.min(1f, y / (float) dp(70)));
            subtitle.setAlpha(1f - f);
            hairline.setAlpha(f);
        });
        setContentView(root);
        root.requestFocus();

        if (state != null) section = state.getInt("section", -1);
        if (state != null && !state.getString("query", "").isEmpty()) search.setText(state.getString("query"));
        else if (section >= 0) showSection(section);
        else showHome();
        String route = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_ROUTE);
        if (route != null && state == null) content.post(() -> {
            try {
                qvk3(route);
            } catch (Exception e) {
                Qvk3Ui.toast(this, Qvk3Jobs.message(e));
            }
        });
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback cb = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String route = intent == null ? null : intent.getStringExtra(EXTRA_ROUTE);
        if (route != null) content.post(() -> {
            try {
                qvk3(route);
            } catch (Exception e) {
                Qvk3Ui.toast(this, Qvk3Jobs.message(e));
            }
        });
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
            pendingAnim = -1;
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
        idx.addScreen("qvk3:quick", "Быстрые настройки", "Главное в одном окне: код настроек, тема, реклама, QVK Music, оболочка, стикеры", 0, "QVK 3.0");
        idx.addScreen("qvk3:settingsCode", "Код настроек", "Все переключатели QVK одной короткой строкой: скопировать, поделиться, вставить", 7, "Резервные копии");
        idx.addScreen("qvk3:resetLook", "Сбросить тему", "Вернуть тему к предустановленной; данные не затрагиваются", 0, "Темы и цвета");
        idx.addScreen("qvk3:searchTabs", "Разделы поиска ВК", "Скрыть ненужные вкладки поиска: игры, клипы, товары, сервисы", 1, "Интерфейс ВК");
        idx.addSwitch(Qvk3ImEntry.PREF, "Мессенджер QVK (бета)", "Свой список чатов и экран чата QVK вместо штатных: ответы, пересылка, реакции, правка, голосовые, фото и файлы, черновики, свайп для ответа. Звонки и настройки бесед — в ВК", 2, "Мессенджер QVK", false);
        idx.addSwitch(Qvk3SendRetry.PREF, "Повторять неотправленное", "Если фото, видео, файл или сообщение не ушли — QVK сам повторит отправку (до 2 раз, когда есть сеть)", 2, "Чаты и диалоги", true);
        idx.addScreen("qvk3:music", "Открыть QVK Music", "Собственный музыкальный раздел и плеер", 4, "QVK Music");
        idx.addSwitch(Qvk3MusicService.KEEP_AFTER_CLOSE, "Музыка играет после закрытия приложения", "Если выключено — смахивание QVK из недавних останавливает музыку и завершает приложение", 4, "QVK Music", false);
        idx.addSwitch(Qvk3CatalogAds.PREF, "Скрывать предложения подписки", "Баннеры «Целый месяц — 0 ₽ … Подключить» в музыке ВК", 4, "QVK Music", true);
        idx.addSwitch(Qvk3FeedNow.PREF, "Кнопка музыки в шапках ВК", "Кольцо с паузой в шапке «Главной», «Мессенджера», «Сообществ», «Поиска», «Сервисов», пока играет QVK Music; удержание — плеер", 4, "QVK Music", true);
        idx.addScreen("qvk3:musicSettings", "Настройки QVK Music", "Воспроизведение, звук, плеер, офлайн, тексты", 4, "QVK Music");
        idx.addScreen("qvk3:push", "Диагностика Push", "Google/microG, транспорт, фоновые ограничения", 6, "Доставка уведомлений");
        idx.addScreen("qvk3:storage", "Место на устройстве", "Музыка, обложки, обновления", 7, "Загрузки и место");
        idx.addScreen("qvk3:perf", "Облегчённый режим", "Без размытия и прозрачности — для слабых устройств", 7, "Производительность и батарея");
        idx.addScreen("qvk3:battery", "Фоновая работа и батарея", "Оптимизация батареи и ограничения Android", 7, "Производительность и батарея");
        idx.addScreen("qvk3:weather", "Погода на экране запуска", "Температура под логотипом QVK; дождь и снег — подписью", 0, "Анимации и запуск");
        idx.addScreen("qvk3:dayNight", "Светлая и тёмная тема", "Режим (как в системе, по времени) и цвета каждой темы", 0, "Темы и цвета");
        idx.addScreen("qvk3:report", "Сообщить о проблеме", "Баг или идея на портал бета-теста — с журналом, снимком экрана и версией", 7, "Портал бета-теста");
        idx.addScreen("qvk3:myTickets", "Мои обращения", "Статусы, ответы команды, проверка исправлений", 7, "Портал бета-теста");
        idx.addScreen("qvk3:ideas", "Идеи тестировщиков", "Голосуйте за идеи других: «Хочу это» поднимает идею выше", 7, "Портал бета-теста");
        idx.addSwitch(Qvk3PortalWatch.SHAKE, "Встряхнуть — сообщить о проблеме", "На экранах ВК: встряхните телефон, чтобы отправить баг со снимком экрана", 7, "Портал бета-теста", true);
        idx.addScreen("qvk3:logs", "Логи приложения", "Скопировать или сохранить журнал без logcat", 7, "Диагностика");
        idx.addScreen("qvk3:faults", "Журнал ошибок", "Сбои модулей с указанием компонента", 7, "Диагностика");
        idx.addSwitch(Qvk3UpdatePrompt.PREF, "Предлагать обновления", "Раз в 6 часов при открытии ВК: «Доступно обновление — обновиться?»", 8, "Обновления", true);
        idx.addScreen("qvk3:channel", "Канал обновлений", "Альфа — все сборки, стабильная — только проверенные", 8, "Обновления");
        idx.addScreen("qvk3:updates", "Проверить обновления", "Загрузка с докачкой, проверка подписи", 8, "Обновления");
        idx.addScreen("qvk3:updateSources", "Серверы обновлений", "Свои адреса, зеркала и ключи подписи", 8, "Обновления");
        idx.addScreen("qvk3:about", "О QVK 3.0", "Версия, база VK 8.100, миграция", 8, "О QVK");
        idx.addScreen("qvk3:shell", "Открыть оболочку QVK", "Чаты и музыка в стиле iOS", 1, "Оболочка QVK");
        idx.addScreen("qvk3:shellToggle", "Запускать оболочку QVK при старте", "Экспериментально: вместо главного экрана ВКонтакте. По умолчанию выключено", 1, "Оболочка QVK");
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
        // a plain View added with wrap_content measures to the scroll viewport's height on real devices (BUG-0029/0031)
        if (trailing.getLayoutParams() == null && trailing.getClass() == View.class) trailing.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
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

    private Qvk3Toggle toggle(boolean checked) {
        return new Qvk3Toggle(this, checked, 0xFF34C759, p.dark ? 0xFF3A3B40 : 0xFFDDDEE3);
    }

    private void itemRow(LinearLayout card, Qvk3SettingsIndex.Item it, boolean showSection) {
        boolean fav = favorites().contains(it.id);
        String sum = it.summary;
        if (showSection) sum = Qvk3SettingsIndex.SECTIONS[it.section] + " › " + it.group + (sum.isEmpty() ? "" : " · " + sum);
        String t = it.title;
        if (it.type == Qvk3SettingsIndex.SWITCH) {
            boolean on = value(it);
            Qvk3Toggle tg = toggle(on);
            LinearLayout[] holder = new LinearLayout[1];
            holder[0] = row(card, null, 0, t, sum, tg, () -> flip(it, tg, holder[0]), () -> toggleFavorite(it));
            LinearLayout r = holder[0];
            r.setContentDescription((fav ? "В избранном. " : "") + it.title + ". " + (on ? "Включено" : "Выключено") + ". " + it.summary);
            if (fav) markFavorite(r);
        } else {
            LinearLayout r = row(card, null, 0, t, sum, null, () -> open(it), () -> toggleFavorite(it));
            if (fav) {
                markFavorite(r);
                r.setContentDescription("В избранном. " + r.getContentDescription());
            }
            if (Qvk3NewBadge.isNew(this, it.target)) {
                newPill(r);
                r.setContentDescription("Новое. " + r.getContentDescription());
            }
        }
    }

    /** «Новое» pill after the title (the item came in the last few builds and was not opened yet). */
    private void newPill(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (!(c instanceof LinearLayout) || !(((LinearLayout) c).getChildAt(0) instanceof TextView)) continue;
            LinearLayout texts = (LinearLayout) c;
            TextView title = (TextView) texts.getChildAt(0);
            texts.removeViewAt(0);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(title, new LinearLayout.LayoutParams(-2, -2, 0f));
            TextView pill = Qvk3Ui.text(this, "Новое", 11, Color.WHITE, true);
            pill.setPadding(dp(7), dp(1), dp(7), dp(2));
            pill.setBackground(Qvk3Ui.rounded(p.accent, dp(8)));
            LinearLayout.LayoutParams pl = Qvk3Ui.wrap();
            pl.leftMargin = dp(8);
            line.addView(pill, pl);
            texts.addView(line, 0);
            return;
        }
    }

    private boolean sectionHasNew(int s) {
        for (Qvk3SettingsIndex.Item it : index.section(s)) if (Qvk3NewBadge.isNew(this, it.target)) return true;
        return false;
    }

    /** Small accent star after the title of a favourite (instead of a «★» glyph in the text). */
    private void markFavorite(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof LinearLayout && ((LinearLayout) c).getChildAt(0) instanceof TextView) {
                TextView t = (TextView) ((LinearLayout) c).getChildAt(0);
                android.graphics.drawable.Drawable star = Qvk3Icons.of(Qvk3Icons.STAR, p.accent);
                star.setBounds(0, 0, dp(14), dp(14));
                t.setCompoundDrawablesRelative(null, null, star, null);
                t.setCompoundDrawablePadding(dp(6));
                return;
            }
        }
    }

    /** BUG-0029/0031 diagnostics: a card taller than the screen is written to the log with the sizes of its rows. */
    private void reportTallCards() {
        int vh = scroll.getHeight();
        if (vh <= 0) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            View v = content.getChildAt(i);
            if (v.getHeight() <= vh * 0.9f) continue;
            StringBuilder b = new StringBuilder("tall card #" + i + " of " + content.getChildCount() + " in «" + title.getText() + "»: h=" + v.getHeight() + " viewport=" + vh);
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int k = 0; k < g.getChildCount() && k < 40; k++) {
                    View c = g.getChildAt(k);
                    b.append(" | ").append(c.getClass().getSimpleName()).append(' ').append(c.getHeight());
                    if (c instanceof android.view.ViewGroup) for (int j = 0; j < ((android.view.ViewGroup) c).getChildCount(); j++) {
                        View cc = ((android.view.ViewGroup) c).getChildAt(j);
                        b.append(j == 0 ? " [" : ",").append(cc.getClass().getSimpleName()).append(' ').append(cc.getWidth()).append('x').append(cc.getHeight());
                        if (j == ((android.view.ViewGroup) c).getChildCount() - 1) b.append(']');
                    }
                }
            }
            android.util.Log.w("QVK3Settings", b.toString());
        }
    }

    private void begin(String heading, String sub, boolean home) {
        content.removeAllViews();
        boolean changed = !heading.contentEquals(title.getText());
        title.setText(heading);
        // a compact, readable header: 24sp on the home screen, 20sp inside — never a giant truncated title
        title.setTextSize(home ? 24 : 20);
        subtitle.setText(sub);
        back.setVisibility(home ? View.GONE : View.VISIBLE);
        scroll.scrollTo(0, 0);
        int anim = pendingAnim;
        pendingAnim = 0;
        if (anim == 0) return;
        if (changed && anim != 2) {
            title.setAlpha(0f);
            title.animate().alpha(1f).setDuration(220).start();
        }
        content.post(() -> {
            if (anim == 1 || anim == -1) Qvk3Motion.slideIn(content, anim == 1);
            Qvk3Motion.stagger(content, anim == 2 && !home);
        });
        content.postDelayed(() -> {
            content.setAlpha(1f);
            content.setTranslationX(0f);
            reportTallCards();
            for (int i = 0; i < content.getChildCount(); i++) {
                View v = content.getChildAt(i);
                if (v.getAlpha() < 1f) {
                    v.animate().cancel();
                    v.setAlpha(1f);
                    v.setTranslationY(0f);
                }
            }
        }, 1500);
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
        pendingAnim = 2;
        begin("Поиск", found.isEmpty() ? "Ничего не найдено" : "Найдено: " + found.size(), false);
        if (found.isEmpty()) return;
        LinearLayout c = card();
        for (Qvk3SettingsIndex.Item it : found) itemRow(c, it, true);
    };

    private void showHome() {
        section = -1;
        if (search.length() > 0) search.setText("");
        begin("Настройки", version(), true);

        // appearance hero: a gradient in the colours of the current style, its swatches and the three main actions
        Qvk3DesignTokens t = Qvk3Appearance.current(this);
        LinearLayout look = new LinearLayout(this);
        look.setOrientation(LinearLayout.VERTICAL);
        look.setPadding(dp(18), dp(18), dp(18), dp(16));
        int a1 = t.accent, a2 = Qvk3Ui.deep(t.accent, 0.55f);
        android.graphics.drawable.GradientDrawable hero = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{a1, a2});
        hero.setCornerRadius(dp(24));
        look.setBackground(hero);
        look.setElevation(dp(6));
        content.addView(look, Qvk3Ui.match());
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout sw = new LinearLayout(this);
        for (int col : new int[]{Qvk3DesignTokens.over(t.page, 0xFF000000), Qvk3DesignTokens.over(t.content, 0xFF000000), Qvk3DesignTokens.over(t.outgoing, 0xFF000000), t.accent}) {
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(col);
            g.setStroke(dp(2), 0xCCFFFFFF);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.leftMargin = sw.getChildCount() == 0 ? 0 : -dp(9);
            sw.addView(dot, lp);
        }
        top.addView(sw);
        LinearLayout lt = new LinearLayout(this);
        lt.setOrientation(LinearLayout.VERTICAL);
        lt.setPadding(dp(14), 0, 0, 0);
        lt.addView(Qvk3Ui.text(this, "Оформление", 13, 0xCCFFFFFF, false));
        lt.addView(Qvk3Ui.line(this, Qvk3Appearance.currentTitle(this), 20, Color.WHITE, true));
        top.addView(lt, Qvk3Ui.weight(1));
        look.addView(top);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(16), 0, 0);
        // BUG-0064: surface «Быстрые настройки» at the top, next to the theme shortcut.
        chips.addView(heroChip("Быстрые настройки", true, this::quick), Qvk3Ui.weight(1));
        chips.addView(gap());
        chips.addView(heroChip("Светлая и тёмная тема", false, () -> Qvk3InfoScreens.dayNight(this)), Qvk3Ui.weight(1));
        look.addView(chips);
        Qvk3Motion.press(look);
        look.setOnClickListener(v -> Qvk3InfoScreens.dayNight(this));

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
            for (int i = 0; i < Math.min(2, hist.size()); i++) {
                Qvk3SettingsHistory.Entry e = hist.get(i);
                Qvk3SettingsIndex.Item it = index.get(e.key);
                TextView when = Qvk3Ui.text(this, df.format(new java.util.Date(e.time)), 12, p.text2, false);
                LinearLayout hrow = row(c, null, 0, e.title, null, when, it == null ? null : () -> openSection(it.section), null);
                hrow.setMinimumHeight(dp(40));   // BUG-0004: the "recent changes" card was too tall for what it shows
                hrow.setPadding(dp(16), dp(5), dp(12), dp(5));
            }

        }

        // sections as a grid of tiles
        header("Разделы");
        int[] order = {0, 1, 2, 4, 5, 6, 3, 7, 8};
        for (int k = 0; k < order.length; k += 2) {
            LinearLayout line = new LinearLayout(this);
            LinearLayout.LayoutParams ll = Qvk3Ui.match();
            if (k > 0) ll.topMargin = dp(10);
            line.addView(sectionTile(order[k]), Qvk3Ui.weight(1));
            line.addView(gap());
            if (k + 1 < order.length) line.addView(sectionTile(order[k + 1]), Qvk3Ui.weight(1));
            else line.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f)); // a bare View with wrap_content takes the whole viewport height on devices (BUG-0031)
            content.addView(line, ll);
        }
        TextView hint = Qvk3Ui.text(this, "Удерживайте настройку, чтобы добавить её в избранное.", 13, p.text2, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(16), dp(18), dp(16), 0);
        content.addView(hint);
    }

    static String plural(int n, String one, String few, String many) {
        int m10 = n % 10, m100 = n % 100;
        String w = m10 == 1 && m100 != 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? few : many;
        return n + " " + w;
    }

    /** Section colours tuned for dark backgrounds (the dark grey of «Приватность» becomes readable). */
    static int brighten(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] = Math.max(hsv[2], 0.85f);
        if (hsv[1] < 0.15f) hsv[2] = 0.75f;
        return Color.HSVToColor(hsv);
    }

    private void openSection(int s) {
        pendingAnim = 1;
        showSection(s);
    }

    private TextView heroChip(String label, boolean primary, Runnable r) {
        TextView b = Qvk3Ui.text(this, label, 14, primary ? Qvk3Ui.deep(Qvk3Appearance.current(this).accent, 0.45f) : Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        b.setMinHeight(dp(44));
        b.setBackground(Qvk3Ui.ripple(this, primary ? Color.WHITE : 0x33FFFFFF, 0x33000000, dp(14)));
        b.setOnClickListener(v -> r.run());
        Qvk3Motion.press(b);
        return b;
    }

    /** A section as a tile: coloured icon, name, two-line hint; sinks when pressed. */
    private View sectionTile(int s) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(14), dp(12), dp(14));
        t.setBackground(Qvk3Ui.ripple(this, p.surface, p.dark ? 0x22FFFFFF : 0x14000000, dp(20)));
        t.setMinimumHeight(dp(124));
        View icon = Qvk3Ui.tile(this, Qvk3SettingsIndex.SECTION_ICONS[s], Qvk3SettingsIndex.SECTION_COLORS[s], 40);
        t.addView(icon);
        TextView name = Qvk3Ui.text(this, Qvk3SettingsIndex.SECTIONS[s], 16, p.text, true);
        name.setPadding(0, dp(12), 0, dp(2));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(name);
        if (sectionHasNew(s)) {
            // a small accent dot in the tile's corner: something new inside this section
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(p.accent);
            dot.setSize(dp(8), dp(8));
            name.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, dot, null);
            name.setCompoundDrawablePadding(dp(6));
            t.setContentDescription("Есть новое. ");
        }
        TextView hint = Qvk3Ui.text(this, Qvk3SettingsIndex.SECTION_HINTS[s], 12, p.text2, false);
        hint.setMaxLines(2);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(hint);
        t.setContentDescription((t.getContentDescription() == null ? "" : t.getContentDescription()) + Qvk3SettingsIndex.SECTIONS[s] + ". " + Qvk3SettingsIndex.SECTION_HINTS[s]);
        t.setOnClickListener(v -> openSection(s));
        Qvk3Motion.press(t);
        return t;
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
        // section hero: its icon on a soft wash of the section colour and what is inside
        int col = Qvk3SettingsIndex.SECTION_COLORS[s];
        int nScreens = 0, nToggles = 0;
        for (Qvk3SettingsIndex.Item it : index.section(s)) {
            if (it.type == Qvk3SettingsIndex.SWITCH) nToggles++;
            else nScreens++;
        }
        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(12), dp(10), dp(14), dp(10));
        android.graphics.drawable.GradientDrawable wash = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{(col & 0x00FFFFFF) | 0x40000000, (col & 0x00FFFFFF) | 0x0A000000});
        wash.setCornerRadius(dp(16));
        hero.setBackground(wash);
        hero.addView(Qvk3Ui.tile(this, Qvk3SettingsIndex.SECTION_ICONS[s], col, 32));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(12), 0, 0, 0);
        ht.addView(Qvk3Ui.line(this, plural(nScreens, "экран", "экрана", "экранов") + " · " + plural(nToggles, "переключатель", "переключателя", "переключателей"), 13, p.text2, false));
        hero.addView(ht, Qvk3Ui.weight(1));
        LinearLayout.LayoutParams hl = Qvk3Ui.match();
        hl.topMargin = dp(6);
        content.addView(hero, hl);
        Qvk3SettingsIndex.Item first = null;
        for (String g : index.groups(s)) {
            header(g);
            ((TextView) content.getChildAt(content.getChildCount() - 1)).setTextColor(Qvk3Ui.night(this) ? brighten(col) : col);
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
            // BUG-0029/0031: a quiet text action instead of a whole card — on some phones that last card grew into a screen of white
            TextView reset = Qvk3Ui.text(this, "Сбросить переключатели раздела", 14, p.text2, false);
            reset.setGravity(Gravity.CENTER);
            reset.setPadding(dp(16), dp(14), dp(16), dp(14));
            reset.setMinHeight(dp(48));
            reset.setBackground(Qvk3Ui.ripple(this, 0, p.dark ? 0x22FFFFFF : 0x14000000, dp(14)));
            reset.setContentDescription("Сбросить переключатели раздела. Вернуть значения по умолчанию; PIN не сбрасывается");
            reset.setOnClickListener(v -> resetSection(s));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
            rl.gravity = Gravity.CENTER_HORIZONTAL;
            rl.topMargin = dp(16);
            content.addView(reset, rl);
        }
    }

    // ------------------------------------------------------------------ actions

    private void flip(Qvk3SettingsIndex.Item it) {
        flip(it, null, null);
    }

    /** Toggles in place: the switch animates, the screen is not rebuilt (no jump, no flicker). */
    private void flip(Qvk3SettingsIndex.Item it, Qvk3Toggle tg, View row) {
        boolean now = !value(it);
        if (it.key.equals("enablePin") && now && !Qvk3MusicSettings.prefs(this).contains("qmods_pin_hash")) {
            SettingsEditorActivity.open(this, "pin");
            return;
        }
        Qvk3SettingsHistory.before(this, it.key, (now ? "Включено: " : "Выключено: ") + it.title);
        if (it.key.startsWith("qvk3_")) Qvk3MusicSettings.prefs(this).edit().putBoolean(it.key, now).apply();
        else try {
            SettingsRuntime.toggle(it.key, now);
        } catch (Throwable e) {
            Qvk3MusicSettings.prefs(this).edit().putBoolean(it.key, now).apply();
        }
        if (tg != null) {
            boolean v = value(it);
            tg.setChecked(v, true);
            tg.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (row != null) row.setContentDescription(it.title + ". " + (v ? "Включено" : "Выключено") + ". " + it.summary);
            if (v != now) Qvk3Ui.toast(this, "Не удалось переключить «" + it.title + "»");
            return;
        }
        int y = scroll.getScrollY();
        refresh();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void open(Qvk3SettingsIndex.Item it) {
        String t = it.target;
        Qvk3NewBadge.markSeen(this, t);
        try {
            if (t.startsWith("qvk3:")) qvk3(t.substring(5));
            else if (Qvk3NativeEditors.handles(t)) Qvk3NativeEditors.open(this, t);
            else if (it.type == Qvk3SettingsIndex.SCREEN) SettingsHub.open(this, t);
            else Qvk3SettingsBridge.runClassicAction(this, t);
        } catch (Throwable e) {
            RuntimeFaults.record("qvk3.settings.open", e);
            Qvk3Ui.toast(this, "Не удалось открыть: " + Qvk3Jobs.message(e));
        }
    }

    private void qvk3(String what) throws Exception {
        if (what.startsWith("ticket:")) {
            Qvk3MyTickets.card(this, what.substring(7));
            return;
        }
        if (what.startsWith("section:")) {
            openSection(Integer.parseInt(what.substring(8)));
            return;
        }
        switch (what) {
            case "report":
                Activity vk = Qvk3Skin.lastVkActivity();
                Qvk3Report.open(this, vk == null || vk.isDestroyed() ? null : Qvk3Report.capture(vk), null);
                break;
            case "ideas":
                Qvk3MyTickets.ideas(this);
                break;
            case "quick":
                quick();
                break;
            case "settingsCode":
                settingsCode();
                break;
            case "channel":
                Qvk3UpdatePrompt.channel(this);
                break;
            case "myTickets":
                Qvk3MyTickets.show(this);
                break;
            case "resetLook":
                Qvk3Ui.dialog(this).setTitle("Сбросить тему?")
                        .setMessage("Тема вернётся к предустановленной. Остальные настройки и данные не изменятся.")
                        .setPositiveButton("Сбросить", (d, w) -> Qvk3Appearance.reset(this)).setNegativeButton("Отмена", null).show();
                break;
            case "weather":
                Qvk3SplashWeather.settings(this);
                break;
            case "searchTabs":
                Qvk3SearchTabs.settings(this);
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
            case "logs":
            case "dayNight":
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
                boolean on = !pr.getBoolean(Qvk3ShellActivity.ENABLED, false);
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

    static final String CODE_BACKUP = "qvk3_settings_code_backup";

    private Boolean switchValue(String key) {
        Qvk3SettingsIndex.Item it = index.get(key);
        if (it == null || it.type != Qvk3SettingsIndex.SWITCH || "enablePin".equals(key)) return null;
        return value(it);
    }

    private void setSwitch(String key, boolean v) {
        if (key.startsWith("qvk3_")) Qvk3MusicSettings.prefs(this).edit().putBoolean(key, v).apply();
        else try {
            SettingsRuntime.toggle(key, v);
        } catch (Throwable e) {
            Qvk3MusicSettings.prefs(this).edit().putBoolean(key, v).apply();
        }
    }

    /** IDEA-0017: every switch in one short code; paste applies it after showing how many settings change. */
    // ------------------------------------------------------------------ IDEA-0023: «Быстрые настройки»

    private TextView quickTile(String title, String sub, int color, Runnable click) {
        // BUG-0064 (2.1): the pill around the text was too chunky — tighter padding and radius, smaller text.
        TextView t = Qvk3Ui.text(this, title + (sub == null ? "" : "\n" + sub), 14, Color.WHITE, true);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(dp(8), dp(9), dp(8), dp(9));
        t.setBackground(Qvk3Ui.ripple(this, color, 0x33FFFFFF, dp(13)));
        t.setOnClickListener(v -> click.run());
        return t;
    }

    private LinearLayout quickPair(View l, View r) {
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, 1f), b = new LinearLayout.LayoutParams(0, -1, 1f);
        a.rightMargin = dp(5);
        b.leftMargin = dp(5);
        row.addView(l, a);
        row.addView(r, b);
        row.setPadding(0, 0, 0, dp(10));
        return row;
    }

    private void go(android.app.Dialog d, String route) {
        d.dismiss();
        try {
            qvk3(route);
        } catch (Throwable e) {
            Qvk3Ui.toast(this, "Не удалось открыть: " + Qvk3Jobs.message(e));
        }
    }

    /** Switches of the catalogue that hide ads (title says so); «on» = ads hidden. */
    private java.util.List<Qvk3SettingsIndex.Item> adSwitches() {
        java.util.List<Qvk3SettingsIndex.Item> out = new java.util.ArrayList<>();
        for (Qvk3SettingsIndex.Item it : index.all()) {
            if (it.type != Qvk3SettingsIndex.SWITCH || it.key == null) continue;
            String t = Qvk3SettingsIndex.normal(it.title);
            boolean ads = t.contains("реклам") || t.contains("промо") || t.contains("баннер") || t.contains("предложения подписки");
            boolean hides = t.startsWith("скры") || t.startsWith("убра") || t.startsWith("отключ") || t.startsWith("блок") || t.startsWith("без ");
            if (ads && hides) out.add(it);
        }
        return out;
    }

    private void quick() {
        android.app.Dialog[] dlg = new android.app.Dialog[1];
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(4), dp(20), dp(8));
        int acc = p.accent, acc2 = 0xFF000000 | ((((p.accent >> 16) & 255) * 3 / 4) << 16) | ((((p.accent >> 8) & 255) * 3 / 4) << 8) | ((p.accent & 255) * 3 / 4);
        col.addView(Qvk3Sheet.section(this, p, "Код настроек"));
        col.addView(quickPair(
                quickTile("Скопировать", "все переключатели", acc, () -> {
                    String code = Qvk3SettingsCode.encode(this::switchValue);
                    getSystemService(android.content.ClipboardManager.class).setPrimaryClip(android.content.ClipData.newPlainText("QVK", code));
                    Qvk3Ui.toast(this, "Скопировано в буфер обмена");
                }),
                quickTile("Вставить", "код с другого телефона", acc2, () -> {
                    dlg[0].dismiss();
                    pasteSettingsCode();
                })));
        col.addView(Qvk3Sheet.section(this, p, "Оформление"));
        col.addView(quickTile("Светлая и тёмная тема", "режим и цвета", acc, () -> go(dlg[0], "dayNight")));
        LinearLayout g = Qvk3Sheet.group(this, p);
        java.util.List<Qvk3SettingsIndex.Item> ads = adSwitches();
        boolean allOff = !ads.isEmpty();
        for (Qvk3SettingsIndex.Item it : ads) allOff &= value(it);
        g.addView(Qvk3Sheet.switchRow(this, p, "Скрыть всю рекламу", ads.size() + " переключателей рекламы QVK (клипы в ленте не трогаются)", allOff, on -> {
            for (Qvk3SettingsIndex.Item it : ads) if (value(it) != on) flip(it);
            Qvk3Ui.toast(this, on ? "Реклама скрыта" : "Переключатели рекламы выключены");
        }));
        g.addView(Qvk3Sheet.divider(this, p));
        g.addView(Qvk3Sheet.switchRow(this, p, "QVK Music", "Свой плеер и интерфейс музыки вместо раздела ВК", Qvk3MusicSettings.enabled(this), on -> {
            Qvk3SettingsHistory.before(this, Qvk3MusicSettings.ENABLED, on ? "QVK Music включён" : "QVK Music выключен");
            Qvk3MusicSettings.prefs(this).edit().putBoolean(Qvk3MusicSettings.ENABLED, on).apply();
        }));
        g.addView(Qvk3Sheet.divider(this, p));
        g.addView(Qvk3Sheet.switchRow(this, p, "Оболочка QVK при запуске", "Свой экран «Чаты / Музыка / Ещё» вместо ВК", Qvk3Shell.enabledByUser(this), on -> {
            Qvk3SettingsHistory.before(this, Qvk3ShellActivity.ENABLED, on ? "Оболочка QVK при старте включена" : "Оболочка QVK при старте выключена");
            Qvk3MusicSettings.prefs(this).edit().putBoolean(Qvk3ShellActivity.ENABLED, on).apply();
        }));
        col.addView(g);
        LinearLayout nav = Qvk3Sheet.group(this, p);
        // BUG-0064 (2.2): «Уведомления QVK» should open the delivery-method chooser, not the diagnostics screen.
        // (2.3): opening it as its own dialog over the quick sheet means closing it returns here, not to search.
        nav.addView(Qvk3Sheet.navRow(this, p, "Доставка уведомлений", v -> {
            try {
                Qvk3NativeEditors.push(this);
            } catch (Throwable e) {
                Qvk3Ui.toast(this, "Не удалось открыть: " + Qvk3Jobs.message(e));
            }
        }));
        nav.addView(Qvk3Sheet.divider(this, p));
        nav.addView(Qvk3Sheet.navRow(this, p, "Стикеры из Telegram", v -> {
            dlg[0].dismiss();
            try {
                startActivity(new Intent(this, TelegramStickersActivity.class));
            } catch (Throwable e) {
                Qvk3Ui.toast(this, "Не удалось открыть: " + Qvk3Jobs.message(e));
            }
        }));
        LinearLayout.LayoutParams nl = Qvk3Ui.match();
        nl.topMargin = dp(10);
        col.addView(nav, nl);
        dlg[0] = Qvk3Ui.dialog(this).setTitle("Быстрые настройки").setView(col).setPositiveButton("Готово", (d, w) -> refresh()).show();
    }

    private void settingsCode() {
        String code = Qvk3SettingsCode.encode(this::switchValue);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(6), dp(22), dp(6));
        TextView c = Qvk3Ui.text(this, code, 20, p.text, true);
        c.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        c.setTextIsSelectable(true);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(Qvk3Ui.rounded(p.dark ? 0x1FFFFFFF : 0x0F000000, dp(14)));
        col.addView(c, Qvk3Ui.match());
        TextView n = Qvk3Ui.text(this, "Код хранит все переключатели QVK (без PIN-кода). Подходит и для будущих версий: новые настройки он не трогает. Вставьте чужой код, чтобы получить такие же настройки.", 13, p.text2, false);
        n.setPadding(0, dp(10), 0, 0);
        col.addView(n);
        android.app.AlertDialog.Builder b = Qvk3Ui.dialog(this).setTitle("Код настроек").setView(col)
                .setPositiveButton("Скопировать", (d, w) -> {
                    android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("QVK", code));
                    Qvk3Ui.toast(this, "Код скопирован");
                })
                .setNegativeButton("Вставить код", (d, w) -> pasteSettingsCode());
        if (Qvk3MusicSettings.prefs(this).contains(CODE_BACKUP)) b.setNeutralButton("Вернуть как было", (d, w) -> restoreSettingsCode());
        b.show();
    }

    private void pasteSettingsCode() {
        EditText e = new EditText(this);
        e.setHint("QVK1-…");
        android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
            if (clip != null && clip.toString().trim().toUpperCase(java.util.Locale.ROOT).startsWith(Qvk3SettingsCode.PREFIX)) e.setText(clip.toString().trim());
        }
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(24), dp(8), dp(24), 0);
        f.addView(e);
        Qvk3Ui.dialog(this).setTitle("Вставить код настроек").setView(f).setPositiveButton("Дальше", (d, w) -> {
            java.util.Map<String, Boolean> want;
            try {
                want = Qvk3SettingsCode.decode(e.getText().toString());
            } catch (IllegalArgumentException ex) {
                Qvk3Ui.toast(this, ex.getMessage());
                return;
            }
            java.util.Map<String, Boolean> change = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<String, Boolean> en : want.entrySet()) {
                Boolean cur = switchValue(en.getKey());
                if (cur != null && !cur.equals(en.getValue())) change.put(en.getKey(), en.getValue());
            }
            if (change.isEmpty()) {
                Qvk3Ui.toast(this, "Все настройки уже такие же");
                return;
            }
            StringBuilder list = new StringBuilder();
            int shown = 0;
            for (java.util.Map.Entry<String, Boolean> en : change.entrySet()) {
                if (shown++ >= 12) {
                    list.append("…\n");
                    break;
                }
                Qvk3SettingsIndex.Item it = index.get(en.getKey());
                list.append(en.getValue() ? "● " : "○ ").append(it == null ? en.getKey() : it.title).append('\n');
            }
            Qvk3Ui.dialog(this).setTitle("Изменится настроек: " + change.size()).setMessage(list.toString().trim() + "\n\n● включится, ○ выключится. Вернуть можно в «Код настроек».")
                    .setPositiveButton("Применить", (x, y) -> {
                        try {
                            org.json.JSONObject backup = new org.json.JSONObject();
                            for (String k : change.keySet()) backup.put(k, switchValue(k));
                            Qvk3MusicSettings.prefs(this).edit().putString(CODE_BACKUP, backup.toString()).apply();
                        } catch (Exception ignored) {
                        }
                        for (java.util.Map.Entry<String, Boolean> en : change.entrySet()) setSwitch(en.getKey(), en.getValue());
                        Qvk3SettingsHistory.record(this, "settingsCode", "Применён код настроек: " + change.size());
                        Qvk3Ui.toast(this, "Применено: " + change.size() + ". Часть настроек сработает после перезапуска QVK");
                        refresh();
                    }).setNegativeButton("Отмена", null).show();
        }).setNegativeButton("Отмена", null).show();
    }

    private void restoreSettingsCode() {
        try {
            org.json.JSONObject b = new org.json.JSONObject(Qvk3MusicSettings.prefs(this).getString(CODE_BACKUP, "{}"));
            java.util.Iterator<String> it = b.keys();
            int n = 0;
            while (it.hasNext()) {
                String k = it.next();
                setSwitch(k, b.getBoolean(k));
                n++;
            }
            Qvk3MusicSettings.prefs(this).edit().remove(CODE_BACKUP).apply();
            Qvk3Ui.toast(this, "Возвращено настроек: " + n);
            refresh();
        } catch (Exception e) {
            Qvk3Ui.toast(this, "Не удалось вернуть: " + Qvk3Jobs.message(e));
        }
    }

    private void undo() {
        String t = Qvk3SettingsHistory.undoLast(this);
        Qvk3Ui.toast(this, t == null ? "Нечего отменять" : "Отменено: " + t);
        refresh();
    }
}
