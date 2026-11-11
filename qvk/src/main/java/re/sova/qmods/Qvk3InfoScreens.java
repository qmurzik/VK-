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
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/** Small screens of the settings centre: Push diagnostics, updates, storage, battery, about, fault log. */
final class Qvk3InfoScreens {
    static TextView body(Activity a, String text) {
        TextView t = Qvk3Ui.text(a, text, 14, Qvk3Ui.night(a) ? 0xFFE6E6E6 : 0xFF1A1A1A, false);
        t.setTextIsSelectable(true);
        int pad = Qvk3Ui.dp(a, 20);
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
        dialog(a, "Диагностический центр Push", col, "Локальная проверка", () -> {
            boolean ok = PushSupport.localTest(a);
            Qvk3Ui.toast(a, ok ? "Тестовое уведомление показано. Это проверка Android, а не сервера ВК"
                    : "Android не разрешает уведомления QVK: проверьте разрешение и каналы");
        });
    }

    /** Logs screen: copy / save the app's own log without adb. */
    static void logs(Activity a) {
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, "modern");
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView info = body(a, "Журнал считается...");
        col.addView(info);
        Runnable refresh = () -> Qvk3Jobs.io(() -> {
            long n = Qvk3Logs.size(a);
            String t = "Запись логов этого приложения (только QVK/ВК, других программ нет). Данные остаются на устройстве, никуда не отправляются. "
                    + "Токены и пароли в тексте скрываются.\n\nСейчас сохранено: " + (n >> 10) + " КБ. Хранится прошлый запуск и текущий, поэтому после вылета журнал можно скопировать при следующем открытии.\n\n"
                    + "«Копировать» кладёт в буфер последние ~300 КБ с описанием устройства и сбоями модулей (буфер обмена не принимает больше). Весь журнал целиком — «В файл» (Загрузки/QVK).";
            Qvk3Jobs.main(() -> info.setText(t));
        });
        refresh.run();
        Switch capture = new Switch(a);
        capture.setText("Записывать логи");
        capture.setChecked(Qvk3Logs.enabled(a));
        capture.setPadding(Qvk3Ui.dp(a, 20), Qvk3Ui.dp(a, 4), Qvk3Ui.dp(a, 20), Qvk3Ui.dp(a, 4));
        capture.setOnCheckedChangeListener((v, on) -> {
            Qvk3Logs.setEnabled(a, on);
            Qvk3Ui.toast(a, on ? "Запись логов включена" : "Запись остановлена");
        });
        col.addView(capture);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Qvk3Ui.dp(a, 14), Qvk3Ui.dp(a, 8), Qvk3Ui.dp(a, 14), Qvk3Ui.dp(a, 8));
        row.addView(Qvk3Ui.button(a, p, "Копировать", true, v -> Qvk3Logs.copy(a)), Qvk3Ui.weight(1));
        row.addView(Qvk3Ui.button(a, p, "В файл", false, v -> Qvk3Logs.save(a)), Qvk3Ui.weight(1));
        row.addView(Qvk3Ui.button(a, p, "Очистить", false, v -> {
            Qvk3Logs.clear(a);
            Qvk3Ui.toast(a, "Журнал очищен");
            refresh.run();
        }), Qvk3Ui.weight(1));
        col.addView(row);
        dialog(a, "Логи приложения", col, null, null);
    }

    static void show(Activity a, String what) {
        switch (what) {
            case "logs":
                logs(a);
                break;
            case "battery": {
                PowerManager pm = a.getSystemService(PowerManager.class);
                boolean ign = pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName());
                String s = "Оптимизация батареи для QVK: " + (ign ? "отключена" : "включена") + "\n\n"
                        + "QVK Music держит службу переднего плана только во время воспроизведения и завершает её через 10 минут паузы. "
                        + "Фоновые загрузки идут по одной. Тяжёлые эффекты стилей автоматически отключаются на слабых устройствах ("
                        + (Qvk3Appearance.weakDevice(a) ? "это устройство считается слабым" : "это устройство не считается слабым") + ").\n\n"
                        + "Если сообщения приходят с задержкой при выключенном экране, отключите оптимизацию батареи — но это увеличит расход.";
                dialog(a, "Фоновая работа и батарея", body(a, s), ign ? null : "Настройки батареи", () -> {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (Exception e) {
                        Qvk3Ui.toast(a, "Настройка недоступна");
                    }
                });
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
                            + "Переписки, кэш ВК и данные аккаунтов здесь не очищаются. Кэш сообщений ВК — в «Классические настройки → Инструменты».";
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
                        + "новая система обновлений, диагностика Push. Подробности — в QVK_REWORK_REPORT.md.";
                dialog(a, "О QVK 3.0", body(a, s), null, null);
            }
        }
    }

    static void updates(Activity a) {
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
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 20);
        col.setPadding(pad, Qvk3Ui.dp(a, 8), pad, 0);
        col.addView(Qvk3Ui.text(a, "Адреса манифестов QVK 3 (по одному в строке, HTTPS). Первый работающий с обновлением используется; остальные — зеркала.", 13, 0xFF888888, false));
        EditText src = new EditText(a);
        src.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        src.setMinLines(3);
        src.setText(String.join("\n", Qvk3Updates.sources(a)));
        col.addView(src);
        Switch community = new Switch(a);
        community.setText("Канал сообщества QVK (qmods.ru)");
        community.setChecked(Qvk3Updates.communityEnabled(a));
        col.addView(community);
        col.addView(Qvk3Ui.text(a, "Доверенные открытые ключи подписи манифеста (base64, по одному в строке). Если ключ задан, неподписанные манифесты отклоняются.", 13, 0xFF888888, false));
        EditText keys = new EditText(a);
        keys.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keys.setMinLines(2);
        keys.setText(Qvk3MusicSettings.prefs(a).getString(Qvk3Updates.KEYS, ""));
        col.addView(keys);
        dialog(a, "Серверы обновлений", col, "Сохранить", () -> {
            try {
                List<String> urls = new java.util.ArrayList<>();
                for (String s : src.getText().toString().split("\n")) if (!s.trim().isEmpty()) urls.add(s.trim());
                Qvk3Updates.setSources(a, urls);
                Qvk3MusicSettings.prefs(a).edit().putBoolean(Qvk3Updates.COMMUNITY, community.isChecked())
                        .putString(Qvk3Updates.KEYS, keys.getText().toString().trim()).apply();
                Qvk3Ui.toast(a, "Сохранено");
            } catch (IllegalArgumentException e) {
                Qvk3Ui.toast(a, e.getMessage());
            }
        });
    }
}
