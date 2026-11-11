package re.sova.qmods;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * QVK 3.0 start-up hook. Declared as a ContentProvider so it needs no change to VK's Application class:
 * providers are created right before Application.onCreate(), and the real work is posted to the main
 * looper so it runs after VK and QVK 2.x have finished their own initialisation.
 *
 * Runs once per process (guarded), only in the main process, and does only cheap work on the main thread;
 * disk/network work goes to background executors. Any failure is recorded and never crashes VK.
 */
public final class Qvk3Runtime extends ContentProvider {
    private static volatile boolean started;

    @Override
    public boolean onCreate() {
        Context c = getContext();
        if (c == null) return true;
        new Handler(Looper.getMainLooper()).post(() -> start(c.getApplicationContext()));
        return true;
    }

    static synchronized void start(Context c) {
        if (started) return;
        started = true;
        try {
            String proc = android.app.Application.getProcessName();
            if (proc != null && !proc.equals(c.getPackageName())) return; // services/guest processes: nothing to do
            Qvk3Logs.start(c);
            Qvk3Migration.run(c);
            shortcuts(c);
            if (c instanceof android.app.Application) Qvk3MiniBar.install((android.app.Application) c);
            if (c instanceof android.app.Application) Qvk3FeedNow.install((android.app.Application) c);
            if (c instanceof android.app.Application) Qvk3UpdatePrompt.install((android.app.Application) c);
            if (c instanceof android.app.Application) Qvk3PortalWatch.install((android.app.Application) c);
            if (c instanceof android.app.Application) Qvk3Shell.install((android.app.Application) c);
            if (c instanceof android.app.Application) Qvk3Skin.install((android.app.Application) c);
            Qvk3Jobs.io(() -> Qvk3Updates.cleanup(c));
            Qvk3Jobs.io(() -> Qvk3Growth.sample(c));
            Qvk3Jobs.io(() -> slowDownQmodsJob(c));
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.runtime.start", t);
        }
    }

    /**
     * QVK 2.x scheduled its announcements job (QModsSupport, id 81949) every 15 minutes with network. Patch 0011 makes
     * new schedules 6-hourly, but an already persisted job keeps its old period: replace it once.
     */
    static void slowDownQmodsJob(Context c) {
        try {
            android.app.job.JobScheduler js = c.getSystemService(android.app.job.JobScheduler.class);
            android.app.job.JobInfo j = js == null ? null : js.getPendingJob(81949);
            if (j != null && j.isPeriodic() && j.getIntervalMillis() < 6 * 3_600_000L) {
                js.cancel(81949);
                QModsSupport.schedule(c);
            }
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.runtime.jobs", t);
        }
    }

    /**
     * Launcher shortcuts to QVK Music and the QVK settings centre. They keep a way into the settings even if
     * the user hides every menu entry (a requirement of the navigation builder).
     */
    static void shortcuts(Context c) {
        ShortcutManager sm = c.getSystemService(ShortcutManager.class);
        if (sm == null) return;
        List<ShortcutInfo> list = new ArrayList<>();
        list.add(new ShortcutInfo.Builder(c, "qvk3_settings").setShortLabel("Настройки QVK").setLongLabel("Центр настроек QVK")
                .setIcon(Icon.createWithResource(c, android.R.drawable.ic_menu_preferences))
                .setIntent(new Intent(c, Qvk3SettingsActivity.class).setAction(Intent.ACTION_VIEW)).build());
        list.add(new ShortcutInfo.Builder(c, "qvk3_shell").setShortLabel("QVK").setLongLabel("Оболочка QVK: чаты и музыка")
                .setIcon(Icon.createWithResource(c, android.R.drawable.sym_action_chat))
                .setIntent(new Intent(c, Qvk3ShellActivity.class).setAction(Intent.ACTION_VIEW)).build());
        list.add(new ShortcutInfo.Builder(c, "qvk3_music").setShortLabel("QVK Music").setLongLabel("Открыть QVK Music")
                .setIcon(Icon.createWithResource(c, android.R.drawable.ic_media_play))
                .setIntent(new Intent(c, Qvk3MusicActivity.class).setAction(Intent.ACTION_VIEW)).build());
        try {
            sm.setDynamicShortcuts(list);
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.runtime.shortcuts", e);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
