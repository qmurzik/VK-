package re.sova.qmods;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class Qvk3SettingsCodeTest {
    @Test public void roundTrip() {
        Map<String, Boolean> v = new HashMap<>();
        for (int i = 0; i < Qvk3SettingsCode.ORDER.length; i++) v.put(Qvk3SettingsCode.ORDER[i], i % 3 == 0);
        String code = Qvk3SettingsCode.encode(v::get);
        assertTrue(code.startsWith("QVK1-"));
        assertTrue("short: " + code, code.length() < 40);
        Map<String, Boolean> back = Qvk3SettingsCode.decode(code.toLowerCase());
        for (String k : Qvk3SettingsCode.ORDER) assertEquals(k, v.get(k), back.get(k));
    }

    @Test public void unknownSwitchesAreOff() {
        String code = Qvk3SettingsCode.encode(k -> null);
        for (Boolean b : Qvk3SettingsCode.decode(code).values()) assertFalse(b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void typoIsRejected() {
        Map<String, Boolean> v = new HashMap<>();
        v.put(Qvk3SettingsCode.ORDER[3], true);
        String code = Qvk3SettingsCode.encode(v::get);
        char last = code.charAt(code.length() - 2);
        Qvk3SettingsCode.decode(code.substring(0, code.length() - 2) + (last == 'Z' ? 'Y' : 'Z') + code.charAt(code.length() - 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void notACode() {
        Qvk3SettingsCode.decode("hello");
    }
}
