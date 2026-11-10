package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * "Недавно изменённые" + "Отменить последнее изменение" for the QVK settings centre.
 * Each entry stores the key, its previous value (or absence) and a human title. Undo writes the previous
 * value back through the same preference file, so it works for any setting, including QVK 2.x ones.
 */
public final class Qvk3SettingsHistory {
    static final String KEY = "qvk3_settings_history";
    static final int LIMIT = 50;

    public static final class Entry {
        public final String key;
        public final String title;
        public final long time;
        final Object previous; // null = key was absent
        final boolean hasPrevious;

        Entry(String key, String title, long time, Object previous, boolean hasPrevious) {
            this.key = key;
            this.title = title;
            this.time = time;
            this.previous = previous;
            this.hasPrevious = hasPrevious;
        }
    }

    static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }

    /** Records a change of {@code key} BEFORE it is written (captures the previous value). */
    public static synchronized void before(Context c, String key, String title) {
        SharedPreferences p = prefs(c);
        Object prev = p.getAll().get(key);
        push(p, key, title, prev, true);
    }

    /** Records an action that is undone by other means (e.g. appearance has its own backup). */
    public static synchronized void record(Context c, String key, String title) {
        push(prefs(c), key, title, null, false);
    }

    private static void push(SharedPreferences p, String key, String title, Object prev, boolean undoable) {
        try {
            JSONArray old = new JSONArray(p.getString(KEY, "[]"));
            JSONArray a = new JSONArray();
            JSONObject e = new JSONObject().put("key", key).put("title", title).put("time", System.currentTimeMillis())
                    .put("undo", undoable);
            if (undoable) {
                e.put("prev", prev == null ? JSONObject.NULL : prev);
                if (prev != null) e.put("type", prev instanceof Boolean ? "b" : prev instanceof Integer ? "i"
                        : prev instanceof Long ? "l" : prev instanceof Float ? "f" : "s");
            }
            a.put(e);
            for (int i = 0; i < old.length() && a.length() < LIMIT; i++) a.put(old.get(i));
            p.edit().putString(KEY, a.toString()).apply();
        } catch (Exception ex) {
            RuntimeFaults.record("qvk3.settings.history", ex);
        }
    }

    public static synchronized List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.getJSONObject(i);
                out.add(new Entry(e.optString("key"), e.optString("title"), e.optLong("time"),
                        e.isNull("prev") ? null : e.opt("prev"), e.optBoolean("undo")));
            }
        } catch (Exception ignored) {
            // corrupted history is simply shown empty
        }
        return out;
    }

    /** Undoes the most recent undoable change; returns its title or null. */
    public static synchronized String undoLast(Context c) {
        SharedPreferences p = prefs(c);
        try {
            JSONArray a = new JSONArray(p.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.getJSONObject(i);
                if (!e.optBoolean("undo")) continue;
                SharedPreferences.Editor ed = p.edit();
                String k = e.getString("key");
                if (e.isNull("prev")) ed.remove(k);
                else switch (e.optString("type")) {
                    case "b":
                        ed.putBoolean(k, e.getBoolean("prev"));
                        break;
                    case "i":
                        ed.putInt(k, e.getInt("prev"));
                        break;
                    case "l":
                        ed.putLong(k, e.getLong("prev"));
                        break;
                    case "f":
                        ed.putFloat(k, (float) e.getDouble("prev"));
                        break;
                    default:
                        ed.putString(k, e.getString("prev"));
                }
                JSONArray rest = new JSONArray();
                for (int j = 0; j < a.length(); j++) if (j != i) rest.put(a.get(j));
                ed.putString(KEY, rest.toString()).apply();
                return e.optString("title");
            }
        } catch (Exception ex) {
            RuntimeFaults.record("qvk3.settings.undo", ex);
        }
        return null;
    }
}
