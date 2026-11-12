package re.sova.qmods;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real-time updates for the QVK messenger while one of its screens is visible (retain/release). VK's own background
 * push keeps delivering notifications, so nothing runs when the messenger is closed.
 *
 * The long poll only wakes us up: new / edited messages are then fetched in full with messages.getLongPollHistory
 * (exact VK objects, no hand-parsing of the compact event format); deletions, read marks, typing and online changes
 * come straight from the events.
 */
final class Qvk3ImLongPoll {
    private static Qvk3ImLongPoll instance;

    static synchronized Qvk3ImLongPoll get(Context c) {
        if (instance == null) instance = new Qvk3ImLongPoll(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private int users;
    private Thread thread;
    private volatile boolean running;
    private String server, key;
    private long ts, pts;

    private Qvk3ImLongPoll(Context app) {
        this.app = app;
    }

    synchronized void retain() {
        users++;
        if (thread == null || !thread.isAlive()) {
            running = true;
            thread = new Thread(this::loop, "qvk3-im-lp");
            thread.setDaemon(true);
            thread.start();
        }
    }

    synchronized void release() {
        users = Math.max(0, users - 1);
        if (users == 0) {
            running = false;
            if (thread != null) thread.interrupt();
        }
    }

    private void loop() {
        int failures = 0;
        while (running) {
            try {
                if (server == null) connect();
                String url = "https://" + server.replaceFirst("^https?://", "") + "?act=a_check&key=" + key + "&ts=" + ts + "&wait=25&mode=234&version=19";
                JSONObject r = new JSONObject(get(url));
                if (r.has("failed")) {
                    int f = r.optInt("failed");
                    if (f == 1) {
                        long old = ts;
                        ts = r.optLong("ts");
                        catchUp(old);          // history was trimmed: fetch what we missed
                    } else {
                        server = null;         // key expired / lost: new server, then catch up from pts
                    }
                    continue;
                }
                long oldTs = ts;
                ts = r.optLong("ts", ts);
                JSONArray updates = r.optJSONArray("updates");
                if (updates != null && updates.length() > 0) handle(updates, oldTs);
                failures = 0;
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (!running) return;
                failures++;
                server = null;
                android.util.Log.i("QVK3Im", "long poll: " + Qvk3Jobs.message(e));
                try {
                    Thread.sleep(Math.min(30_000L, 1000L << Math.min(5, failures)));
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void connect() throws Exception {
        JSONObject r = Qvk3ImApi.longPollServer();
        boolean reconnect = pts > 0;
        long oldTs = ts;
        server = r.getString("server");
        key = r.getString("key");
        ts = r.optLong("ts");
        if (reconnect) catchUp(oldTs > 0 ? oldTs : ts); // pts keeps our position: fetch what came while we were away
        else pts = r.optLong("pts");
    }

    private void handle(JSONArray updates, long oldTs) throws Exception {
        boolean needHistory = false;
        Map<Long, Set<Long>> deleted = new HashMap<>();
        for (int i = 0; i < updates.length(); i++) {
            JSONArray u = updates.optJSONArray(i);
            if (u == null || u.length() == 0) continue;
            int type = u.optInt(0);
            switch (type) {
                case 10004:   // new message
                case 10005:   // edited
                case 10018:   // reactions changed
                    needHistory = true;
                    break;
                case 10002: { // flags set: [10002, cmid, flags, peer]
                    long flags = u.optLong(2);
                    if ((flags & 128) != 0 || (flags & 131072) != 0) {
                        long peer = u.optLong(3);
                        Set<Long> s = deleted.get(peer);
                        if (s == null) deleted.put(peer, s = new HashSet<>());
                        s.add(u.optLong(1));
                    } else needHistory = true;
                    break;
                }
                case 10006: { // incoming read up to cmid: [10006, peer, cmid]
                    long peer = u.optLong(1), cmid = u.optLong(2);
                    Qvk3Jobs.main(() -> Qvk3ImStore.get(app).read(peer, cmid, false));
                    break;
                }
                case 10007: { // our messages read
                    long peer = u.optLong(1), cmid = u.optLong(2);
                    Qvk3Jobs.main(() -> Qvk3ImStore.get(app).read(peer, cmid, true));
                    break;
                }
                case 8:       // friend online: [8, -user, platform, ts]
                case 9: {     // friend offline
                    long user = -u.optLong(1);
                    boolean on = type == 8;
                    Qvk3Jobs.main(() -> Qvk3ImStore.get(app).online(user, on));
                    break;
                }
                case 63:      // typing: [63, peer, [users], total, ts]
                case 64: {    // recording a voice message
                    long peer = u.optLong(1);
                    JSONArray who = u.optJSONArray(2);
                    List<Long> ids = new ArrayList<>();
                    if (who != null) for (int k = 0; k < who.length(); k++) ids.add(who.optLong(k));
                    if (!ids.isEmpty()) Qvk3Jobs.main(() -> Qvk3ImStore.get(app).typing(peer, ids));
                    break;
                }
                default:
                    break;
            }
        }
        for (Map.Entry<Long, Set<Long>> e : deleted.entrySet()) {
            long peer = e.getKey();
            Set<Long> s = e.getValue();
            Qvk3Jobs.main(() -> Qvk3ImStore.get(app).removeMessages(peer, s));
        }
        if (needHistory) catchUp(oldTs);
    }

    /** Full objects of everything that changed since (ts, pts). */
    private void catchUp(long sinceTs) throws Exception {
        if (pts == 0) return;
        JSONObject r = Qvk3ImApi.longPollHistory(sinceTs, pts);
        pts = r.optLong("new_pts", pts);
        JSONObject msgs = r.optJSONObject("messages");
        JSONArray items = msgs == null ? null : msgs.optJSONArray("items");
        if (items == null || items.length() == 0) return;
        Map<Long, Qvk3ImModel.Person> who = Qvk3ImModel.people(r);
        List<Qvk3ImModel.Message> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            Qvk3ImModel.Message m = Qvk3ImModel.message(items.optJSONObject(i));
            if (m != null) list.add(m);
        }
        Qvk3Jobs.main(() -> {
            Qvk3ImStore s = Qvk3ImStore.get(app);
            s.people.putAll(who);
            s.put(list, true);
        });
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(35_000);
        try (InputStream in = con.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
                if (out.size() > 4 << 20) break;
                if (Thread.interrupted()) throw new InterruptedException();
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }
}
