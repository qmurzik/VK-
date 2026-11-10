package re.sova.qmods;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Playback queue of QVK Music. Pure Java (no Android types) so it is unit-tested on the JVM and persisted
 * as JSON independently of any screen.
 *
 * Shuffle keeps the user-visible order intact and plays through a separate permutation, so turning shuffle
 * off returns to the original order at the current track. Every mutating edit pushes an undo snapshot.
 */
public final class Qvk3Queue {
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;
    private static final int UNDO_LIMIT = 20;
    private static final int HISTORY_LIMIT = 200;

    private final List<Qvk3Track> items = new ArrayList<>();
    /** Play order: indexes into {@link #items}. Identity permutation when shuffle is off. */
    private final List<Integer> order = new ArrayList<>();
    private int position = -1; // index into order
    private boolean shuffle;
    private int repeat = REPEAT_OFF;
    private long seed = System.nanoTime();
    private final Deque<Snapshot> undo = new ArrayDeque<>();

    /** Undo snapshot: shares the immutable track objects, so it costs only two reference lists. */
    private static final class Snapshot {
        final List<Qvk3Track> items;
        final List<Integer> order;
        final int position;

        Snapshot(List<Qvk3Track> items, List<Integer> order, int position) {
            this.items = new ArrayList<>(items);
            this.order = new ArrayList<>(order);
            this.position = position;
        }
    }
    private final Deque<Qvk3Track> history = new ArrayDeque<>();

    public synchronized int size() {
        return items.size();
    }

    public synchronized List<Qvk3Track> items() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public synchronized Qvk3Track current() {
        return position < 0 || position >= order.size() ? null : items.get(order.get(position));
    }

    /** Index of the current track in the visible list, -1 when nothing is selected. */
    public synchronized int currentIndex() {
        return position < 0 || position >= order.size() ? -1 : order.get(position);
    }

    public synchronized boolean shuffle() {
        return shuffle;
    }

    public synchronized int repeat() {
        return repeat;
    }

    public synchronized List<Qvk3Track> history() {
        return new ArrayList<>(history);
    }

    /** Replaces the queue with {@code tracks} and selects {@code start}. */
    public synchronized void set(List<Qvk3Track> tracks, int start) {
        snapshot();
        items.clear();
        items.addAll(tracks);
        rebuildOrder(items.isEmpty() ? -1 : clamp(start, items.size()));
    }

    /** Replaces a track in place (e.g. with a refreshed stream URL); not an undoable edit. */
    public synchronized void replaceAt(int index, Qvk3Track t) {
        if (index >= 0 && index < items.size()) items.set(index, t);
    }

    public synchronized void addNext(Qvk3Track t) {
        snapshot();
        int at = currentIndex() + 1;
        insert(at < 0 ? items.size() : at, t);
    }

    public synchronized void addLast(Qvk3Track t) {
        snapshot();
        insert(items.size(), t);
    }

    public synchronized void remove(int index) {
        if (index < 0 || index >= items.size()) return;
        snapshot();
        int cur = currentIndex();
        items.remove(index);
        int newCur = cur == index ? Math.min(index, items.size() - 1) : (cur > index ? cur - 1 : cur);
        rebuildOrder(items.isEmpty() ? -1 : newCur);
    }

    /** Drag-and-drop reorder in the visible list. */
    public synchronized void move(int from, int to) {
        if (from < 0 || from >= items.size() || to < 0 || to >= items.size() || from == to) return;
        snapshot();
        Qvk3Track cur = current();
        Qvk3Track t = items.remove(from);
        items.add(to, t);
        rebuildOrder(cur == null ? -1 : indexOfIdentity(cur));
    }

    /** Shuffles only the visible range [from, to] (inclusive), keeping the current track selected. */
    public synchronized void shuffleRange(int from, int to, long rngSeed) {
        if (from < 0 || to >= items.size() || from >= to) return;
        snapshot();
        Qvk3Track cur = current();
        Collections.shuffle(items.subList(from, to + 1), new Random(rngSeed));
        rebuildOrder(cur == null ? -1 : indexOfIdentity(cur));
    }

    public synchronized void setShuffle(boolean on) {
        if (on == shuffle) return;
        int cur = currentIndex();
        shuffle = on;
        seed = System.nanoTime();
        rebuildOrder(cur);
    }

    public synchronized void setRepeat(int mode) {
        repeat = mode == REPEAT_ALL || mode == REPEAT_ONE ? mode : REPEAT_OFF;
    }

    /** Selects a track by visible index (user tapped it). */
    public synchronized Qvk3Track select(int index) {
        if (index < 0 || index >= items.size()) return null;
        remember();
        position = order.indexOf(index);
        return current();
    }

    /**
     * Advances after a track ended ({@code auto}) or after the user pressed "next".
     * REPEAT_ONE repeats only on automatic completion; an explicit "next" always moves on.
     */
    public synchronized Qvk3Track next(boolean auto) {
        if (order.isEmpty()) return null;
        if (auto && repeat == REPEAT_ONE) return current();
        remember();
        if (position + 1 < order.size()) {
            position++;
        } else if (repeat == REPEAT_ALL || (repeat == REPEAT_ONE && !auto)) {
            if (shuffle) reshuffleKeeping(-1);
            position = 0;
        } else {
            return null; // end of queue
        }
        return current();
    }

    /** Peeks the track that {@link #next(boolean) next(true)} would return, used for gapless pre-loading. */
    public synchronized Qvk3Track peekNext() {
        if (order.isEmpty()) return null;
        if (repeat == REPEAT_ONE) return current();
        if (position + 1 < order.size()) return items.get(order.get(position + 1));
        return repeat == REPEAT_ALL && !shuffle ? items.get(order.get(0)) : null;
    }

    public synchronized Qvk3Track previous() {
        if (order.isEmpty()) return null;
        if (position > 0) {
            position--;
        } else if (repeat == REPEAT_ALL) {
            position = order.size() - 1;
        }
        return current();
    }

    public synchronized boolean canUndo() {
        return !undo.isEmpty();
    }

    public synchronized boolean undo() {
        Snapshot s = undo.pollFirst();
        if (s == null) return false;
        items.clear();
        items.addAll(s.items);
        order.clear();
        order.addAll(s.order);
        if (order.size() != items.size()) {
            order.clear();
            for (int i = 0; i < items.size(); i++) order.add(i);
        }
        position = items.isEmpty() ? -1 : Math.min(s.position, items.size() - 1);
        return true;
    }

    public synchronized JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            JSONArray order = new JSONArray();
            for (int i : this.order) order.put(i);
            o.put("items", Qvk3Track.toJson(items)).put("order", order).put("position", position)
                    .put("shuffle", shuffle).put("repeat", repeat).put("seed", seed);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return o;
    }

    public synchronized void restore(JSONObject o) {
        items.clear();
        order.clear();
        JSONArray a = o.optJSONArray("items");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject t = a.optJSONObject(i);
            if (t != null) items.add(Qvk3Track.fromJson(t));
        }
        shuffle = o.optBoolean("shuffle");
        repeat = o.optInt("repeat");
        seed = o.optLong("seed", seed);
        JSONArray ord = o.optJSONArray("order");
        boolean valid = ord != null && ord.length() == items.size();
        if (valid) {
            boolean[] seen = new boolean[items.size()];
            for (int i = 0; i < ord.length(); i++) {
                int v = ord.optInt(i, -1);
                if (v < 0 || v >= items.size() || seen[v]) { valid = false; break; }
                seen[v] = true;
                order.add(v);
            }
        }
        if (!valid) {
            order.clear();
            for (int i = 0; i < items.size(); i++) order.add(i);
        }
        position = items.isEmpty() ? -1 : clamp(o.optInt("position", 0), items.size());
        undo.clear();
    }

    private void insert(int at, Qvk3Track t) {
        Qvk3Track cur = current();
        items.add(Math.max(0, Math.min(at, items.size())), t);
        int curIndex = cur == null ? -1 : indexOfIdentity(cur);
        if (shuffle) {
            // keep existing permutation, put the new track right after the current one
            List<Integer> shifted = new ArrayList<>();
            int inserted = Math.max(0, Math.min(at, items.size() - 1));
            for (int i : order) shifted.add(i >= inserted ? i + 1 : i);
            order.clear();
            order.addAll(shifted);
            int p = curIndex < 0 ? order.size() : order.indexOf(curIndex) + 1;
            order.add(p, inserted);
            position = curIndex < 0 ? -1 : order.indexOf(curIndex);
        } else {
            rebuildOrder(curIndex);
        }
    }

    private int indexOfIdentity(Qvk3Track t) {
        for (int i = 0; i < items.size(); i++) if (items.get(i) == t) return i;
        return -1;
    }

    private void rebuildOrder(int currentItem) {
        order.clear();
        for (int i = 0; i < items.size(); i++) order.add(i);
        if (shuffle) reshuffleKeeping(currentItem);
        position = currentItem < 0 ? -1 : order.indexOf(currentItem);
    }

    /** Shuffled order with the current track first so the listening flow is not interrupted. */
    private void reshuffleKeeping(int currentItem) {
        Collections.shuffle(order, new Random(seed++));
        if (currentItem >= 0) {
            order.remove(Integer.valueOf(currentItem));
            order.add(0, currentItem);
        }
    }

    private void snapshot() {
        undo.addFirst(new Snapshot(items, order, position));
        while (undo.size() > UNDO_LIMIT) undo.removeLast();
    }

    private void remember() {
        Qvk3Track c = current();
        if (c == null) return;
        history.addFirst(c);
        while (history.size() > HISTORY_LIMIT) history.removeLast();
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(v, size - 1));
    }
}
