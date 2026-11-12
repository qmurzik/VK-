package re.sova.qmods;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * «Новое» marks in the settings centre: what came in the last few builds and has not been opened yet, so testers see
 * what to check. Append to {@link #SINCE} when a settings screen is added (target → versionCode of the first build).
 */
final class Qvk3NewBadge {
    static final String SEEN = "qvk3_new_seen";
    /** How many builds a new item stays marked. */
    static final int WINDOW = 6;

    static final Object[][] SINCE = {
            {"qvk3:weather", 23829L},
            {"qvk3:report", 23830L}, {"qvk3:myTickets", 23830L}, {"qvk3:ideas", 23830L}, {"qvk3:channel", 23830L},
            {"qvk3:settingsCode", 23833L},
            {"qvk3:about", 23835L},     // remote switch-offs are listed there
            {"qvk3:searchTabs", 23836L}, // BUG-0048: rebuilt per-tab editor
            {"qvk3:quick", 23837L},      // IDEA-0023
    };

    private Qvk3NewBadge() {
    }

    static long since(String target) {
        for (Object[] o : SINCE) if (o[0].equals(target)) return (Long) o[1];
        return 0;
    }

    /** Pure rule (unit-free): introduced within the window before the installed build and not opened yet. */
    static boolean fresh(long since, long installed, boolean seen) {
        return since > 0 && !seen && installed >= since && installed - since < WINDOW;
    }

    static boolean isNew(Context c, String target) {
        return fresh(since(target), Qvk3Updates.installedVersion(c), seen(c).contains(target));
    }

    static void markSeen(Context c, String target) {
        if (since(target) == 0) return;
        Set<String> s = seen(c);
        if (s.add(target)) prefs(c).edit().putString(SEEN, String.join(",", s)).apply();
    }

    private static Set<String> seen(Context c) {
        return new HashSet<>(Arrays.asList(prefs(c).getString(SEEN, "").split(",")));
    }

    private static SharedPreferences prefs(Context c) {
        return Qvk3MusicSettings.prefs(c);
    }
}
