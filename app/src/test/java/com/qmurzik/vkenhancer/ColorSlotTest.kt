package com.qmurzik.vkenhancer

import com.qmurzik.vkenhancer.common.ColorSlot
import com.qmurzik.vkenhancer.common.ThemePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ColorSlotTest {

    @Test fun classifiesCanonicalTokensAcrossPrefixes() {
        // Same meaning, different vendor prefixes -> same slot.
        assertSame(ColorSlot.TEXT, ColorSlot.classify("vk_ui_text_primary"))
        assertSame(ColorSlot.TEXT, ColorSlot.classify("vk_legacy_text_primary"))
        assertSame(ColorSlot.CARD, ColorSlot.classify("vk_ui_background_content"))
        assertSame(ColorSlot.BACKGROUND, ColorSlot.classify("vk_ui_background"))
        assertSame(ColorSlot.ACCENT, ColorSlot.classify("vk_ui_background_accent"))
        assertSame(ColorSlot.TABBAR, ColorSlot.classify("vk_legacy_tabbar_background"))
        assertSame(ColorSlot.HEADER, ColorSlot.classify("vk_legacy_header_background"))
        assertSame(ColorSlot.BUBBLE_OUT, ColorSlot.classify("vkontakte_color_im_bubble_outgoing"))
        assertSame(ColorSlot.BUBBLE_IN, ColorSlot.classify("vk_legacy_im_bubble_incoming"))
    }

    @Test fun backgroundAndCardAreDistinct() {
        assertSame(ColorSlot.BACKGROUND, ColorSlot.classify("vk_ui_background"))
        assertSame(ColorSlot.CARD, ColorSlot.classify("vk_ui_background_content"))
    }

    @Test fun matchesStateVariants() {
        assertSame(ColorSlot.ACCENT, ColorSlot.classify("vk_ui_background_accent_hover"))
        assertSame(ColorSlot.TEXT, ColorSlot.classify("vk_ui_text_primary_alpha"))
        assert(ColorSlot.isStateVariant("vk_ui_background_secondary_active"))
    }

    @Test fun ignoresUnrelatedAndExpiringTokens() {
        assertNull(ColorSlot.classify("vk_ui_typography_title1"))
        assertNull(ColorSlot.classify("vk_legacy_im_bubble_incoming_expiring"))
        assertNull(ColorSlot.classify("random_attr_name"))
        // Palette swatch, not a semantic accent slot.
        assertNull(ColorSlot.classify("vk_ui_accent_azure"))
    }

    @Test fun presetsResolveAndFallBack() {
        assertSame(ThemePresets.ALL.first(), ThemePresets.byId("does-not-exist"))
        assertNotNull(ThemePresets.byId("amoled"))
        assertEquals("dracula", ThemePresets.byId("dracula").id)
        // AMOLED only recolours the dark palette.
        assert(ThemePresets.byId("amoled").light.isEmpty())
        assert(!ThemePresets.byId("amoled").dark.isEmpty())
    }
}
