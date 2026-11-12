package re.sova.qmods;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.Arrays;
import java.util.Set;

/**
 * Settings that used to open QVK 2.x screens and are now edited inside the QVK 3.0 centre. Values are stored
 * under the same keys as before, so QVK 2.x, backups and the classic screens keep seeing them.
 */
final class Qvk3NativeEditors {
    /** Catalog actions / hub targets handled natively. */
    static final Set<String> ACTIONS = Qvk3SettingsIndex.NATIVE;

    static boolean handles(String target) {
        return target != null && ACTIONS.contains(target);
    }

    static SharedPreferences prefs() {
        return RuntimeBridge.prefs;
    }

    static void open(Activity a, String target) {
        switch (target) {
            case "pushModes":
                push(a);
                break;
            case "lock":
                if (!prefs().contains("qmods_pin_hash")) {
                    Qvk3Ui.toast(a, "Сначала задайте PIN-код (Приватность и защита → Блокировка приложения)");
                    return;
                }
                RuntimeBridge.lockNow(a);
                break;
            case "timeout":
                timeout(a);
                break;
            case "folder":
                text(a, "Папка для загрузок", "Папка внутри «Загрузки»", "qmods_download_folder", "QMods", v -> MediaFiles.folder(v));
                break;
            case "deletedMark":
                text(a, "Метка удалённого сообщения", "Подпись у сохранённых удалённых сообщений", "qmods_deleted_mark", "", v -> v.length() > 40 ? v.substring(0, 40) : v);
                break;
            case "host":
                text(a, "Адрес API ВК", "host[:порт], пусто — штатный адрес", "apiHost", "", v -> {
                    if (!v.isEmpty() && !v.matches("[A-Za-z0-9.-]+(?::[0-9]{1,5})?")) throw new IllegalArgumentException("Нужен адрес вида host или host:порт");
                    return v;
                });
                break;
            case "reset":
                Qvk3Ui.dialog(a).setTitle("Восстановить адреса серверов?").setMessage("Все адреса вернутся к штатным значениям ВК.")
                        .setPositiveButton("Восстановить", (d, w) -> {
                            DomainSettings.reset();
                            Qvk3Ui.toast(a, "Адреса восстановлены. Перезапустите приложение");
                        }).setNegativeButton("Отмена", null).show();
                break;
            default:
                throw new IllegalArgumentException(target);
        }
    }

    static final String[] PUSH_VALUES = {"auto", "fcm", "rustore", "longpoll", "poll", "job", "alarm"};
    static final String[] PUSH_LABELS = {"Автоматически", "Firebase (FCM)", "RuStore Push", "Long Poll", "Периодический опрос", "Системное задание", "Будильник"};

    static void push(Activity a) {
        String cur = PushModes.mode();
        int idx = Math.max(0, Arrays.asList(PUSH_VALUES).indexOf(cur));
        Qvk3Ui.dialog(a).setTitle("Способ доставки уведомлений").setSingleChoiceItems(PUSH_LABELS, idx, (d, i) -> {
            Qvk3SettingsHistory.before(a, PushModes.KEY, "Способ доставки: " + PUSH_LABELS[i]);
            prefs().edit().putString(PushModes.KEY, PUSH_VALUES[i]).apply();
            try {
                PushSupport.changedVendor();
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.native.push", t);
            }
            Qvk3Ui.toast(a, PUSH_LABELS[i] + (i >= 3 ? ": фоновая доставка без Firebase" : ""));
            d.dismiss();
        }).setNegativeButton("Закрыть", null).show();
    }

    static void timeout(Activity a) {
        int[] v = {0, 5, 15, 30, 60, 300};
        String[] l = {"Сразу", "5 секунд", "15 секунд", "30 секунд", "1 минута", "5 минут"};
        int cur = prefs().getInt("qmods_pin_timeout", 15), idx = 2;
        for (int i = 0; i < v.length; i++) if (v[i] == cur) idx = i;
        Qvk3Ui.dialog(a).setTitle("Задержка блокировки").setSingleChoiceItems(l, idx, (d, i) -> {
            Qvk3SettingsHistory.before(a, "qmods_pin_timeout", "Задержка блокировки: " + l[i]);
            prefs().edit().putInt("qmods_pin_timeout", v[i]).apply();
            d.dismiss();
        }).setNegativeButton("Закрыть", null).show();
    }

    interface Normalizer {
        String apply(String v);
    }

    static void text(Activity a, String title, String hint, String key, String def, Normalizer n) {
        LinearLayout col = new LinearLayout(a);
        int pad = Qvk3Ui.dp(a, 24);
        col.setPadding(pad, Qvk3Ui.dp(a, 8), pad, 0);
        EditText in = new EditText(a);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setHint(hint);
        in.setText(prefs().getString(key, def));
        in.setSelection(in.getText().length());
        col.addView(in, new LinearLayout.LayoutParams(-1, -2));
        Qvk3Ui.dialog(a).setTitle(title).setView(col).setPositiveButton("Сохранить", (d, w) -> {
            try {
                String v = n.apply(in.getText().toString().trim());
                Qvk3SettingsHistory.before(a, key, title);
                if (v.isEmpty() && def.isEmpty()) prefs().edit().remove(key).apply();
                else prefs().edit().putString(key, v).apply();
            } catch (IllegalArgumentException e) {
                Qvk3Ui.toast(a, e.getMessage());
            }
        }).setNegativeButton("Отмена", null).show();
    }
}
