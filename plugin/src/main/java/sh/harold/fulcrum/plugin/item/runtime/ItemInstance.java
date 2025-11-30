package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.model.AbilityComponent;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.StatsComponent;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ItemInstance {

    private final CustomItem definition;
    private final ItemStack stack;

    public ItemInstance(CustomItem definition, ItemStack stack) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.stack = Objects.requireNonNull(stack, "stack");
    }

    public CustomItem definition() {
        return definition;
    }

    public ItemStack stack() {
        return stack;
    }

    public Map<StatId, Double> computeFinalStats() {
        Map<StatId, Double> stats = new HashMap<>();
        StatsComponent statsComponent = definition.component(ComponentType.STATS, StatsComponent.class).orElse(null);
        if (statsComponent != null) {
            stats.putAll(statsComponent.baseStats());
        }
        return Collections.unmodifiableMap(stats);
    }

    public boolean hasAbilities() {
        return definition.component(ComponentType.ABILITY, AbilityComponent.class).isPresent();
    }
}
