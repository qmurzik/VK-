package re.sova.qmods;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * IDEA-0014: the temperature (and «дождь» / «снег» / «гроза») under the QVK logo on the launch screen.
 *
 * Off by default: it sends rounded coordinates (0.1°, about 10 km) to Open-Meteo (api.open-meteo.com, no key, no account).
 * The location is Android's last known one — QVK never turns on GPS for this and asks for no new permission. The launch screen
 * shows the cached value at once (no waiting for the network); a fresh value is fetched in the background at most once in
 * 30 minutes for the next launch.
 */
public final class Qvk3SplashWeather {
    public static final String PREF = "qvk3_splash_weather";
    static final String TEMP = "qvk3_weather_temp", CODE = "qvk3_weather_code", AT = "qvk3_weather_at";
    static final long FRESH = 30 * 60_000L, SHOW_MAX_AGE = 6 * 3_600_000L;
    private static volatile boolean loading;

    private Qvk3SplashWeather() {
    }

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    /** Called from QvkStartupActivity.onCreate (patch 0022) with the splash delay; returns the delay to use. */
    public static int show(Activity a, int delay) {
        try {
            SharedPreferences p = prefs(a);
            if (!p.getBoolean(PREF, false)) return delay;
            refresh(a.getApplicationContext(), false);
            long at = p.getLong(AT, 0);
            if (at == 0 || System.currentTimeMillis() - at > SHOW_MAX_AGE) return delay;
            ViewGroup root = a.findViewById(android.R.id.content);
            if (root == null || root.getChildCount() == 0 || !(root.getChildAt(0) instanceof FrameLayout)) return delay;
            FrameLayout frame = (FrameLayout) root.getChildAt(0);
            int color = 0xFFFFFFFF;
            TextView logo = findText(frame, "QVK");
            if (logo != null) color = logo.getCurrentTextColor();
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView t = new TextView(a);
            t.setText(temperature(p.getFloat(TEMP, 0f)));
            t.setTextColor(color);
            t.setTextSize(44);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
            t.setGravity(Gravity.CENTER);
            box.addView(t);
            String sky = sky(p.getInt(CODE, -1));
            if (!sky.isEmpty()) {
                TextView s = new TextView(a);
                s.setText(sky);
                s.setTextColor((color & 0x00FFFFFF) | 0xCC000000);
                s.setTextSize(18);
                s.setGravity(Gravity.CENTER);
                box.addView(s);
            }
            box.setContentDescription("Погода: " + t.getText() + (sky.isEmpty() ? "" : ", " + sky));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            lp.bottomMargin = Qvk3Ui.dp(a, 120);
            frame.addView(box, lp);
            box.setAlpha(0f);
            box.animate().alpha(1f).setDuration(350).start();
            return delay + 900; // enough time to read it
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.splash.weather", t);
            return delay;
        }
    }

    static TextView findText(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView r = findText(g.getChildAt(i), text);
                if (r != null) return r;
            }
        }
        return null;
    }

    static String temperature(float c) {
        int t = Math.round(c);
        return (t > 0 ? "+" : t < 0 ? "−" : "") + Math.abs(t) + "°";
    }

    /** WMO weather codes (Open-Meteo): only precipitation and storms are named. */
    static String sky(int code) {
        if (code >= 95) return "гроза";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "снег";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "дождь";
        return "";
    }

    /** Background refresh: at most once per 30 minutes (force: right after the user turns the option on). */
    public static void refresh(Context c, boolean force) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(PREF, false) || loading) return;
        if (!force && System.currentTimeMillis() - p.getLong(AT, 0) < FRESH) return;
        loading = true;
        Qvk3Jobs.io(() -> {
            try {
                Location l = lastLocation(c);
                if (l == null) return;
                String url = String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.1f&longitude=%.1f&current=temperature_2m,weather_code",
                        l.getLatitude(), l.getLongitude());
                JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
                p.edit().putFloat(TEMP, (float) cur.getDouble("temperature_2m")).putInt(CODE, cur.optInt("weather_code", -1))
                        .putLong(AT, System.currentTimeMillis()).apply();
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.weather.refresh", t);
            } finally {
                loading = false;
            }
        });
    }

    static Location lastLocation(Context c) {
        boolean coarse = c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean fine = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!coarse && !fine) return null;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException | IllegalArgumentException ignored) {
                // provider missing or not allowed
            }
        }
        return best;
    }

    static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try {
            if (con.getResponseCode() != 200) throw new java.io.IOException("HTTP " + con.getResponseCode());
            try (InputStream in = con.getInputStream()) {
                byte[] b = new byte[16384];
                int n = Qvk3Id3.readFully(in, b);
                return new String(b, 0, n, "UTF-8");
            }
        } finally {
            con.disconnect();
        }
    }

    /** Settings: switch with an honest explanation; turning it on asks Android for location if VK never got it. */
    static void settings(Activity a) {
        SharedPreferences p = prefs(a);
        boolean on = p.getBoolean(PREF, false);
        String msg = "Под логотипом QVK при запуске с иконки — температура, а в дождь, снег или грозу ещё и подпись.\n\n"
                + "Для этого QVK берёт последнее известное Android местоположение (GPS не включается) и отправляет округлённые "
                + "координаты (около 10 км) в сервис погоды Open-Meteo, без аккаунта и без ключа. Обновление — не чаще раза в 30 минут, "
                + "в фоне; запуск не ждёт сеть.\n\nЛоготип QVK вместо иллюстрации: Оформление → Экран запуска.";
        Qvk3Ui.dialog(a).setTitle("Погода на экране запуска").setMessage(msg)
                .setPositiveButton(on ? "Выключить" : "Включить", (d, w) -> {
                    p.edit().putBoolean(PREF, !on).apply();
                    if (!on) {
                        if (a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 7314);
                        refresh(a.getApplicationContext(), true);
                        Qvk3Ui.toast(a, "Погода появится при следующем запуске с иконки");
                    }
                }).setNegativeButton("Закрыть", null).show();
    }
}
