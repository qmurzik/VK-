package com.vkmod.theme;

import java.util.regex.Pattern;

/**
 * Semantic colour slots. VK colour attributes follow the VKUI token naming (vk_ui_*, vk_legacy_*,
 * vkontakte_color_*); a slot describes the *meaning* of a token through a regex over its name with the
 * vendor prefix removed, so a renamed prefix in a new VK build keeps matching.
 */
public enum ColorSlot {
    ACCENT("accent", "Акцент",
            "accent|accent_alternate|color_accent_alternate|background_accent(_themed|_alternative)?|icon_accent(_themed)?|"
                    + "text_accent(_themed|_active)?|stroke_accent(_themed)?|button_primary_background|button_outline_(border|foreground)|"
                    + "button_secondary_foreground|button_tertiary_foreground|header_(alternate_)?tab_active_indicator|"
                    + "activity_indicator_tint|loader_tint|im_attach_tint|control_background|segmented_control_active|"
                    + "colorAccent|colorPrimary|colorControlActivated|colorSecondary"),
    LINK("link", "Ссылки", "text_link(_themed|_tint|_in_primary|_in_secondary)?|link_contrast"),
    BACKGROUND("background", "Фон страниц", "background|background_page|landing_background"),
    CARD("card", "Фон карточек",
            "background_content|background_modal|modal_card_background|background_light|snippet_background|background_suggestions"),
    HEADER("header", "Шапка", "header_background|header_alternate_background|header_background_before_blur(_alternate)?"),
    HEADER_TEXT("header_text", "Текст и иконки шапки", "header_text|header_text_alternate|header_tint|header_tint_alternate|panel_header_icon"),
    TABBAR("tabbar", "Нижняя панель", "tabbar_background|tabbar_tablet_background"),
    TABBAR_ACTIVE("tabbar_active", "Активная вкладка", "tabbar_active_icon|tabbar_tablet_active_icon"),
    TABBAR_INACTIVE("tabbar_inactive", "Неактивные вкладки", "tabbar_inactive_icon|tabbar_tablet_inactive_icon|tabbar_text_inactive"),
    TEXT("text", "Основной текст", "text_primary|text_name|color_text_name|im_text_name"),
    TEXT_SECONDARY("text_secondary", "Второстепенный текст", "text_secondary|text_subhead|text_tertiary|text_muted|text_placeholder"),
    ICON("icon", "Иконки",
            "icon_primary|icon_secondary|icon_tertiary|icon_medium|icon_outline_medium|icon_outline_secondary|icon_name|writebar_icon|write_bar_icon"),
    SEPARATOR("separator", "Разделители", "separator_[a-z0-9_]+|im_reply_separator|im_toolbar_separator"),
    FIELD("field", "Поля ввода",
            "field_background|search_field_background|header_search_field_background|search_bar_background|write_bar_input_background"),
    CHAT_BG("chat_bg", "Фон чата", "im_bg_chat|im_chat_background|im_history_background"),
    BUBBLE_OUT("bubble_out", "Исходящие сообщения", "im_bubble_(mable_)?(wallpaper_)?outgoing(_alternate)?"),
    BUBBLE_IN("bubble_in", "Входящие сообщения", "im_bubble_(wallpaper_)?incoming(_alternate)?");

    public final String id;
    public final String title;
    private final Pattern regex;

    ColorSlot(String id, String title, String pattern) {
        this.id = id;
        this.title = title;
        this.regex = Pattern.compile("^(" + pattern + ")((_alpha\\d*)|(_highlighted|_active|_hover))?$");
    }

    private static final String[] PREFIXES = {"vk_ui_vkontakte_color_", "vk_ui_vkontakte_", "vk_ui_", "vk_legacy_", "vkontakte_color_", "vkontakte_", "vk_"};

    public static ColorSlot byId(String id) {
        for (ColorSlot s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    public static String normalize(String name) {
        for (String p : PREFIXES) if (name.startsWith(p)) return name.substring(p.length());
        return name;
    }

    /** The slot an attribute belongs to, or null when the mod must not touch it. */
    public static ColorSlot classify(String attrName) {
        String n = normalize(attrName);
        if (n.contains("expiring")) return null;
        if (n.startsWith("accent_") && !n.startsWith("accent_alpha") && !n.equals("accent_alternate")) return null;
        for (ColorSlot s : values()) if (s.regex.matcher(n).matches()) return s;
        return null;
    }

    public static boolean isStateVariant(String attrName) {
        String n = normalize(attrName);
        return n.endsWith("_highlighted") || n.endsWith("_active") || n.endsWith("_hover");
    }

    /** `_alphaNN` tokens keep the original transparency of the VK token. */
    public static boolean isAlphaVariant(String attrName) {
        return normalize(attrName).matches(".*_alpha\\d*$");
    }
}
