package com.vkmod.core;

/** One entry of the feature registry: what it is, where it lives in the UI and whether it is trustworthy. */
public final class Feature {
    public enum Category {
        APPEARANCE("Внешний вид"), ADS("Реклама и чистота"), MESSAGES("Сообщения"), MEDIA("Медиа"),
        PRIVACY("Приватность"), CONVENIENCE("Удобство"), MOD("Мод");
        public final String title;
        Category(String t) { title = t; }
    }

    /** OK = hook located and logic covered by tests. PARTIAL = works only in the described cases. */
    public enum Status { OK, PARTIAL }

    public enum Kind { TOGGLE, CHOICE, INT, TEXT, ACTION }

    public final String id;
    public final int number;
    public final String title;
    public final String description;
    public final Category category;
    public final Status status;
    public final String partialNote;
    public final Kind kind;
    public final boolean defaultOn;
    public final String[] options;      // CHOICE: option ids; labels in optionLabels
    public final String[] optionLabels;
    public final int min, max, defInt;  // INT
    public final String defString;      // CHOICE/TEXT default

    private Feature(Builder b) {
        id = b.id; number = b.number; title = b.title; description = b.desc; category = b.cat;
        status = b.status; partialNote = b.note; kind = b.kind; defaultOn = b.defOn;
        options = b.options; optionLabels = b.labels; min = b.min; max = b.max; defInt = b.defInt; defString = b.defStr;
    }

    public String enabledKey() { return "f." + id; }
    public String valueKey() { return "v." + id; }

    public static Builder of(String id, int number, String title, Category cat) { return new Builder(id, number, title, cat); }

    public static final class Builder {
        private final String id, title; private final int number; private final Category cat;
        private String desc = "", note = "", defStr = "";
        private Status status = Status.OK; private Kind kind = Kind.TOGGLE; private boolean defOn;
        private String[] options = new String[0], labels = new String[0];
        private int min, max = 100, defInt;

        private Builder(String id, int number, String title, Category cat) { this.id = id; this.number = number; this.title = title; this.cat = cat; }

        public Builder desc(String d) { desc = d; return this; }
        public Builder on() { defOn = true; return this; }
        public Builder partial(String why) { status = Status.PARTIAL; note = why; return this; }
        public Builder choice(String def, String... idLabelPairs) {
            kind = Kind.CHOICE; defStr = def;
            options = new String[idLabelPairs.length / 2]; labels = new String[idLabelPairs.length / 2];
            for (int i = 0; i + 1 < idLabelPairs.length; i += 2) { options[i / 2] = idLabelPairs[i]; labels[i / 2] = idLabelPairs[i + 1]; }
            return this;
        }
        public Builder integer(int min, int max, int def) { kind = Kind.INT; this.min = min; this.max = max; defInt = def; return this; }
        public Builder text(String def) { kind = Kind.TEXT; defStr = def; return this; }
        public Builder action() { kind = Kind.ACTION; return this; }
        public Feature build() { return new Feature(this); }
    }
}
