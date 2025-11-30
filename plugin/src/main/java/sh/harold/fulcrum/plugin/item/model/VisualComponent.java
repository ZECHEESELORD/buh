package sh.harold.fulcrum.plugin.item.model;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record VisualComponent(
    Component displayName,
    List<Component> flavor,
    ItemRarity rarity
) implements ItemComponent {

    public VisualComponent {
        displayName = Optional.ofNullable(displayName).orElse(Component.empty());
        flavor = flavor == null ? List.of() : List.copyOf(flavor);
        rarity = Optional.ofNullable(rarity).orElse(ItemRarity.COMMON);
    }

    public boolean hasDisplayName() {
        return displayName != null && !displayName.equals(Component.empty());
    }
}
