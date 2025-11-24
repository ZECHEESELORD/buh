package sh.harold.fulcrum.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Lightweight tag markers to prefix outgoing messages.
 */
public enum MessageTag {
    STAFF(Component.text("[STAFF]", NamedTextColor.AQUA)),
    DEBUG(Component.text("[DEBUG]", NamedTextColor.DARK_GRAY));

    private final Component component;

    MessageTag(Component component) {
        this.component = component;
    }

    public Component component() {
        return component;
    }
}
