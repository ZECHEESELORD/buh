package sh.harold.fulcrum.message;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Minimal styling for outbound messages.
 */
public enum MessageStyle {
    SUCCESS(NamedTextColor.GREEN),
    INFO(NamedTextColor.GRAY),
    DEBUG(NamedTextColor.DARK_GRAY),
    ERROR(NamedTextColor.RED);

    private final NamedTextColor bodyColor;

    MessageStyle(NamedTextColor bodyColor) {
        this.bodyColor = bodyColor;
    }

    public NamedTextColor bodyColor() {
        return bodyColor;
    }
}
