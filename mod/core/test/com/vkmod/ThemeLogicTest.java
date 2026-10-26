package com.vkmod;

import static org.junit.Assert.*;

import com.vkmod.theme.ColorSlot;
import com.vkmod.theme.Palette;
import com.vkmod.theme.Preset;
import com.vkmod.theme.Presets;

import org.junit.Test;

public class ThemeLogicTest {
    @Test public void classifiesVkTokensByMeaning() {
        assertEquals(ColorSlot.ACCENT, ColorSlot.classify("vk_legacy_accent"));
        assertEquals(ColorSlot.ACCENT, ColorSlot.classify("vk_legacy_accent_alpha20"));
        assertEquals(ColorSlot.BACKGROUND, ColorSlot.classify("vk_ui_background"));
        assertEquals(ColorSlot.CARD, ColorSlot.classify("vk_ui_background_content"));
        assertEquals(ColorSlot.TEXT, ColorSlot.classify("vk_legacy_text_primary"));
        assertEquals(ColorSlot.TABBAR_ACTIVE, ColorSlot.classify("vk_legacy_tabbar_active_icon"));
        assertEquals(ColorSlot.BUBBLE_OUT, ColorSlot.classify("vk_legacy_im_bubble_outgoing"));
        assertEquals(ColorSlot.BUBBLE_IN, ColorSlot.classify("vk_legacy_im_bubble_incoming"));
        assertEquals(ColorSlot.CHAT_BG, ColorSlot.classify("im_bg_chat"));
        assertEquals(ColorSlot.SEPARATOR, ColorSlot.classify("vk_ui_separator_common"));
    }

    @Test public void leavesUnrelatedAttributesAlone() {
        assertNull(ColorSlot.classify("vk_ui_accent_azure"));        // palette swatch, not the accent role
        assertNull(ColorSlot.classify("vk_legacy_im_bubble_outgoing_expiring"));
        assertNull(ColorSlot.classify("textAppearance"));
        assertNull(ColorSlot.classify("vk_ui_typography_title_1"));
    }

    @Test public void variants() {
        assertTrue(ColorSlot.isStateVariant("vk_legacy_text_link_highlighted"));
        assertFalse(ColorSlot.isStateVariant("vk_legacy_accent"));
        assertTrue(ColorSlot.isAlphaVariant("vk_legacy_accent_alpha10"));
        assertFalse(ColorSlot.isAlphaVariant("vk_legacy_accent"));
    }

    @Test public void presetsAreWellFormed() {
        assertTrue("need 10+ presets", Presets.ALL.size() >= 10);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Preset p : Presets.ALL) {
            assertTrue("duplicate id " + p.id, ids.add(p.id));
            assertNotNull(p.light); assertNotNull(p.dark);
        }
        for (String need : new String[]{"amoled", "nord", "catppuccin", "dracula", "sepia", "solarized"}) assertTrue(need, ids.contains(need));
        assertEquals(0xFF000000, (int) Presets.byId("amoled").dark.get(ColorSlot.BACKGROUND));
        assertEquals("none", Presets.byId("does-not-exist").id);
    }

    @Test public void colorParsing() {
        assertEquals(0xFF112233, Palette.parse("#112233"));
        assertEquals(0x80112233, Palette.parse("80112233"));
        assertEquals("#FF112233", Palette.hex(0xFF112233));
    }
}
