package re.sova.qmods;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** QVK 3.1: the alarm of scheduled messages; also re-arms it after a reboot (alarms do not survive one). */
public final class Qvk3ImScheduledReceiver extends BroadcastReceiver {
    static final String ACTION = "ru.qmods.qvk.SEND_SCHEDULED";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        String a = intent == null ? null : intent.getAction();
        if (!ACTION.equals(a)) {   // BOOT_COMPLETED / MY_PACKAGE_REPLACED
            Qvk3ImScheduled.arm(c);
            return;
        }
        final PendingResult done = goAsync();
        new Thread(() -> {
            try {
                Qvk3ImScheduled.sendDue(c);
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.im.scheduled.send", t);
            } finally {
                done.finish();
            }
        }, "qvk3-scheduled").start();
    }
}
