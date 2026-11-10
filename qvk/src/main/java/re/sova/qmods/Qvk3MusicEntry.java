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

    /**
     * Called (patch 0007) at the start of VK's MusicFragmentNavigator.a(Context) — the single launcher used by
     * VK's own entries (services, profile "Музыка", side menu, links). Only the user's own music library is
     * redirected; podcasts/audiobooks, searches, specific catalog links and other people's music stay in VK.
     */
    public static boolean fromNavigator(Object navigator, Context ctx) {
        try {
            if (navigator == null || ctx == null || !Qvk3MusicSettings.enabled(ctx)) return false;
            if (navigator.getClass().getField("b").getBoolean(navigator)) return false; // podcasts / audiobooks
            Object builder = navigator.getClass().getField("a").get(navigator);
            android.os.Bundle args = (android.os.Bundle) builder.getClass().getField("W3").get(builder);
            if (args != null) {
                if (args.containsKey("query") || args.containsKey("key_url")) return false;
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

    private static final java.util.WeakHashMap<Object, Qvk3MusicScreen> tabs = new java.util.WeakHashMap<>();

    /**
     * Patch 0008, MusicContainerFragment.onCreateView(): VK's music tab/tile shows QVK Music inline instead of
     * the VK catalog. Returns null (→ VK builds its own view) when QVK Music is disabled or anything fails.
     */
    public static android.view.View tabView(Object fragment, android.view.LayoutInflater inflater) {
        try {
            Context c = inflater.getContext();
            Activity a = ProfileActions.activity(c);
            if (a == null || !Qvk3MusicSettings.enabled(a)) return null;
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
                    || !Qvk3MusicSettings.prefs(c).getBoolean(Qvk3MusicSettings.TAKE_OVER, true)) return false;
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
            if (a == null || !Qvk3MusicSettings.enabled(a)) return false;
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
