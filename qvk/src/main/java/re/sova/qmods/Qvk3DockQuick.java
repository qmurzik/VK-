package re.sova.qmods;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

/**
 * IDEA-0012: long press on a tab of VK's bottom bar → «Настроить нижнее меню» opens the QVK dock editor directly
 * (a strip of the dock's icons: drag to reorder, «Добавить вкладку», move/remove), instead of VK's own list editor.
 * Called from VK's BottomActionPopupDelegate (patch 0021); returning false keeps VK's editor.
 */
public final class Qvk3DockQuick {
    private Qvk3DockQuick() {
    }

    public static boolean open(Context c) {
        try {
            while (c instanceof ContextWrapper && !(c instanceof Activity)) c = ((ContextWrapper) c).getBaseContext();
            if (!(c instanceof Activity)) return false;
            SettingsEditorActivity.open((Activity) c, "dock");
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.dockQuick", t);
            return false;
        }
    }
}
