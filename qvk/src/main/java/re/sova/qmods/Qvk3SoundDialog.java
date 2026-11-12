package re.sova.qmods;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/**
 * "Звук": equalizer with device presets and user presets, Bass Boost, virtualizer and crossfade.
 * Controls appear only when the device actually provides the effect; otherwise the reason is shown.
 */
public final class Qvk3SoundDialog {
    public static void show(Activity a) {
        Qvk3Ui.Palette p = Qvk3Sheet.palette(a);
        Qvk3AudioEffects fx = Qvk3MusicEngine.get(a).effects();
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 24);
        col.setPadding(pad, 0, pad, Qvk3Ui.dp(a, 8));
        LinearLayout main = Qvk3Sheet.group(a, p);
        main.addView(Qvk3Sheet.switchRow(a, p, "Звуковые эффекты", "Эквалайзер, басы и объём для QVK Music", fx.enabled(), fx::setEnabled));
        col.addView(main);
        if (!fx.unavailable.isEmpty()) col.addView(Qvk3Sheet.note(a, p, "На этом устройстве недоступно: " + fx.unavailable));
        if (fx.hasEqualizer()) {
            col.addView(Qvk3Sheet.section(a, p, "Эквалайзер"));
            LinearLayout eq = Qvk3Sheet.group(a, p);
            short[] range = fx.range();
            for (short b = 0; b < fx.bands(); b++) {
                final short band = b;
                int hz = fx.centerHz(b);
                SeekBar s = new SeekBar(a);
                s.setMax(range[1] - range[0]);
                s.setProgress(fx.level(b) - range[0]);
                LinearLayout row = Qvk3Sheet.slider(a, p, hz >= 1000 ? (hz / 1000) + " кГц" : hz + " Гц", s, v -> db(v + range[0]));
                TextView val = Qvk3Sheet.sliderValue(row);
                s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int v, boolean user) {
                        val.setText(db(v + range[0]));
                        if (user) fx.setLevel(band, (short) (v + range[0]));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar sb) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar sb) {
                    }
                });
                eq.addView(row);
            }
            col.addView(eq);
            LinearLayout presets = new LinearLayout(a);
            presets.setPadding(0, Qvk3Ui.dp(a, 10), 0, 0);
            presets.addView(Qvk3Ui.button(a, p, "Пресеты", false, v -> {
                String[] dev = fx.presets();
                List<String> user = fx.userPresets();
                String[] all = new String[dev.length + user.size()];
                for (int i = 0; i < dev.length; i++) all[i] = dev[i];
                for (int i = 0; i < user.size(); i++) all[dev.length + i] = user.get(i) + "\nМой пресет";
                Qvk3Ui.dialog(a).setTitle("Пресет эквалайзера").setItems(all, (d, i) -> {
                    if (i < dev.length) fx.usePreset((short) i);
                    else fx.useUserPreset(user.get(i - dev.length));
                    show(a);
                }).show();
            }), Qvk3Ui.weight(1));
            View gap = new View(a);
            presets.addView(gap, new LinearLayout.LayoutParams(Qvk3Ui.dp(a, 8), 1));
            presets.addView(Qvk3Ui.button(a, p, "Сохранить свой", false, v -> {
                EditText in = Qvk3Sheet.field(a, p, new EditText(a));
                in.setInputType(InputType.TYPE_CLASS_TEXT);
                in.setHint("Например, «В наушниках»");
                Qvk3Ui.dialog(a).setTitle("Название пресета").setView(in)
                        .setPositiveButton("Сохранить", (d, w) -> fx.saveUserPreset(in.getText().toString()))
                        .setNegativeButton("Отмена", null).show();
            }), Qvk3Ui.weight(1));
            col.addView(presets);
        }
        col.addView(Qvk3Sheet.section(a, p, "Эффекты"));
        LinearLayout more = Qvk3Sheet.group(a, p);
        if (fx.hasBass()) more.addView(slider(a, p, "Усиление басов", fx.bassStrength(), 1000, fx::setBass, v -> v / 10 + "%"));
        if (fx.hasVirtualizer()) more.addView(slider(a, p, "Объёмный звук", fx.virtualizerStrength(), 1000, fx::setVirtualizer, v -> v / 10 + "%"));
        more.addView(slider(a, p, "Кроссфейд", Qvk3MusicSettings.crossfadeSeconds(a), 12,
                v -> {
                    Qvk3SettingsHistory.before(a, Qvk3MusicSettings.CROSSFADE, "Кроссфейд");
                    Qvk3MusicSettings.prefs(a).edit().putInt(Qvk3MusicSettings.CROSSFADE, v).apply();
                }, v -> v == 0 ? "выкл" : v + " с"));
        col.addView(more);
        col.addView(Qvk3Sheet.note(a, p, "Кроссфейд 0 — треки идут без паузы (gapless). Нормализации громкости нет: ВК не передаёт данные о громкости треков, а без них она искажала бы звук."));
        Qvk3Ui.dialog(a).setTitle("Звук").setView(col).setPositiveButton("Готово", null).show();
    }

    private static String db(int milli) {
        int v = Math.round(milli / 100f);
        return (v > 0 ? "+" : "") + v + " дБ";
    }

    interface IntSink {
        void set(int v);
    }

    static LinearLayout slider(Activity a, Qvk3Ui.Palette p, String title, int value, int max, IntSink sink) {
        return slider(a, p, title, value, max, sink, String::valueOf);
    }

    static LinearLayout slider(Activity a, Qvk3Ui.Palette p, String title, int value, int max, IntSink sink, Qvk3Sheet.Seek fmt) {
        SeekBar s = new SeekBar(a);
        s.setMax(max);
        s.setProgress(value);
        LinearLayout row = Qvk3Sheet.slider(a, p, title, s, fmt);
        TextView val = Qvk3Sheet.sliderValue(row);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int v, boolean user) {
                val.setText(fmt.label(v));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                sink.set(sb.getProgress());
            }
        });
        return row;
    }
}
