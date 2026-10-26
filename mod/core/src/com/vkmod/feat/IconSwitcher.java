package com.vkmod.feat;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.vkmod.core.ModLog;

import java.util.ArrayList;
import java.util.List;

/** Switches between the launcher icon aliases that ship inside VK. */
public final class IconSwitcher {
    private IconSwitcher() {}

    private static final String PREFIX = "com.vk.android.launcher.icons.IconAlias";

    public static final class Alias {
        public final String name; public final boolean enabled; public final CharSequence label;
        Alias(String n, boolean e, CharSequence l) { name = n; enabled = e; label = l; }
    }

    public static List<Alias> list(Context c) {
        List<Alias> out = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_ACTIVITIES | PackageManager.MATCH_DISABLED_COMPONENTS);
            if (pi.activities == null) return out;
            for (ActivityInfo ai : pi.activities) {
                if (!ai.name.startsWith(PREFIX)) continue;
                int st = pm.getComponentEnabledSetting(new ComponentName(c.getPackageName(), ai.name));
                boolean en = st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && ai.enabled);
                String label = ai.name.substring(ai.name.lastIndexOf('.') + 1);
                out.add(new Alias(ai.name, en, label));
            }
        } catch (Throwable t) { ModLog.w("icon list failed", t); }
        return out;
    }

    /** Enables [name] and disables every other alias. */
    public static boolean select(Context c, String name) {
        try {
            PackageManager pm = c.getPackageManager();
            for (Alias a : list(c)) {
                boolean on = a.name.equals(name);
                pm.setComponentEnabledSetting(new ComponentName(c.getPackageName(), a.name),
                        on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
            return true;
        } catch (Throwable t) { ModLog.w("icon switch failed", t); return false; }
    }
}
