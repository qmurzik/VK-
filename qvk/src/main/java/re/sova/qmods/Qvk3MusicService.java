package re.sova.qmods;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;

/**
 * Background service of QVK Music: the only MediaSession of QVK Music, the media notification and the
 * foreground state that keeps playback alive after QVK is minimised (Android 14 "mediaPlayback" type).
 *
 * The service is foreground only while music plays; on pause it leaves the foreground (notification stays,
 * swipeable) and stops itself after 10 idle minutes so it does not drain the battery in the background.
 */
public final class Qvk3MusicService extends Service implements Qvk3MusicEngine.Listener {
    static final String CHANNEL = "qvk3_music";
    static final int NOTIFICATION = 0x51c3;
    static final String ACTION_TOGGLE = "ru.qmods.qvk.music.TOGGLE";
    static final String ACTION_NEXT = "ru.qmods.qvk.music.NEXT";
    static final String ACTION_PREV = "ru.qmods.qvk.music.PREV";
    static final String ACTION_STOP = "ru.qmods.qvk.music.STOP";
    private static final long IDLE_STOP_MS = 10 * 60_000L;

    private static volatile boolean running;
    private MediaSession session;
    private Qvk3MusicEngine engine;
    private boolean foreground;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String coverFor = "";
    private Bitmap cover;
    private final Runnable idleStop = this::stopSelf;

    /** Starts the service when playback begins (allowed: called while the app is in the foreground or playing). */
    static void ensureRunning(Context c) {
        if (running) return;
        Intent i = new Intent(c, Qvk3MusicService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException when started from the background on Android 12+:
            // playback continues, the notification appears once the app returns to the foreground.
            RuntimeFaults.record("qvk3.music.service.start", e);
        }
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        engine = Qvk3MusicEngine.get(this);
        session = new MediaSession(this, "QVK Music");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                engine.resume();
            }

            @Override
            public void onPause() {
                engine.pause();
            }

            @Override
            public void onSkipToNext() {
                engine.next();
            }

            @Override
            public void onSkipToPrevious() {
                engine.previous();
            }

            @Override
            public void onStop() {
                engine.stop();
            }

            @Override
            public void onSeekTo(long pos) {
                engine.seekTo(pos);
            }

            @Override
            public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent ev = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                // Single headset button: play/pause; double press handled by the system as NEXT.
                if (ev != null && ev.getAction() == KeyEvent.ACTION_DOWN && ev.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {
                    engine.toggle();
                    return true;
                }
                return super.onMediaButtonEvent(intent);
            }
        }, main);
        Intent open = new Intent(this, Qvk3MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        session.setSessionActivity(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        session.setActive(true);
        engine.addListener(this);
        createChannel();
        // Must call startForeground() promptly after startForegroundService().
        goForeground(build());
        onState(engine);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(a)) engine.toggle();
        else if (ACTION_NEXT.equals(a)) engine.next();
        else if (ACTION_PREV.equals(a)) engine.previous();
        else if (ACTION_STOP.equals(a)) {
            engine.stop();
            stopForegroundCompat(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping QVK away from recents keeps music playing; when paused, the service goes away with the task.
        if (!engine.isPlaying()) stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        main.removeCallbacks(idleStop);
        engine.removeListener(this);
        session.setActive(false);
        session.release();
        stopForegroundCompat(true);
        super.onDestroy();
    }

    @Override
    public void onState(Qvk3MusicEngine e) {
        Qvk3Track t = e.current();
        int st;
        switch (e.state()) {
            case Qvk3MusicEngine.PLAYING:
                st = PlaybackState.STATE_PLAYING;
                break;
            case Qvk3MusicEngine.BUFFERING:
                st = PlaybackState.STATE_BUFFERING;
                break;
            case Qvk3MusicEngine.ERROR:
                st = PlaybackState.STATE_ERROR;
                break;
            case Qvk3MusicEngine.PAUSED:
                st = PlaybackState.STATE_PAUSED;
                break;
            default:
                st = PlaybackState.STATE_STOPPED;
        }
        PlaybackState.Builder ps = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .setState(st, e.position(), st == PlaybackState.STATE_PLAYING ? e.speed() : 0f);
        if (st == PlaybackState.STATE_ERROR) ps.setErrorMessage(e.error());
        session.setPlaybackState(ps.build());
        if (t != null) {
            MediaMetadata.Builder md = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, e.duration());
            if (cover != null && t.cover.equals(coverFor)) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover);
            session.setMetadata(md.build());
            if (!t.cover.isEmpty() && !t.cover.equals(coverFor)) {
                coverFor = t.cover;
                cover = null;
                Qvk3Images.get(this).load(t.cover, 512, bmp -> {
                    if (t.cover.equals(coverFor)) {
                        cover = bmp;
                        onState(engine);
                    }
                });
            }
        }
        Notification n = build();
        boolean playing = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING;
        main.removeCallbacks(idleStop);
        if (playing) {
            goForeground(n);
        } else {
            stopForegroundCompat(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (t != null) nm.notify(NOTIFICATION, n);
            else nm.cancel(NOTIFICATION);
            main.postDelayed(idleStop, IDLE_STOP_MS);
        }
    }

    private void goForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFICATION, n);
        foreground = true;
    }

    private void stopForegroundCompat(boolean remove) {
        if (!foreground && !remove) return;
        stopForeground(remove ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        foreground = false;
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "QVK Music", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    private PendingIntent action(String a, int code) {
        return PendingIntent.getService(this, code, new Intent(this, Qvk3MusicService.class).setAction(a),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification build() {
        Qvk3Track t = engine.current();
        boolean playing = engine.isPlaying();
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(NotificationIcons.icon(android.R.drawable.ic_media_play))
                .setContentTitle(t == null ? "QVK Music" : t.title)
                .setContentText(t == null ? "" : (engine.state() == Qvk3MusicEngine.ERROR ? engine.error() : t.artist))
                .setLargeIcon(cover)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(session.getController().getSessionActivity())
                .setDeleteIntent(action(ACTION_STOP, 4))
                .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Назад", action(ACTION_PREV, 1)).build())
                .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play), playing ? "Пауза" : "Играть", action(ACTION_TOGGLE, 2)).build())
                .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next), "Далее", action(ACTION_NEXT, 3)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }
}
