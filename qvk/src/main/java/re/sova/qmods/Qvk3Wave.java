package re.sova.qmods;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * QVK 3.1 «Моя волна»: an endless stream. It starts from VK's recommendations for the account mixed with a few of
 * your own tracks, and whenever the queue gets down to its last tracks it asks VK for tracks similar to the one
 * playing (audio.getRecommendations target_audio) and appends the new ones. Playing anything else ends the wave.
 */
final class Qvk3Wave implements Qvk3MusicEngine.Listener {
    private static Qvk3Wave instance;

    private final Context app;
    private final Qvk3MusicEngine engine;
    private final Set<String> seen = new HashSet<>();
    private boolean active, loading;

    private Qvk3Wave(Context c) {
        app = c.getApplicationContext();
        engine = Qvk3MusicEngine.get(app);
    }

    static synchronized Qvk3Wave get(Context c) {
        if (instance == null) instance = new Qvk3Wave(c);
        return instance;
    }

    static boolean playing(Context c) {
        return instance != null && instance.active;
    }

    /** Builds the first batch and starts playing; done(error) on the main thread. */
    interface Done {
        void done(String error);
    }

    void start(Done done) {
        Qvk3Jobs.io(() -> {
            List<Qvk3Track> batch = new ArrayList<>();
            String err = null;
            try {
                batch.addAll(Qvk3MusicSource.recommendations(30));
            } catch (Exception e) {
                err = Qvk3Jobs.message(e);
            }
            // a few of your own tracks keep the wave «yours»
            List<Qvk3Track> mine = new ArrayList<>(Qvk3MusicLibrary.get(app).page("my", 0, 200));
            Collections.shuffle(mine);
            for (int i = 0; i < Math.min(8, mine.size()); i++) batch.add(mine.get(i));
            List<Qvk3Track> out = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            Collections.shuffle(batch);
            for (Qvk3Track t : batch) if (t.playable() && keys.add(t.key())) out.add(t);
            final String fe = out.isEmpty() ? (err == null ? "ВК не прислал рекомендаций" : err) : null;
            Qvk3Jobs.main(() -> {
                if (fe != null) {
                    done.done(fe);
                    return;
                }
                seen.clear();
                seen.addAll(keys);
                active = true;
                engine.removeListener(this);
                engine.addListener(this);
                engine.play(out, 0);
                done.done(null);
            });
        });
    }

    void stop() {
        active = false;
        engine.removeListener(this);
    }

    @Override
    public void onState(Qvk3MusicEngine e) {
        if (!active) return;
        Qvk3Track cur = e.current();
        if (cur == null) return;
        if (!seen.contains(cur.key())) {   // the user played something else — the wave is over
            stop();
            return;
        }
        int left = e.queue().size() - 1 - e.queue().currentIndex();
        if (left > 3 || loading) return;
        loading = true;
        final Qvk3Track seed = cur;
        Qvk3Jobs.io(() -> {
            List<Qvk3Track> more = new ArrayList<>();
            try {
                more.addAll(Qvk3MusicSource.similar(seed, 25));
            } catch (Exception ignored) {
            }
            if (more.size() < 5) {
                try {
                    more.addAll(Qvk3MusicSource.recommendations(25));
                } catch (Exception ignored) {
                }
            }
            Collections.shuffle(more);
            Qvk3Jobs.main(() -> {
                loading = false;
                if (!active) return;
                final List<Qvk3Track> fresh = new ArrayList<>();
                for (Qvk3Track t : more) if (t.playable() && seen.add(t.key())) fresh.add(t);
                if (fresh.isEmpty()) return;
                engine.editQueue(() -> {
                    for (Qvk3Track t : fresh) engine.queue().addLast(t);
                });
            });
        });
    }
}
