package com.vkmod;

import static org.junit.Assert.*;

import com.vkmod.core.Prefs;
import com.vkmod.core.Registry;
import com.vkmod.theme.ColorSlot;
import com.vkmod.theme.ThemeEngine;

import org.junit.Before;
import org.junit.Test;

/** Theme engine pipeline on the JVM: names -> slots -> palette (preset, AMOLED, custom, transparency). */
public class ThemeEngineTest {
    @Before public void setUp() {
        Prefs.attach(new FakeStore());
        Registry.ensureLoaded();
        Registry.setOn("theme_engine", true);
    }

    @Test public void presetRecolorsAccentPerBrightness() {
        Prefs.putString("v.theme_preset", "nord");
        assertEquals(0xFF88C0D0, ThemeEngine.applyColor("vk_legacy_accent", 0xFF2688EB, true));
        assertEquals(0xFF5E81AC, ThemeEngine.applyColor("vk_legacy_accent", 0xFF2688EB, false));
    }

    @Test public void alphaVariantsKeepOriginalTransparency() {
        Prefs.putString("v.theme_preset", "nord");
        assertEquals(0x3388C0D0, ThemeEngine.applyColor("vk_legacy_accent_alpha20", 0x332688EB, true));
    }

    @Test public void stateVariantsAreNudged() {
        Prefs.putString("v.theme_preset", "dracula");
        int base = ThemeEngine.applyColor("vk_legacy_text_link", 0xFF2688EB, true);
        int pressed = ThemeEngine.applyColor("vk_legacy_text_link_highlighted", 0xFF2688EB, true);
        assertEquals(0xFF8BE9FD, base);
        assertNotEquals(base, pressed);
    }

    @Test public void unrelatedAttributesAreUntouched() {
        Prefs.putString("v.theme_preset", "dracula");
        assertEquals(0xFF123456, ThemeEngine.applyColor("vk_ui_accent_azure", 0xFF123456, true));
        assertEquals(0xFF123456, ThemeEngine.applyColor("textAppearance", 0xFF123456, true));
    }

    @Test public void amoledOnlyAffectsDark() {
        Registry.setOn("theme_amoled", true);
        assertEquals(0xFF000000, ThemeEngine.applyColor("vk_ui_background", 0xFF19191A, true));
        assertEquals(0xFF000000, ThemeEngine.applyColor("vk_ui_background_content", 0xFF222222, true));
        assertEquals(0xFF000000, ThemeEngine.applyColor("vk_legacy_tabbar_background", 0xFF222222, true));
        assertEquals(0xFFEBEDF0, ThemeEngine.applyColor("vk_ui_background", 0xFFEBEDF0, false));
    }

    @Test public void customColorsBeatPresetAndAreScopedPerBrightness() {
        Prefs.putString("v.theme_preset", "nord");
        ThemeEngine.setCustom(ColorSlot.ACCENT, true, 0xFF123456);
        assertEquals(0xFF123456, ThemeEngine.applyColor("vk_legacy_accent", 0xFF000000, true));
        assertEquals(0xFF5E81AC, ThemeEngine.applyColor("vk_legacy_accent", 0xFF000000, false));
        ThemeEngine.setCustom(ColorSlot.ACCENT, true, null);
        assertEquals(0xFF88C0D0, ThemeEngine.applyColor("vk_legacy_accent", 0xFF000000, true));
    }

    @Test public void chatBackgroundAndBubblesHaveSlots() {
        ThemeEngine.setCustom(ColorSlot.CHAT_BG, false, 0xFF101010);
        ThemeEngine.setCustom(ColorSlot.BUBBLE_OUT, false, 0xFF202020);
        assertEquals(0xFF101010, ThemeEngine.applyColor("im_bg_chat", 0xFFFFFFFF, false));
        assertEquals(0xFF202020, ThemeEngine.applyColor("vk_legacy_im_bubble_outgoing", 0xFFFFFFFF, false));
    }

    @Test public void barsTransparencyOnlyForHeaderAndTabbar() {
        Registry.setOn("bars_alpha", true);
        Prefs.putInt("v.bars_alpha", 50);
        int header = ThemeEngine.applyColor("vk_legacy_header_background", 0xFFFFFFFF, false);
        assertEquals(0x80, header >>> 24, 1);
        assertEquals(0xFFFFFFFF, ThemeEngine.applyColor("vk_ui_background", 0xFFFFFFFF, false));
    }

    @Test public void engineOffMeansNoChange() {
        Prefs.putString("v.theme_preset", "dracula");
        Registry.setOn("theme_engine", false);
        assertEquals(0xFF2688EB, ThemeEngine.applyColor("vk_legacy_accent", 0xFF2688EB, true));
    }

    @Test public void themeExportImportRoundTrip() {
        Prefs.putString("v.theme_preset", "catppuccin");
        Registry.setOn("theme_amoled", true);
        ThemeEngine.setCustom(ColorSlot.LINK, false, 0xFF0A0B0C);
        ThemeEngine.setCustom(ColorSlot.TEXT, true, 0xFFFAFBFC);
        String json = ThemeEngine.exportTheme("test");
        assertNotNull(json);
        ThemeEngine.clearCustom();
        Prefs.putString("v.theme_preset", "none");
        Registry.setOn("theme_amoled", false);
        assertNull(ThemeEngine.importTheme(json));
        assertEquals("catppuccin", Registry.str("theme_preset"));
        assertTrue(Registry.isOn("theme_amoled"));
        assertEquals(0xFF0A0B0C, ThemeEngine.applyColor("vk_legacy_text_link", 0, false));
        assertEquals(0xFFFAFBFC, ThemeEngine.applyColor("vk_legacy_text_primary", 0, true));
    }

    @Test public void importRejectsForeignFiles() {
        assertNotNull(ThemeEngine.importTheme("{\"hello\":1}"));
        assertNotNull(ThemeEngine.importTheme("not json"));
    }

    @Test public void backupRoundTripRestoresEverything() {
        Registry.setOn("no_read", true);
        Prefs.putInt("v.text_scale", 130);
        Prefs.putString("v.keywords", "foo,bar");
        Registry.markCrashed("hide_reposts");
        String dump = Prefs.exportJson();
        assertFalse("runtime crash latches are not settings", dump.contains("crash."));
        Prefs.resetAll();
        assertFalse(Registry.isOn("no_read"));
        assertTrue(Prefs.importJson(dump) > 0);
        assertTrue(Registry.isOn("no_read"));
        assertEquals(130, Registry.num("text_scale"));
        assertEquals("foo,bar", Registry.str("keywords"));
        assertEquals(-1, Prefs.importJson("garbage"));
    }

    @Test public void crashLatchDisablesFeatureUntilReEnabled() {
        Registry.setOn("hide_reposts", true);
        assertTrue(Registry.isOn("hide_reposts"));
        com.vkmod.core.Guard.fail("hide_reposts", new RuntimeException("boom"));
        assertFalse(Registry.isOn("hide_reposts"));
        Registry.setOn("hide_reposts", true);
        assertTrue(Registry.isOn("hide_reposts"));
    }

    @Test public void guardSwallowsAndLatches() {
        Registry.setOn("keep_screen_on", true);
        int v = com.vkmod.core.Guard.call("keep_screen_on", 7, () -> { throw new IllegalStateException("x"); });
        assertEquals(7, v);
        assertFalse(Registry.isOn("keep_screen_on"));
    }
}
