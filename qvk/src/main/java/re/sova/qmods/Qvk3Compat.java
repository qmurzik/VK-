package re.sova.qmods;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

/** Small API-level shims for Android 9–14+. */
public final class Qvk3Compat {
    /** Android 13+ requires an explicit exported flag for runtime receivers. */
    public static void registerReceiver(Context c, BroadcastReceiver r, IntentFilter f, Handler h) {
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, f, null, h, Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(r, f, null, h);
    }

    public static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static int dpi(Context c, float v) {
        return Math.round(dp(c, v));
    }

    public static boolean lowRam(Context c) {
        android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && (am.isLowRamDevice() || am.getMemoryClass() <= 128);
    }
}
