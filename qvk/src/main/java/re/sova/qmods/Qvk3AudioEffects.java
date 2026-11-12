package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;

/**
 * Equalizer / Bass Boost / Virtualizer bound to QVK Music's fixed audio session. Availability is probed
 * on the actual device: an effect the device does not provide is reported as unsupported instead of being
 * shown as a working control.
 */
public final class Qvk3AudioEffects {
    private final SharedPreferences prefs;
    private final int session;
    private Equalizer eq;
    private BassBoost bass;
    private Virtualizer virt;
    public final String unavailable;

    Qvk3AudioEffects(Context c, int session) {
        this.session = session;
        this.prefs = c.getSharedPreferences("qvk3_music", Context.MODE_PRIVATE);
        StringBuilder missing = new StringBuilder();
        try {
            eq = new Equalizer(0, session);
        } catch (Throwable t) {
            missing.append("эквалайзер ");
        }
        try {
            bass = new BassBoost(0, session);
            if (!bass.getStrengthSupported()) {
                bass.release();
                bass = null;
                missing.append("усиление басов ");
            }
        } catch (Throwable t) {
            missing.append("усиление басов ");
        }
        try {
            virt = new Virtualizer(0, session);
            if (!virt.getStrengthSupported()) {
                virt.release();
                virt = null;
            }
        } catch (Throwable t) {
            virt = null;
        }
        unavailable = missing.toString().trim().replaceAll("(басов|эквалайзер|звук) (?=\\S)", "$1, ");
        apply();
    }

    public boolean hasEqualizer() {
        return eq != null;
    }

    public boolean hasBass() {
        return bass != null;
    }

    public boolean hasVirtualizer() {
        return virt != null;
    }

    public short bands() {
        return eq == null ? 0 : eq.getNumberOfBands();
    }

    public short[] range() {
        return eq == null ? new short[]{0, 0} : eq.getBandLevelRange();
    }

    public int centerHz(short band) {
        return eq == null ? 0 : eq.getCenterFreq(band) / 1000;
    }

    public short level(short band) {
        return (short) prefs.getInt("eq_band_" + band, eq == null ? 0 : eq.getBandLevel(band));
    }

    public String[] presets() {
        if (eq == null) return new String[0];
        String[] names = new String[eq.getNumberOfPresets()];
        for (short i = 0; i < names.length; i++) names[i] = eq.getPresetName(i);
        return names;
    }

    public boolean enabled() {
        return prefs.getBoolean("eq_enabled", false);
    }

    public void setEnabled(boolean on) {
        prefs.edit().putBoolean("eq_enabled", on).apply();
        apply();
    }

    public void setLevel(short band, short level) {
        prefs.edit().putInt("eq_band_" + band, level).putInt("eq_preset", -1).apply();
        apply();
    }

    public void usePreset(short preset) {
        if (eq == null) return;
        try {
            eq.usePreset(preset);
            SharedPreferences.Editor e = prefs.edit().putInt("eq_preset", preset);
            for (short b = 0; b < eq.getNumberOfBands(); b++) e.putInt("eq_band_" + b, eq.getBandLevel(b));
            e.apply();
        } catch (RuntimeException ignored) {
        }
        apply();
    }

    /** Saves the current bands as a named user preset. */
    public void saveUserPreset(String name) {
        if (eq == null || name == null || name.trim().isEmpty()) return;
        StringBuilder b = new StringBuilder();
        for (short i = 0; i < eq.getNumberOfBands(); i++) b.append(i == 0 ? "" : ",").append(level(i));
        prefs.edit().putString("eq_user_" + name.trim(), b.toString()).apply();
    }

    public java.util.List<String> userPresets() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String k : prefs.getAll().keySet()) if (k.startsWith("eq_user_")) out.add(k.substring(8));
        java.util.Collections.sort(out);
        return out;
    }

    public void useUserPreset(String name) {
        String v = prefs.getString("eq_user_" + name, "");
        String[] parts = v.split(",");
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < parts.length && i < bands(); i++) {
            try {
                e.putInt("eq_band_" + i, Integer.parseInt(parts[i]));
            } catch (NumberFormatException ignored) {
            }
        }
        e.apply();
        apply();
    }

    public int bassStrength() {
        return prefs.getInt("bass", 0);
    }

    public void setBass(int strength) {
        prefs.edit().putInt("bass", Math.max(0, Math.min(1000, strength))).apply();
        apply();
    }

    public int virtualizerStrength() {
        return prefs.getInt("virt", 0);
    }

    public void setVirtualizer(int strength) {
        prefs.edit().putInt("virt", Math.max(0, Math.min(1000, strength))).apply();
        apply();
    }

    private void apply() {
        boolean on = enabled();
        try {
            if (eq != null) {
                for (short b = 0; b < eq.getNumberOfBands(); b++) eq.setBandLevel(b, level(b));
                eq.setEnabled(on);
            }
            if (bass != null) {
                bass.setStrength((short) bassStrength());
                bass.setEnabled(on && bassStrength() > 0);
            }
            if (virt != null) {
                virt.setStrength((short) virtualizerStrength());
                virt.setEnabled(on && virtualizerStrength() > 0);
            }
        } catch (RuntimeException e) {
            RuntimeFaults.record("qvk3.music.effects", e);
        }
    }

    public int session() {
        return session;
    }
}
