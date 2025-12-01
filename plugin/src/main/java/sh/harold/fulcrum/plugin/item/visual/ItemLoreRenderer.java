package sh.harold.fulcrum.plugin.item.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.enchantments.Enchantment;
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
import sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ItemLoreRenderer {

    private static final DecimalFormat STAT_FORMAT = new DecimalFormat("#.##");
    private final ItemResolver resolver;
    private final EnchantRegistry enchantRegistry;

    public ItemLoreRenderer(ItemResolver resolver, EnchantRegistry enchantRegistry) {
        this.resolver = resolver;
        this.enchantRegistry = enchantRegistry;
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
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        CustomItem definition = instance.definition();
        VisualComponent visual = definition.component(ComponentType.VISUAL, VisualComponent.class).orElse(null);
        Component name = visual != null && visual.hasDisplayName()
            ? visual.displayName()
            : Component.text(definition.id(), NamedTextColor.WHITE);
        name = rarityColorize(name, visual);
        meta.displayName(noItalics(name));
        ensureGlint(meta, instance);

        StatSnapshot stats = buildStats(instance);
        List<Component> lore = new ArrayList<>();
        for (LoreSection section : definition.loreLayout()) {
            switch (section) {
                case HEADER -> {
                    // header handled via display name
                }
                case RARITY -> addRarity(lore, visual);
                case TAGS -> addTags(lore, definition, stats);
                case ENCHANTS -> addEnchants(lore, instance);
                case PRIMARY_STATS -> addStats(lore, stats);
                case ABILITIES -> addAbilities(lore, instance);
                case FOOTER -> addFlavor(lore, visual);
            }
        }
        trimTrailingEmptyLines(lore);
        if (!lore.isEmpty()) {
            lore.add(noItalics(Component.empty()));
        }
        String idLabel = definition.id().startsWith("vanilla:")
            ? definition.material().name()
            : definition.id();
        lore.add(noItalics(Component.text("ID: " + idLabel, NamedTextColor.DARK_GRAY)));
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
        lore.add(Component.text(String.join(", ", tags), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
    }

    private void addStats(List<Component> lore, StatSnapshot stats) {
        List<Component> lines = new ArrayList<>();
        addStatLine(lines, "Damage", stats.damage());
        addStatLine(lines, "Attack Speed", stats.attackSpeed());
        addStatLine(lines, "Armor", stats.armor());
        addStatLine(lines, "Max Health", stats.maxHealth());
        addStatLine(lines, "Movement Speed", stats.movementSpeed());
        addStatLine(lines, "Crit Damage", stats.critDamage() * 100.0, "%");
        if (lines.isEmpty()) {
            return;
        }
        lore.addAll(lines);
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

    private void addEnchants(List<Component> lore, ItemInstance instance) {
        Map<String, Integer> enchants = instance.enchants();
        if (enchants.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(enchants.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, Integer> entry : entries) {
            EnchantDefinition definition = enchantRegistry.get(entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            int level = entry.getValue();
            String levelLabel = roman(level);
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(definition.displayName());
            NamedTextColor color = level > definition.maxLevel() ? NamedTextColor.GOLD : NamedTextColor.BLUE;
            Component line = Component.text(name + " " + levelLabel, color);
            lore.add(line);
        }
        lore.add(Component.empty());
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(value);
        };
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

    private StatSnapshot buildStats(ItemInstance instance) {
        Map<StatId, Double> stats = instance.computeFinalStats();
        double damage = stats.getOrDefault(StatIds.ATTACK_DAMAGE, 0.0);
        double armor = stats.getOrDefault(StatIds.ARMOR, 0.0);
        double attackSpeed = stats.getOrDefault(StatIds.ATTACK_SPEED, 0.0);
        double movementSpeed = stats.getOrDefault(StatIds.MOVEMENT_SPEED, 0.0);
        double maxHealth = stats.getOrDefault(StatIds.MAX_HEALTH, 0.0);
        double critDamage = stats.getOrDefault(StatIds.CRIT_DAMAGE, 0.0);
        return new StatSnapshot(damage, armor, attackSpeed, movementSpeed, maxHealth, critDamage);
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

    private void addStatLine(List<Component> lore, String label, double value) {
        if (Double.compare(value, 0.0) == 0) {
            return;
        }
        lore.add(Component.text(label + ": ", NamedTextColor.GRAY)
            .append(Component.text("+" + STAT_FORMAT.format(value), NamedTextColor.RED)));
    }

    private void addStatLine(List<Component> lore, String label, double value, String suffix) {
        if (Double.compare(value, 0.0) == 0) {
            return;
        }
        lore.add(Component.text(label + ": ", NamedTextColor.GRAY)
            .append(Component.text("+" + STAT_FORMAT.format(value) + suffix, NamedTextColor.RED)));
    }

    private record StatSnapshot(double damage, double armor, double attackSpeed, double movementSpeed, double maxHealth, double critDamage) {
    }

    private void trimTrailingEmptyLines(List<Component> lore) {
        while (!lore.isEmpty() && lore.get(lore.size() - 1).equals(Component.empty())) {
            lore.remove(lore.size() - 1);
        }
    }

    private void ensureGlint(ItemMeta meta, ItemInstance instance) {
        if (meta == null) {
            return;
        }
        if (!meta.getEnchants().isEmpty()) {
            return;
        }
        if (!instance.enchants().isEmpty()) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        }
    }
}
