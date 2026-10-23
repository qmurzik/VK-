package com.qmurzik.vkenhancer.common

/**
 * Semantic colour slots the user can recolour.
 *
 * VK does not expose a stable colour API, but its theme attributes follow the VKUI token naming
 * (`vk_ui_background_content`, `vk_legacy_text_primary`, `vkontakte_color_im_bubble_outgoing`, ...).
 * Each slot therefore describes the *meaning* of a colour through a regular expression over the
 * attribute name with its vendor prefix stripped. New VK versions that add or rename prefixes keep
 * working as long as the token itself keeps its meaning.
 */
enum class ColorSlot(val id: String, val title: String, pattern: String) {
    ACCENT(
        "accent", "Акцент",
        "accent|accent_alternate|color_accent_alternate|background_accent(_themed|_alternative)?|" +
            "icon_accent(_themed)?|text_accent(_themed|_active)?|stroke_accent(_themed)?|" +
            "button_primary_background|button_outline_(border|foreground)|button_secondary_foreground|" +
            "button_tertiary_foreground|header_(alternate_)?tab_active_indicator|activity_indicator_tint|" +
            "loader_tint|im_attach_tint|control_background|segmented_control_active|" +
            "colorAccent|colorPrimary|colorControlActivated|colorSecondary"
    ),
    LINK("link", "Ссылки", "text_link(_themed|_tint|_in_primary|_in_secondary)?|link_contrast"),
    BACKGROUND("background", "Фон страниц", "background|background_page|landing_background"),
    CARD(
        "card", "Фон карточек",
        "background_content|background_modal|modal_card_background|background_light|snippet_background|" +
            "background_suggestions"
    ),
    HEADER("header", "Шапка", "header_background|header_alternate_background|header_background_before_blur(_alternate)?"),
    HEADER_TEXT(
        "header_text", "Текст и иконки шапки",
        "header_text|header_text_alternate|header_tint|header_tint_alternate|panel_header_icon"
    ),
    TABBAR("tabbar", "Нижняя панель", "tabbar_background|tabbar_tablet_background"),
    TABBAR_ACTIVE("tabbar_active", "Активная вкладка", "tabbar_active_icon|tabbar_tablet_active_icon"),
    TABBAR_INACTIVE(
        "tabbar_inactive", "Неактивные вкладки",
        "tabbar_inactive_icon|tabbar_tablet_inactive_icon|tabbar_text_inactive"
    ),
    TEXT("text", "Основной текст", "text_primary|text_name|color_text_name|im_text_name"),
    TEXT_SECONDARY(
        "text_secondary", "Второстепенный текст",
        "text_secondary|text_subhead|text_tertiary|text_muted|text_placeholder"
    ),
    ICON(
        "icon", "Иконки",
        "icon_primary|icon_secondary|icon_tertiary|icon_medium|icon_outline_medium|icon_outline_secondary|" +
            "icon_name|writebar_icon|write_bar_icon"
    ),
    SEPARATOR("separator", "Разделители", "separator_[a-z0-9_]+|im_reply_separator|im_toolbar_separator"),
    FIELD(
        "field", "Поля ввода",
        "field_background|search_field_background|header_search_field_background|search_bar_background|" +
            "write_bar_input_background"
    ),
    BUBBLE_OUT("bubble_out", "Исходящие сообщения", "im_bubble_(mable_)?(wallpaper_)?outgoing(_alternate)?"),
    BUBBLE_IN("bubble_in", "Входящие сообщения", "im_bubble_(wallpaper_)?incoming(_alternate)?");

    /** Matches a *normalised* attribute name; `_alpha`/`_alphaNN`/`_highlighted`/`_active` suffixes are variants. */
    val regex = Regex("^($pattern)((_alpha\\d*)|(_highlighted|_active|_hover))?$")

    companion object {
        private val PREFIXES = listOf("vk_ui_vkontakte_color_", "vk_ui_vkontakte_", "vk_ui_", "vk_legacy_", "vkontakte_color_", "vkontakte_", "vk_")

        fun byId(id: String) = entries.firstOrNull { it.id == id }

        /** Strips vendor prefixes so that `vk_legacy_text_primary` and `vk_ui_text_primary` look alike. */
        fun normalize(name: String): String {
            for (p in PREFIXES) if (name.startsWith(p)) return name.substring(p.length)
            return name
        }

        /** Finds the slot an attribute belongs to, or null when the module must not touch it. */
        fun classify(attrName: String): ColorSlot? {
            val n = normalize(attrName)
            // Expiring/disappearing bubbles and palette swatches have their own meaning.
            if (n.contains("expiring")) return null
            if (n.startsWith("accent_") && !n.startsWith("accent_alpha") && n != "accent_alternate") return null
            return entries.firstOrNull { it.regex.matches(n) }
        }

        /** True for derived variants (pressed/hovered) that should follow the base colour but stay distinguishable. */
        fun isStateVariant(attrName: String): Boolean {
            val n = normalize(attrName)
            return n.endsWith("_highlighted") || n.endsWith("_active") || n.endsWith("_hover")
        }
    }
}

/** A palette for one brightness mode: only the slots that differ from VK's own theme are listed. */
class Palette(val colors: Map<ColorSlot, Int>) {
    operator fun get(slot: ColorSlot): Int? = colors[slot]
    fun isEmpty() = colors.isEmpty()

    companion object {
        val EMPTY = Palette(emptyMap())
        fun of(vararg pairs: Pair<ColorSlot, Long>) = Palette(pairs.associate { it.first to it.second.toInt() })
    }
}

class ThemePreset(val id: String, val title: String, val description: String, val light: Palette, val dark: Palette)

object ThemePresets {
    val ALL: List<ThemePreset> = listOf(
        ThemePreset("none", "Стандарт ВК", "Без изменений — только ваши ручные цвета", Palette.EMPTY, Palette.EMPTY),
        ThemePreset(
            "amoled", "AMOLED", "Чистый чёрный для тёмной темы, экономит заряд",
            Palette.EMPTY,
            Palette.of(
                ColorSlot.BACKGROUND to 0xFF000000, ColorSlot.CARD to 0xFF000000, ColorSlot.HEADER to 0xFF000000,
                ColorSlot.TABBAR to 0xFF000000, ColorSlot.FIELD to 0xFF141414, ColorSlot.SEPARATOR to 0xFF1F1F1F,
                ColorSlot.BUBBLE_IN to 0xFF1A1A1A, ColorSlot.BUBBLE_OUT to 0xFF262A33,
            )
        ),
        ThemePreset(
            "classic", "Классика 2016", "Синяя шапка и спокойные цвета старого ВКонтакте",
            Palette.of(
                ColorSlot.ACCENT to 0xFF5181B8, ColorSlot.HEADER to 0xFF4A76A8, ColorSlot.HEADER_TEXT to 0xFFFFFFFF,
                ColorSlot.BACKGROUND to 0xFFEDEEF0, ColorSlot.CARD to 0xFFFFFFFF, ColorSlot.LINK to 0xFF2A5885,
                ColorSlot.TEXT_SECONDARY to 0xFF828282, ColorSlot.BUBBLE_OUT to 0xFFDCE6F2, ColorSlot.BUBBLE_IN to 0xFFF0F2F5,
                ColorSlot.TABBAR_ACTIVE to 0xFF4A76A8,
            ),
            Palette.of(
                ColorSlot.ACCENT to 0xFF71AAEB, ColorSlot.HEADER to 0xFF2B3A4B, ColorSlot.HEADER_TEXT to 0xFFE1E3E6,
                ColorSlot.BACKGROUND to 0xFF141414, ColorSlot.CARD to 0xFF1E1F21, ColorSlot.TABBAR to 0xFF1E1F21,
                ColorSlot.BUBBLE_OUT to 0xFF2B3A4B, ColorSlot.BUBBLE_IN to 0xFF2C2D2F,
            )
        ),
        ThemePreset(
            "telegram", "Telegram", "Знакомые цвета мессенджера",
            Palette.of(
                ColorSlot.ACCENT to 0xFF3390EC, ColorSlot.BUBBLE_OUT to 0xFFEEFFDE, ColorSlot.BUBBLE_IN to 0xFFFFFFFF,
                ColorSlot.BACKGROUND to 0xFFF1F1F1, ColorSlot.LINK to 0xFF168ACD,
            ),
            Palette.of(
                ColorSlot.ACCENT to 0xFF8774E1, ColorSlot.BACKGROUND to 0xFF0F0F0F, ColorSlot.CARD to 0xFF212121,
                ColorSlot.HEADER to 0xFF212121, ColorSlot.TABBAR to 0xFF212121, ColorSlot.FIELD to 0xFF2C2C2C,
                ColorSlot.SEPARATOR to 0xFF2F2F2F, ColorSlot.BUBBLE_OUT to 0xFF766AC8, ColorSlot.BUBBLE_IN to 0xFF212121,
                ColorSlot.TEXT_SECONDARY to 0xFFAAAAAA,
            )
        ),
        ThemePreset(
            "midnight", "Полночь", "Глубокий синий ночной режим",
            Palette.of(ColorSlot.ACCENT to 0xFF3D6FE0, ColorSlot.BACKGROUND to 0xFFEEF2FA, ColorSlot.BUBBLE_OUT to 0xFFD9E4FB),
            Palette.of(
                ColorSlot.ACCENT to 0xFF4F8CFF, ColorSlot.BACKGROUND to 0xFF0B1220, ColorSlot.CARD to 0xFF111A2E,
                ColorSlot.HEADER to 0xFF111A2E, ColorSlot.TABBAR to 0xFF0E1626, ColorSlot.FIELD to 0xFF18233A,
                ColorSlot.SEPARATOR to 0xFF1E2A44, ColorSlot.BUBBLE_OUT to 0xFF1E3A6E, ColorSlot.BUBBLE_IN to 0xFF18233A,
                ColorSlot.TEXT to 0xFFE6ECF5, ColorSlot.TEXT_SECONDARY to 0xFF8A97B0, ColorSlot.ICON to 0xFF8A97B0,
            )
        ),
        ThemePreset(
            "nord", "Nord", "Холодная арктическая палитра",
            Palette.of(
                ColorSlot.ACCENT to 0xFF5E81AC, ColorSlot.BACKGROUND to 0xFFECEFF4, ColorSlot.CARD to 0xFFF8F9FB,
                ColorSlot.HEADER to 0xFFF8F9FB, ColorSlot.TABBAR to 0xFFF8F9FB, ColorSlot.TEXT to 0xFF2E3440,
                ColorSlot.TEXT_SECONDARY to 0xFF4C566A, ColorSlot.FIELD to 0xFFE5E9F0, ColorSlot.BUBBLE_OUT to 0xFFD8E3F0,
                ColorSlot.BUBBLE_IN to 0xFFE5E9F0,
            ),
            Palette.of(
                ColorSlot.ACCENT to 0xFF88C0D0, ColorSlot.BACKGROUND to 0xFF2E3440, ColorSlot.CARD to 0xFF3B4252,
                ColorSlot.HEADER to 0xFF3B4252, ColorSlot.TABBAR to 0xFF3B4252, ColorSlot.FIELD to 0xFF434C5E,
                ColorSlot.SEPARATOR to 0xFF4C566A, ColorSlot.TEXT to 0xFFECEFF4, ColorSlot.TEXT_SECONDARY to 0xFFB4BCCB,
                ColorSlot.ICON to 0xFFA5ADBA, ColorSlot.BUBBLE_OUT to 0xFF5E81AC, ColorSlot.BUBBLE_IN to 0xFF434C5E,
            )
        ),
        ThemePreset(
            "dracula", "Dracula", "Тёмная тема с фиолетовыми акцентами",
            Palette.EMPTY,
            Palette.of(
                ColorSlot.ACCENT to 0xFFBD93F9, ColorSlot.LINK to 0xFF8BE9FD, ColorSlot.BACKGROUND to 0xFF21222C,
                ColorSlot.CARD to 0xFF282A36, ColorSlot.HEADER to 0xFF282A36, ColorSlot.TABBAR to 0xFF21222C,
                ColorSlot.FIELD to 0xFF343746, ColorSlot.SEPARATOR to 0xFF44475A, ColorSlot.TEXT to 0xFFF8F8F2,
                ColorSlot.TEXT_SECONDARY to 0xFF9EA3C2, ColorSlot.ICON to 0xFF9EA3C2, ColorSlot.BUBBLE_OUT to 0xFF4B3A78,
                ColorSlot.BUBBLE_IN to 0xFF343746,
            )
        ),
        ThemePreset(
            "mint", "Мята", "Свежие зелёные оттенки",
            Palette.of(
                ColorSlot.ACCENT to 0xFF2BB673, ColorSlot.BACKGROUND to 0xFFEEF7F2, ColorSlot.BUBBLE_OUT to 0xFFD5F2E3,
                ColorSlot.FIELD to 0xFFE3F2EA,
            ),
            Palette.of(
                ColorSlot.ACCENT to 0xFF3DDC97, ColorSlot.BACKGROUND to 0xFF0F1A15, ColorSlot.CARD to 0xFF15241D,
                ColorSlot.HEADER to 0xFF15241D, ColorSlot.TABBAR to 0xFF15241D, ColorSlot.FIELD to 0xFF1C2F26,
                ColorSlot.SEPARATOR to 0xFF20352B, ColorSlot.BUBBLE_OUT to 0xFF1F4D38, ColorSlot.BUBBLE_IN to 0xFF1C2F26,
            )
        ),
        ThemePreset(
            "sakura", "Сакура", "Нежно-розовая тема",
            Palette.of(
                ColorSlot.ACCENT to 0xFFE0558B, ColorSlot.BACKGROUND to 0xFFFFF0F5, ColorSlot.HEADER to 0xFFFFF7FA,
                ColorSlot.TABBAR to 0xFFFFF7FA, ColorSlot.BUBBLE_OUT to 0xFFFFD6E5, ColorSlot.FIELD to 0xFFFBE4EC,
            ),
            Palette.of(
                ColorSlot.ACCENT to 0xFFFF7AA8, ColorSlot.BACKGROUND to 0xFF1A0F14, ColorSlot.CARD to 0xFF24141B,
                ColorSlot.HEADER to 0xFF24141B, ColorSlot.TABBAR to 0xFF24141B, ColorSlot.FIELD to 0xFF301B24,
                ColorSlot.SEPARATOR to 0xFF3A2230, ColorSlot.BUBBLE_OUT to 0xFF5A2340, ColorSlot.BUBBLE_IN to 0xFF301B24,
            )
        ),
        ThemePreset(
            "sunset", "Закат", "Тёплые оранжевые тона",
            Palette.of(ColorSlot.ACCENT to 0xFFF2711C, ColorSlot.BACKGROUND to 0xFFFFF5EC, ColorSlot.BUBBLE_OUT to 0xFFFFE2CC),
            Palette.of(
                ColorSlot.ACCENT to 0xFFFF8A3D, ColorSlot.BACKGROUND to 0xFF1A120C, ColorSlot.CARD to 0xFF241911,
                ColorSlot.HEADER to 0xFF241911, ColorSlot.TABBAR to 0xFF241911, ColorSlot.FIELD to 0xFF2F2117,
                ColorSlot.SEPARATOR to 0xFF3A2A1E, ColorSlot.BUBBLE_OUT to 0xFF5A3216, ColorSlot.BUBBLE_IN to 0xFF2F2117,
            )
        ),
        ThemePreset(
            "ocean", "Океан", "Бирюзовые морские оттенки",
            Palette.of(ColorSlot.ACCENT to 0xFF0097A7, ColorSlot.BACKGROUND to 0xFFEAF6F8, ColorSlot.BUBBLE_OUT to 0xFFCDEEF2),
            Palette.of(
                ColorSlot.ACCENT to 0xFF26C6DA, ColorSlot.BACKGROUND to 0xFF071A1F, ColorSlot.CARD to 0xFF0C252C,
                ColorSlot.HEADER to 0xFF0C252C, ColorSlot.TABBAR to 0xFF0C252C, ColorSlot.FIELD to 0xFF12313A,
                ColorSlot.SEPARATOR to 0xFF153843, ColorSlot.BUBBLE_OUT to 0xFF0F4B57, ColorSlot.BUBBLE_IN to 0xFF12313A,
            )
        ),
        ThemePreset(
            "lavender", "Лаванда", "Мягкий фиолетовый",
            Palette.of(
                ColorSlot.ACCENT to 0xFF7E57C2, ColorSlot.BACKGROUND to 0xFFF4F0FB, ColorSlot.BUBBLE_OUT to 0xFFE5DAF7,
                ColorSlot.FIELD to 0xFFECE5F7,
            ),
            Palette.of(
                ColorSlot.ACCENT to 0xFFB39DDB, ColorSlot.BACKGROUND to 0xFF120F1A, ColorSlot.CARD to 0xFF1B1726,
                ColorSlot.HEADER to 0xFF1B1726, ColorSlot.TABBAR to 0xFF1B1726, ColorSlot.FIELD to 0xFF241E33,
                ColorSlot.SEPARATOR to 0xFF2C2540, ColorSlot.BUBBLE_OUT to 0xFF3D2E63, ColorSlot.BUBBLE_IN to 0xFF241E33,
            )
        ),
        ThemePreset(
            "forest", "Лес", "Природные зелёные цвета",
            Palette.of(ColorSlot.ACCENT to 0xFF2E7D32, ColorSlot.BACKGROUND to 0xFFEEF5EE, ColorSlot.BUBBLE_OUT to 0xFFD7EBD8),
            Palette.of(
                ColorSlot.ACCENT to 0xFF7BC47F, ColorSlot.BACKGROUND to 0xFF0E140F, ColorSlot.CARD to 0xFF152017,
                ColorSlot.HEADER to 0xFF152017, ColorSlot.TABBAR to 0xFF152017, ColorSlot.FIELD to 0xFF1C2B1F,
                ColorSlot.SEPARATOR to 0xFF223525, ColorSlot.BUBBLE_OUT to 0xFF2D4D31, ColorSlot.BUBBLE_IN to 0xFF1C2B1F,
            )
        ),
        ThemePreset(
            "coffee", "Кофе", "Тёплая сепия для чтения",
            Palette.of(
                ColorSlot.ACCENT to 0xFF8B5E3C, ColorSlot.BACKGROUND to 0xFFF4ECE1, ColorSlot.CARD to 0xFFFBF6EF,
                ColorSlot.HEADER to 0xFFFBF6EF, ColorSlot.TABBAR to 0xFFFBF6EF, ColorSlot.TEXT to 0xFF3B2A1E,
                ColorSlot.TEXT_SECONDARY to 0xFF7A6656, ColorSlot.ICON to 0xFF8A7462, ColorSlot.FIELD to 0xFFEFE4D6,
                ColorSlot.SEPARATOR to 0xFFE2D4C2, ColorSlot.BUBBLE_OUT to 0xFFE8D5BF, ColorSlot.BUBBLE_IN to 0xFFFBF6EF,
            ),
            Palette.of(
                ColorSlot.ACCENT to 0xFFC89B6D, ColorSlot.BACKGROUND to 0xFF17120E, ColorSlot.CARD to 0xFF221A14,
                ColorSlot.HEADER to 0xFF221A14, ColorSlot.TABBAR to 0xFF221A14, ColorSlot.TEXT to 0xFFEDE0D2,
                ColorSlot.TEXT_SECONDARY to 0xFFA8968A, ColorSlot.FIELD to 0xFF2C221A, ColorSlot.SEPARATOR to 0xFF3A2D22,
                ColorSlot.BUBBLE_OUT to 0xFF4A3624, ColorSlot.BUBBLE_IN to 0xFF2C221A,
            )
        ),
        ThemePreset(
            "mono", "Монохром", "Без цвета — только оттенки серого",
            Palette.of(ColorSlot.ACCENT to 0xFF333333, ColorSlot.LINK to 0xFF111111, ColorSlot.BUBBLE_OUT to 0xFFE6E6E6),
            Palette.of(
                ColorSlot.ACCENT to 0xFFE0E0E0, ColorSlot.LINK to 0xFFF5F5F5, ColorSlot.BACKGROUND to 0xFF101010,
                ColorSlot.CARD to 0xFF1A1A1A, ColorSlot.HEADER to 0xFF1A1A1A, ColorSlot.TABBAR to 0xFF1A1A1A,
                ColorSlot.BUBBLE_OUT to 0xFF3A3A3A, ColorSlot.BUBBLE_IN to 0xFF242424,
            )
        ),
    )

    fun byId(id: String?): ThemePreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
