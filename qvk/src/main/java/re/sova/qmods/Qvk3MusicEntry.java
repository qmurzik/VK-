package re.sova.qmods;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Compatibility bridge between QVK's navigation and QVK Music.
 *
 * QVK routes every "open music" action (bottom bar, side menu, start page, settings shortcut, deep links
 * handled by RuntimeBridge.navigateChecked) through NativeRoutes.music(); a two-instruction smali patch
 * calls {@link #open} first. When QVK Music is enabled the VK music catalog is not created at all, which
 * also removes the QVK 2.6.x "Мои треки" redirect that sent users back to the home screen.
 */
public final class Qvk3MusicEntry {
    private static long lastOpen;
    /** Until this moment VK's own music screens are not replaced (the "Музыка ВК" tab opens them on purpose). */
    private static long classicUntil;

    private static boolean classicWanted() {
        return SystemClock.elapsedRealtime() < classicUntil;
    }

    /** Opens VK's own music section (its home with джемы and suggested playlists) without QVK taking it over. */
    public static void openClassic(Activity a) {
        classicUntil = SystemClock.elapsedRealtime() + 4000;
        try {
            NativeRoutes.music(a);
        } catch (Throwable t) {
            classicUntil = 0;
            RuntimeFaults.record("qvk3.music.classic", t);
            Qvk3Ui.toast(a, "Музыка ВК не открылась: " + Qvk3Jobs.message(t));
        }
    }

    /**
     * Called (patch 0007) at the start of VK's MusicFragmentNavigator.a(Context) — the single launcher used by
     * VK's own entries (services, profile "Музыка", side menu, links). Only the user's own music library is
     * redirected; podcasts/audiobooks, searches, specific catalog links and other people's music stay in VK.
     */
    public static boolean fromNavigator(Object navigator, Context ctx) {
        try {
            if (navigator == null || ctx == null || !Qvk3MusicSettings.enabled(ctx) || classicWanted()) return false;
            if (navigator.getClass().getField("b").getBoolean(navigator)) return false; // podcasts / audiobooks
            Object builder = navigator.getClass().getField("a").get(navigator);
            android.os.Bundle args = (android.os.Bundle) builder.getClass().getField("W3").get(builder);
            if (args != null) {
                String url = args.getString("key_url");
                android.util.Log.i("QVK3Music", "navigator: keys=" + args.keySet() + (url != null ? " url=" + url : ""));
                if (args.containsKey("query")) return false;
                // BUG-0034: the «Музыка» tile of «Сервисы» opens VK music by its root link — that is the user's own
                // music too; links to a specific playlist / artist / section still open in VK
                if (url != null && !musicRoot(url)) return false;
                Object owner = args.get("owner_id");
                if (owner != null) {
                    long id = ((Number) owner.getClass().getMethod("getValue").invoke(owner)).longValue();
                    if (id != RuntimeBridge.account()) return false;
                }
            }
            if (ctx instanceof Activity) return open((Activity) ctx);
            long now = SystemClock.elapsedRealtime();
            if (now - lastOpen < 700) return true;
            lastOpen = now;
            ctx.startActivity(new Intent(ctx, Qvk3MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.music.navigator", t);
            return false;
        }
    }

    /**
     * Patch 0026, xsna/h2x#p: the «Музыка» item of VK's side menu and of «Сервисы» (MenuItemUiData.AUDIOS) opens
     * MusicCatalogFragment directly, past MusicFragmentNavigator (BUG-0034). With QVK Music on it opens
     * MusicContainerFragment instead, which shows QVK Music inline (patch 0008).
     */
    public static boolean menuToQvk() {
        try {
            Context c = RuntimeBridge.context;
            boolean on = c != null && Qvk3MusicSettings.enabled(c) && !classicWanted();
            android.util.Log.i("QVK3Music", "menu «Музыка» → " + (on ? "QVK Music" : "VK (QVK Music off or «Музыка ВК» asked)"));
            return on;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.music.menu", t);
            return false;
        }
    }

    /** vk.com/audio, /audios123, /music (optionally with ?section=…/my/main): the root of VK music. */
    static boolean musicRoot(String url) {
        String u = url.toLowerCase(java.util.Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^(m\\.|www\\.)", "");
        int q = u.indexOf('?');
        String path = q < 0 ? u : u.substring(0, q), query = q < 0 ? "" : u.substring(q + 1);
        if (!path.matches("(vk\\.(com|ru)|vkontakte\\.ru)/(audio|audios\\d*|music)(/(my|main|recent|playlists))?/?")) return false;
        return query.isEmpty() || query.matches("(section=(all|my|main|recent|recoms|playlists)|ref=[\\w.]*)(&[\\w.]+=[\\w.]*)*");
    }

    private static final java.util.WeakHashMap<Object, Qvk3MusicScreen> tabs = new java.util.WeakHashMap<>();

    /**
     * Patch 0008, MusicContainerFragment.onCreateView(): VK's music tab/tile shows QVK Music inline instead of
     * the VK catalog. Returns null (→ VK builds its own view) when QVK Music is disabled or anything fails.
     */
    public static android.view.View tabView(Object fragment, android.view.LayoutInflater inflater) {
        try {
            Context c = inflater.getContext();
            Activity a = ProfileActions.activity(c);
            if (a == null || !Qvk3MusicSettings.enabled(a) || classicWanted()) return null;
            Qvk3MusicScreen screen = new Qvk3MusicScreen(a, true, -1, false);
            tabs.put(fragment, screen);
            return screen;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.music.tab", t);
            return null;
        }
    }

    /** True when this music tab fragment shows QVK Music (VK must not load its catalog into it). */
    public static boolean embedded(Object fragment) {
        return tabs.containsKey(fragment);
    }

    /** Back press inside the embedded tab: pops QVK Music's inner screens first. */
    public static boolean tabBack(Object fragment) {
        try {
            Qvk3MusicScreen s = tabs.get(fragment);
            return s != null && s.isAttachedToWindow() && s.handleBack();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Patch 0009, AudioFacade.E(): every "play this list" request of VK (feed, messages, profiles, search,
     * playlists) is played by the QVK Music engine instead of VK's player. Returns false → VK plays as before.
     */
    public static boolean play(java.util.List<?> vkTracks, Integer startIndex, Object shuffleMode) {
        try {
            Context c = RuntimeBridge.context;
            if (c == null || vkTracks == null || vkTracks.isEmpty() || !Qvk3MusicSettings.enabled(c)
                    || !Qvk3MusicSettings.prefs(c).getBoolean(Qvk3MusicSettings.TAKE_OVER, true) || Qvk3Kill.off(c, "music_takeover")) return false;
            java.util.List<Qvk3Track> list = new java.util.ArrayList<>();
            int start = startIndex == null ? 0 : startIndex, mapped = 0;
            for (int i = 0; i < vkTracks.size(); i++) {
                Object t = vkTracks.get(i);
                if (t == null) continue;
                Qvk3Track q = Qvk3Track.fromVk(NativeMusic.json(t));
                if (q.id == 0) continue;
                if (i == start) mapped = list.size();
                list.add(q);
            }
            if (list.isEmpty()) return false;
            boolean shuffle = shuffleMode != null && !String.valueOf(shuffleMode).contains("OFF");
            if (shuffle) mapped = (int) (Math.random() * list.size());
            Qvk3MusicEngine e = Qvk3MusicEngine.get(c);
            Qvk3Track cur = e.current(), want = list.get(mapped);
            if (cur != null && cur.key().equals(want.key()) && e.queue().size() == list.size() && e.state() != Qvk3MusicEngine.ERROR) {
                e.toggle(); // tapping the playing track again pauses/resumes, like VK does
                return true;
            }
            e.play(list, mapped);
            if (shuffle) e.setShuffle(true);
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.music.takeover", t);
            return false;
        }
    }

    /** Returns true when QVK Music handled the navigation. Never throws into VK code. */
    public static boolean open(Activity a) {
        try {
            if (a == null || !Qvk3MusicSettings.enabled(a) || classicWanted()) return false;
            long now = SystemClock.elapsedRealtime();
            if (now - lastOpen < 700) return true; // swallow double taps / re-entrant routing
            lastOpen = now;
            if (a instanceof Qvk3MusicActivity) return true;
            a.startActivity(new Intent(a, Qvk3MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            return true;
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.music.entry", t);
            return false; // fall back to the classic VK route
        }
    }
}
