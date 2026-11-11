package re.sova.qmods;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IDEA-0010: automatic retry of messages VK marked as failed (a photo/video/file of the message did not upload, or the
 * send itself failed after VK's own 7 attempts).
 *
 * Patch 0019 calls {@link #failed} from MsgSendJob.l0 — the single place where VK 8.100 sets MsgSyncState.ERROR. The retry is
 * exactly what the «Повторить» button does: ImEngine.x0(tag, MsgSendRetryViaBgCmd(localId, peer)), which re-queues the SAME
 * message (same random_id, already uploaded attachments kept) — no new message is created.
 *
 * Limits: at most {@link #MAX} automatic retries per message per app run, a pause between them, never when the user cancelled
 * an attachment, never for errors a retry cannot fix (access, captcha, credentials). Without network the retry waits for a
 * one-shot network callback — no polling, no wakeups.
 */
public final class Qvk3SendRetry {
    public static final String PREF = "qvk3_send_retry";
    static final int MAX = 2;
    static final long[] DELAY = {4000, 30000};
    static final String TAG = "QVK3SendRetry";

    private static final Map<Integer, Integer> attempts = new HashMap<>();
    private static final List<Object[]> waiting = new ArrayList<>();
    private static ConnectivityManager.NetworkCallback callback;

    private Qvk3SendRetry() {
    }

    /** Called from VK's MsgSendJob on terminal failure. Must never throw. */
    public static void failed(Object peer, int localId, Throwable error, boolean cancelled) {
        try {
            if (cancelled || peer == null || localId == 0) return;
            Context c = RuntimeBridge.context;
            if (c == null || !Qvk3MusicSettings.prefs(c).getBoolean(PREF, true)) return;
            if (permanent(error)) {
                Log.i(TAG, "no retry, permanent error " + (error == null ? "" : error.getClass().getSimpleName()));
                return;
            }
            int n;
            synchronized (attempts) {
                Integer was = attempts.get(localId);
                n = was == null ? 0 : was;
                if (n >= MAX) return;
                attempts.put(localId, n + 1);
            }
            Log.i(TAG, "retry " + (n + 1) + "/" + MAX + " of msg " + localId);
            new Handler(Looper.getMainLooper()).postDelayed(() -> whenOnline(c, peer, localId), DELAY[Math.min(n, DELAY.length - 1)]);
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.sendRetry", t);
        }
    }

    /** Error classes a repeat cannot fix; matched by name so the check survives VK obfuscation of the wrappers. */
    static boolean permanent(Throwable e) {
        for (int i = 0; e != null && i < 6; i++, e = e.getCause()) {
            String n = e.getClass().getName();
            if (n.contains("AccessDenied") || n.contains("InvalidCredentials") || n.contains("Captcha")
                    || n.contains("ApiNotImplemented") || n.contains("ArgNotFound") || n.contains("Cancel")
                    || n.contains("WrongFormat") || n.contains("TooLarge") || n.contains("LimitExceeded")) return true;
        }
        return false;
    }

    static void whenOnline(Context c, Object peer, int localId) {
        if (online(c)) {
            resend(peer, localId);
            return;
        }
        synchronized (waiting) {
            waiting.add(new Object[]{peer, localId});
            if (callback != null) return;
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    List<Object[]> now;
                    synchronized (waiting) {
                        now = new ArrayList<>(waiting);
                        waiting.clear();
                        try {
                            cm.unregisterNetworkCallback(this);
                        } catch (Throwable ignored) {
                            // already unregistered
                        }
                        callback = null;
                    }
                    for (Object[] w : now) resend(w[0], (Integer) w[1]);
                }
            };
            try {
                cm.registerDefaultNetworkCallback(callback);
            } catch (Throwable t) {
                callback = null;
                RuntimeFaults.record("qvk3.sendRetry.network", t);
            }
        }
    }

    static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) {
            return true;
        }
    }

    /** The «Повторить» action of VK's message popup (MsgListComponent.U2). */
    static void resend(Object peer, int localId) {
        try {
            xsna.exq.a().x0(TAG, new xsna.yqy(localId, (com.vk.dto.common.Peer) peer));
        } catch (Throwable t) {
            RuntimeFaults.record("qvk3.sendRetry.resend", t);
        }
    }
}
