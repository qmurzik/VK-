package re.sova.qmods;

import android.app.Activity;
import android.text.InputType;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/**
 * "Звук": equalizer with device presets and user presets, Bass Boost, virtualizer and crossfade.
 * Controls appear only when the device actually provides the effect; otherwise the reason is shown.
 */
public final class Qvk3SoundDialog {
    public static void show(Activity a) {
        Qvk3Ui.Palette p = Qvk3Ui.palette(a, Qvk3MusicSettings.layout(a));
        Qvk3AudioEffects fx = Qvk3MusicEngine.get(a).effects();
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Qvk3Ui.dp(a, 20);
        col.setPadding(pad, Qvk3Ui.dp(a, 8), pad, Qvk3Ui.dp(a, 8));
        Switch on = new Switch(a);
        on.setText("Звуковые эффекты QVK Music");
        on.setChecked(fx.enabled());
        on.setOnCheckedChangeListener((CompoundButton b, boolean v) -> fx.setEnabled(v));
        col.addView(on);
        if (!fx.unavailable.isEmpty()) {
            col.addView(Qvk3Ui.text(a, "На этом устройстве недоступно: " + fx.unavailable, 13, p.text2, false));
        }
        if (fx.hasEqualizer()) {
            short[] range = fx.range();
            for (short b = 0; b < fx.bands(); b++) {
                final short band = b;
                int hz = fx.centerHz(b);
                TextView label = Qvk3Ui.text(a, (hz >= 1000 ? (hz / 1000) + " кГц" : hz + " Гц") + ": " + fx.level(b) / 100 + " дБ", 13, p.text2, false);
                SeekBar s = new SeekBar(a);
                s.setMax(range[1] - range[0]);
                s.setProgress(fx.level(b) - range[0]);
                s.setContentDescription("Полоса " + label.getText());
                s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int v, boolean user) {
                        if (!user) return;
                        short lvl = (short) (v + range[0]);
                        fx.setLevel(band, lvl);
                        label.setText((hz >= 1000 ? (hz / 1000) + " кГц" : hz + " Гц") + ": " + lvl / 100 + " дБ");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar sb) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar sb) {
                    }
                });
                col.addView(label);
                col.addView(s);
            }
            LinearLayout presets = new LinearLayout(a);
            presets.setGravity(Gravity.CENTER_VERTICAL);
            presets.addView(Qvk3Ui.button(a, p, "Пресеты", false, v -> {
                String[] dev = fx.presets();
                List<String> user = fx.userPresets();
                String[] all = new String[dev.length + user.size()];
                for (int i = 0; i < dev.length; i++) all[i] = dev[i];
                for (int i = 0; i < user.size(); i++) all[dev.length + i] = "★ " + user.get(i);
                Qvk3Ui.dialog(a).setTitle("Пресет эквалайзера").setItems(all, (d, i) -> {
                    if (i < dev.length) fx.usePreset((short) i);
                    else fx.useUserPreset(user.get(i - dev.length));
                    show(a);
                }).show();
            }));
            presets.addView(Qvk3Ui.button(a, p, "Сохранить свой", false, v -> {
                EditText in = new EditText(a);
                in.setInputType(InputType.TYPE_CLASS_TEXT);
                Qvk3Ui.dialog(a).setTitle("Название пресета").setView(in)
                        .setPositiveButton("Сохранить", (d, w) -> fx.saveUserPreset(in.getText().toString()))
                        .setNegativeButton("Отмена", null).show();
            }));
            col.addView(presets);
        }
        if (fx.hasBass()) col.addView(slider(a, p, "Усиление басов", fx.bassStrength(), 1000, fx::setBass));
        if (fx.hasVirtualizer()) col.addView(slider(a, p, "Объёмный звук", fx.virtualizerStrength(), 1000, fx::setVirtualizer));
        col.addView(slider(a, p, "Кроссфейд, с (0 — без перехода, работает gapless)", Qvk3MusicSettings.crossfadeSeconds(a), 12,
                v -> {
                    Qvk3SettingsHistory.before(a, Qvk3MusicSettings.CROSSFADE, "Кроссфейд");
                    Qvk3MusicSettings.prefs(a).edit().putInt(Qvk3MusicSettings.CROSSFADE, v).apply();
                }));
        col.addView(Qvk3Ui.text(a, "Нормализация громкости не реализована: ВК не передаёт данные о громкости треков, а без них она искажала бы звук.", 12, p.text2, false));
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        Qvk3Ui.dialog(a).setTitle("Звук").setView(sv).setPositiveButton("Готово", null).show();
    }

    interface IntSink {
        void set(int v);
    }

    static LinearLayout slider(Activity a, Qvk3Ui.Palette p, String title, int value, int max, IntSink sink) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = Qvk3Ui.text(a, title + ": " + value, 14, p.text, false);
        SeekBar s = new SeekBar(a);
        s.setMax(max);
        s.setProgress(value);
        s.setContentDescription(title);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int v, boolean user) {
                t.setText(title + ": " + v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                sink.set(sb.getProgress());
            }
        });
        l.addView(t);
        l.addView(s);
        return l;
    }
}
