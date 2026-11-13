package re.sova.qmods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/** Small screens of the settings centre: Push diagnostics, updates, storage, battery, about, fault log. */
final class Qvk3InfoScreens {
    static TextView body(Activity a, String text) {
        TextView t = Qvk3Ui.text(a, text, 15, Qvk3Sheet.palette(a).text, false);
        t.setLineSpacing(0, 1.2f);
        t.setTextIsSelectable(true);
        int pad = Qvk3Ui.dp(a, 24);
        t.setPadding(pad, Qvk3Ui.dp(a, 8), pad, pad);
        return t;
    }

    static void dialog(Activity a, String title, android.view.View v, String positive, Runnable onPositive) {
        ScrollView sv = new ScrollView(a);
        sv.addView(v);
        AlertDialog.Builder b = Qvk3Ui.dialog(a).setTitle(title).setView(sv);
        if (positive != null) b.setPositiveButton(positive, (d, w) -> onPositive.run());
        b.setNegativeButton("Закрыть", null).show();
    }

    static void push(Activity a) {
        TextView t = body(a, "Проверка…");
        Qvk3Jobs.io(() -> {
            StringBuilder s = new StringBuilder();
            for (Qvk3Push.Line l : Qvk3Push.diagnostics(a)) {
                s.append(l.level > 0 ? "✓ " : l.level < 0 ? "✗ " : "• ").append(l.title).append(":\n").append(l.value).append("\n\n");
            }
            Qvk3Jobs.main(() -> t.setText(s.toString().trim()));
        });
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(t);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        AlertDialog.Builder b = Qvk3Ui.dialog(a).setTitle("Диагностический центр Push").setView(sv);
        b.setPositiveButton("Локальная проверка", (d, w) -> {
            boolean ok = PushSupport.localTest(a);
            Qvk3Ui.toast(a, ok ? "Тестовое уведомление показано. Это проверка Android, а не сервера ВК"
                    : "Android не разрешает уведомления QVK: проверьте разрешение и каналы");
        });
        if (Qvk3MicroG.installed(a) || Qvk3MicroG.revanced(a)) {
            b.setNeutralButton("Проверить microG", (d, w) -> microgCheck(a));
        }
        b.setNegativeButton("Закрыть", null).show();
    }

    /** Live microG push check: request a real FCM token through microG and report the true result. */
    static void microgCheck(Activity a) {
        TextView t = body(a, "Запрашиваем токен FCM через microG…");
        AlertDialog dlg = Qvk3Ui.dialog(a).setTitle("Проверка microG")
                .setView(wrapScroll(a, t))
                .setPositiveButton("Настройки microG", (d, w) -> {
                    if (!Qvk3MicroG.openSettings(a)) {
                        Qvk3Ui.toast(a, "Не удалось открыть настройки microG");
                    }
                })
                .setNegativeButton("Закрыть", null).show();
        Qvk3Jobs.io(() -> {
            Qvk3MicroG.Result r = Qvk3MicroG.check(a);
            Qvk3Jobs.main(() -> {
                if (dlg.isShowing()) t.setText(r.text);
            });
        });
    }

    /**
     * Battery centre: what in QVK can keep the phone awake right now, how heavy it is, and quick ways to turn it down.
     * Nothing is changed without a tap.
     */
    static void battery(Activity a) {
        StringBuilder s = new StringBuilder();
        PowerManager pm = a.getSystemService(PowerManager.class);
        boolean ign = pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName());
        String mode = "auto";
        try {
            mode = PushModes.mode();
        } catch (Throwable ignored) {
            // QVK 2.x push module unavailable
        }
        int mi = Arrays.asList(Qvk3NativeEditors.PUSH_VALUES).indexOf(mode);
        String label = mi >= 0 ? Qvk3NativeEditors.PUSH_LABELS[mi] : mode;
        String cost;
        switch (mode) {
            case "longpoll":
                cost = "высокий: постоянное соединение с сервером ВК всё время";
                break;
            case "poll":
            case "alarm":
                cost = "высокий: телефон просыпается примерно раз в минуту";
                break;
            case "job":
                cost = "низкий: Android сам выбирает время, уведомления могут приходить с задержкой";
                break;
            default:
                cost = "низкий, если работают сервисы Google/RuStore";
        }
        s.append(Qvk3Energy.summary(a)).append("\n\n");
        s.append("Что сейчас может расходовать батарею:\n\n");
        s.append("• Доставка уведомлений: ").append(label).append(" — ").append(cost).append(".\n");
        boolean bg = false;
        try {
            bg = RuntimeBridge.flag("qvk_background_delivery");
        } catch (Throwable ignored) {
            // flag unavailable
        }
        s.append("• Уведомления без Firebase (постоянная служба): ").append(bg ? "включены — заметный расход" : "выключены").append(".\n");
        s.append("• Непрерывная запись логов: ").append(Qvk3Logs.enabled(a) ? "включена — расходует батарею, выключите после поиска бага" : "выключена").append(".\n");
        s.append("• Мини-плеер поверх экранов ВК: ").append(Qvk3MusicSettings.prefs(a).getBoolean(Qvk3MusicSettings.MINI_ON_VK, false) ? "включён" : "выключен").append(".\n");
        s.append("• Оптимизация батареи Android для QVK: ").append(ign ? "отключена — приложение может работать в фоне без ограничений" : "включена").append(".\n\n");
        s.append("QVK Music: во время игры обновляет уведомление только при смене трека или паузе, при выключенном экране проверяет плеер раз в секунду, "
                + "службу закрывает через 10 минут паузы.\n\nСамый большой расход обычно даёт доставка уведомлений без Firebase. "
                + "Если на телефоне есть сервисы Google, выберите «Автоматически».");
        Qvk3Ui.dialog(a).setTitle("Фоновая работа и батарея").setView(wrapScroll(a, body(a, s.toString())))
                .setPositiveButton("Выключить лишнее", (d, w) -> {
                    Qvk3SettingsHistory.before(a, Qvk3Logs.PREF_CAPTURE, "Экономия батареи");
                    Qvk3Logs.setEnabled(a, false);
                    Qvk3MusicSettings.prefs(a).edit().putBoolean(Qvk3MusicSettings.MINI_ON_VK, false).apply();
                    Qvk3Ui.toast(a, "Выключены непрерывные логи и мини-плеер поверх ВК. Доставку уведомлений выберите сами");
                })
                .setNeutralButton("Доставка уведомлений", (d, w) -> Qvk3NativeEditors.push(a))
                .setNegativeButton("Сообщить о расходе", (d, w) -> Qvk3Report.open(a, null, null, "Расход батареи",
                        "Цифры QVK:\n" + Qvk3Energy.summary(a) + "\n\nЧто замечаю: ", "performance")).show();
    }

    private static ScrollView wrapScroll(Activity a, android.view.View v) {
        ScrollView sv = new ScrollView(a);
        sv.addView(v);
        return sv;
    }

    /** Light / dark theme in one place (BUG-0012): mode choice plus the two palette editors, no hunting through menus. */
    static void dayNight(Activity a) {
        String cur = RuntimeBridge.prefs.getString(ThemeProfiles.MODE, "current");
        int at = Math.max(0, java.util.Arrays.asList(ThemeProfiles.MODES).indexOf(cur));
        Qvk3Ui.dialog(a).setTitle("Светлая и тёмная тема")
                .setSingleChoiceItems(ThemeProfiles.LABELS, at, (d, i) -> {
                    try {
                        ThemeProfiles.chooseMode(ThemeProfiles.MODES[i]);
                        if (i == 1 || i == 2) ThemeEditorActivity.setNativeMode(i == 2);
                        else if (i == 3) ThemeEditorActivity.setSystemMode(a);
                        else if (i == 4) ThemeEditorActivity.setNativeMode(ThemeProfiles.NIGHT.equals(ThemeProfiles.slot(a)));
                        d.dismiss();
                        ThemePalette.refresh(a);
                        a.recreate();
                    } catch (Exception e) {
                        Qvk3Ui.toast(a, "Не удалось сохранить режим темы");
                    }
                })
                .setPositiveButton("Светлая: цвета", (d, w) -> ThemeProfiles.edit(a, ThemeProfiles.DAY))
                .setNeutralButton("Тёмная: цвета", (d, w) -> ThemeProfiles.edit(a, ThemeProfiles.NIGHT))
                .setNegativeButton("Закрыть", null).show();
    }

    /** Logs screen: copy / save the app's own log without adb. */
    static void logs(Activity a) {
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView info = body(a, "Журнал считается...");
        col.addView(info);
        Runnable refresh = () -> Qvk3Jobs.io(() -> {
            long n = Qvk3Logs.size(a);
            String t = "Журнал этого приложения (только QVK/ВК, других программ нет). Данные остаются на устройстве, никуда не отправляются, "
                    + "токены и пароли в тексте скрываются.\n\nБез настройки: при копировании берутся последние строки журнала Android, а при вылете "
                    + "QVK сам сохраняет описание падения, его можно скопировать при следующем открытии. Это не расходует батарею.\n\n"
                    + "«Непрерывная запись» пишет журнал всё время, пока работает приложение: нужна, если баг трудно поймать. Она расходует батарею, "
                    + "включайте её только на время поиска бага.\n\nСохранено: " + (n >> 10) + " КБ. «Копировать» — последние ~300 КБ, «В файл» — всё (Загрузки/QVK).";
            Qvk3Jobs.main(() -> info.setText(t));
        });
        refresh.run();
        int pad = Qvk3Ui.dp(a, 24);
        LinearLayout g = Qvk3Sheet.group(a, p);
        g.addView(Qvk3Sheet.switchRow(a, p, "Непрерывная запись", "Только на время поиска бага: расходует батарею", Qvk3Logs.enabled(a), on -> {
            Qvk3Logs.setEnabled(a, on);
            Qvk3Ui.toast(a, on ? "Непрерывная запись включена. Не забудьте выключить после поиска бага" : "Непрерывная запись остановлена");
        }));
        LinearLayout.LayoutParams glp = Qvk3Ui.match();
        glp.leftMargin = glp.rightMargin = pad;
        col.addView(g, glp);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView copy = Qvk3Ui.button(a, p, "Копировать журнал", true, v -> Qvk3Logs.copy(a));
        LinearLayout.LayoutParams clp = Qvk3Ui.match();
        clp.leftMargin = clp.rightMargin = pad;
        clp.topMargin = Qvk3Ui.dp(a, 12);
        col.addView(copy, clp);
        row.setPadding(pad, Qvk3Ui.dp(a, 8), pad, Qvk3Ui.dp(a, 8));
        row.addView(Qvk3Ui.button(a, p, "В файл", false, v -> Qvk3Logs.save(a)), Qvk3Ui.weight(1));
        row.addView(spacer(a));
        row.addView(Qvk3Ui.button(a, p, "Очистить", false, v -> {
            Qvk3Logs.clear(a);
            Qvk3Ui.toast(a, "Журнал очищен");
            refresh.run();
        }), Qvk3Ui.weight(1));
        col.addView(row);
        dialog(a, "Логи приложения", col, null, null);
    }

    private static android.view.View spacer(Activity a) {
        android.view.View v = new android.view.View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 8), 1));
        return v;
    }

    static void show(Activity a, String what) {
        switch (what) {
            case "logs":
                logs(a);
                break;
            case "dayNight":
                dayNight(a);
                break;
            case "battery": {
                battery(a);
                break;
            }
            case "storage": {
                TextView t = body(a, "Подсчёт…");
                Qvk3Jobs.io(() -> {
                    long covers = Qvk3Images.get(a).diskBytes(), music = Qvk3MusicLibrary.get(a).downloadedBytes(), upd = 0;
                    try {
                        java.io.File[] f = Qvk3Updates.dir(a).listFiles();
                        if (f != null) for (java.io.File x : f) upd += x.length();
                    } catch (Exception ignored) {
                    }
                    final long u = upd;
                    String s = "Загруженная музыка (Музыка/QVK): " + (music >> 20) + " МБ\nКэш обложек QVK Music: " + (covers >> 20)
                            + " МБ (лимит " + Qvk3MusicSettings.cacheLimitMb(a) + " МБ)\nСкачанные обновления: " + (u >> 20) + " МБ\n\n"
                            + "Переписки, кэш ВК и данные аккаунтов здесь не очищаются. Кэш сообщений ВК — в «Классические настройки → Инструменты».\n\n— Рост данных —\n" + Qvk3Growth.report(a);
                    Qvk3Jobs.main(() -> t.setText(s));
                });
                dialog(a, "Место на устройстве", t, "Очистить обложки и старые APK", () -> Qvk3Jobs.io(() -> {
                    Qvk3Images.get(a).clear();
                    Qvk3Updates.cleanup(a);
                    Qvk3Ui.toast(a, "Очищено");
                }));
                break;
            }
            case "faults": {
                String r;
                try {
                    r = RuntimeFaults.hasFailures() ? RuntimeFaults.report() : "Сбоев не зарегистрировано.";
                } catch (Throwable t) {
                    r = "Журнал недоступен: " + Qvk3Jobs.message(t);
                }
                dialog(a, "Журнал ошибок", body(a, "Компонент и число сбоев с запуска приложения. Модули QVK 3.0 начинаются с «qvk3.». Полные логи с описанием устройства: «Логи приложения».\n\n" + r), null, null);
                break;
            }
            default: {
                String from = Qvk3MusicSettings.prefs(a).getString("qvk3_migrated_from", "—");
                String s = "QVK " + Qvk3Updates.installedName(a) + " (" + Qvk3Updates.installedVersion(a) + ")\n"
                        + "Основа: VK " + Qvk3UpdateManifest.VK_BASE + " — без перехода на новые версии ВК\n"
                        + "Пакет: " + a.getPackageName() + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                        + "Обновлено с: " + from + "\n\nQVK 3.0: QVK Music, центр настроек, стили и конструктор оформления, сценарии, "
                        + "новая система обновлений, диагностика Push.\n\n" + Qvk3Kill.describe(a);
                dialog(a, "О QVK 3.0", body(a, s), null, null);
            }
        }
    }

    static void updates(Activity a) {
        // «Орбита»: the new update screen; the old dialog stays as a fallback
        try {
            Qvk3UpdateScreen.check(a);
            return;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.updateScreen", t);
        }
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView status = body(a, "Установлено: " + Qvk3Updates.installedName(a) + "\nПроверка…");
        col.addView(status);
        final Qvk3Updates.Check[] last = new Qvk3Updates.Check[1];
        Qvk3Updates.listener = () -> {
            if (!Qvk3Updates.status.isEmpty()) status.setText(Qvk3Updates.status);
        };
        AlertDialog d = Qvk3Ui.dialog(a).setTitle("Обновления QVK").setView(wrap(a, col))
                .setPositiveButton("Скачать и установить", null)
                .setNeutralButton("История версий", null)
                .setNegativeButton("Закрыть", (x, w) -> Qvk3Updates.listener = null).create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (Qvk3Updates.busy()) {
                    Qvk3Updates.cancel();
                    return;
                }
                if (last[0] != null && last[0].release != null) Qvk3Updates.downloadAndInstall(a, last[0].release);
            });
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> history(a));
        });
        d.show();
        Qvk3Jobs.io(() -> {
            Qvk3Updates.Check c = Qvk3Updates.check(a);
            last[0] = c;
            Qvk3Jobs.main(() -> {
                StringBuilder s = new StringBuilder("Установлено: " + Qvk3Updates.installedName(a) + "\n" + c.message);
                if (c.release != null) {
                    s.append("\n\nИсточник: ").append(c.source).append("\nПодпись манифеста: ")
                            .append(c.signed ? "проверена" : "не проверена (ключ не настроен или канал без подписи)")
                            .append("\nРазмер: ").append(c.release.size >> 20).append(" МБ")
                            .append("\nПеред установкой проверяются SHA-256, пакет, версия и сертификат подписи APK.");
                    if (!c.release.notes.isEmpty()) s.append("\n\n").append(c.release.notes);
                }
                if (Qvk3Updates.sources(a).isEmpty()) s.append("\n\nСвои серверы обновлений не заданы — используется только канал сообщества QVK.");
                status.setText(s);
                if (d.isShowing()) d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(c.release != null);
            });
        });
    }

    static ScrollView wrap(Activity a, android.view.View v) {
        ScrollView sv = new ScrollView(a);
        sv.addView(v);
        return sv;
    }

    static void history(Activity a) {
        JSONArray h = Qvk3Updates.history(a);
        StringBuilder s = new StringBuilder();
        long installed = Qvk3Updates.installedVersion(a);
        for (int i = 0; i < h.length(); i++) {
            JSONObject o = h.optJSONObject(i);
            if (o == null) continue;
            s.append(o.optLong("code") == installed ? "● " : "○ ").append(o.optString("name"));
            if (!o.optString("published").isEmpty()) s.append(" · ").append(o.optString("published"));
            if (!o.optString("notes").isEmpty()) s.append('\n').append(o.optString("notes"));
            s.append("\n\n");
        }
        dialog(a, "История версий", body(a, s.length() == 0 ? "История появится после первой проверки обновлений." : s.toString().trim()), null, null);
    }

    static void updateSources(Activity a) {
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 24);
        col.setPadding(pad, 0, pad, 0);
        LinearLayout g = Qvk3Sheet.group(a, p);
        final boolean[] community = {Qvk3Updates.communityEnabled(a)};
        g.addView(Qvk3Sheet.switchRow(a, p, "Канал сообщества QVK", "Официальные сборки QVK", community[0], v -> community[0] = v));
        col.addView(g);
        col.addView(Qvk3Sheet.section(a, p, "Свои серверы"));
        EditText src = Qvk3Sheet.field(a, p, new EditText(a));
        src.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        src.setMinLines(3);
        src.setGravity(android.view.Gravity.TOP);
        src.setHint("https://…/qvk3-update.json");
        src.setText(String.join("\n", Qvk3Updates.sources(a)));
        col.addView(src);
        col.addView(Qvk3Sheet.note(a, p, "По одному адресу в строке, только HTTPS. Используется первый работающий с обновлением, остальные — зеркала."));
        col.addView(Qvk3Sheet.section(a, p, "Ключи подписи"));
        EditText keys = Qvk3Sheet.field(a, p, new EditText(a));
        keys.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keys.setMinLines(2);
        keys.setGravity(android.view.Gravity.TOP);
        keys.setHint("base64");
        keys.setText(Qvk3MusicSettings.prefs(a).getString(Qvk3Updates.KEYS, ""));
        col.addView(keys);
        col.addView(Qvk3Sheet.note(a, p, "Открытые ключи, по одному в строке. Если ключ задан, неподписанные манифесты отклоняются."));
        dialog(a, "Серверы обновлений", col, "Сохранить", () -> {
            try {
                List<String> urls = new java.util.ArrayList<>();
                for (String s : src.getText().toString().split("\n")) if (!s.trim().isEmpty()) urls.add(s.trim());
                Qvk3Updates.setSources(a, urls);
                Qvk3MusicSettings.prefs(a).edit().putBoolean(Qvk3Updates.COMMUNITY, community[0])
                        .putString(Qvk3Updates.KEYS, keys.getText().toString().trim()).apply();
                Qvk3Ui.toast(a, "Сохранено");
            } catch (IllegalArgumentException e) {
                Qvk3Ui.toast(a, e.getMessage());
            }
        });
    }
}
