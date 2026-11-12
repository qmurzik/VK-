package re.sova.qmods;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * QVK Music playback engine. Owns its own MediaPlayer instances, audio session, audio focus and queue; it
 * never calls VK's music player.
 *
 * Why the framework MediaPlayer and not Media3: VK 8.100 already ships an R8-obfuscated copy of
 * androidx.media3 under the original package names, so bundling another Media3 would produce duplicate
 * classes; using VK's obfuscated copy would tie QVK Music to VK internals. MediaPlayer plays VK's HLS
 * (including AES-128 segments) and MP3 streams, supports gapless hand-over (setNextMediaPlayer), speed
 * (PlaybackParams) and audio effects on a fixed session id.
 *
 * Threading: every MediaPlayer call runs on the "qvk-music" HandlerThread; listeners are notified on the
 * main thread. Public methods are safe to call from any thread and never block.
 */
public final class Qvk3MusicEngine {
    public interface Listener {
        void onState(Qvk3MusicEngine engine);
    }

    public static final int IDLE = 0, BUFFERING = 1, PLAYING = 2, PAUSED = 3, ERROR = 4;

    private static volatile Qvk3MusicEngine instance;

    public static Qvk3MusicEngine get(Context context) {
        Qvk3MusicEngine e = instance;
        if (e == null) {
            synchronized (Qvk3MusicEngine.class) {
                if (instance == null) instance = new Qvk3MusicEngine(context.getApplicationContext());
                e = instance;
            }
        }
        return e;
    }

    /** Non-creating accessor (used by the compatibility bridge to check whether QVK Music is active). */
    public static Qvk3MusicEngine peek() {
        return instance;
    }

    final Context app;
    final Qvk3Queue queue = new Qvk3Queue();
    private Handler worker; // assigned once in the constructor (non-final only so lambdas below may reference it)
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final int sessionId;
    final SharedPreferences prefs;

    private MediaPlayer player;
    private MediaPlayer nextPlayer;
    private Qvk3Track nextTrack;
    private MediaPlayer fading; // old player during crossfade
    private volatile int state = IDLE;
    private volatile String error = "";
    private volatile long duration;
    private volatile long position;
    private boolean playWhenReady;
    private boolean prepared;
    private boolean nextPrepared;
    private boolean resumeOnFocusGain;
    private boolean ducked;
    private AudioFocusRequest focusRequest;
    private boolean noisyRegistered;
    private int retries;
    private long abStart = -1, abEnd = -1;
    private long sleepAt;
    private boolean sleepAfterTrack;
    private float speed = 1f;
    private long lastSave;
    private Qvk3AudioEffects effects;

    private Qvk3MusicEngine(Context app) {
        this.app = app;
        HandlerThread t = new HandlerThread("qvk-music", android.os.Process.THREAD_PRIORITY_AUDIO);
        t.start();
        worker = new Handler(t.getLooper());
        audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        sessionId = audio.generateAudioSessionId();
        prefs = app.getSharedPreferences("qvk3_music", Context.MODE_PRIVATE);
        speed = prefs.getFloat("speed", 1f);
        worker.post(this::restoreState);
        Qvk3Jobs.main(() -> {
            Qvk3MiniBar.engineCreated(this);
            Qvk3FeedNow.engineCreated(this);
        });
    }

    // ---------------------------------------------------------------- public API (thread-safe, async)

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public int state() {
        return state;
    }

    public boolean isPlaying() {
        return state == PLAYING || state == BUFFERING && playWhenReady;
    }

    public String error() {
        return error;
    }

    public long duration() {
        return duration;
    }

    public long position() {
        return position;
    }

    public int audioSessionId() {
        return sessionId;
    }

    public Qvk3Queue queue() {
        return queue;
    }

    public Qvk3Track current() {
        return queue.current();
    }

    public float speed() {
        return speed;
    }

    public long[] abRange() {
        return new long[]{abStart, abEnd};
    }

    public long sleepRemaining() {
        return sleepAt == 0 ? 0 : Math.max(0, sleepAt - SystemClock.elapsedRealtime());
    }

    public boolean sleepAfterTrack() {
        return sleepAfterTrack;
    }

    public Qvk3AudioEffects effects() {
        if (effects == null) effects = new Qvk3AudioEffects(app, sessionId);
        return effects;
    }

    /** Replaces the queue and starts playing {@code start}. */
    public void play(List<Qvk3Track> tracks, int start) {
        worker.post(() -> {
            queue.set(tracks, start);
            startCurrent(true);
        });
    }

    /** Plays a track that is already in the queue (tap in the queue screen). */
    public void playIndex(int index) {
        worker.post(() -> smoothSwitch(() -> {
            if (queue.select(index) != null) startCurrent(true);
        }));
    }

    public void toggle() {
        worker.post(() -> {
            // a tap during the pause fade-out (still PLAYING, but no longer wanted) means «play on»
            if ((state == PLAYING && playWhenReady) || (state == BUFFERING && playWhenReady)) pauseSmooth();
            else resumeInternal();
        });
    }

    public void pause() {
        worker.post(this::pauseSmooth);
    }

    public void resume() {
        worker.post(this::resumeInternal);
    }

    public void next() {
        worker.post(() -> smoothSwitch(() -> advance(false)));
    }

    /** Previous track, or restart of the current one when more than 3 s were played. */
    public void previous() {
        worker.post(() -> {
            if (player != null && prepared && safePosition() > 3000) {
                seekSmooth(0);
                return;
            }
            smoothSwitch(() -> {
                if (queue.previous() != null) startCurrent(true);
            });
        });
    }

    public void seekTo(long ms) {
        worker.post(() -> {
            if (player != null && prepared) {
                seekSmooth(ms);
                position = ms;
                notifyState();
            }
        });
    }

    public void stop() {
        worker.post(() -> {
            playWhenReady = false;
            releasePlayers();
            abandonFocus();
            state = IDLE;
            saveState(true);
            notifyState();
        });
    }

    public void setShuffle(boolean on) {
        worker.post(() -> {
            queue.setShuffle(on);
            dropNext();
            prepareNextIfNeeded();
            saveState(true);
            notifyState();
        });
    }

    public void setRepeat(int mode) {
        worker.post(() -> {
            queue.setRepeat(mode);
            dropNext();
            prepareNextIfNeeded();
            saveState(true);
            notifyState();
        });
    }

    /** Runs a queue edit on the engine thread (add next, move, remove, undo...) and re-arms gapless. */
    public void editQueue(Runnable edit) {
        worker.post(() -> {
            Qvk3Track before = queue.current();
            edit.run();
            Qvk3Track after = queue.current();
            if (after == null) {
                releasePlayers();
                state = IDLE;
            } else if (before != after && (before == null || !before.key().equals(after.key()))) {
                startCurrent(playWhenReady);
            } else {
                dropNext();
                prepareNextIfNeeded();
            }
            saveState(true);
            notifyState();
        });
    }

    public void setSpeed(float s) {
        worker.post(() -> {
            speed = Math.max(0.5f, Math.min(2f, s));
            prefs.edit().putFloat("speed", speed).apply();
            applySpeed();
            notifyState();
        });
    }

    /** A–B loop. Pass -1/-1 to clear. */
    public void setAb(long a, long b) {
        worker.post(() -> {
            if (a >= 0 && b > a + 500) {
                abStart = a;
                abEnd = b;
            } else {
                abStart = abEnd = -1;
            }
            notifyState();
        });
    }

    /** Sleep timer: {@code minutes} > 0 stops after that time; {@code afterTrack} stops at the end of the track. */
    public void setSleep(int minutes, boolean afterTrack) {
        worker.post(() -> {
            worker.removeCallbacks(sleepTask);
            sleepAfterTrack = afterTrack;
            sleepAt = minutes > 0 ? SystemClock.elapsedRealtime() + minutes * 60_000L : 0;
            if (sleepAt > 0) worker.postAtTime(sleepTask, sleepAt);
            notifyState();
        });
    }

    // ---------------------------------------------------------------- engine thread

    private final Runnable sleepTask = () -> {
        sleepAt = 0;
        pauseInternal(false);
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (player != null && prepared) {
                position = safePosition();
                if (abEnd > 0 && position >= abEnd) {
                    player.seekTo((int) abStart);
                    position = abStart;
                }
                maybeCrossfade();
                saveState(false);
                notifyState();
            }
            // 4 updates a second only while someone can see them; with the screen off once a second is enough
            if (state == PLAYING) worker.postDelayed(this, screenOn() ? 250 : 1000);
        }
    };

    private void startCurrent(boolean play) {
        Qvk3Track t = queue.current();
        releasePlayers();
        retries = 0;
        abStart = abEnd = -1;
        if (t == null) {
            state = IDLE;
            notifyState();
            return;
        }
        playWhenReady = play;
        open(t, false);
    }

    /** Resolves a playable source (local file, or a fresh VK URL) and prepares a player for it. */
    private void open(Qvk3Track t, boolean refreshed) {
        state = BUFFERING;
        error = "";
        duration = t.duration * 1000L;
        position = 0;
        notifyState();
        if (t.local.isEmpty() && (t.url.isEmpty() || refreshed)) {
            // URL missing or rejected: ask VK for a fresh one off the engine thread.
            Qvk3Jobs.io(() -> {
                try {
                    Qvk3Track fresh = Qvk3MusicSource.refresh(t);
                    worker.post(() -> {
                        if (queue.current() != t) return;
                        replaceCurrent(fresh);
                        if (!fresh.playable()) fail(fresh.unplayableReason());
                        else open(fresh, false);
                    });
                } catch (Exception e) {
                    worker.post(() -> {
                        if (queue.current() == t) fail(Qvk3Jobs.message(e));
                    });
                }
            });
            return;
        }
        if (!t.playable()) {
            fail(t.unplayableReason());
            return;
        }
        try {
            player = create(t, this::onPrepared);
            prepared = false;
            player.prepareAsync();
        } catch (Exception e) {
            onPlayerError(t, e.getMessage());
        }
    }

    private MediaPlayer create(Qvk3Track t, MediaPlayer.OnPreparedListener onPrepared) throws Exception {
        MediaPlayer mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setAudioSessionId(sessionId);
        try {
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
        } catch (SecurityException ignored) {
            // WAKE_LOCK missing: playback still works while the foreground service keeps the CPU awake.
        }
        if (!t.local.isEmpty()) {
            if (t.local.startsWith("content:")) mp.setDataSource(app, Uri.parse(t.local));
            else mp.setDataSource(t.local);
        } else {
            mp.setDataSource(app, Uri.parse(t.url), null);
        }
        mp.setOnPreparedListener(onPrepared);
        mp.setOnCompletionListener(this::onCompletion);
        mp.setOnErrorListener((p, what, extra) -> {
            if (p == player) onPlayerError(queue.current(), "код " + what + "/" + extra);
            else if (p == nextPlayer) dropNext();
            return true;
        });
        mp.setOnInfoListener((p, what, extra) -> {
            if (p != player) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                state = BUFFERING;
                notifyState();
            } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END && p.isPlaying()) {
                state = PLAYING;
                notifyState();
            }
            return false;
        });
        return mp;
    }

    private void onPrepared(MediaPlayer mp) {
        if (mp != player) return;
        prepared = true;
        int d = mp.getDuration();
        if (d > 0) duration = d;
        long resumeAt = prefs.getLong("resume_position", 0);
        Qvk3Track cur = queue.current();
        if (resumeAt > 0 && cur != null && cur.key().equals(prefs.getString("resume_key", ""))) {
            mp.seekTo((int) Math.min(resumeAt, Math.max(0, duration - 1000)));
            prefs.edit().remove("resume_position").apply();
        }
        if (playWhenReady) resumeInternal();
        else {
            state = PAUSED;
            notifyState();
        }
        prepareNextIfNeeded();
        Qvk3MusicLibrary.get(app).recordPlay(cur);
    }

    private void onCompletion(MediaPlayer mp) {
        if (mp == fading) {
            releaseQuietly(fading);
            fading = null;
            return;
        }
        if (mp != player) return;
        if (sleepAfterTrack) {
            sleepAfterTrack = false;
            playWhenReady = false;
            state = PAUSED;
            position = 0;
            mp.seekTo(0);
            notifyState();
            return;
        }
        Qvk3Track expected = queue.peekNext();
        Qvk3Track n = queue.next(true);
        if (n == null) {
            state = PAUSED;
            playWhenReady = false;
            position = 0;
            notifyState();
            saveState(true);
            return;
        }
        if (nextPlayer != null && nextPrepared && n == expected && n == nextTrack && crossfadeMs() == 0) {
            // Gapless hand-over: MediaPlayer already started nextPlayer via setNextMediaPlayer.
            releaseQuietly(player);
            player = nextPlayer;
            prepared = true;
            nextPlayer = null;
            nextTrack = null;
            duration = Math.max(0, player.getDuration());
            position = 0;
            state = PLAYING;
            applySpeed();
            Qvk3MusicLibrary.get(app).recordPlay(n);
            prepareNextIfNeeded();
            notifyState();
            worker.removeCallbacks(ticker);
            worker.post(ticker);
        } else {
            startCurrent(true);
        }
    }

    private void onPlayerError(Qvk3Track t, String what) {
        if (t == null) return;
        releasePlayers();
        if (t.local.isEmpty() && retries++ == 0) {
            // Most stream errors are expired VK links: refresh the URL once, then give up on this track.
            open(t, true);
            return;
        }
        fail("Не удалось воспроизвести «" + t.label() + "» (" + what + ")");
    }

    private void fail(String message) {
        error = message == null || message.isEmpty() ? "Ошибка воспроизведения" : message;
        state = ERROR;
        notifyState();
        // Skip a broken track automatically if the user was listening, but never loop over a broken queue.
        if (playWhenReady && queue.peekNext() != null && queue.peekNext() != queue.current() && retries < 3) {
            worker.postDelayed(() -> {
                if (state == ERROR) advance(true);
            }, 1500);
        }
    }

    private void advance(boolean auto) {
        Qvk3Track n = queue.next(auto);
        if (n != null) startCurrent(true);
        else {
            pauseInternal(false);
        }
    }

    private void resumeInternal() {
        playWhenReady = true;
        rampGen++; // cancels a pause fade-out still running
        if (player == null) {
            if (queue.current() != null) startCurrent(true);
            return;
        }
        if (!prepared) return;
        if (!requestFocus()) {
            error = "Другое приложение заняло аудиовыход";
            state = PAUSED;
            notifyState();
            return;
        }
        registerNoisy();
        float target = ducked ? 0.2f : 1f;
        boolean fade = Qvk3MusicSettings.smooth(app) && !player.isPlaying();
        player.setVolume(fade ? 0f : target, fade ? 0f : target);
        player.start();
        if (fade) ramp(player, 0f, target, 300, null);
        applySpeed();
        state = PLAYING;
        Qvk3MusicService.ensureRunning(app);
        worker.removeCallbacks(ticker);
        worker.post(ticker);
        notifyState();
    }

    // ---------------------------------------------------------------- smooth transitions

    private int rampGen;

    /** Volume ramp on the engine thread; a newer ramp cancels an older one. */
    private void ramp(MediaPlayer mp, float from, float to, int ms, Runnable end) {
        final int gen = ++rampGen;
        final long start = SystemClock.elapsedRealtime();
        worker.post(new Runnable() {
            @Override
            public void run() {
                if (gen != rampGen || mp != player) return;
                float f = Math.min(1f, (SystemClock.elapsedRealtime() - start) / (float) Math.max(1, ms));
                float v = from + (to - from) * f * f * (3 - 2 * f); // smoothstep: no audible corners
                try {
                    mp.setVolume(v, v);
                } catch (IllegalStateException e) {
                    return;
                }
                if (f < 1f) worker.postDelayed(this, 16);
                else if (end != null) end.run();
            }
        });
    }

    /** Pause with a short fade-out instead of cutting the sound. */
    private void pauseSmooth() {
        if (!Qvk3MusicSettings.smooth(app) || player == null || !prepared || !safeIsPlaying()) {
            pauseInternal(false);
            return;
        }
        playWhenReady = false;
        final MediaPlayer mp = player;
        ramp(mp, ducked ? 0.2f : 1f, 0f, 220, () -> {
            if (mp == player && !playWhenReady) pauseInternal(false);
        });
    }

    /** Manual track change: the current track fades out first, then the switch runs. */
    private void smoothSwitch(Runnable change) {
        if (!Qvk3MusicSettings.smooth(app) || player == null || !prepared || !safeIsPlaying()) {
            change.run();
            return;
        }
        final MediaPlayer mp = player;
        ramp(mp, ducked ? 0.2f : 1f, 0f, 180, () -> {
            if (mp != player) return;
            change.run();
            // nothing to switch to (start / end of the queue) and still playing: bring the sound back
            if (mp == player && safeIsPlaying()) ramp(mp, 0f, ducked ? 0.2f : 1f, 260, null);
        });
    }

    /** Seek without the click: silence, jump, fade back in. */
    private void seekSmooth(long ms) {
        int to = (int) Math.max(0, Math.min(ms, duration));
        if (!Qvk3MusicSettings.smooth(app) || !safeIsPlaying()) {
            player.seekTo(to);
            return;
        }
        final MediaPlayer mp = player;
        try {
            mp.setVolume(0f, 0f);
        } catch (IllegalStateException ignored) {
        }
        mp.seekTo(to);
        ramp(mp, 0f, ducked ? 0.2f : 1f, 260, null);
    }

    private boolean safeIsPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void pauseInternal(boolean transientLoss) {
        if (!transientLoss) playWhenReady = false;
        if (player != null && prepared && player.isPlaying()) player.pause();
        if (fading != null) {
            releaseQuietly(fading);
            fading = null;
        }
        if (state == PLAYING || state == BUFFERING) state = PAUSED;
        unregisterNoisy();
        if (!transientLoss) abandonFocus();
        position = safePosition();
        saveState(true);
        notifyState();
    }

    private void applySpeed() {
        if (player == null || !prepared) return;
        try {
            // setPlaybackParams() starts a paused MediaPlayer, so it is applied only while playing.
            if (player.isPlaying()) player.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1f));
        } catch (Exception ignored) {
            // Some HLS sources do not support speed changes; playback continues at 1x.
        }
    }

    private int crossfadeMs() {
        return Math.max(0, Math.min(12, Qvk3MusicSettings.crossfadeSeconds(app))) * 1000;
    }

    private void prepareNextIfNeeded() {
        Qvk3Track n = queue.peekNext();
        if (n == null || player == null || n == queue.current() || !n.playable() || !n.local.isEmpty() && !new File(n.local).exists() && !n.local.startsWith("content:")) {
            return;
        }
        if (nextPlayer != null && nextTrack == n) return;
        dropNext();
        if (!Qvk3MusicSettings.gapless(app) && crossfadeMs() == 0) return;
        try {
            nextTrack = n;
            nextPrepared = false;
            nextPlayer = create(n, mp -> {
                if (mp != nextPlayer) return;
                nextPrepared = true;
                if (crossfadeMs() == 0 && player != null) {
                    try {
                        player.setNextMediaPlayer(mp);
                    } catch (Exception e) {
                        dropNext();
                    }
                }
            });
            nextPlayer.prepareAsync();
        } catch (Exception e) {
            dropNext();
        }
    }

    private void maybeCrossfade() {
        int cf = crossfadeMs();
        if (cf == 0 || fading != null || nextPlayer == null || !nextPrepared || duration <= cf * 2L) {
            return;
        }
        long left = duration - position;
        if (left > cf || left <= 0) return;
        Qvk3Track n = queue.next(true);
        if (n != nextTrack) {
            dropNext();
            return;
        }
        fading = player;
        player = nextPlayer;
        nextPlayer = null;
        nextTrack = null;
        prepared = true;
        duration = Math.max(0, player.getDuration());
        position = 0;
        final MediaPlayer out = fading, in = player;
        in.setVolume(0f, 0f);
        in.start();
        applySpeed();
        Qvk3MusicLibrary.get(app).recordPlay(n);
        final long start = SystemClock.elapsedRealtime();
        worker.post(new Runnable() {
            @Override
            public void run() {
                float f = Math.min(1f, (SystemClock.elapsedRealtime() - start) / (float) cf);
                try {
                    in.setVolume(f, f);
                    if (out == fading) out.setVolume(1f - f, 1f - f);
                } catch (IllegalStateException ignored) {
                    return;
                }
                if (f < 1f) worker.postDelayed(this, 50);
                else if (out == fading) {
                    releaseQuietly(fading);
                    fading = null;
                }
            }
        });
        prepareNextIfNeeded();
    }

    private void dropNext() {
        if (player != null) {
            try {
                player.setNextMediaPlayer(null);
            } catch (Exception ignored) {
            }
        }
        releaseQuietly(nextPlayer);
        nextPlayer = null;
        nextTrack = null;
        nextPrepared = false;
    }

    private void replaceCurrent(Qvk3Track fresh) {
        int idx = queue.currentIndex();
        if (idx < 0) return;
        queue.replaceAt(idx, fresh);
    }

    private long safePosition() {
        try {
            return player == null || !prepared ? position : player.getCurrentPosition();
        } catch (IllegalStateException e) {
            return position;
        }
    }

    private void releasePlayers() {
        worker.removeCallbacks(ticker);
        dropNext();
        releaseQuietly(player);
        releaseQuietly(fading);
        player = null;
        fading = null;
        prepared = false;
    }

    private static void releaseQuietly(MediaPlayer mp) {
        if (mp == null) return;
        try {
            mp.reset();
        } catch (Exception ignored) {
        }
        try {
            mp.release();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- focus & noisy

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> this.worker.post(() -> {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnFocusGain = false;
                pauseInternal(false); // another player (e.g. VK) took over permanently
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                resumeOnFocusGain = isPlaying();
                pauseInternal(true); // call, voice message, navigation prompt
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player != null && prepared) {
                    ducked = true;
                    player.setVolume(0.2f, 0.2f);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (ducked && player != null && prepared) {
                    ducked = false;
                    player.setVolume(1f, 1f);
                }
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false;
                    resumeInternal();
                }
                break;
            default:
                break;
        }
    });

    private boolean requestFocus() {
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(focusListener, worker)
                    .setWillPauseWhenDucked(false)
                    .build();
        }
        return audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
    }

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()) && Qvk3MusicSettings.pauseOnUnplug(app)) {
                pause(); // headphones / Bluetooth disconnected
            }
        }
    };

    private void registerNoisy() {
        if (noisyRegistered) return;
        Qvk3Compat.registerReceiver(app, noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), worker);
        noisyRegistered = true;
    }

    private void unregisterNoisy() {
        if (!noisyRegistered) return;
        try {
            app.unregisterReceiver(noisy);
        } catch (Exception ignored) {
        }
        noisyRegistered = false;
    }

    // ---------------------------------------------------------------- persistence

    private File stateFile() {
        return new File(app.getFilesDir(), "qvk3_music/state.json");
    }

    /** Debounced: at most once per 5 s during playback, immediately on pause/stop/edits. */
    private android.os.PowerManager power;

    private boolean screenOn() {
        try {
            if (power == null) power = (android.os.PowerManager) app.getSystemService(Context.POWER_SERVICE);
            return power == null || power.isInteractive();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void saveState(boolean now) {
        long t = SystemClock.elapsedRealtime();
        if (!now && t - lastSave < 15000) return;
        lastSave = t;
        Qvk3Track cur = queue.current();
        prefs.edit().putLong("resume_position", safePosition()).putString("resume_key", cur == null ? "" : cur.key()).apply();
        // Periodic saves keep only the position (cheap). The queue file, which can be megabytes for a whole library,
        // is written with fsync only when the queue or the track changes (saveState(true)), not every few seconds.
        if (!now) return;
        try {
            File f = stateFile();
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(queue.toJson().toString().getBytes("UTF-8"));
                out.getFD().sync();
            }
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.save", e);
        }
    }

    private void restoreState() {
        try {
            File f = stateFile();
            if (!f.isFile() || f.length() > 32L << 20) return;
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            queue.restore(new JSONObject(new String(data, "UTF-8")));
            Qvk3Track cur = queue.current();
            if (cur != null) {
                duration = cur.duration * 1000L;
                position = prefs.getLong("resume_position", 0);
                state = PAUSED;
            }
            notifyState();
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.music.restore", e);
        }
    }

    private void notifyState() {
        main.post(() -> {
            for (Listener l : listeners) {
                try {
                    l.onState(this);
                } catch (RuntimeException e) {
                    RuntimeFaults.record("qvk3.music.listener", e);
                }
            }
        });
    }
}
