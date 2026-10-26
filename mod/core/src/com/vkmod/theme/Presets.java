package com.vkmod.theme;

import java.util.ArrayList;
import java.util.List;

/** Built-in themes. A palette is written as "slot=RRGGBB ..." using [ColorSlot] ids. */
public final class Presets {
    private Presets() {}

    public static final List<Preset> ALL = new ArrayList<>();

    static Palette pal(String spec) {
        Palette p = new Palette();
        if (spec == null) return p;
        for (String tok : spec.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            int eq = tok.indexOf('=');
            ColorSlot s = ColorSlot.byId(tok.substring(0, eq));
            if (s == null) throw new IllegalArgumentException("unknown slot in preset: " + tok);
            p.put(s, Palette.parse(tok.substring(eq + 1)));
        }
        return p;
    }

    private static void add(String id, String title, String desc, String light, String dark) {
        ALL.add(new Preset(id, title, desc, pal(light), pal(dark)));
    }

    static {
        add("none", "Стандарт ВК", "Без изменений: только ваши ручные цвета", "", "");
        add("amoled", "AMOLED", "Чистый чёрный для тёмной темы, экономит заряд", "",
                "background=000000 card=000000 header=000000 tabbar=000000 field=141414 separator=1F1F1F bubble_in=1A1A1A bubble_out=262A33 chat_bg=000000");
        add("classic", "Классика 2016", "Синяя шапка старого ВКонтакте",
                "accent=5181B8 header=4A76A8 header_text=FFFFFF background=EDEEF0 card=FFFFFF link=2A5885 text_secondary=828282 bubble_out=DCE6F2 bubble_in=F0F2F5 tabbar_active=4A76A8",
                "accent=71AAEB header=2B3A4B header_text=E1E3E6 background=141414 card=1E1F21 tabbar=1E1F21 bubble_out=2B3A4B bubble_in=2C2D2F");
        add("telegram", "Telegram", "Знакомые цвета мессенджера",
                "accent=3390EC bubble_out=EEFFDE bubble_in=FFFFFF background=F1F1F1 link=168ACD",
                "accent=8774E1 background=0F0F0F card=212121 header=212121 tabbar=212121 field=2C2C2C separator=2F2F2F bubble_out=766AC8 bubble_in=212121 text_secondary=AAAAAA");
        add("midnight", "Полночь", "Глубокий синий ночной режим",
                "accent=3D6FE0 background=EEF2FA bubble_out=D9E4FB",
                "accent=4F8CFF background=0B1220 card=111A2E header=111A2E tabbar=0E1626 field=18233A separator=1E2A44 bubble_out=1E3A6E bubble_in=18233A text=E6ECF5 text_secondary=8A97B0 icon=8A97B0");
        add("nord", "Nord", "Холодная арктическая палитра",
                "accent=5E81AC background=ECEFF4 card=F8F9FB header=F8F9FB tabbar=F8F9FB text=2E3440 text_secondary=4C566A field=E5E9F0 bubble_out=D8E3F0 bubble_in=E5E9F0",
                "accent=88C0D0 background=2E3440 card=3B4252 header=3B4252 tabbar=3B4252 field=434C5E separator=4C566A text=ECEFF4 text_secondary=B4BCCB icon=A5ADBA bubble_out=5E81AC bubble_in=434C5E");
        add("dracula", "Dracula", "Тёмная тема с фиолетовыми акцентами", "",
                "accent=BD93F9 link=8BE9FD background=21222C card=282A36 header=282A36 tabbar=21222C field=343746 separator=44475A text=F8F8F2 text_secondary=9EA3C2 icon=9EA3C2 bubble_out=4B3A78 bubble_in=343746");
        add("catppuccin", "Catppuccin", "Latte днём, Mocha ночью",
                "accent=1E66F5 link=1E66F5 background=EFF1F5 card=E6E9EF header=E6E9EF tabbar=E6E9EF field=CCD0DA separator=BCC0CC text=4C4F69 text_secondary=6C6F85 icon=6C6F85 bubble_out=C9D6FB bubble_in=DCE0E8",
                "accent=89B4FA link=89B4FA background=1E1E2E card=181825 header=181825 tabbar=11111B field=313244 separator=45475A text=CDD6F4 text_secondary=A6ADC8 icon=A6ADC8 bubble_out=45548B bubble_in=313244");
        add("solarized", "Solarized", "Классическая палитра Итана Шоулза",
                "accent=268BD2 link=268BD2 background=FDF6E3 card=EEE8D5 header=EEE8D5 tabbar=EEE8D5 field=EEE8D5 separator=D9D2BD text=586E75 text_secondary=657B83 icon=657B83 bubble_out=D4E4EE bubble_in=EEE8D5",
                "accent=268BD2 link=2AA198 background=002B36 card=073642 header=073642 tabbar=073642 field=0B3E4B separator=1A4E5B text=93A1A1 text_secondary=839496 icon=839496 bubble_out=134B63 bubble_in=073642");
        add("sepia", "Сепия", "Тёплая бумага для долгого чтения",
                "accent=8B5E3C background=F4ECD8 card=FBF5E6 header=FBF5E6 tabbar=FBF5E6 text=5B4636 text_secondary=8A7462 icon=8A7462 field=EFE4D0 separator=E2D4BC bubble_out=E8D5BF bubble_in=FBF5E6 link=7A4E2D",
                "accent=C89B6D background=17120E card=221A14 header=221A14 tabbar=221A14 text=EDE0D2 text_secondary=A8968A field=2C221A separator=3A2D22 bubble_out=4A3624 bubble_in=2C221A");
        add("gruvbox", "Gruvbox", "Ретро-палитра с тёплым контрастом",
                "accent=AF3A03 link=076678 background=FBF1C7 card=F2E5BC header=F2E5BC tabbar=F2E5BC text=3C3836 text_secondary=7C6F64 icon=7C6F64 field=EBDBB2 separator=D5C4A1 bubble_out=EBD9A6 bubble_in=F2E5BC",
                "accent=FABD2F link=83A598 background=282828 card=3C3836 header=3C3836 tabbar=32302F field=504945 separator=504945 text=EBDBB2 text_secondary=A89984 icon=A89984 bubble_out=6B5B1F bubble_in=3C3836");
        add("rosepine", "Rosé Pine", "Приглушённая роскошь",
                "accent=907AA9 link=286983 background=FAF4ED card=FFFAF3 header=FFFAF3 tabbar=FFFAF3 text=575279 text_secondary=797593 icon=797593 field=F2E9E1 separator=DFDAD9 bubble_out=EADFF0 bubble_in=F2E9E1",
                "accent=C4A7E7 link=9CCFD8 background=191724 card=1F1D2E header=1F1D2E tabbar=1F1D2E field=26233A separator=393552 text=E0DEF4 text_secondary=908CAA icon=908CAA bubble_out=403D63 bubble_in=26233A");
        add("mint", "Мята", "Свежие зелёные оттенки",
                "accent=2BB673 background=EEF7F2 bubble_out=D5F2E3 field=E3F2EA",
                "accent=3DDC97 background=0F1A15 card=15241D header=15241D tabbar=15241D field=1C2F26 separator=20352B bubble_out=1F4D38 bubble_in=1C2F26");
        add("sakura", "Сакура", "Нежно-розовая тема",
                "accent=E0558B background=FFF0F5 header=FFF7FA tabbar=FFF7FA bubble_out=FFD6E5 field=FBE4EC",
                "accent=FF7AA8 background=1A0F14 card=24141B header=24141B tabbar=24141B field=301B24 separator=3A2230 bubble_out=5A2340 bubble_in=301B24");
        add("sunset", "Закат", "Тёплые оранжевые тона",
                "accent=F2711C background=FFF5EC bubble_out=FFE2CC",
                "accent=FF8A3D background=1A120C card=241911 header=241911 tabbar=241911 field=2F2117 separator=3A2A1E bubble_out=5A3216 bubble_in=2F2117");
        add("ocean", "Океан", "Бирюзовые морские оттенки",
                "accent=0097A7 background=EAF6F8 bubble_out=CDEEF2",
                "accent=26C6DA background=071A1F card=0C252C header=0C252C tabbar=0C252C field=12313A separator=153843 bubble_out=0F4B57 bubble_in=12313A");
        add("lavender", "Лаванда", "Мягкий фиолетовый",
                "accent=7E57C2 background=F4F0FB bubble_out=E5DAF7 field=ECE5F7",
                "accent=B39DDB background=120F1A card=1B1726 header=1B1726 tabbar=1B1726 field=241E33 separator=2C2540 bubble_out=3D2E63 bubble_in=241E33");
        add("forest", "Лес", "Природные зелёные цвета",
                "accent=2E7D32 background=EEF5EE bubble_out=D7EBD8",
                "accent=7BC47F background=0E140F card=152017 header=152017 tabbar=152017 field=1C2B1F separator=223525 bubble_out=2D4D31 bubble_in=1C2B1F");
        add("mono", "Монохром", "Только оттенки серого",
                "accent=333333 link=111111 bubble_out=E6E6E6",
                "accent=E0E0E0 link=F5F5F5 background=101010 card=1A1A1A header=1A1A1A tabbar=1A1A1A bubble_out=3A3A3A bubble_in=242424");
    }

    public static Preset byId(String id) {
        for (Preset p : ALL) if (p.id.equals(id)) return p;
        return ALL.get(0);
    }
}
