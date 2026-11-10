package re.sova.qmods;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * QVK 3.0 design tokens (pure Java, unit-tested).
 *
 * A token set is the single source of truth for a visual style. It is compiled into the formats QVK's
 * existing appearance engine already applies to VK 8.100 screens:
 *   - colours + "appearance" → a "sova-vk-theme-1" theme (ThemePalette.apply);
 *   - shape/density/motion → existing QVK keys (iOS / Material components, compact dialogs, motion blur).
 * So a preset changes the real VK interface consistently instead of patching single views.
 *
 * Every compiled theme passes {@link #ensureContrast}: text colours are corrected to WCAG AA (4.5:1)
 * against the backgrounds they are drawn on, which addresses the low-contrast reports for some themes.
 */
public final class Qvk3DesignTokens {
    public final String id;
    public final String title;
    public final String description;
    public final boolean dark;
    public final int accent, page, content, header, text, text2, icon, separator, highlight, incoming, outgoing;
    /** Corner radius in dp for cards/bubbles (0–28, QVK ThemeStyle limit). */
    public final int radius;
    /** Text scale in percent (90–125). */
    public final int textScale;
    public final String font;
    /** "solid" | "gradient" | "dots" | "grid" (ThemeStyle backgrounds). */
    public final String background;
    /** "compact" | "normal" | "comfortable". */
    public final String density;
    /** "flat" | "ios" | "material" — which component family QVK uses for buttons, lists, headers. */
    public final String components;
    /** Heavy effects (motion blur, translucent layers); auto-disabled on weak devices. */
    public final boolean heavy;

    public Qvk3DesignTokens(String id, String title, String description, boolean dark, int accent, int page, int content,
                            int header, int text, int text2, int icon, int separator, int highlight, int incoming,
                            int outgoing, int radius, int textScale, String font, String background, String density,
                            String components, boolean heavy) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.dark = dark;
        this.accent = accent;
        this.page = page;
        this.content = content;
        this.header = header;
        this.text = text;
        this.text2 = text2;
        this.icon = icon;
        this.separator = separator;
        this.highlight = highlight;
        this.incoming = incoming;
        this.outgoing = outgoing;
        this.radius = Math.max(0, Math.min(28, radius));
        this.textScale = Math.max(90, Math.min(125, textScale));
        this.font = font;
        this.background = background;
        this.density = density;
        this.components = components;
        this.heavy = heavy;
    }

    private static Qvk3DesignTokens p(String id, String title, String d, boolean dark, int accent, int page, int content,
                                      int header, int text, int text2, int sep, int hi, int in, int out, int radius,
                                      int scale, String font, String bg, String density, String comp, boolean heavy) {
        return new Qvk3DesignTokens(id, title, d, dark, accent, page, content, header, text, text2, text2, sep, hi, in, out,
                radius, scale, font, bg, density, comp, heavy);
    }

    /** Built-in presets. Each one changes colours, shape, density and component family. */
    public static final Map<String, Qvk3DesignTokens> PRESETS = new LinkedHashMap<>();

    static {
        put(p("classic", "QVK Classic", "Привычный ВК с аккуратными улучшениями", false,
                0xFF2688EB, 0xFFEBEDF0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFF818C99, 0xFFDCE1E6, 0xFFF0F2F5,
                0xFFFFFFFF, 0xFFCCE4FF, 12, 100, "system", "solid", "normal", "flat", false));
        put(p("modern", "QVK Modern", "Минимализм, крупные заголовки, мягкие формы", false,
                0xFF3F8AE0, 0xFFF6F7F9, 0xFFFFFFFF, 0xFFF6F7F9, 0xFF111214, 0xFF6D7380, 0xFFE8EAED, 0xFFEEF2F8,
                0xFFFFFFFF, 0xFFDDEBFF, 20, 100, "inter", "solid", "normal", "flat", false));
        put(p("glass", "QVK Glass", "Полупрозрачные поверхности и мягкий градиент", true,
                0xFF7AB8FF, 0xFF0F1724, 0xCC1B2536, 0xB31B2536, 0xFFF2F6FC, 0xFFA9B6C8, 0x33FFFFFF, 0x33FFFFFF,
                0xCC243248, 0xCC2F5A8F, 24, 100, "system", "gradient", "normal", "flat", true));
        put(p("amoled", "QVK AMOLED", "Чистый чёрный фон для OLED-экранов", true,
                0xFF71AAEB, 0xFF000000, 0xFF000000, 0xFF000000, 0xFFF5F5F5, 0xFFA8A8A8, 0xFF1F1F1F, 0xFF141414,
                0xFF161616, 0xFF1E3A5C, 16, 100, "system", "solid", "normal", "flat", false));
        put(p("compact", "QVK Compact", "Меньше отступов — больше информации на экране", false,
                0xFF2688EB, 0xFFF2F3F5, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFF6F7985, 0xFFDCE1E6, 0xFFEEF0F3,
                0xFFFFFFFF, 0xFFD6E8FF, 6, 92, "condensed", "solid", "compact", "flat", false));
        put(p("cozy", "QVK Cozy", "Тёплые мягкие цвета и спокойный интерфейс", false,
                0xFFC07A4F, 0xFFF7F1EA, 0xFFFFFBF6, 0xFFFFFBF6, 0xFF2E2620, 0xFF7C6E62, 0xFFEADFD3, 0xFFF1E6DA,
                0xFFFFFBF6, 0xFFF3DCC8, 22, 104, "noto_serif", "gradient", "comfortable", "flat", false));
        put(p("neon", "QVK Neon", "Тёмный фон и выразительные неоновые акценты", true,
                0xFFFF2BD6, 0xFF0B0B14, 0xFF14142A, 0xFF0B0B14, 0xFFF4F0FF, 0xFFB7A9E8, 0xFF2A2350, 0xFF221D44,
                0xFF1B1838, 0xFF3D1F66, 18, 100, "inter", "grid", "normal", "flat", true));
        put(p("ios", "QVK iOS", "Элементы iOS-эстетики: группы, крупные заголовки, плавность", false,
                0xFF007AFF, 0xFFF2F2F7, 0xFFFFFFFF, 0xFFF9F9F9, 0xFF000000, 0xFF8A8A8E, 0xFFC6C6C8, 0xFFE5E5EA,
                0xFFE9E9EB, 0xFF007AFF, 14, 100, "system", "solid", "normal", "ios", false));
    }

    private static void put(Qvk3DesignTokens t) {
        PRESETS.put(t.id, t);
    }

    // ---------------------------------------------------------------- colour maths

    public static double luminance(int c) {
        double r = lin(((c >> 16) & 0xff) / 255.0), g = lin(((c >> 8) & 0xff) / 255.0), b = lin((c & 0xff) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Composites a translucent colour over an opaque one. */
    public static int over(int fg, int bg) {
        int a = (fg >>> 24);
        if (a == 255) return fg;
        double f = a / 255.0;
        int r = (int) Math.round(((fg >> 16) & 0xff) * f + ((bg >> 16) & 0xff) * (1 - f));
        int g = (int) Math.round(((fg >> 8) & 0xff) * f + ((bg >> 8) & 0xff) * (1 - f));
        int b = (int) Math.round((fg & 0xff) * f + (bg & 0xff) * (1 - f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static double contrast(int fg, int bg) {
        bg = over(bg, 0xFF000000 | bg);
        fg = over(fg, bg);
        double l1 = luminance(fg), l2 = luminance(bg);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    /** Black or white, whichever reads better on {@code bg}. */
    public static int onColor(int bg) {
        // White wins whenever it is readable (≥3:1, buttons use bold/large text): accent buttons stay white-on-colour.
        return contrast(0xFFFFFFFF, bg) >= 3.0 || contrast(0xFFFFFFFF, bg) >= contrast(0xFF000000, bg) ? 0xFFFFFFFF : 0xFF000000;
    }

    /** Moves {@code fg} towards black/white until it reaches {@code ratio} against {@code bg}. */
    public static int ensureContrast(int fg, int bg, double ratio) {
        int solidBg = over(bg, 0xFF000000 | bg);
        if (contrast(fg, solidBg) >= ratio) return fg;
        int target = onColor(solidBg);
        int out = fg;
        for (int step = 1; step <= 20 && contrast(out, solidBg) < ratio; step++) out = mix(fg, target, step / 20f);
        return out | 0xFF000000;
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xff) * (1 - t) + ((b >> 16) & 0xff) * t);
        int g = Math.round(((a >> 8) & 0xff) * (1 - t) + ((b >> 8) & 0xff) * t);
        int bl = Math.round((a & 0xff) * (1 - t) + (b & 0xff) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static String hex(int c) {
        return String.format(Locale.ROOT, "#%08X", c);
    }

    // ---------------------------------------------------------------- compile / (de)serialise

    /** Contrast-checked copy of these tokens. */
    public Qvk3DesignTokens checked() {
        int t1 = ensureContrast(text, content, 4.5);
        t1 = ensureContrast(t1, page, 4.5);
        int t2 = ensureContrast(text2, content, 3.0);
        int ic = ensureContrast(icon, content, 3.0);
        int acc = ensureContrast(accent, content, 3.0);
        return new Qvk3DesignTokens(id, title, description, dark, acc, page, content, header, t1, t2, ic, separator,
                highlight, incoming, outgoing, radius, textScale, font, background, density, components, heavy);
    }

    /**
     * Counterpart of this style for the other VK base theme. QVK's palette overrides only 9 colours on top of
     * VK's current light/dark theme, so a style must exist in both variants; otherwise a dark palette over a
     * light VK theme leaves hundreds of light elements (the "перекрас" bugs).
     */
    public Qvk3DesignTokens variant(boolean wantDark) {
        if (wantDark == dark) return this;
        if (wantDark) {
            int page = mix(0xFF0E0F12, accent, 0.04f), content = mix(0xFF1B1C20, accent, 0.05f), in = 0xFF26272C;
            return new Qvk3DesignTokens(id, title, description, true, accent, page, content, content, 0xFFEDEEF0, 0xFF9A9DA5,
                    0xFF9A9DA5, 0xFF2B2C31, mix(content, accent, 0.15f), in, mix(in, accent, 0.45f), radius, textScale, font,
                    background, density, components, heavy);
        }
        int content = 0xFFFFFFFF;
        return new Qvk3DesignTokens(id, title, description, false, accent, 0xFFF2F3F5, content, content, 0xFF000000, 0xFF818C99,
                0xFF818C99, 0xFFDCE1E6, mix(content, accent, 0.10f), 0xFFFFFFFF, mix(content, accent, 0.22f), radius, textScale, font,
                background, density, components, heavy);
    }

    /** Theme JSON understood by QVK's ThemePalette.apply (format sova-vk-theme-1). */
    public JSONObject toTheme() {
        Qvk3DesignTokens t = checked();
        try {
            JSONObject colors = new JSONObject()
                    .put("accent", hex(t.accent)).put("background_page", hex(over(t.page, 0xFF000000)))
                    .put("background_content", hex(over(t.content, over(t.page, 0xFF000000))))
                    .put("header_background", hex(over(t.header, over(t.page, 0xFF000000))))
                    .put("text_primary", hex(t.text)).put("text_secondary", hex(t.text2))
                    .put("icon_secondary", hex(t.icon)).put("separator_common", hex(over(t.separator, over(t.content, 0xFF000000))))
                    .put("background_highlighted", hex(over(t.highlight, over(t.content, 0xFF000000))));
            JSONObject appearance = new JSONObject()
                    .put("category", id.equals("amoled") ? "amoled" : dark ? "dark" : "light")
                    .put("font", font).put("emoji", "vk").put("background", background)
                    .put("background_color", hex(over(t.page, 0xFF000000)))
                    .put("background_end", hex(over(t.highlight, over(t.page, 0xFF000000))))
                    .put("incoming", hex(over(t.incoming, over(t.page, 0xFF000000))))
                    .put("outgoing", hex(over(t.outgoing, over(t.page, 0xFF000000))))
                    .put("text_scale", t.textScale).put("radius", t.radius).put("angle", 135);
            return new JSONObject().put("format", "sova-vk-theme-1").put("colors", colors).put("appearance", appearance);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Boolean keys of QVK's existing component engines that this style turns on/off. */
    public Map<String, Boolean> componentFlags(boolean weakDevice) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        boolean ios = components.equals("ios"), material = components.equals("material");
        // Only the chosen family is switched on; colour overrides of those engines stay off so that the
        // token palette is the single source of colour (two engines recolouring the same views flickered).
        m.put("qvk_ios_enabled", ios);
        for (String k : new String[]{"qvk_ios_typography", "qvk_ios_headers", "qvk_ios_dock", "qvk_ios_chats", "qvk_ios_cards", "qvk_ios_motion", "qvk_ios_controls"}) {
            m.put(k, ios);
        }
        m.put("qvk_ios_colors", false);
        m.put("qvk_material3_enabled", material);
        for (String k : new String[]{"qvk_material3_shapes", "qvk_material3_navigation", "qvk_material3_motion"}) m.put(k, material);
        m.put("qvk_material3_colors", false);
        m.put("qvk_material3_dynamic", false);
        m.put("qvk_monet_colors", false);
        m.put("qvk_compact_dialogs", density.equals("compact"));
        m.put("qvk_motion_blur", heavy && !weakDevice);
        return m;
    }

    public JSONObject toJson() {
        try {
            return new JSONObject().put("id", id).put("title", title).put("description", description).put("dark", dark)
                    .put("accent", accent).put("page", page).put("content", content).put("header", header)
                    .put("text", text).put("text2", text2).put("icon", icon).put("separator", separator)
                    .put("highlight", highlight).put("incoming", incoming).put("outgoing", outgoing)
                    .put("radius", radius).put("textScale", textScale).put("font", font).put("background", background)
                    .put("density", density).put("components", components).put("heavy", heavy);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Qvk3DesignTokens fromJson(JSONObject o, Qvk3DesignTokens base) {
        return new Qvk3DesignTokens(o.optString("id", base.id), o.optString("title", base.title),
                o.optString("description", base.description), o.optBoolean("dark", base.dark),
                o.optInt("accent", base.accent), o.optInt("page", base.page), o.optInt("content", base.content),
                o.optInt("header", base.header), o.optInt("text", base.text), o.optInt("text2", base.text2),
                o.optInt("icon", base.icon), o.optInt("separator", base.separator), o.optInt("highlight", base.highlight),
                o.optInt("incoming", base.incoming), o.optInt("outgoing", base.outgoing), o.optInt("radius", base.radius),
                o.optInt("textScale", base.textScale), oneOf(o.optString("font", base.font), ThemeFonts.ALL, base.font),
                oneOf(o.optString("background", base.background), new String[]{"solid", "gradient", "dots", "grid"}, base.background),
                oneOf(o.optString("density", base.density), new String[]{"compact", "normal", "comfortable"}, base.density),
                oneOf(o.optString("components", base.components), new String[]{"flat", "ios", "material"}, base.components),
                o.optBoolean("heavy", base.heavy));
    }

    /** Fonts accepted by QVK's ThemeStyle (kept here so the pure class does not depend on Android). */
    public static final class ThemeFonts {
        public static final String[] ALL = {"system", "sans", "serif", "mono", "condensed", "inter", "open_sans", "noto_sans", "noto_serif", "dejavu_mono"};
        public static final String[] TITLES = {"Системный", "Без засечек", "С засечками", "Моноширинный", "Узкий", "Inter", "Open Sans", "Noto Sans", "Noto Serif", "DejaVu Mono"};
    }

    static String oneOf(String v, String[] allowed, String def) {
        for (String a : allowed) if (a.equals(v)) return v;
        return def;
    }

    /** Builder-style copy used by the visual editor. */
    public Qvk3DesignTokens with(String field, Object value) {
        JSONObject o = toJson();
        try {
            o.put(field, value);
            o.put("id", "custom");
            o.put("title", "Свой стиль");
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return fromJson(o, this);
    }
}
