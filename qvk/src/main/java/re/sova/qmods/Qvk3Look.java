package re.sova.qmods;

/**
 * One look for every QVK settings screen. QVK 2.x screens are built by MenuUi / SettingsScaffold, which switch to
 * rounded cards on the page background, centred iOS-style headers and iOS typography only when VK-wide iOS mode is
 * on. Patch 0010 routes their IosAppearance.part(...) checks here: inside settings screens these three parts are
 * always on (the stock VK screens are not affected, no other class asks), so all ~70 older screens look like the QVK
 * 3.0 centre. Switch off with qvk3_unified_look = false.
 */
public final class Qvk3Look {
    public static final String KEY = "qvk3_unified_look";
    /**
     * True while a QVK settings screen is on top. MenuUi is also used by QVK 2.x inside VK's own screens (the side
     * drawer), where forcing cards inset the whole menu away from the screen edge (BUG-0023); there the original
     * IosAppearance answer applies. Kept up to date from the activity callbacks in Qvk3Skin.
     */
    static volatile boolean settingsOnTop;

    public static boolean part(String name) {
        try {
            if (settingsOnTop && ("cards".equals(name) || "headers".equals(name) || "typography".equals(name))
                    && RuntimeBridge.prefs != null && RuntimeBridge.prefs.getBoolean(KEY, true)) return true;
        } catch (Throwable ignored) {
            // fall through to the original check
        }
        // BUG-0016: the QVK 2.x appearance ("style") layer no longer touches VK screens — only QVK's own
        // settings screens keep the unified card look. On a stock VK screen no part is ever forced on.
        return settingsOnTop && IosAppearance.part(name);
    }
}
