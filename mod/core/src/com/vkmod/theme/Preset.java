package com.vkmod.theme;

public final class Preset {
    public final String id, title, description;
    public final Palette light, dark;

    public Preset(String id, String title, String description, Palette light, Palette dark) {
        this.id = id; this.title = title; this.description = description; this.light = light; this.dark = dark;
    }
}
