package re.sova.qmods;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Qvk3UiThrottleTest {
    @Test
    public void selfCausedLayoutsBackOffAndInputResets() {
        Qvk3UiThrottle.reset();
        assertEquals(200, Qvk3UiThrottle.next(500));   // external change: normal pace
        assertEquals(400, Qvk3UiThrottle.next(16));    // layout right after a pass: back off
        assertEquals(800, Qvk3UiThrottle.next(20));
        assertEquals(1600, Qvk3UiThrottle.next(20));
        assertEquals(1600, Qvk3UiThrottle.next(20));   // capped
        Qvk3UiThrottle.input(null);
        assertEquals(200, Qvk3UiThrottle.next(10));    // scrolling: prompt again
        assertEquals(400, Qvk3UiThrottle.next(10));
        assertEquals(200, Qvk3UiThrottle.next(3000));  // a later, external layout
    }
}
