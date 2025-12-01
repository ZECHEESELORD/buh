package sh.harold.fulcrum.plugin.item.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.fulcrum.plugin.item.ability.AbilityDefinition;
import sh.harold.fulcrum.plugin.item.ability.AbilityTrigger;
import sh.harold.fulcrum.plugin.item.model.AbilityComponent;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.model.LoreSection;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;

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
        meta.setAttributeModifiers(null);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        CustomItem definition = instance.definition();
        VisualComponent visual = definition.component(ComponentType.VISUAL, VisualComponent.class).orElse(null);
        Component name = visual != null && visual.hasDisplayName()
            ? visual.displayName()
            : Component.text(definition.id(), NamedTextColor.WHITE);
        name = rarityColorize(name, visual);
        meta.displayName(noItalics(name));

        StatSnapshot stats = buildStats(instance, meta);
        List<Component> lore = new ArrayList<>();
        for (LoreSection section : definition.loreLayout()) {
            switch (section) {
                case HEADER -> {
                    // header handled via display name
                }
                case RARITY -> addRarity(lore, visual);
                case TAGS -> addTags(lore, definition, stats);
                case PRIMARY_STATS -> addStats(lore, stats);
                case ABILITIES -> addAbilities(lore, instance);
                case FOOTER -> addFlavor(lore, visual);
            }
        }
        if (!lore.isEmpty()) {
            lore.add(noItalics(Component.empty()));
        }
        lore.add(noItalics(Component.text("ID: " + definition.id(), NamedTextColor.DARK_GRAY)));
        meta.lore(lore.stream().map(this::noItalics).toList());
        clone.setItemMeta(meta);
        return clone;
    }

    private void addTags(List<Component> lore, CustomItem definition, StatSnapshot stats) {
        List<String> tags = new ArrayList<>();
        if (definition.id().startsWith("vanilla:")) {
            tags.add("Vanilla");
        }
        if (isTool(definition.category())) {
            tags.add("Tool");
        }
        if (isMelee(definition.category())) {
            tags.add("Melee");
        }
        if (tags.isEmpty()) {
            return;
        }
        lore.add(Component.text(String.join(" • ", tags), NamedTextColor.GRAY));
        lore.add(Component.empty());
    }

    private void addStats(List<Component> lore, StatSnapshot stats) {
        if (!stats.hasValues()) {
            return;
        }
        String damage = "Damage: +" + STAT_FORMAT.format(stats.damage());
        String defense = "Defense: +" + STAT_FORMAT.format(stats.armor());
        lore.add(Component.text(damage + ", " + defense, NamedTextColor.AQUA));
        lore.add(Component.empty());
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
        lore.add(Component.empty());
    }

    private void addRarity(List<Component> lore, VisualComponent visual) {
        if (visual == null) {
            return;
        }
        lore.add(rarityComponent(visual.rarity()));
    }

    private String formatTrigger(AbilityTrigger trigger) {
        return trigger.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private Component rarityComponent(sh.harold.fulcrum.plugin.item.model.ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> Component.text("COMMON", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true);
            case UNCOMMON -> Component.text("UNCOMMON", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
            case RARE -> Component.text("RARE", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, true);
            case EPIC -> Component.text("EPIC", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true);
            case LEGENDARY -> Component.text("LEGENDARY", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
        };
    }

    private Component noItalics(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private Component rarityColorize(Component component, VisualComponent visual) {
        if (visual == null) {
            return component;
        }
        return switch (visual.rarity()) {
            case COMMON -> component.color(NamedTextColor.WHITE);
            case UNCOMMON -> component.color(NamedTextColor.GREEN);
            case RARE -> component.color(NamedTextColor.BLUE);
            case EPIC -> component.color(NamedTextColor.DARK_PURPLE);
            case LEGENDARY -> component.color(NamedTextColor.GOLD);
        };
    }

    private StatSnapshot buildStats(ItemInstance instance, ItemMeta meta) {
        Map<StatId, Double> stats = instance.computeFinalStats();
        double damage = stats.getOrDefault(StatIds.ATTACK_DAMAGE, readAttribute(meta, Attribute.ATTACK_DAMAGE));
        double armor = stats.getOrDefault(StatIds.ARMOR, readAttribute(meta, Attribute.ARMOR));
        return new StatSnapshot(damage, armor, !stats.isEmpty());
    }

    private double readAttribute(ItemMeta meta, Attribute attribute) {
        if (meta == null || attribute == null || meta.getAttributeModifiers(attribute) == null) {
            return 0.0;
        }
        return meta.getAttributeModifiers(attribute).stream()
            .mapToDouble(AttributeModifier::getAmount)
            .sum();
    }

    private boolean isTool(ItemCategory category) {
        return category == ItemCategory.AXE
            || category == ItemCategory.PICKAXE
            || category == ItemCategory.SHOVEL
            || category == ItemCategory.HOE
            || category == ItemCategory.FISHING_ROD;
    }

    private boolean isMelee(ItemCategory category) {
        return category == ItemCategory.SWORD
            || category == ItemCategory.AXE
            || category == ItemCategory.WAND
            || category == ItemCategory.TRIDENT;
    }

    private record StatSnapshot(double damage, double armor, boolean hasStats) {
        boolean hasValues() {
            return hasStats || damage > 0.0 || armor > 0.0;
        }
    }
}
