package re.sova.qmods;

import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * QVK actions («Скрыть диалог», «Переименовать локально», архив, папки…) in the long-press menu of VK's chat list.
 *
 * <p>VK 8.100 has two chat lists. QVK 2 hooked only the older one ({@code DialogsListVc}); the newer
 * {@code DialogsListViewController} — the one VK shows today — builds its menu in
 * {@code DialogsListActionsUIController.r0()} as a {@code Popup$b0}: a fixed table of entries ({@code pf60}, field
 * {@code h}) filtered by the actions VK allows for this chat (field {@code j}). We append our own entries to both
 * lists; their payload is a private {@link Mark} so VK's click handler never sees an action it does not know —
 * {@link #click} takes them first and runs them through QVK 2's {@link DialogActions}.</p>
 */
public final class Qvk3VkDialogMenu {
    private Qvk3VkDialogMenu() {
    }

    /** Payload of our popup entries: carries QVK 2's action item. */
    static final class Mark extends xsna.a3i {
        final Object item;

        Mark(int id, Object item) {
            super(id);
            this.item = item;
        }
    }

    /** {@link DialogActions#click} reads the caller's Context from a field named {@code e}. */
    static final class Host {
        public final Context e;

        Host(Context e) {
            this.e = e;
        }
    }

    private static final Map<Object, Host> HOSTS = new WeakHashMap<>();

    /** Called right after VK built the popup (patch 0038). Never throws: VK's own menu must always open. */
    public static void extend(Object popup, Context context, Object dialog) {
        if (popup == null || context == null || dialog == null) return;
        try {
            List<?> ours = DialogActions.items(context, dialog, new ArrayList<>());
            if (ours == null || ours.isEmpty()) return;
            Class<?> o = Class.forName("com.vk.im.ui.components.viewcontrollers.popup.Popup$o");
            Field fh = o.getDeclaredField("h");
            Field fj = o.getDeclaredField("j");
            fh.setAccessible(true);
            fj.setAccessible(true);
            List<Object> entries = new ArrayList<>((List<?>) fh.get(popup));
            List<Object> allowed = new ArrayList<>((List<?>) fj.get(popup));
            Integer tint = tint(entries);
            Class<?> pf = Class.forName("xsna.pf60");
            Constructor<?> make = pf.getDeclaredConstructor(CharSequence.class, int.class, android.graphics.drawable.Drawable.class,
                    Integer.class, int.class, Object.class, boolean.class, boolean.class, Class.forName("xsna.t7f0"),
                    boolean.class, int.class, Class.forName("xsna.y9h"));
            make.setAccessible(true);
            for (Object item : ours) {
                int id = (Integer) item.getClass().getMethod("a").invoke(item);
                int icon = (Integer) item.getClass().getMethod("b").invoke(item);
                String title = (String) item.getClass().getMethod("c").invoke(item);
                Mark mark = new Mark(id, item);
                // mask 0x3c0: VK's defaults for the flags and the badge, our title/icon/tint/payload
                entries.add(make.newInstance(title, 0, null, tint, icon, mark, false, false, null, false, 0x3c0, null));
                allowed.add(mark);
            }
            fh.set(popup, entries);
            fj.set(popup, allowed);
            synchronized (HOSTS) {
                HOSTS.put(dialog, new Host(context));
            }
        } catch (Throwable t) {
            RuntimeFaults.record("dialog.menu.vk", t);
        }
    }

    /** First look at a click (patch 0038): true when it was one of ours and is handled. */
    public static boolean click(Object action, Object dialog) {
        if (!(action instanceof Mark)) return false;
        try {
            Host host;
            synchronized (HOSTS) {
                host = HOSTS.get(dialog);
            }
            if (host != null) DialogActions.click(host, dialog, ((Mark) action).item);
        } catch (Throwable t) {
            RuntimeFaults.record("dialog.menu.vk", t);
        }
        return true;
    }

    /** VK's own entries carry the tint the popup was built with; reuse it so our icons match. */
    private static Integer tint(List<Object> entries) {
        for (Object e : entries) {
            try {
                Object d = e.getClass().getMethod("d").invoke(e);
                if (d instanceof Integer) return (Integer) d;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
