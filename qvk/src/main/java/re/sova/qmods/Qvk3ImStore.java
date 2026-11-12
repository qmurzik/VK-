package re.sova.qmods;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * State of the QVK messenger: the conversation list, loaded histories, people, typing and read marks. Everything is
 * updated on the main thread and pushed to listeners. The first page of conversations and of each opened chat is
 * kept on disk (filesDir/qvk3_im, private to the app) so the messenger opens instantly and shows something offline.
 */
final class Qvk3ImStore {
    interface Listener {
        /** peer = 0: the conversation list changed; otherwise that chat changed. */
        void changed(long peer);
    }

    private static Qvk3ImStore instance;

    static synchronized Qvk3ImStore get(Context c) {
        if (instance == null) instance = new Qvk3ImStore(c.getApplicationContext());
        return instance;
    }

    final Context app;
    final List<Qvk3ImModel.Conversation> conversations = new ArrayList<>();
    final Map<Long, Qvk3ImModel.Conversation> byPeer = new HashMap<>();
    final Map<Long, Qvk3ImModel.Person> people = new HashMap<>();
    final Map<Long, List<Qvk3ImModel.Message>> history = new HashMap<>();
    final Set<Long> historyComplete = new HashSet<>();
    /** peer → (user → until ms) */
    final Map<Long, Map<Long, Long>> typing = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    boolean listLoaded, listLoading, listEnd;
    String listError = "";

    private Qvk3ImStore(Context app) {
        this.app = app;
        try {
            JSONObject cached = readCache("conversations");
            if (cached != null) applyConversations(cached, true);
        } catch (Exception ignored) {
        }
    }

    void addListener(Listener l) {
        listeners.add(l);
        Qvk3ImLongPoll.get(app).retain();
    }

    void removeListener(Listener l) {
        listeners.remove(l);
        Qvk3ImLongPoll.get(app).release();
    }

    void notifyChanged(long peer) {
        for (Listener l : listeners) {
            try {
                l.changed(peer);
            } catch (Throwable t) {
                RuntimeFaults.record("qvk3.im.listener", t);
            }
        }
    }

    // ------------------------------------------------------------------ conversations

    /** Loads the first page (offset 0, replaces the list) or the next one. */
    void loadConversations(boolean more) {
        if (listLoading || (more && listEnd)) return;
        listLoading = true;
        int offset = more ? conversations.size() : 0;
        Qvk3Jobs.io(() -> {
            try {
                JSONObject r = Qvk3ImApi.conversations(offset, 40, "all");
                if (offset == 0) writeCache("conversations", r);
                Qvk3Jobs.main(() -> {
                    listLoading = false;
                    listError = "";
                    applyConversations(r, offset == 0);
                    listEnd = r.optJSONArray("items") == null || r.optJSONArray("items").length() < 40;
                    notifyChanged(0);
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> {
                    listLoading = false;
                    listError = Qvk3Jobs.message(e);
                    notifyChanged(0);
                });
            }
        });
    }

    private void applyConversations(JSONObject r, boolean replace) {
        people.putAll(Qvk3ImModel.people(r));
        List<Qvk3ImModel.Conversation> page = Qvk3ImModel.conversations(r);
        if (replace) conversations.clear();
        for (Qvk3ImModel.Conversation c : page) {
            Qvk3ImModel.Conversation old = byPeer.get(c.peer);
            if (old != null) conversations.remove(old);
            conversations.add(c);
            byPeer.put(c.peer, c);
        }
        sort();
        listLoaded = true;
    }

    void sort() {
        Collections.sort(conversations, (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            if (a.pinned) return Long.compare(b.sortId, a.sortId);
            long da = a.last == null ? 0 : a.last.date, db = b.last == null ? 0 : b.last.date;
            return Long.compare(db, da);
        });
    }

    Qvk3ImModel.Conversation conversation(long peer) {
        return byPeer.get(peer);
    }

    /** Fetches one conversation (chat opened from a notification / profile before the list knows it). */
    void loadConversation(long peer) {
        Qvk3Jobs.io(() -> {
            try {
                JSONObject r = Qvk3ImApi.conversationById(peer);
                Qvk3Jobs.main(() -> {
                    people.putAll(Qvk3ImModel.people(r));
                    JSONArray items = r.optJSONArray("items");
                    if (items != null && items.length() > 0) {
                        Qvk3ImModel.Conversation old = byPeer.get(peer);
                        Qvk3ImModel.Conversation c = Qvk3ImModel.conversation(items.optJSONObject(0), old == null ? null : old.last, people);
                        if (old != null) conversations.remove(old);
                        byPeer.put(peer, c);
                        conversations.add(c);
                        sort();
                    }
                    notifyChanged(peer);
                    notifyChanged(0);
                });
            } catch (Exception e) {
                RuntimeFaults.record("qvk3.im.conversation", e);
            }
        });
    }

    // ------------------------------------------------------------------ history

    List<Qvk3ImModel.Message> messages(long peer) {
        List<Qvk3ImModel.Message> l = history.get(peer);
        if (l == null) {
            l = new ArrayList<>();
            try {
                JSONObject cached = readCache("h" + peer);
                if (cached != null) {
                    people.putAll(Qvk3ImModel.people(cached));
                    l.addAll(Qvk3ImModel.history(cached));
                }
            } catch (Exception ignored) {
            }
            history.put(peer, l);
        }
        return l;
    }

    interface Done {
        void done(String error);
    }

    /** older = false: latest page (merged with what is loaded); true: the page before the oldest loaded message. */
    void loadHistory(long peer, boolean older, Done done) {
        List<Qvk3ImModel.Message> cur = messages(peer);
        long startId = 0;
        if (older) {
            for (Qvk3ImModel.Message m : cur) if (m.id > 0) {
                startId = m.id;
                break;
            }
            if (startId == 0) {
                done.done(null);
                return;
            }
        }
        final long from = startId;
        Qvk3Jobs.io(() -> {
            try {
                JSONObject r = Qvk3ImApi.history(peer, older ? 1 : 0, 50, from);
                if (!older) writeCache("h" + peer, r);
                Qvk3Jobs.main(() -> {
                    people.putAll(Qvk3ImModel.people(r));
                    List<Qvk3ImModel.Message> page = Qvk3ImModel.history(r);
                    List<Qvk3ImModel.Message> merged = Qvk3ImModel.merge(messages(peer), page);
                    history.put(peer, merged);
                    if (older && page.size() < 50) historyComplete.add(peer);
                    JSONObject convs = r.optJSONArray("conversations") == null ? null : r.optJSONArray("conversations").optJSONObject(0);
                    if (convs != null) {
                        Qvk3ImModel.Conversation old = byPeer.get(peer);
                        Qvk3ImModel.Conversation c = Qvk3ImModel.conversation(convs, old != null ? old.last : merged.isEmpty() ? null : merged.get(merged.size() - 1), people);
                        if (old != null) conversations.remove(old);
                        conversations.add(c);
                        byPeer.put(peer, c);
                        sort();
                    }
                    notifyChanged(peer);
                    done.done(null);
                });
            } catch (Exception e) {
                Qvk3Jobs.main(() -> done.done(Qvk3Jobs.message(e)));
            }
        });
    }

    /** New or edited messages (Long Poll history, send result): into the chat and the list. */
    void put(List<Qvk3ImModel.Message> msgs, boolean incomingCountsAsUnread) {
        Set<Long> peers = new HashSet<>();
        for (Qvk3ImModel.Message m : msgs) {
            peers.add(m.peer);
            List<Qvk3ImModel.Message> l = history.get(m.peer);
            if (l != null) {
                // a sent message replaces its local copy (same random_id)
                if (m.randomId != 0) for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == 0 && l.get(i).randomId == m.randomId) l.remove(i);
                history.put(m.peer, Qvk3ImModel.merge(l, Collections.singletonList(m)));
            }
            Qvk3ImModel.Conversation c = byPeer.get(m.peer);
            if (c != null && (c.last == null || m.id >= c.last.id)) {
                boolean isNew = c.last == null || m.id > c.last.id;
                c.last = m;
                if (isNew && !m.out && incomingCountsAsUnread) c.unread++;
                if (m.out) c.unread = 0;
                // typing stops when the message arrives
                Map<Long, Long> t = typing.get(m.peer);
                if (t != null) t.remove(m.from);
            } else if (c == null) {
                loadConversation(m.peer);
            }
        }
        sort();
        for (Long p : peers) notifyChanged(p);
        notifyChanged(0);
    }

    void removeMessages(long peer, Set<Long> cmids) {
        List<Qvk3ImModel.Message> l = history.get(peer);
        if (l != null) for (int i = l.size() - 1; i >= 0; i--) if (cmids.contains(l.get(i).cmid)) l.remove(i);
        notifyChanged(peer);
    }

    void read(long peer, long cmid, boolean outgoing) {
        Qvk3ImModel.Conversation c = byPeer.get(peer);
        if (c == null) return;
        if (outgoing) c.outRead = Math.max(c.outRead, cmid);
        else {
            c.inRead = Math.max(c.inRead, cmid);
            if (c.last != null && c.last.cmid <= cmid) c.unread = 0;
        }
        notifyChanged(peer);
        notifyChanged(0);
    }

    void typing(long peer, List<Long> users) {
        Map<Long, Long> t = typing.get(peer);
        if (t == null) typing.put(peer, t = new HashMap<>());
        long until = System.currentTimeMillis() + 6000;
        for (Long u : users) t.put(u, until);
        notifyChanged(peer);
        notifyChanged(0);
    }

    /** «Анна печатает…» / «Анна и Олег печатают…»; empty when nobody types. */
    String typingText(long peer, boolean chat) {
        Map<Long, Long> t = typing.get(peer);
        if (t == null || t.isEmpty()) return "";
        long now = System.currentTimeMillis();
        List<String> names = new ArrayList<>();
        for (Map.Entry<Long, Long> e : t.entrySet()) {
            if (e.getValue() < now) continue;
            Qvk3ImModel.Person p = people.get(e.getKey());
            names.add(p == null ? "Кто-то" : p.first);
        }
        if (names.isEmpty()) return "";
        if (!chat) return "печатает…";
        return names.size() == 1 ? names.get(0) + " печатает…" : names.get(0) + " и ещё " + (names.size() - 1) + " печатают…";
    }

    void online(long user, boolean on) {
        Qvk3ImModel.Conversation c = byPeer.get(user);
        if (c == null) return;
        c.online = on;
        if (!on) c.lastSeen = System.currentTimeMillis() / 1000;
        notifyChanged(user);
        notifyChanged(0);
    }

    // ------------------------------------------------------------------ disk cache

    private File dir() {
        File d = new File(app.getFilesDir(), "qvk3_im");
        d.mkdirs();
        return d;
    }

    void writeCache(String name, JSONObject r) {
        try {
            java.nio.file.Files.write(new File(dir(), name + ".json").toPath(), r.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            RuntimeFaults.record("qvk3.im.cache", e);
        }
    }

    JSONObject readCache(String name) {
        File f = new File(dir(), name + ".json");
        if (!f.isFile() || f.length() > 4 << 20) return null;
        try {
            return new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** «Удалить кэш мессенджера» / logout. */
    void clearCache() {
        File[] fs = dir().listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }
}
