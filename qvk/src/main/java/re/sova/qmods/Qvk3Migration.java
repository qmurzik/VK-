package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings/data migration to QVK 3.0. QVK 3.0 keeps every QVK 2.x key and file in place — the new settings
 * centre and QVK Music read the same preference keys (old music keys serve as defaults) — so migration is
 * additive: it records where the user came from and indexes previously exported music. Nothing is deleted.
 */
final class Qvk3Migration {
    static final String DONE = "qvk3_migrated_version";

    static void run(Context c) {
        SharedPreferences p = Qvk3MusicSettings.prefs(c);
        long current = Qvk3Updates.installedVersion(c);
        long done = p.getLong(DONE, 0);
        if (done >= current) return;
        SharedPreferences.Editor e = p.edit();
        if (done == 0) {
            // first start of 3.0 over 2.x: remember the origin for diagnostics and the report
            e.putString("qvk3_migrated_from", p.contains("qmods_seen_8.100-qvk-2.6.18") ? "8.100-qvk-2.6.18" : "чистая установка или ранее 2.6.18");
            e.putLong("qvk3_migrated_at", System.currentTimeMillis());
        }
        // IDEA-0014: the QVK logo is the default launch screen; an illustration the user chose stays
        if (!p.contains("qvk_splash_girl") && !p.contains("qvk_splash_scene")) e.putBoolean("qvk_splash_girl", false);
        e.putLong(DONE, current).apply();
        // index QVK 2.x music exports as local tracks of QVK Music (files are referenced, never moved)
        Qvk3Jobs.io(() -> {
            try {
                Qvk3MusicDownloads.importExisting(c);
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.migration.music", t);
            }
        });
    }
}
