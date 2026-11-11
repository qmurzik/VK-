package re.sova.qmods;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IDEA-0017: every QVK switch in one short code, e.g. «QVK1-7HQ2K…», to copy to another phone or share.
 *
 * Each switch has a permanent number — its place in {@link #ORDER}. New switches are only ever appended, so a code made in an
 * older version still applies in a newer one (missing tail = untouched), and a removed switch is simply skipped. One bit per
 * switch, a checksum byte against typos, base32 without look-alike letters. The PIN lock is never part of the code.
 * Pure Java: unit-tested on the JVM.
 */
public final class Qvk3SettingsCode {
    public static final String PREFIX = "QVK1-";
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"; // Crockford base32: no I, L, O, U

    /** NEVER reorder or remove: append new switches at the end. */
    public static final String[] ORDER = {
            "dnrGlobal",
            "dnrPM",
            "dnrGroups",
            "dnrConvs",
            "dntGlobal",
            "dntPM",
            "dntGroups",
            "dntConvs",
            "voiceDownload",
            "disableVoipButtons",
            "replace_vk_reactions",
            "rounded_writebar",
            "swap_attachments_with_emoji",
            "hide_vmsg_button",
            "emojiHints",
            "undelete",
            "unedit",
            "disableBTHeadsetRecording",
            "voice_platform_codec",
            "voiceEnhance",
            "screenshot_dnr",
            "qvk_hide_dialog_greeting",
            "offline",
            "telemetry",
            "news_telemetry",
            "music_telemetry",
            "device_mask",
            "disableStories",
            "storyDNR",
            "storyAdblock",
            "__dbg_no_ads",
            "disableAdsInGroups",
            "disableRecommendedPosts",
            "disableClipsBlock",
            "disableSourcePosts",
            "for_you_as_feed",
            "poll_preview",
            "rightLikes",
            "feed_post_footer_redesign_v3",
            "feed_redesign_islands",
            "feed_repost_headers_redesign",
            "hidePostPromotion",
            "qvk_story_download_action",
            "bottomDescription",
            "transparentNavbar",
            "fullTime",
            "disable_nft_avatars",
            "disable_emoji_status",
            "forceShowDrawer",
            "drawer_ignore_navigation_level",
            "hide_bottom_navigation",
            "hide_drawer_dashboard",
            "disableServicesShowcase",
            "disableSuperAppForYou",
            "disableSuperPromo",
            "use_old_read_indicator",
            "disable_reactions",
            "disable_message_tooltip",
            "audio_saving_player_state",
            "vas_new_stickers_suggestion",
            "con_posting_redesign",
            "core_christmas_garland",
            "core_new_photo_editor_collage",
            "feed_double_tap_like",
            "qmods_lite_services",
            "qvk_hide_widgets_settings",
            "qvk_smooth_motion",
            "qvk_monet_colors",
            "qvk_hide_possible_friends",
            "qvk_system_emoji",
            "qvk_compact_community_tabs",
            "qvk_compact_dialogs",
            "qvk_hide_friends_contacts_prompt",
            "qvk_hide_friends_suggest_prompt",
            "qvk_compact_community_info",
            "autoCache",
            "shuffle_hint",
            "audio_service",
            "use_legacy_shuffle",
            "remove_catalog_banners",
            "show_my_music",
            "qmods_music_compat",
            "qvk_disable_audio_ads",
            "fingerUnlock",
            "vibrateOnTap",
            "sslPinning",
            "qvk_hide_vpn_detection",
            "qvk_background_delivery",
            "disableAway",
            "disableClipsAds",
            "disableVideoAds",
            "disable_push_warning",
            "disable_contacts_warning",
            "short_share_timeout",
            "export_server_auto",
            "qmods_announcements",
            "qmods_updates",
            "qmods_groups_compat",
            "multiaccount75",
            "sova_badges_enabled",
            "qvk3_send_retry",
            "qvk3_music_keep_after_close",
            "qvk3_hide_subscription_promo",
            "qvk3_feed_now",
            "qvk3_shake_report",
            "qvk3_update_prompt"
    };

    private Qvk3SettingsCode() {
    }

    public interface Values {
        /** null when the switch does not exist in this version. */
        Boolean get(String key);
    }

    public static String encode(Values v) {
        int n = ORDER.length;
        byte[] bits = new byte[(n + 7) / 8 + 1];
        for (int i = 0; i < n; i++) {
            Boolean b = v.get(ORDER[i]);
            if (b != null && b) bits[i / 8] |= (byte) (1 << (i % 8));
        }
        bits[bits.length - 1] = checksum(bits, bits.length - 1, n);
        return PREFIX + base32(bits);
    }

    /** key → value for every switch the code covers (in this version's ORDER); throws on a broken code. */
    public static Map<String, Boolean> decode(String code) {
        String c = code.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
        if (!c.startsWith(PREFIX)) throw new IllegalArgumentException("Это не код настроек QVK");
        byte[] bits = unbase32(c.substring(PREFIX.length()));
        if (bits.length < 2) throw new IllegalArgumentException("Код слишком короткий");
        int bytes = bits.length - 1;
        // the code knows only as many switches as its version had: full bytes; extra bits of the last byte are zero
        int n = Math.min(ORDER.length, bytes * 8);
        boolean ok = false;
        for (int len = Math.max(1, (bytes - 1) * 8 + 1); len <= bytes * 8 && !ok; len++) ok = checksum(bits, bytes, len) == bits[bytes];
        if (!ok) throw new IllegalArgumentException("Код повреждён — скопируйте его целиком");
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) out.put(ORDER[i], (bits[i / 8] & (1 << (i % 8))) != 0);
        return out;
    }

    static byte checksum(byte[] b, int len, int switches) {
        int h = 0x5A ^ switches;
        for (int i = 0; i < len; i++) h = ((h << 1) | (h >>> 7)) & 0xFF ^ (b[i] & 0xFF);
        return (byte) h;
    }

    static String base32(byte[] data) {
        StringBuilder s = new StringBuilder();
        int buf = 0, bits = 0;
        for (byte d : data) {
            buf = (buf << 8) | (d & 0xFF);
            bits += 8;
            while (bits >= 5) {
                s.append(ALPHABET.charAt((buf >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) s.append(ALPHABET.charAt((buf << (5 - bits)) & 31));
        return s.toString();
    }

    static byte[] unbase32(String s) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buf = 0, bits = 0;
        for (char ch : s.toCharArray()) {
            if (ch == '-') continue;
            int v = ALPHABET.indexOf(ch == 'O' ? '0' : ch == 'I' || ch == 'L' ? '1' : ch);
            if (v < 0) throw new IllegalArgumentException("Недопустимый символ в коде: " + ch);
            buf = (buf << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buf >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
