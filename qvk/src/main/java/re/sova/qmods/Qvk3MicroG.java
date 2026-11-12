package re.sova.qmods;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;

/**
 * microG-aware push support (QVK 3.0).
 *
 * <p>QVK already registers for notifications through Firebase Cloud Messaging: the APK carries VK's real
 * Firebase project ({@code gcm_defaultSenderId}) and VK's signing certificate is sent as {@code X-Android-Cert}.
 * On a de-Googled phone that FCM path is served not by Google Play services but by <b>microG GmsCore</b>, which
 * answers the same {@code com.google.android.gms} APIs locally and forwards the registration to Google's servers
 * under the <i>calling app's own package and signature</i> — so no signature spoofing of QVK is required for the
 * token itself.</p>
 *
 * <p>What actually breaks delivery on microG is configuration, not code: microG's «Cloud Messaging» (GCM) and
 * device registration (checkin) must be switched on, and the OS must let QVK keep a background socket. This class
 * does three honest things and nothing more:</p>
 * <ol>
 *     <li>detects whether the gms provider on this device is microG;</li>
 *     <li>runs a <b>real</b> FCM token request through it ({@link PushSupport#nativeToken()}) and reports the
 *         true outcome, translating Firebase/microG errors via {@link PushSupport#errorLabel};</li>
 *     <li>opens microG's own settings so the user can enable Cloud Messaging.</li>
 * </ol>
 * It never claims that a notification was delivered: only a real incoming message from VK's servers proves that.
 */
public final class Qvk3MicroG {

    public static final String GMS = "com.google.android.gms";
    /** ReVanced GmsCore installs under its own package to coexist with real Play services. */
    public static final String REVANCED_GMS = "app.revanced.android.gms";

    private Qvk3MicroG() {
    }

    private static boolean has(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when ReVanced GmsCore (app.revanced.android.gms) is installed. */
    public static boolean revanced(Context c) {
        return c != null && has(c, REVANCED_GMS);
    }

    /**
     * Which GmsCore package QVK's push (FCM) path should talk to. Called from the patched FCM Rpc (xsna.ygx).
     *
     * <p>Safety: this returns the real {@code com.google.android.gms} whenever it exists — so devices with real
     * Google Play services or a standard microG (installed as com.google.android.gms) are completely unaffected.
     * Only when there is no com.google.android.gms at all and ReVanced GmsCore is present do we redirect FCM
     * registration to {@code app.revanced.android.gms}. With the SPOOFED_PACKAGE_* meta-data in the manifest,
     * ReVanced GmsCore then registers the token under VK's real package and certificate.</p>
     */
    public static String gmsPackage(Context c) {
        try {
            if (c != null && !has(c, GMS) && has(c, REVANCED_GMS)) {
                return REVANCED_GMS;
            }
        } catch (Throwable ignored) {
            // fall back to the standard package
        }
        return GMS;
    }

    /** True when {@code com.google.android.gms} on this device is provided by microG, not Google Play services. */
    public static boolean installed(Context c) {
        PackageManager pm = c.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(GMS, 0);
            // microG's GmsCore ships a settings activity in the GMS package that stock Play services never has.
            try {
                if (pm.getActivityInfo(new ComponentName(GMS, "org.microg.gms.ui.SettingsActivity"), 0) != null) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // not microG's component — keep checking
            }
            try {
                pm.getPackageInfo("org.microg.gms.droidguard", 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
                // no standalone DroidGuard helper
            }
            ApplicationInfo ai = pi.applicationInfo;
            CharSequence label = ai == null ? null : pm.getApplicationLabel(ai);
            return label != null && label.toString().toLowerCase(Locale.ROOT).contains("microg");
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** microG version string, or empty if microG is not the gms provider. */
    public static String version(Context c) {
        if (!installed(c)) return "";
        try {
            return c.getPackageManager().getPackageInfo(GMS, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * Best-effort read of whether microG's Cloud Messaging (GCM/MCS) is switched on. microG exposes a settings
     * provider, but it is permission-guarded, so this may legitimately return {@code null} (unknown) — in that
     * case we rely on the live token check instead of guessing.
     */
    public static Boolean cloudMessagingEnabled(Context c) {
        Uri uri = Uri.parse("content://org.microg.gms.settings/gcm");
        try (android.database.Cursor cur = c.getContentResolver().query(
                uri, new String[]{"enable_gcm", "gcm_enable_mcs"}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                for (int i = 0; i < cur.getColumnCount(); i++) {
                    if (!cur.isNull(i)) return cur.getInt(i) != 0;
                }
            }
        } catch (Throwable ignored) {
            // provider missing or protected — unknown
        }
        return null;
    }

    /** Intent that opens microG's settings (Cloud Messaging / registration live there). Never null. */
    public static Intent settingsIntent(Context c) {
        PackageManager pm = c.getPackageManager();
        String[] candidates = {
                "org.microg.gms.ui.SettingsActivity",
                "org.microg.gms.ui.settings.SettingsActivity",
                "org.microg.gms.ui.PushNotificationFragment",
        };
        for (String name : candidates) {
            ComponentName cn = new ComponentName(GMS, name);
            try {
                if (pm.getActivityInfo(cn, 0) != null) {
                    Intent i = new Intent();
                    i.setComponent(cn);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    return i;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // try next
            }
        }
        // Fallback: the microG app's launcher, else its system App Info page.
        Intent launch = pm.getLaunchIntentForPackage(GMS);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return launch;
        }
        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + GMS));
        info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return info;
    }

    /** Result of a live microG push check. */
    public static final class Result {
        /** true = a real FCM token was issued through microG. */
        public final boolean token;
        /** Human-readable Russian summary. */
        public final String text;

        Result(boolean token, String text) {
            this.token = token;
            this.text = text;
        }
    }

    /**
     * Run a real FCM registration through microG and report what happened. Blocking — call off the main thread
     * (e.g. from {@link Qvk3Jobs#io}).
     */
    public static Result check(Context c) {
        if (!installed(c)) {
            if (revanced(c)) {
                // ReVanced GmsCore lives under its own package; QVK's FCM path is now redirected to it
                // (patch 0031) when no real com.google.android.gms exists, with SPOOFED_PACKAGE_* in the manifest.
                boolean ok;
                Throwable err = null;
                try {
                    PushSupport.changedVendor();
                } catch (Throwable ignored) {
                }
                try {
                    ok = PushSupport.nativeToken();
                } catch (Throwable t) {
                    ok = false;
                    err = t;
                }
                StringBuilder s = new StringBuilder("Обнаружен ReVanced GmsCore (app.revanced.android.gms).\n"
                        + "QVK направляет регистрацию push в него, а в манифесте есть подмена имени и подписи на ВК.\n\n");
                if (ok) {
                    s.append("✓ Токен FCM получен через ReVanced GmsCore. Доставку подтвердит только реальное входящее "
                            + "сообщение с другого аккаунта.");
                } else {
                    s.append("✗ Токен FCM пока не получен.");
                    if (err != null) {
                        try {
                            s.append('\n').append(PushSupport.errorLabel(err));
                        } catch (Throwable t2) {
                            s.append('\n').append(Qvk3Jobs.message(err));
                        }
                    }
                    s.append("\n\nПроверьте в ReVanced GmsCore: выполнена регистрация устройства (checkin) и включён "
                            + "Cloud Messaging; снимите ограничения батареи с GmsCore и QVK.");
                }
                return new Result(ok, s.toString());
            }
            return new Result(false, "microG не найден: за com.google.android.gms отвечают настоящие сервисы Google "
                    + "или они не установлены. Этот путь — для сборок microG и ReVanced GmsCore.");
        }
        // Force the vendor cache to re-evaluate, then request a native (FCM) token through microG.
        boolean ok;
        Throwable err = null;
        try {
            PushSupport.changedVendor();
        } catch (Throwable ignored) {
            // cache reset is best-effort
        }
        try {
            ok = PushSupport.nativeToken();
        } catch (Throwable t) {
            ok = false;
            err = t;
        }
        StringBuilder s = new StringBuilder();
        s.append("microG GmsCore ").append(version(c)).append('\n');
        Boolean cm = cloudMessagingEnabled(c);
        if (cm != null) {
            s.append("Cloud Messaging в microG: ").append(cm ? "включён" : "выключен — включите его").append('\n');
        }
        if (ok) {
            s.append("\n✓ Токен FCM получен через microG. Значит microG отдаёт регистрацию для подписи этой "
                    + "сборки QVK. Осталось, чтобы сервер ВК принял токен — это подтвердит только настоящее "
                    + "входящее сообщение с другого аккаунта.");
        } else {
            s.append("\n✗ Токен FCM через microG не получен.");
            if (err != null) {
                String label;
                try {
                    label = PushSupport.errorLabel(err);
                } catch (Throwable t2) {
                    label = Qvk3Jobs.message(err);
                }
                s.append('\n').append(label);
            }
            s.append("\n\nЧто проверить в microG:\n")
                    .append("• «Cloud Messaging» (Google Cloud Messaging) — включён;\n")
                    .append("• «Registration» / «Device registration» (checkin) — выполнена, статус зелёный;\n")
                    .append("• вошедший Google-аккаунт не обязателен, но ускоряет регистрацию;\n")
                    .append("• снимите ограничения батареи с microG и с QVK, иначе сокет MCS засыпает.");
        }
        if (Build.VERSION.SDK_INT >= 23) {
            s.append("\n\nПодмена подписи приложению QVK не требуется: microG регистрирует токен под фактическим "
                    + "пакетом (" + c.getPackageName() + ") и сертификатом сборки.");
        }
        return new Result(ok, s.toString());
    }

    /** Try to open microG settings; returns false if nothing could be launched. */
    public static boolean openSettings(Activity a) {
        try {
            a.startActivity(settingsIntent(a));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
