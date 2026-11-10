package re.sova.qmods;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class Qvk3QueueTest {
    static Qvk3Track t(int id) {
        return new Qvk3Track(1, id, "", "A" + id, "T" + id, "", 100, "https://x/" + id, "", false, 0, "", 0);
    }

    static List<Qvk3Track> list(int n) {
        List<Qvk3Track> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(t(i));
        return l;
    }

    @Test public void playsInOrderAndStopsAtEnd() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(3), 0);
        assertEquals(0, q.current().id);
        assertEquals(1, q.next(true).id);
        assertEquals(2, q.next(true).id);
        assertNull(q.next(true));
    }

    @Test public void repeatOneRepeatsOnlyOnAutoAdvance() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(3), 1);
        q.setRepeat(Qvk3Queue.REPEAT_ONE);
        assertEquals(1, q.next(true).id);
        assertEquals(2, q.next(false).id);
    }

    @Test public void repeatAllWraps() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(2), 1);
        q.setRepeat(Qvk3Queue.REPEAT_ALL);
        assertEquals(0, q.next(true).id);
        assertEquals(1, q.previous().id == 1 ? 1 : q.current().id);
    }

    @Test public void shuffleKeepsCurrentAndVisitsEveryTrackOnce() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(50), 7);
        q.setShuffle(true);
        assertEquals(7, q.current().id);
        Set<Long> seen = new HashSet<>();
        seen.add(q.current().id);
        Qvk3Track n;
        while ((n = q.next(true)) != null) assertTrue("duplicate " + n.id, seen.add(n.id));
        assertEquals(50, seen.size());
    }

    @Test public void shuffleOffReturnsToVisibleOrderAtCurrentTrack() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(10), 0);
        q.setShuffle(true);
        q.next(false);
        long cur = q.current().id;
        q.setShuffle(false);
        assertEquals(cur, q.current().id);
        if (cur < 9) assertEquals(cur + 1, q.next(true).id);
    }

    @Test public void addNextPlaysRightAfterCurrent() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(3), 0);
        q.addNext(t(99));
        assertEquals(99, q.next(true).id);
        assertEquals(1, q.next(true).id);
    }

    @Test public void addNextInShuffleModeAlsoPlaysNext() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(20), 3);
        q.setShuffle(true);
        q.addNext(t(99));
        assertEquals(3, q.current().id);
        assertEquals(99, q.next(true).id);
        assertEquals(21, q.size());
    }

    @Test public void removeCurrentSelectsFollowingTrack() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(3), 1);
        q.remove(1);
        assertEquals(2, q.current().id);
        q.remove(0);
        assertEquals(2, q.current().id);
        assertEquals(1, q.size());
    }

    @Test public void moveKeepsCurrentTrack() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(5), 2);
        q.move(2, 4);
        assertEquals(2, q.current().id);
        assertEquals(4, q.currentIndex());
        q.move(0, 3);
        assertEquals(2, q.current().id);
    }

    @Test public void undoRestoresPreviousState() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(3), 0);
        q.remove(2);
        assertEquals(2, q.size());
        assertTrue(q.undo());
        assertEquals(3, q.size());
        assertEquals(2, q.items().get(2).id);
    }

    @Test public void persistsAndRestoresIncludingShuffleOrder() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(30), 4);
        q.setShuffle(true);
        q.setRepeat(Qvk3Queue.REPEAT_ALL);
        q.next(false);
        Qvk3Queue r = new Qvk3Queue();
        r.restore(q.toJson());
        assertEquals(q.current().key(), r.current().key());
        assertEquals(q.peekNext().key(), r.peekNext().key());
        assertTrue(r.shuffle());
        assertEquals(Qvk3Queue.REPEAT_ALL, r.repeat());
    }

    @Test public void restoreRejectsCorruptOrder() throws Exception {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(3), 0);
        org.json.JSONObject o = q.toJson();
        o.put("order", new org.json.JSONArray("[0,0,7]"));
        Qvk3Queue r = new Qvk3Queue();
        r.restore(o);
        assertEquals(3, r.size());
        assertEquals(0, r.current().id);
    }

    @Test public void largeQueueRandomOperationsKeepInvariants() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(5000), 0);
        java.util.Random rnd = new java.util.Random(1);
        for (int i = 0; i < 2000; i++) {
            switch (rnd.nextInt(6)) {
                case 0: q.next(rnd.nextBoolean()); break;
                case 1: q.previous(); break;
                case 2: q.move(rnd.nextInt(q.size()), rnd.nextInt(q.size())); break;
                case 3: q.addNext(t(10000 + i)); break;
                case 4: if (q.size() > 1) q.remove(rnd.nextInt(q.size())); break;
                default: q.setShuffle(!q.shuffle());
            }
            if (q.current() != null) assertSame(q.current(), q.items().get(q.currentIndex()));
        }
    }

    @Test public void historyRecordsPlayedTracks() {
        Qvk3Queue q = new Qvk3Queue();
        q.set(list(3), 0);
        q.next(true);
        q.next(true);
        assertEquals(1, q.history().get(0).id);
        assertEquals(0, q.history().get(1).id);
    }
}
