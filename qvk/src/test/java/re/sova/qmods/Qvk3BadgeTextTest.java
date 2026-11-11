package re.sova.qmods;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class Qvk3BadgeTextTest {
    @Test
    public void keepsOnlyTheNumber() {
        assertEquals("6", Qvk3BadgeText.number("6 непрочитанных чатов"));
        assertEquals("12", Qvk3BadgeText.number("12"));
        assertEquals("99+", Qvk3BadgeText.number("99+"));
        assertEquals("1200", Qvk3BadgeText.number("1 200 новых"));
        assertEquals("", Qvk3BadgeText.number(""));
        assertEquals("", Qvk3BadgeText.number("Новые сообщения"));
        assertNull(Qvk3BadgeText.number(null));
    }
}
