package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.model.AbilityComponent;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ItemInstance {

    private final CustomItem definition;
    private final ItemStack stack;
    private final Map<StatId, Double> baseStats;
    private final Map<String, Integer> enchants;
    private final EnchantRegistry enchantRegistry;
    private final DurabilityState durabilityState;

    public ItemInstance(CustomItem definition, ItemStack stack, Map<StatId, Double> baseStats, Map<String, Integer> enchants, EnchantRegistry enchantRegistry, DurabilityState durabilityState) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.stack = Objects.requireNonNull(stack, "stack");
        this.baseStats = baseStats == null ? Map.of() : Map.copyOf(baseStats);
        this.enchants = enchants == null ? Map.of() : Map.copyOf(enchants);
        this.enchantRegistry = Objects.requireNonNull(enchantRegistry, "enchantRegistry");
        this.durabilityState = durabilityState;
    }

    public CustomItem definition() {
        return definition;
    }

    public ItemStack stack() {
        return stack;
    }

    public Map<StatId, Double> computeFinalStats() {
        if (durabilityState != null && durabilityState.defunct()) {
            return Map.of();
        }
        Map<StatId, Double> result = new HashMap<>(baseStats);
        double baseAttackDamage = result.getOrDefault(sh.harold.fulcrum.stats.core.StatIds.ATTACK_DAMAGE, 0.0);
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            EnchantDefinition definition = enchantRegistry.get(entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            Map<StatId, Double> bonuses = definition.bonusForLevel(entry.getValue(), baseAttackDamage);
            bonuses.forEach((statId, value) -> result.merge(statId, value, Double::sum));
        }
        return Map.copyOf(result);
    }

    public boolean hasAbilities() {
        return definition.component(ComponentType.ABILITY, AbilityComponent.class).isPresent();
    }

    public Map<String, Integer> enchants() {
        return enchants;
    }

    public Optional<DurabilityState> durability() {
        return Optional.ofNullable(durabilityState);
    }
}
