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
                Context host = context instanceof android.app.Activity ? context : ProfileActions.activity(context);
                if (host == null) host = activity();
                HOSTS.put(dialog, new Host(host != null ? host : context));
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
            Mark mark = (Mark) action;
            if (mark.a == HIDE_ID) {   // BUG-0077: hide directly and redraw VK's list right away
                hide(host == null ? null : host.e, dialog);
                return true;
            }
            if (host != null) DialogActions.click(host, dialog, mark.item);
        } catch (Throwable t) {
            RuntimeFaults.record("dialog.menu.vk", t);
        }
        return true;
    }

    private static final int HIDE_ID = 0x71503601;

    private static void hide(Context c, Object dialog) throws Exception {
        long peer = DialogActions.peer(dialog);
        if (peer == 0) return;
        boolean wasHidden = HiddenDialogs.contains(peer);
        HiddenDialogs.toggle(peer);
        redraw();
        Context ctx = c != null ? c : activity();
        if (ctx != null) Qvk3Ui.toast(ctx, wasHidden ? "Диалог снова виден"
                : "Диалог скрыт. Показать скрытые: удерживайте заголовок «Чаты» или «Сообщения»");
    }

    /** Re-submits every VK chat list's last full list so the hidden filter applies now, not on the next update. */
    @SuppressWarnings("unchecked")
    static void redraw() {
        try {
            Field f = HiddenDialogs.class.getDeclaredField("adapters");
            f.setAccessible(true);
            Map<Object, Object> map = (Map<Object, Object>) f.get(null);
            List<Map.Entry<Object, Object>> all;
            synchronized (map) {
                all = new ArrayList<>(map.entrySet());
            }
            for (Map.Entry<Object, Object> e : all) {
                Object adapter = e.getKey(), snap = e.getValue();
                if (adapter == null || snap == null) continue;
                Field orig = snap.getClass().getDeclaredField("original");
                orig.setAccessible(true);
                Object list = orig.get(snap);
                if (!(list instanceof List)) continue;
                java.lang.reflect.Method set = adapter.getClass().getMethod("setItems", List.class);
                Runnable r = () -> {
                    try {
                        set.invoke(adapter, new ArrayList<>((List<?>) list));
                    } catch (Throwable t) {
                        RuntimeFaults.record("dialog.menu.vk.redraw", t);
                    }
                };
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) r.run();
                else new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
            }
        } catch (Throwable t) {
            RuntimeFaults.record("dialog.menu.vk.redraw", t);
        }
    }

    static android.app.Activity activity() {
        java.lang.ref.WeakReference<android.app.Activity> w = RuntimeBridge.lastNative;
        return w == null ? null : w.get();
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
