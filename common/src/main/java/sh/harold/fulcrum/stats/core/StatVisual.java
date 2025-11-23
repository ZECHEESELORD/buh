package sh.harold.fulcrum.stats.core;

import java.util.Objects;

public record StatVisual(String icon, String color) {

    private static final StatVisual EMPTY = new StatVisual("", "");

    public StatVisual {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(color, "color");
        icon = icon.trim();
        color = color.trim();
    }

    public static StatVisual empty() {
        return EMPTY;
    }

    public boolean hasIcon() {
        return !icon.isEmpty();
    }

    public boolean hasColor() {
        return !color.isEmpty();
    }
}
