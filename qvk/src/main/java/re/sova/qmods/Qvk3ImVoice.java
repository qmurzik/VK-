package re.sova.qmods;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/** Plays voice messages one at a time; QVK Music pauses meanwhile. The waveform shows the progress. */
final class Qvk3ImVoice {
    private static MediaPlayer player;
    private static String url;
    private static Qvk3ImBubble.Wave view;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            MediaPlayer p = player;
            if (p == null || view == null) return;
            try {
                int d = p.getDuration();
                view.setProgress(d > 0 ? p.getCurrentPosition() / (float) d : 0, p.isPlaying());
            } catch (IllegalStateException ignored) {
            }
            h.postDelayed(this, 100);
        }
    };

    private Qvk3ImVoice() {
    }

    static void toggle(Context c, String link, Qvk3ImBubble.Wave v) {
        if (link == null || link.isEmpty()) return;
        if (player != null && link.equals(url)) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                    v.setProgress(v.progress, false);
                } else {
                    player.start();
                    h.post(tick);
                }
                view = v;
                return;
            } catch (IllegalStateException ignored) {
            }
        }
        stop();
        try {
            Qvk3MusicEngine e = Qvk3MusicEngine.peek();
            if (e != null && e.isPlaying()) e.pause();
        } catch (Throwable ignored) {
        }
        url = link;
        view = v;
        MediaPlayer p = new MediaPlayer();
        player = p;
        try {
            p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            p.setDataSource(link);
            p.setOnPreparedListener(mp -> {
                if (mp != player) return;
                mp.start();
                h.post(tick);
            });
            p.setOnCompletionListener(mp -> {
                if (view != null) view.setProgress(0, false);
                stop();
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Qvk3Ui.toast(c, "Не удалось воспроизвести голосовое");
                stop();
                return true;
            });
            v.setProgress(0, true);
            p.prepareAsync();
        } catch (Exception e) {
            Qvk3Ui.toast(c, "Не удалось воспроизвести голосовое");
            stop();
        }
    }

    static void stop() {
        h.removeCallbacks(tick);
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
        }
        if (view != null) view.setProgress(0, false);
        player = null;
        url = null;
        view = null;
    }
}
