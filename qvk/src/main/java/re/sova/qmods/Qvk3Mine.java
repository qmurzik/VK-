package re.sova.qmods;

import android.content.Context;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * «Моя музыка» toggle shared by the player's «+» button and the lock-screen / notification media controls
 * (BUG-0030, BUG-0067, BUG-0072, IDEA-0029). One place keeps the copy id and the "request in flight" guard, so a
 * quick double tap — in the player or on the lock screen — never adds the same track twice.
 */
final class Qvk3Mine {
    static final String MINE = "qvk3_mine_copy_";
    private static final Set<String> BUSY = Collections.synchronizedSet(new HashSet<>());

    interface Done {
        /** Main thread. {@code mine} — the state after the request; {@code message} — what to tell the user. */
        void done(boolean mine, String message);
    }

    private Qvk3Mine() {
    }

    /** "ownerId_audioId" of this track's copy in «Моя музыка», or "" when it is not there (as far as QVK knows). */
    static String copy(Context c, Qvk3Track t) {
        if (t == null || t.id == 0) return "";
        long me = Qvk3MusicSource.account();
        if (me > 0 && t.ownerId == me) return me + "_" + t.id;
        return Qvk3MusicSettings.prefs(c).getString(MINE + t.key(), "");
    }

    static boolean mine(Context c, Qvk3Track t) {
        return !copy(c, t).isEmpty();
    }

    static boolean busy(Qvk3Track t) {
        return t != null && BUSY.contains(t.key());
    }

    /** Adds or removes the track. Returns false (and does nothing) while a request for this track is still running. */
    static boolean toggle(Context ctx, Qvk3Track t, Done done) {
        if (t == null) return false;
        final Context c = ctx.getApplicationContext();
        final String key = t.key();
        if (!BUSY.add(key)) return false;
        final String copy = copy(c, t);
        Qvk3Jobs.io(() -> {
            String m;
            boolean nowMine = !copy.isEmpty();
            try {
                if (copy.isEmpty()) {
                    String id = Qvk3MusicSource.addToMine(t);
                    Qvk3MusicSettings.prefs(c).edit().putString(MINE + key, id).apply();
                    m = "Добавлено в «Мою музыку»: " + t.title;
                    nowMine = true;
                } else {
                    Qvk3MusicSource.removeFromMine(copy);
                    Qvk3MusicSettings.prefs(c).edit().remove(MINE + key).apply();
                    m = "Удалено из «Моей музыки»: " + t.title;
                    nowMine = false;
                }
            } catch (Exception e) {
                m = Qvk3Jobs.message(e);
            }
            final boolean mine = nowMine;
            final String msg = m;
            Qvk3Jobs.main(() -> {
                BUSY.remove(key);
                if (done != null) done.done(mine, msg);
            });
        });
        return true;
    }
}
