package re.sova.qmods;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

/**
 * Where the QVK messenger plugs into VK 8.100. VK's own fragments ask these methods first (smali patch 0023):
 * the «Мессенджер» tab (ImDialogsFragment / DialogsFragment) and the chat screen (ChatFragment) return QVK's views
 * instead of building theirs, so there is no layer drawn over VK — VK's navigation, back stack, bottom bar and
 * notifications stay. «Открыть в ВК» sets a one-shot bypass and opens VK's own screen.
 */
public final class Qvk3ImEntry {
    static final String PREF = "qvk3_im";
    private static long bypassPeer = Long.MIN_VALUE;
    private static long bypassUntil;

    private Qvk3ImEntry() {
    }

    static boolean enabled(Context c) {
        return Qvk3MusicSettings.prefs(c).getBoolean(PREF, false) && !Qvk3Kill.off(c, "messenger");
    }

    /**
     * VK 8.100 picks the «Мессенджер» tab between the new DialogsScreenFragment (a server experiment) and the classic
     * DialogsFragment (xsna/nrq0#h, patch 0024). Everything QVK adds to the chat list lives in the classic one —
     * «Скрыть чат» and hidden chats, QVK's dialog actions and folders, and the QVK messenger — so QVK keeps the classic
     * list unless it is switched off remotely ("classic_chats"). No Context there: QVK's preferences are loaded by then.
     */
    public static boolean classicTab() {
        try {
            boolean on = !Qvk3Kill.off("classic_chats");
            if (on && !loggedTab) {
                loggedTab = true;
                android.util.Log.i("QVK3Im", "classic chats tab (QVK chat list features)");
            }
            return on;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean loggedTab;

    private static boolean bypass(long peer) {
        if (System.currentTimeMillis() > bypassUntil) return false;
        if (bypassPeer == peer || bypassPeer == Long.MAX_VALUE) {
            bypassUntil = 0;
            return true;
        }
        return false;
    }

    private static Activity activity(Object fragment) {
        try {
            Object a = fragment.getClass().getMethod("getActivity").invoke(fragment);
            return a instanceof Activity ? (Activity) a : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bundle args(Object fragment) {
        try {
            Object b = fragment.getClass().getMethod("getArguments").invoke(fragment);
            return b instanceof Bundle ? (Bundle) b : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** VK's conversation list asks for a view; null = build VK's own. */
    public static View listView(Object fragment, LayoutInflater inflater) {
        try {
            Activity a = activity(fragment);
            if (a == null) {
                android.util.Log.w("QVK3Im", "list: no activity");
                return null;
            }
            if (!enabled(a) || bypass(0)) return null;
            android.util.Log.i("QVK3Im", "list: QVK view in " + fragment.getClass().getSimpleName());
            return new Qvk3ImListView(a);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.im.list", t);
            return null;
        }
    }

    /** VK's chat screen asks for a view; null = build VK's own. */
    public static View chatView(Object fragment, LayoutInflater inflater) {
        try {
            Activity a = activity(fragment);
            if (a == null || !enabled(a)) return null;
            long peer = peer(args(fragment));
            if (peer == 0 || bypass(peer)) return null;
            Qvk3ImChatView v = new Qvk3ImChatView(a, peer, () -> a.onBackPressed());
            v.setTag(fragment);
            return v;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.im.chat", t);
            return null;
        }
    }

    private static Object view(Object fragment) {
        try {
            return fragment.getClass().getMethod("getView").invoke(fragment);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when this VK fragment shows a QVK view: its own view logic must not run (patch guards). */
    public static boolean ours(Object fragment) {
        Object v = view(fragment);
        return v instanceof Qvk3ImListView || v instanceof Qvk3ImChatView;
    }

    /** Back inside a QVK view (closes selection / reply / search first); false lets VK go back. */
    public static boolean back(Object fragment) {
        Object v = view(fragment);
        if (v instanceof Qvk3ImChatView) return ((Qvk3ImChatView) v).back();
        if (v instanceof Qvk3ImListView) return ((Qvk3ImListView) v).back();
        return false;
    }

    /**
     * VK's navigator is about to open a screen (com.vk.navigation.d#t). A chat goes to QVK's chat screen; a chat opened
     * to share something (attachments, forwarded messages, text) stays VK's so nothing the user shares gets lost.
     */
    public static boolean fromNavigator(Object nav, Object host) {
        try {
            Object cls = nav.getClass().getMethod("n").invoke(nav);
            if (!(cls instanceof Class) || !((Class<?>) cls).getName().equals("com.vk.im.ui.fragments.chat.ChatFragment")) return false;
            Context c = null;
            try {
                Object ctx = host.getClass().getMethod("M0").invoke(host);
                if (ctx instanceof Context) c = (Context) ctx;
            } catch (Throwable ignored) {
            }
            Activity a = unwrap(c);
            if (a == null) a = Qvk3Skin.lastVkActivity();
            if (a == null || !enabled(a)) return false;
            Object b = nav.getClass().getMethod("o").invoke(nav);
            Bundle args = b instanceof Bundle ? (Bundle) b : null;
            long peer = peer(args);
            if (peer == 0 || bypass(peer) || sharing(args)) return false;
            Qvk3ImChatActivity.open(a, peer);
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.im.navigator", t);
            return false;
        }
    }

    private static Activity unwrap(Context c) {
        while (c instanceof android.content.ContextWrapper && !(c instanceof Activity)) c = ((android.content.ContextWrapper) c).getBaseContext();
        return c instanceof Activity ? (Activity) c : null;
    }

    /** The chat is opened to share something: arrays / lists of attachments or messages, or a prepared text. */
    static boolean sharing(Bundle b) {
        if (b == null) return false;
        for (String k : b.keySet()) {
            Object v;
            try {
                v = b.get(k);
            } catch (Throwable t) {
                continue;
            }
            if (v instanceof Object[] && ((Object[]) v).length > 0) return true;
            if (v instanceof java.util.Collection && !((java.util.Collection<?>) v).isEmpty()) return true;
            String kl = k.toLowerCase(java.util.Locale.ROOT);
            if (v instanceof String && !((String) v).isEmpty() && (kl.contains("text") || kl.contains("share") || kl.contains("draft"))) return true;
        }
        return false;
    }

    /** The dialog peer VK passes to its chat screen: a com.vk.dto.common.Peer parcelable (id via n()) or a long. */
    static long peer(Bundle b) {
        if (b == null) return 0;
        for (String k : b.keySet()) {
            Object v;
            try {
                v = b.get(k);
            } catch (Throwable t) {
                continue;
            }
            if (v == null) continue;
            if (v.getClass().getName().equals("com.vk.dto.common.Peer")) {
                try {
                    return (Long) v.getClass().getMethod("n").invoke(v);
                } catch (Throwable ignored) {
                }
            }
        }
        for (String k : new String[]{"dialog_id", "peer_id", "peer"}) if (b.containsKey(k)) return b.getLong(k, b.getInt(k, 0));
        return 0;
    }

    /** VK's own screen: the chat (peer ≠ 0) or the messenger (0), once, without QVK taking it over. */
    static void openStock(Activity a, long peer) {
        bypassPeer = peer == 0 ? Long.MAX_VALUE : peer;
        bypassUntil = System.currentTimeMillis() + 15_000;
        String url = peer == 0 ? "https://vk.com/im" : "https://vk.com/im?sel=" + (peer > 2_000_000_000L ? "c" + (peer - 2_000_000_000L) : String.valueOf(peer));
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(a.getPackageName()));
        } catch (Exception e) {
            Qvk3Ui.toast(a, "Не удалось открыть экран ВК");
        }
    }

    static void settings(Activity a) {
        a.startActivity(new Intent(a, Qvk3SettingsActivity.class).putExtra(Qvk3SettingsActivity.EXTRA_ROUTE, "section:2"));
    }
}
