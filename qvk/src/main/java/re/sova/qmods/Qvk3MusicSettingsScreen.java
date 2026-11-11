package re.sova.qmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Settings of QVK Music, grouped as requested: Воспроизведение, Звук, Внешний вид плеера, Библиотека,
 * Плейлисты, Кэш и загрузки, Тексты песен, Фоновая работа, Bluetooth и гарнитуры, CoverFlow, История,
 * Диагностика. Search filters rows by title; every category has its own reset.
 */
public final class Qvk3MusicSettingsScreen {
    static final int REQ_LRC = 42;

    private interface Section {
        void build(Activity a, Qvk3Ui.Palette p, LinearLayout c, Runnable refresh);
    }

    static final String[] TITLES = {"Воспроизведение", "Звук", "Внешний вид плеера", "Библиотека", "Плейлисты",
            "Кэш и загрузки", "Тексты песен", "Фоновая работа", "Bluetooth и гарнитуры", "CoverFlow", "История", "Диагностика"};

    static final String[][] RESET_KEYS = {
            {Qvk3MusicSettings.GAPLESS, Qvk3MusicSettings.CROSSFADE, Qvk3MusicSettings.START_TAB},
            {},
            {Qvk3MusicSettings.LAYOUT, Qvk3MusicSettings.COVER_SIZE, Qvk3MusicSettings.COVER_RADIUS, Qvk3MusicSettings.PROGRESS_STYLE},
            {}, {},
            {Qvk3MusicSettings.CACHE_LIMIT_MB},
            {Qvk3MusicSettings.LYRICS_EXTERNAL, Qvk3MusicSettings.LYRICS_SIZE, Qvk3MusicSettings.LYRICS_OVER_COVER},
            {}, {Qvk3MusicSettings.PAUSE_ON_UNPLUG},
            {Qvk3MusicSettings.COVERFLOW_SPACING},
            {Qvk3MusicSettings.HISTORY},
            {Qvk3MusicSettings.ENABLED}};

    public static void show(Activity a) {
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        EditText filter = new EditText(a);
        filter.setHint("Поиск по настройкам музыки");
        filter.setSingleLine(true);
        col.addView(filter);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        col.addView(body);
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            body.removeAllViews();
            String q = Qvk3Lyrics.normal(filter.getText().toString());
            for (int i = 0; i < TITLES.length; i++) {
                LinearLayout sec = new LinearLayout(a);
                sec.setOrientation(LinearLayout.VERTICAL);
                section(i).build(a, p, sec, render[0]);
                boolean match = q.isEmpty() || Qvk3Lyrics.normal(TITLES[i]).contains(q);
                if (!match) {
                    for (int k = sec.getChildCount() - 1; k >= 0; k--) {
                        View v = sec.getChildAt(k);
                        CharSequence label = v instanceof TextView ? ((TextView) v).getText() : v.getContentDescription();
                        if (label == null || !Qvk3Lyrics.normal(label.toString()).contains(q)) sec.removeViewAt(k);
                    }
                    if (sec.getChildCount() == 0) continue;
                }
                TextView h = Qvk3Ui.text(a, TITLES[i], 17, p.accent, true);
                h.setAccessibilityHeading(true);
                h.setPadding(0, Qvk3Ui.dp(a, 16), 0, Qvk3Ui.dp(a, 4));
                body.addView(h);
                body.addView(sec);
                final int idx = i;
                if (RESET_KEYS[i].length > 0) {
                    TextView reset = Qvk3Ui.text(a, "Сбросить раздел «" + TITLES[i] + "»", 13, p.text2, false);
                    reset.setPadding(0, Qvk3Ui.dp(a, 6), 0, Qvk3Ui.dp(a, 6));
                    reset.setOnClickListener(v -> Qvk3Ui.dialog(a).setTitle("Сбросить «" + TITLES[idx] + "»?")
                            .setPositiveButton("Сбросить", (d, w) -> {
                                Qvk3MusicSettings.reset(a, RESET_KEYS[idx]);
                                render[0].run();
                                refreshPlayer(a);
                            }).setNegativeButton("Отмена", null).show());
                    body.addView(reset);
                }
            }
        };
        filter.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                render[0].run();
            }
        });
        render[0].run();
        ScrollView sv = new ScrollView(a);
        int pad = Qvk3Ui.dp(a, 20);
        col.setPadding(pad, 0, pad, pad);
        sv.addView(col);
        Qvk3Ui.dialog(a).setTitle("Настройки QVK Music").setView(sv).setPositiveButton("Готово", null).show();
    }

    static void refreshPlayer(Activity a) {
        Qvk3MusicScreen sc = Qvk3MusicScreen.of(a);
        if (sc != null) sc.rebuildPlayer();
    }

    static Switch toggle(Activity a, String title, String key, boolean def, Runnable after) {
        Switch s = new Switch(a);
        s.setText(title);
        s.setContentDescription(title);
        s.setMinHeight(Qvk3Ui.dp(a, 48));
        SharedPreferences prefs = Qvk3MusicSettings.prefs(a);
        s.setChecked(prefs.getBoolean(key, def));
        s.setOnCheckedChangeListener((b, v) -> {
            Qvk3SettingsHistory.before(a, key, title);
            prefs.edit().putBoolean(key, v).apply();
            if (after != null) after.run();
        });
        return s;
    }

    static TextView link(Activity a, Qvk3Ui.Palette p, String title, View.OnClickListener l) {
        TextView t = Qvk3Ui.text(a, title, 15, p.accent, false);
        t.setMinHeight(Qvk3Ui.dp(a, 44));
        t.setGravity(android.view.Gravity.CENTER_VERTICAL);
        t.setOnClickListener(l);
        return t;
    }

    static TextView info(Activity a, Qvk3Ui.Palette p, String text) {
        TextView t = Qvk3Ui.text(a, text, 13, p.text2, false);
        t.setPadding(0, Qvk3Ui.dp(a, 2), 0, Qvk3Ui.dp(a, 6));
        return t;
    }

    static void choose(Activity a, String title, String[] labels, int checked, java.util.function.IntConsumer onPick) {
        Qvk3Ui.dialog(a).setTitle(title).setSingleChoiceItems(labels, checked, (d, w) -> {
            onPick.accept(w);
            d.dismiss();
        }).show();
    }

    static void putInt(Activity a, String key, String title, int v) {
        Qvk3SettingsHistory.before(a, key, title);
        Qvk3MusicSettings.prefs(a).edit().putInt(key, v).apply();
    }

    private static Section section(int i) {
        switch (i) {
            case 0:
                return (a, p, c, r) -> {
                    c.addView(toggle(a, "Без пауз между треками (gapless)", Qvk3MusicSettings.GAPLESS, true, null));
                    c.addView(link(a, p, "Кроссфейд: " + Qvk3MusicSettings.crossfadeSeconds(a) + " с", v -> {
                        String[] l = {"Выключен", "2 с", "4 с", "6 с", "8 с", "12 с"};
                        int[] val = {0, 2, 4, 6, 8, 12};
                        choose(a, "Кроссфейд", l, -1, w -> {
                            putInt(a, Qvk3MusicSettings.CROSSFADE, "Кроссфейд", val[w]);
                            r.run();
                        });
                    }));
                    c.addView(link(a, p, "Стартовая вкладка: " + Qvk3MusicActivity.TABS[Math.max(0, Math.min(4, Qvk3MusicSettings.prefs(a).getInt(Qvk3MusicSettings.START_TAB, 0)))], v ->
                            choose(a, "Стартовая вкладка", Qvk3MusicActivity.TABS, -1, w -> {
                                putInt(a, Qvk3MusicSettings.START_TAB, "Стартовая вкладка музыки", w);
                                r.run();
                            })));
                    c.addView(info(a, p, "Скорость, таймер сна и повтор A–B — кнопки под плеером."));
                };
            case 1:
                return (a, p, c, r) -> c.addView(link(a, p, "Эквалайзер, басы, объёмный звук, кроссфейд", v -> Qvk3SoundDialog.show(a)));
            case 2:
                return (a, p, c, r) -> c.addView(link(a, p, "Макет: " + Qvk3MusicSettings.LAYOUT_TITLES[indexOf(Qvk3MusicSettings.layout(a))] + " · размер и форма обложки", v -> playerLook(a, () -> {
                    r.run();
                    refreshPlayer(a);
                })));
            case 3:
                return (a, p, c, r) -> {
                    c.addView(info(a, p, Qvk3MusicLibrary.get(a).stats()));
                    c.addView(link(a, p, "Найти музыку, ранее сохранённую QVK 2.x", v -> Qvk3Jobs.io(() -> {
                        int n = Qvk3MusicDownloads.importExisting(a);
                        Qvk3Ui.toast(a, "Найдено файлов: " + n);
                    })));
                };
            case 4:
                return (a, p, c, r) -> c.addView(info(a, p, "Плейлисты ВК открываются из вкладки «Плейлисты». Очередь можно сохранить как плейлист QVK (кнопка в окне очереди)."));
            case 5:
                return (a, p, c, r) -> {
                    Qvk3Images img = Qvk3Images.get(a);
                    c.addView(info(a, p, "Кэш обложек: " + (img.diskBytes() >> 20) + " МБ из " + Qvk3MusicSettings.cacheLimitMb(a)
                            + " МБ. Загруженные треки хранятся отдельно (" + (Qvk3MusicLibrary.get(a).downloadedBytes() >> 20) + " МБ) и в лимит не входят."));
                    c.addView(link(a, p, "Лимит кэша: " + Qvk3MusicSettings.cacheLimitMb(a) + " МБ", v -> {
                        String[] l = {"100 МБ", "300 МБ", "500 МБ", "1 ГБ", "2 ГБ"};
                        int[] val = {100, 300, 500, 1024, 2048};
                        choose(a, "Лимит временного кэша", l, -1, w -> {
                            putInt(a, Qvk3MusicSettings.CACHE_LIMIT_MB, "Лимит кэша музыки", val[w]);
                            Qvk3Jobs.io(img::trim);
                            r.run();
                        });
                    }));
                    c.addView(link(a, p, "Очистить кэш обложек", v -> {
                        Qvk3Jobs.io(img::clear);
                        Qvk3Ui.toast(a, "Кэш обложек очищен");
                    }));
                    c.addView(info(a, p, "Загрузки сохраняются в «Музыка/QVK» (Android 10+) с названием и тегами."));
                };
            case 6:
                return (a, p, c, r) -> {
                    c.addView(info(a, p, "Порядок: текст ВК → сохранённый текст → внешний источник. Внешний текст принимается только при совпадении исполнителя, названия и длительности."));
                    c.addView(toggle(a, "Искать недостающий текст в LRCLIB", Qvk3MusicSettings.LYRICS_EXTERNAL, Qvk3MusicSettings.lyricsExternal(a), null));
                    c.addView(toggle(a, "Текст поверх обложки", Qvk3MusicSettings.LYRICS_OVER_COVER, Qvk3MusicSettings.lyricsOverCover(a), () -> refreshPlayer(a)));
                    c.addView(link(a, p, "Размер шрифта текста: " + Qvk3MusicSettings.lyricsSizeSp(a), v -> {
                        String[] l = {"14", "16", "18", "22", "26", "30"};
                        choose(a, "Размер текста песни", l, -1, w -> {
                            putInt(a, Qvk3MusicSettings.LYRICS_SIZE, "Размер текста песни", Integer.parseInt(l[w]));
                            r.run();
                            refreshPlayer(a);
                        });
                    }));
                    c.addView(link(a, p, "Загрузить свой .lrc для текущего трека", v -> {
                        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                        try {
                            a.startActivityForResult(pick, REQ_LRC);
                        } catch (Exception e) {
                            Qvk3Ui.toast(a, "Нет приложения для выбора файлов");
                        }
                    }));
                };
            case 7:
                return (a, p, c, r) -> {
                    PowerManager pm = a.getSystemService(PowerManager.class);
                    boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName());
                    c.addView(info(a, p, "Музыка играет через собственную службу QVK Music с уведомлением; после паузы служба сама завершается через 10 минут. "
                            + (ignoring ? "Оптимизация батареи для QVK отключена." : "Если прошивка закрывает QVK в фоне, отключите для него оптимизацию батареи.")));
                    if (!ignoring) c.addView(link(a, p, "Открыть настройки батареи", v -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                        } catch (Exception e) {
                            Qvk3Ui.toast(a, "Настройка недоступна на этом устройстве");
                        }
                    }));
                };
            case 8:
                return (a, p, c, r) -> {
                    c.addView(toggle(a, "Пауза при отключении наушников / Bluetooth", Qvk3MusicSettings.PAUSE_ON_UNPLUG, true, null));
                    c.addView(info(a, p, "Кнопки гарнитуры и Bluetooth-устройств управляют QVK Music через системную медиасессию (воспроизведение, пауза, следующий/предыдущий)."));
                };
            case 9:
                return (a, p, c, r) -> c.addView(link(a, p, "Расстояние между альбомами: " + Qvk3MusicSettings.coverflowSpacing(a) + "%", v -> {
                    String[] l = {"Плотно (40%)", "Обычно (55%)", "Просторно (70%)", "Очень просторно (85%)"};
                    int[] val = {40, 55, 70, 85};
                    choose(a, "CoverFlow", l, -1, w -> {
                        putInt(a, Qvk3MusicSettings.COVERFLOW_SPACING, "Расстояние CoverFlow", val[w]);
                        r.run();
                        refreshPlayer(a);
                    });
                }));
            case 10:
                return (a, p, c, r) -> {
                    c.addView(toggle(a, "Вести историю прослушивания (только на устройстве)", Qvk3MusicSettings.HISTORY, true, null));
                    c.addView(link(a, p, "Очистить историю", v -> Qvk3Ui.dialog(a).setTitle("Очистить историю прослушивания?")
                            .setPositiveButton("Очистить", (d, w) -> Qvk3Jobs.io(() -> Qvk3MusicLibrary.get(a).clearHistory()))
                            .setNegativeButton("Отмена", null).show()));
                };
            default:
                return (a, p, c, r) -> {
                    Qvk3MusicEngine e = Qvk3MusicEngine.get(a);
                    String[] states = {"остановлен", "буферизация", "играет", "пауза", "ошибка"};
                    c.addView(info(a, p, "Движок: " + states[e.state()] + (e.error().isEmpty() ? "" : " · " + e.error())
                            + "\nСлужба: " + (Qvk3MusicService.isRunning() ? "запущена" : "не запущена")
                            + "\nАудиосессия: " + e.audioSessionId()
                            + "\nЭффекты: " + (e.effects().unavailable.isEmpty() ? "все доступны" : "нет: " + e.effects().unavailable)
                            + "\nАккаунт: " + (Qvk3MusicLibrary.account() > 0 ? "вход выполнен" : "нет входа")
                            + "\n" + Qvk3MusicLibrary.get(a).stats()));
                    c.addView(toggle(a, "QVK Music вместо раздела «Музыка» ВК", Qvk3MusicSettings.ENABLED, true, null));
                    c.addView(toggle(a, "Мини-плеер поверх экранов ВКонтакте", Qvk3MusicSettings.MINI_ON_VK, false, null));
                    c.addView(toggle(a, "Играть треки из ленты и сообщений в QVK Music", Qvk3MusicSettings.TAKE_OVER, true, null));
                    c.addView(info(a, p, "Выключите, чтобы временно вернуться к штатному музыкальному разделу ВК (классический режим)."));
                };
        }
    }

    static int indexOf(String layout) {
        for (int i = 0; i < Qvk3MusicSettings.LAYOUTS.length; i++) if (Qvk3MusicSettings.LAYOUTS[i].equals(layout)) return i;
        return 0;
    }

    /** Player appearance editor; changes apply live without restarting playback. */
    static void playerLook(Activity a, Runnable changed) {
        String[] items = {"Макет плеера", "Размер обложки", "Скругление обложки", "Полоса прогресса"};
        Qvk3Ui.dialog(a).setTitle("Оформление плеера").setItems(items, (d, w) -> {
            switch (w) {
                case 0:
                    choose(a, "Макет плеера", Qvk3MusicSettings.LAYOUT_TITLES, indexOf(Qvk3MusicSettings.layout(a)), i -> {
                        Qvk3SettingsHistory.before(a, Qvk3MusicSettings.LAYOUT, "Макет плеера");
                        Qvk3MusicSettings.prefs(a).edit().putString(Qvk3MusicSettings.LAYOUT, Qvk3MusicSettings.LAYOUTS[i]).apply();
                        changed.run();
                    });
                    break;
                case 1: {
                    String[] l = {"Компактная (60%)", "Средняя (75%)", "Большая (86%)", "Во всю ширину (100%)"};
                    int[] val = {60, 75, 86, 100};
                    choose(a, "Размер обложки", l, -1, i -> {
                        putInt(a, Qvk3MusicSettings.COVER_SIZE, "Размер обложки", val[i]);
                        changed.run();
                    });
                    break;
                }
                case 2: {
                    String[] l = {"Квадрат", "Слегка скруглённая", "Скруглённая", "Сильно скруглённая", "Максимально скруглённая"};
                    int[] val = {0, 8, 16, 28, 40};
                    choose(a, "Форма обложки", l, -1, i -> {
                        putInt(a, Qvk3MusicSettings.COVER_RADIUS, "Форма обложки", val[i]);
                        changed.run();
                    });
                    break;
                }
                default: {
                    String[] l = {"Тонкая линия", "Толстая линия"};
                    String[] val = {"line", "thick"};
                    choose(a, "Полоса прогресса", l, -1, i -> {
                        Qvk3SettingsHistory.before(a, Qvk3MusicSettings.PROGRESS_STYLE, "Полоса прогресса");
                        Qvk3MusicSettings.prefs(a).edit().putString(Qvk3MusicSettings.PROGRESS_STYLE, val[i]).apply();
                        changed.run();
                    });
                }
            }
        }).show();
    }
}
