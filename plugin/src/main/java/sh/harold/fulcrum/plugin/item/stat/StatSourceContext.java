package sh.harold.fulcrum.plugin.item.stat;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public record StatSourceContext(
    String name,
    String secondary,
    String description,
    ItemStack displayItem,
    SourceCategory category,
    String slotTag
) {
    public StatSourceContext {
        name = Objects.requireNonNullElse(name, "");
        secondary = Objects.requireNonNullElse(secondary, "");
        description = Objects.requireNonNullElse(description, "");
        displayItem = displayItem == null ? null : displayItem.clone();
        category = category == null ? SourceCategory.UNKNOWN : category;
        slotTag = slotTag == null ? "" : slotTag;
    }
}
