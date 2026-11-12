package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;

/** Unsent text per chat: kept when the chat is left and restored when it is opened again (only on this phone). */
final class Qvk3ImDrafts {
    private Qvk3ImDrafts() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("qvk3_im_drafts", Context.MODE_PRIVATE);
    }

    static String get(Context c, long peer) {
        return prefs(c).getString(Long.toString(peer), "");
    }

    static void save(Context c, long peer, String text) {
        String t = text == null ? "" : text;
        if (t.trim().isEmpty()) prefs(c).edit().remove(Long.toString(peer)).apply();
        else prefs(c).edit().putString(Long.toString(peer), t.length() > 4000 ? t.substring(0, 4000) : t).apply();
    }
}
