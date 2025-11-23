package sh.harold.fulcrum.plugin.stash;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

public record StashView(List<ItemStack> items) {

    public StashView {
        List<ItemStack> normalized = items == null
            ? List.of()
            : items.stream()
            .filter(Objects::nonNull)
            .map(ItemStack::clone)
            .toList();
        items = List.copyOf(normalized);
    }
}
