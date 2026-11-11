package re.sova.qmods;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Qvk3LogRedactTest {
    @Test
    public void masksTokensInUrlsAndHeaders() {
        String s = Qvk3LogRedact.clean("GET /method/x?access_token=abcdef123456&v=5.1 Authorization: Bearer abc.def-ghi_jkl123");
        assertFalse(s.contains("abcdef123456"));
        assertFalse(s.contains("abc.def-ghi_jkl123"));
        assertTrue(s.contains("v=5.1"));
    }

    @Test
    public void masksJsonSecretsAndLongHex() {
        String s = Qvk3LogRedact.clean("{\"password\":\"hunter2xx\"} id=0123456789abcdef0123456789abcdef01234567");
        assertFalse(s.contains("hunter2xx"));
        assertFalse(s.contains("0123456789abcdef0123456789abcdef01234567"));
    }

    @Test
    public void leavesOrdinaryTextAndHandlesEmpty() {
        assertEquals("E/AndroidRuntime: FATAL EXCEPTION: main", Qvk3LogRedact.clean("E/AndroidRuntime: FATAL EXCEPTION: main"));
        assertEquals("", Qvk3LogRedact.clean(null));
    }
}
