package sh.harold.fulcrum.plugin.item.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.fulcrum.plugin.item.ability.AbilityDefinition;
import sh.harold.fulcrum.plugin.item.ability.AbilityTrigger;
import sh.harold.fulcrum.plugin.item.model.AbilityComponent;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.LoreSection;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.stats.core.StatId;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ItemLoreRenderer {

    private static final DecimalFormat STAT_FORMAT = new DecimalFormat("#.##");
    private final ItemResolver resolver;

    public ItemLoreRenderer(ItemResolver resolver) {
        this.resolver = resolver;
    }

    public ItemStack render(ItemStack stack, Player viewer) {
        return resolver.resolve(stack).map(instance -> renderInstance(instance, viewer)).orElse(stack);
    }

    private ItemStack renderInstance(ItemInstance instance, Player viewer) {
        ItemStack clone = instance.stack().clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return clone;
        }

        CustomItem definition = instance.definition();
        VisualComponent visual = definition.component(ComponentType.VISUAL, VisualComponent.class).orElse(null);
        Component name = visual != null && visual.hasDisplayName()
            ? visual.displayName()
            : Component.text(definition.id(), NamedTextColor.WHITE);
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        for (LoreSection section : definition.loreLayout()) {
            switch (section) {
                case HEADER -> {
                    // header handled via display name
                }
                case RARITY -> addRarity(lore, visual);
                case TAGS -> addTags(lore, definition);
                case PRIMARY_STATS -> addStats(lore, instance);
                case ABILITIES -> addAbilities(lore, instance);
                case FOOTER -> addFlavor(lore, visual);
            }
        }
        meta.lore(lore);
        clone.setItemMeta(meta);
        return clone;
    }

    private void addTags(List<Component> lore, CustomItem definition) {
        if (definition.traits().isEmpty()) {
            return;
        }
        Component tags = Component.text("Traits: ", NamedTextColor.GRAY)
            .append(Component.text(String.join(", ",
                definition.traits().stream().map(Enum::name).toList()), NamedTextColor.WHITE));
        lore.add(tags);
    }

    private void addStats(List<Component> lore, ItemInstance instance) {
        Map<StatId, Double> stats = instance.computeFinalStats();
        if (stats.isEmpty()) {
            return;
        }
        stats.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().value()))
            .forEach(entry -> lore.add(Component.text(
                entry.getKey().value() + ": " + STAT_FORMAT.format(entry.getValue()),
                NamedTextColor.AQUA
            )));
    }

    private void addAbilities(List<Component> lore, ItemInstance instance) {
        AbilityComponent abilityComponent = instance.definition().component(ComponentType.ABILITY, AbilityComponent.class).orElse(null);
        if (abilityComponent == null || abilityComponent.abilities().isEmpty()) {
            return;
        }
        for (AbilityDefinition ability : abilityComponent.abilities().values()) {
            lore.add(Component.text(formatTrigger(ability.trigger()) + ": ", NamedTextColor.GOLD)
                .append(Optional.ofNullable(ability.displayName()).orElse(Component.text(ability.id(), NamedTextColor.WHITE))));
            for (Component line : ability.description()) {
                lore.add(Component.text("  ").append(line));
            }
        }
    }

    private void addFlavor(List<Component> lore, VisualComponent visual) {
        if (visual == null || visual.flavor().isEmpty()) {
            return;
        }
        lore.addAll(visual.flavor());
    }

    private void addRarity(List<Component> lore, VisualComponent visual) {
        if (visual == null) {
            return;
        }
        NamedTextColor color = rarityColor(visual.rarity());
        lore.add(Component.text("Rarity: ", NamedTextColor.GRAY)
            .append(Component.text(visual.rarity().name().toLowerCase(Locale.ROOT), color)));
    }

    private String formatTrigger(AbilityTrigger trigger) {
        return trigger.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private NamedTextColor rarityColor(sh.harold.fulcrum.plugin.item.model.ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
    }
}
