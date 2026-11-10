package re.sova.qmods;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** PackageInstaller status callback for QVK 3.0 updates (not exported; addressed by explicit PendingIntent). */
public final class Qvk3UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Qvk3Updates.ACTION_INSTALL.equals(intent.getAction())) Qvk3Updates.Receiver.handle(context, intent);
    }
}
