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
        enchants.forEach((id, level) -> {
            EnchantDefinition definition = enchantRegistry.get(id).orElse(null);
            if (definition == null) {
                return;
            }
            Map<StatId, Double> bonuses = definition.bonusForLevel(level, baseAttackDamage);
            if (!definition.condition().isAlways()) {
                return; // Avoid overstating conditionally applied stats in the unconditional view.
            }
            bonuses.forEach((statId, value) -> result.merge(statId, value, Double::sum));
        });
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

    public Map<String, Map<StatId, StatContribution>> statSources() {
        if (durabilityState != null && durabilityState.defunct()) {
            return Map.of();
        }
        Map<String, Map<StatId, StatContribution>> sources = new HashMap<>();
        if (!baseStats.isEmpty()) {
            Map<StatId, StatContribution> base = new HashMap<>();
            baseStats.forEach((statId, value) -> base.put(statId, StatContribution.of(value)));
            sources.put("base", Map.copyOf(base));
        }
        double baseAttackDamage = baseStats.getOrDefault(sh.harold.fulcrum.stats.core.StatIds.ATTACK_DAMAGE, 0.0);
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            EnchantDefinition definition = enchantRegistry.get(entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            Map<StatId, Double> bonus = definition.bonusForLevel(entry.getValue(), baseAttackDamage);
            if (!bonus.isEmpty()) {
                Map<StatId, StatContribution> conditioned = new HashMap<>();
                bonus.forEach((statId, value) -> conditioned.put(statId, new StatContribution(value, definition.condition())));
                sources.put("enchant:" + entry.getKey(), Map.copyOf(conditioned));
            }
        }
        return Map.copyOf(sources);
    }
}
