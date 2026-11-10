package re.sova.qmods;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class Qvk3DesignTokensTest {
    static int c(JSONObject theme, String k) {
        return (int) Long.parseLong(theme.optJSONObject("colors").optString(k).substring(1), 16);
    }

    @Test public void allEightPresetsExist() {
        for (String id : new String[]{"classic", "modern", "glass", "amoled", "compact", "cozy", "neon", "ios"})
            assertNotNull(id, Qvk3DesignTokens.PRESETS.get(id));
    }

    @Test public void everyPresetCompilesToReadableTheme() {
        for (Qvk3DesignTokens t : Qvk3DesignTokens.PRESETS.values()) {
            JSONObject theme = t.toTheme();
            assertEquals("sova-vk-theme-1", theme.optString("format"));
            assertEquals(9, theme.optJSONObject("colors").length());
            int text = c(theme, "text_primary"), content = c(theme, "background_content"), page = c(theme, "background_page");
            assertTrue(t.id + " text/content", Qvk3DesignTokens.contrast(text, content) >= 4.5);
            assertTrue(t.id + " text/page", Qvk3DesignTokens.contrast(text, page) >= 4.5);
            JSONObject a = theme.optJSONObject("appearance");
            assertTrue(a.optInt("radius") >= 0 && a.optInt("radius") <= 28);
            assertTrue(a.optInt("text_scale") >= 90 && a.optInt("text_scale") <= 125);
        }
    }

    @Test public void presetsDifferInMoreThanColour() {
        Qvk3DesignTokens compact = Qvk3DesignTokens.PRESETS.get("compact"), cozy = Qvk3DesignTokens.PRESETS.get("cozy");
        assertNotEquals(compact.radius, cozy.radius);
        assertNotEquals(compact.textScale, cozy.textScale);
        assertTrue(compact.componentFlags(false).get("qvk_compact_dialogs"));
        assertFalse(cozy.componentFlags(false).get("qvk_compact_dialogs"));
        assertTrue(Qvk3DesignTokens.PRESETS.get("ios").componentFlags(false).get("qvk_ios_enabled"));
        assertFalse("presets must not force Material", Qvk3DesignTokens.PRESETS.get("modern").componentFlags(false).get("qvk_material3_enabled"));
        assertFalse(Qvk3DesignTokens.PRESETS.get("ios").componentFlags(false).get("qvk_ios_colors"));
    }

    @Test public void everyPresetHasReadableLightAndDarkVariants() {
        for (Qvk3DesignTokens t : Qvk3DesignTokens.PRESETS.values()) {
            for (boolean dark : new boolean[]{false, true}) {
                Qvk3DesignTokens v = t.variant(dark);
                assertEquals(dark, v.dark);
                assertEquals(t.accent, v.accent);
                JSONObject theme = v.toTheme();
                int text = c(theme, "text_primary"), content = c(theme, "background_content");
                assertTrue(t.id + " dark=" + dark, Qvk3DesignTokens.contrast(text, content) >= 4.5);
                boolean pageDark = Qvk3DesignTokens.luminance(c(theme, "background_page")) < 0.2;
                assertEquals(t.id + " page brightness matches variant", dark, pageDark);
            }
        }
    }

    @Test public void heavyEffectsOffOnWeakDevices() {
        Qvk3DesignTokens glass = Qvk3DesignTokens.PRESETS.get("glass");
        assertTrue(glass.componentFlags(false).get("qvk_motion_blur"));
        assertFalse(glass.componentFlags(true).get("qvk_motion_blur"));
    }

    @Test public void ensureContrastFixesUnreadableText() {
        int fixed = Qvk3DesignTokens.ensureContrast(0xFFAAAAAA, 0xFFFFFFFF, 4.5);
        assertTrue(Qvk3DesignTokens.contrast(fixed, 0xFFFFFFFF) >= 4.5);
        assertEquals(0xFF000000, Qvk3DesignTokens.ensureContrast(0xFF000000, 0xFFFFFFFF, 4.5));
    }

    @Test public void customEditsRoundTripAndAreClamped() {
        Qvk3DesignTokens t = Qvk3DesignTokens.PRESETS.get("modern").with("radius", 99).with("font", "comic");
        assertEquals("custom", t.id);
        assertEquals(28, t.radius);
        assertEquals("inter", t.font);
        Qvk3DesignTokens r = Qvk3DesignTokens.fromJson(t.toJson(), Qvk3DesignTokens.PRESETS.get("classic"));
        assertEquals(t.accent, r.accent);
        assertEquals(t.radius, r.radius);
    }
}
