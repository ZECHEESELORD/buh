package sh.harold.fulcrum.plugin.item.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Registry;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import sh.harold.fulcrum.plugin.item.ability.AbilityDefinition;
import sh.harold.fulcrum.plugin.item.ability.AbilityTrigger;
import sh.harold.fulcrum.plugin.item.model.AbilityComponent;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.model.LoreSection;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;
import sh.harold.fulcrum.plugin.item.runtime.DurabilityState;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;
import sh.harold.fulcrum.plugin.playerdata.PlayerSettingsService;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.core.StatRegistry;
import sh.harold.fulcrum.stats.core.StatVisual;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ItemLoreRenderer {

    private static final DecimalFormat STAT_FORMAT = new DecimalFormat("#.##");
    private final ItemResolver resolver;
    private final EnchantRegistry enchantRegistry;
    private final sh.harold.fulcrum.plugin.item.runtime.ItemPdc itemPdc;
    private final StatRegistry statRegistry;
    private final PlayerSettingsService playerSettingsService;

    public ItemLoreRenderer(ItemResolver resolver, EnchantRegistry enchantRegistry, sh.harold.fulcrum.plugin.item.runtime.ItemPdc itemPdc, StatRegistry statRegistry, PlayerSettingsService playerSettingsService) {
        this.resolver = resolver;
        this.enchantRegistry = enchantRegistry;
        this.itemPdc = itemPdc;
        this.statRegistry = statRegistry;
        this.playerSettingsService = playerSettingsService;
    }

    public ItemStack render(ItemStack stack, Player viewer) {
        return resolver.resolve(stack).map(instance -> renderInstance(instance, viewer)).orElse(stack);
    }

    private ItemStack renderInstance(ItemInstance instance, Player viewer) {
        ItemMeta sourceMeta = instance.stack().getItemMeta();
        ItemStack clone = instance.stack().clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return clone;
        }
        meta.setAttributeModifiers(null);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        CustomItem definition = instance.definition();
        VisualComponent visual = definition.component(ComponentType.VISUAL, VisualComponent.class).orElse(null);
        Component baseName = visual != null && visual.hasDisplayName()
            ? visual.displayName()
            : Component.text(definition.id(), NamedTextColor.WHITE);
        Component defaultDisplayName = rarityColorize(baseName, visual);
        String customName = customName(sourceMeta, defaultDisplayName, definition.material());
        boolean showCustomInline = playerSettingsService != null
            && playerSettingsService.cachedCustomItemNames(viewer.getUniqueId())
            && customName != null;
        Component displayName = noItalics(showCustomInline
            ? defaultDisplayName.append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(Component.text(customName, NamedTextColor.DARK_GRAY))
                .append(Component.text(")", NamedTextColor.DARK_GRAY))
            : defaultDisplayName);
        meta.displayName(displayName);
        ensureGlint(meta, instance);

        StatSnapshot stats = buildStats(instance);
        String anvilName = showCustomInline ? null : customName(sourceMeta, defaultDisplayName, definition.material());
        boolean hasStats = !stats.all().isEmpty();
        boolean hasEnchants = !instance.enchants().isEmpty();
        boolean hasAbilities = definition.component(ComponentType.ABILITY, AbilityComponent.class)
            .map(component -> !component.abilities().isEmpty())
            .orElse(false);
        boolean hasTrimSlot = instance.stack().getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta;
        VisualComponent visualComponent = visual;
        boolean hasFlavor = visualComponent != null && !visualComponent.flavor().isEmpty();
        boolean hasDurability = instance.durability().map(data -> data.data().max() > 0).orElse(false);
        boolean hasFollowingSections = hasStats || hasEnchants || hasAbilities || hasTrimSlot || hasFlavor || hasDurability;
        List<Component> lore = new ArrayList<>();
        for (LoreSection section : definition.loreLayout()) {
            switch (section) {
                case HEADER -> {
                    // header handled via display name
                }
                case RARITY -> addRarity(lore, visual);
                case TAGS -> addTags(lore, definition, anvilName, hasFollowingSections);
                case ENCHANTS -> addEnchants(lore, instance);
                case SOCKET -> addTrimSlots(lore, instance);
                case PRIMARY_STATS -> addStats(lore, stats);
                case ABILITIES -> addAbilities(lore, instance);
                case FOOTER -> addFlavor(lore, visual);
            }
        }
        trimTrailingEmptyLines(lore);
        addDurability(lore, instance);
        addId(lore, definition);
        meta.lore(lore.stream().map(this::noItalics).toList());
        mirrorVanillaBar(meta, instance);
        clone.setItemMeta(meta);
        return clone;
    }

    private void addTags(List<Component> lore, CustomItem definition, String anvilName, boolean spacerAfter) {
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
        if (anvilName != null && !anvilName.isBlank()) {
            tags.add("\"" + anvilName + "\"");
        }
        if (tags.isEmpty()) {
            return;
        }
        lore.add(Component.text(String.join(", ", tags), NamedTextColor.DARK_GRAY));
        if (spacerAfter) {
            lore.add(Component.empty());
        }
    }

    private void addStats(List<Component> lore, StatSnapshot stats) {
        Map<StatId, Double> allStats = new java.util.LinkedHashMap<>(stats.all());
        List<Component> lines = new ArrayList<>();
        appendStatIfPresent(lines, allStats, StatIds.ATTACK_DAMAGE, "Damage", false);
        appendStatIfPresent(lines, allStats, StatIds.ATTACK_SPEED, "Attack Speed", false);
        appendStatIfPresent(lines, allStats, StatIds.ARMOR, "Defense", false);
        appendStatIfPresent(lines, allStats, StatIds.MAX_HEALTH, "Max Health", false);
        appendStatIfPresent(lines, allStats, StatIds.MOVEMENT_SPEED, "Movement Speed", false);
        appendStatIfPresent(lines, allStats, StatIds.CRIT_DAMAGE, "Crit Damage", true);

        if (!allStats.isEmpty()) {
            allStats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(StatId::value)))
                .forEach(entry -> {
                    Component line = statLine(entry.getKey(), labelFor(entry.getKey()), entry.getValue(), false);
                    if (!line.equals(Component.empty())) {
                        lines.add(line);
                    }
                });
        }

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
                wrap(line, 40).forEach(wrapped -> lore.add(Component.text("  ").append(wrapped)));
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
        if (entries.size() <= 3) {
            for (Map.Entry<String, Integer> entry : entries) {
                EnchantDefinition definition = enchantRegistry.get(entry.getKey()).orElse(null);
                if (definition == null) {
                    continue;
                }
                int level = entry.getValue();
                String levelLabel = roman(level);
                String name = plain(definition.displayName());
                NamedTextColor color = level > definition.maxLevel() ? NamedTextColor.GOLD : NamedTextColor.BLUE;
                Component title = Component.text(name + " " + levelLabel, color);
                lore.add(title);
                String description = enchantDescription(definition, level);
                wrap(Component.text(description, NamedTextColor.GRAY), 40).forEach(lore::add);
            }
        } else {
            List<Component> labels = new ArrayList<>(entries.size());
            for (Map.Entry<String, Integer> entry : entries) {
                EnchantDefinition definition = enchantRegistry.get(entry.getKey()).orElse(null);
                if (definition == null) {
                    continue;
                }
                int level = entry.getValue();
                String levelLabel = roman(level);
                String name = plain(definition.displayName());
                NamedTextColor color = level > definition.maxLevel() ? NamedTextColor.GOLD : NamedTextColor.BLUE;
                labels.add(Component.text(name + " " + levelLabel, color));
            }
            if (labels.isEmpty()) {
                return;
            }
            if (entries.size() <= 4) {
                lore.addAll(labels);
            } else {
                lore.addAll(wrapComponents(labels, 40));
            }
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
        int filled = switch (rarity) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5;
        };
        NamedTextColor color = switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.BLUE;
            case EPIC -> NamedTextColor.DARK_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
        String filledStars = "★".repeat(filled);
        String emptyStars = "☆".repeat(5 - filled);
        Component stars = Component.text()
            .append(Component.text(filledStars, color))
            .append(Component.text(emptyStars, NamedTextColor.DARK_GRAY))
            .build();
        Component label = switch (rarity) {
            case COMMON -> Component.text("COMMON", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true);
            case UNCOMMON -> Component.text("UNCOMMON", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true);
            case RARE -> Component.text("RARE", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true);
            case EPIC -> Component.text("EPIC", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true);
            case LEGENDARY -> Component.text("LEGENDARY", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true);
        };
        Component coloredLabel = label.color(color);
        return Component.text()
            .append(stars)
            .append(Component.text(" (", NamedTextColor.DARK_GRAY))
            .append(coloredLabel)
            .append(Component.text(")", NamedTextColor.DARK_GRAY))
            .build();
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

    private String formattedId(CustomItem definition) {
        String raw = definition.id();
        if (raw == null || raw.isBlank()) {
            return "";
        }
        int colon = raw.indexOf(':');
        String path = colon >= 0 && colon + 1 < raw.length() ? raw.substring(colon + 1) : raw;
        return path.toUpperCase(Locale.ROOT);
    }

    private StatSnapshot buildStats(ItemInstance instance) {
        Map<StatId, Double> stats = instance.computeFinalStats();
        return new StatSnapshot(stats, instance.stack().getItemMeta());
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
            || category == ItemCategory.MACE
            || category == ItemCategory.WAND
            || category == ItemCategory.TRIDENT;
    }

    private void addStatLine(List<Component> lore, String label, double value) {
        lore.add(statLine(null, label, value, false));
    }

    private void addStatLine(List<Component> lore, String label, double value, String suffix) {
        lore.add(statLine(null, label, value, true));
    }

    private Component statLine(StatId id, String label, double value, boolean percent) {
        if (Double.compare(value, 0.0) == 0) {
            return Component.empty();
        }
        String suffix = percent ? "%" : "";
        double displayValue = percent ? value * 100.0 : value;
        String number = (displayValue >= 0 ? "+" : "") + STAT_FORMAT.format(displayValue) + suffix;
        StatVisual visual = visualFor(id);
        TextColor color = visual.hasColor() ? TextColor.fromCSSHexString(visual.color()) : NamedTextColor.RED;
        String icon = visual.hasIcon() ? visual.icon() : "";
        return Component.text(label + ": ", NamedTextColor.GRAY)
            .append(Component.text(number + icon, color == null ? NamedTextColor.RED : color));
    }

    private void appendStatIfPresent(List<Component> lore, Map<StatId, Double> stats, StatId id, String label, boolean percent) {
        Double value = stats.remove(id);
        if (value == null || Double.compare(value, 0.0) == 0) {
            return;
        }
        double displayValue = StatIds.MOVEMENT_SPEED.equals(id) ? value * 1000.0 : value;
        Component line = statLine(id, label, displayValue, percent);
        if (!line.equals(Component.empty())) {
            lore.add(line);
        }
    }

    private StatVisual visualFor(StatId id) {
        if (statRegistry == null || id == null) {
            return StatVisual.empty();
        }
        try {
            StatVisual visual = statRegistry.get(id).visual();
            return visual == null ? StatVisual.empty() : visual;
        } catch (IllegalArgumentException ignored) {
            return StatVisual.empty();
        }
    }

    private void addTrimSlots(List<Component> lore, ItemInstance instance) {
        ItemStack stack = instance.stack();
        ArmorTrim trim = itemPdc.readTrim(stack).map(this::toTrim).orElse(null);
        if (!(stack.getItemMeta() instanceof ArmorMeta)) {
            return;
        }
        if (trim == null) {
            lore.add(Component.text("◇ Empty Trim Upgrade Slot", NamedTextColor.DARK_GRAY));
            return;
        }
        String pattern = humanize(trim.getPattern());
        String material = humanize(trim.getMaterial());
        NamedTextColor patternColor = patternColor(trim.getPattern());
        Component line = Component.text()
            .append(Component.text("◆ ", NamedTextColor.GOLD))
            .append(Component.text(pattern + " Trim Upgrade ", patternColor))
            .append(Component.text("(" + material + ")", NamedTextColor.DARK_GRAY))
            .build();
        lore.add(line);
    }

    private String labelFor(StatId id) {
        String raw = id.value();
        if (raw == null || raw.isBlank()) {
            return "Stat";
        }
        String[] tokens = raw.split("_");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" ");
            }
            builder.append(token.substring(0, 1).toUpperCase(Locale.ROOT))
                .append(token.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private ArmorTrim toTrim(sh.harold.fulcrum.plugin.item.runtime.TrimData data) {
        TrimPattern pattern = findPattern(data.patternKey());
        TrimMaterial material = findMaterial(data.materialKey());
        if (pattern == null || material == null) {
            return null;
        }
        return new ArmorTrim(material, pattern);
    }

    private String humanize(TrimPattern pattern) {
        return humanize(pattern.getKey().getKey());
    }

    private String humanize(TrimMaterial material) {
        return humanize(material.getKey().getKey());
    }

    private NamedTextColor patternColor(TrimPattern pattern) {
        String key = pattern.getKey().getKey();
        return switch (key) {
            case "vex", "ward", "eye", "spire" -> NamedTextColor.GREEN; // uncommon
            case "silence" -> NamedTextColor.BLUE; // rare
            default -> NamedTextColor.WHITE; // common
        };
    }

    private TrimPattern findPattern(String key) {
        return Registry.TRIM_PATTERN.stream()
            .filter(pattern -> pattern.getKey().getKey().equalsIgnoreCase(key))
            .findFirst()
            .orElse(null);
    }

    private TrimMaterial findMaterial(String key) {
        return Registry.TRIM_MATERIAL.stream()
            .filter(material -> material.getKey().getKey().equalsIgnoreCase(key))
            .findFirst()
            .orElse(null);
    }

    private String humanize(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String[] tokens = key.split("_");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" ");
            }
            builder.append(token.substring(0, 1).toUpperCase(Locale.ROOT))
                .append(token.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private String customName(ItemMeta meta, Component defaultDisplayName, org.bukkit.Material material) {
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        String plainName = plain(meta.displayName()).trim();
        if (plainName.isEmpty()) {
            return null;
        }
        String defaultPlain = plain(defaultDisplayName).trim();
        if (!defaultPlain.isEmpty() && plainName.equalsIgnoreCase(defaultPlain)) {
            return null;
        }
        String base = material == null ? "" : material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (!base.isBlank() && plainName.equalsIgnoreCase(base)) {
            return null;
        }
        return plainName;
    }

    private record StatSnapshot(Map<StatId, Double> all, ItemMeta meta) {
    }

    private void trimTrailingEmptyLines(List<Component> lore) {
        while (!lore.isEmpty() && lore.get(lore.size() - 1).equals(Component.empty())) {
            lore.remove(lore.size() - 1);
        }
    }

    private void addDurability(List<Component> lore, ItemInstance instance) {
        DurabilityState durability = instance.durability().orElse(null);
        if (durability == null || durability.data().max() <= 0) {
            return;
        }
        if (!lore.isEmpty() && !lore.get(lore.size() - 1).equals(Component.empty())) {
            lore.add(Component.empty());
        }
        Component percentComponent = Component.text(STAT_FORMAT.format(durability.percent()) + "%", durability.color())
            .decoration(TextDecoration.BOLD, Double.compare(durability.percent(), 100.0) == 0);
        Component line = Component.text()
            .append(Component.text(durability.displayedCurrent(), durability.color()))
            .append(Component.text("/", NamedTextColor.DARK_GRAY))
            .append(Component.text(durability.data().max(), NamedTextColor.GRAY))
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(Component.text("(", NamedTextColor.DARK_GRAY))
            .append(percentComponent)
            .append(Component.text(")", NamedTextColor.DARK_GRAY))
            .build();
        Component label = Component.text(durabilityFlavor(durability), NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, true);
        lore.add(line);
        lore.add(label);
    }

    private String durabilityFlavor(DurabilityState state) {
        return switch (state.grade()) {
            case "PERFECT" -> "Perfect. Absolutely pristine. So shiny.";
            case "LIGHTLY USED" -> "Lightly used. Barely a scuff.";
            case "STURDY" -> "Sturdy. Holds up to scrapes.";
            case "WEATHERED" -> "Weathered. Stories in every mark.";
            case "FALLING APART" -> "Falling apart. One swing from ruin.";
            case "DEFUNCT" -> "Defunct. It does nothing now.";
            default -> state.grade().toLowerCase(Locale.ROOT);
        };
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

    private void mirrorVanillaBar(ItemMeta meta, ItemInstance instance) {
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            return;
        }
        DurabilityState durability = instance.durability().orElse(null);
        if (durability == null || durability.data().max() <= 0) {
            damageable.setDamage(0);
            return;
        }
        int vanillaMax = instance.stack().getType().getMaxDurability();
        if (vanillaMax <= 0) {
            return;
        }
        double fractionRemaining = durability.data().fraction();
        int visualDamage = (int) Math.round((1.0 - fractionRemaining) * vanillaMax);
        damageable.setDamage(Math.max(0, Math.min(visualDamage, vanillaMax)));
    }

    private List<Component> wrap(Component component, int maxChars) {
        String plain = plain(component);
        if (plain.length() <= maxChars) {
            return List.of(component);
        }
        List<String> tokens = List.of(plain.split(" "));
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String token : tokens) {
            if (current.length() + token.length() + 1 > maxChars) {
                lines.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(token).append(" ");
        }
        if (!current.isEmpty()) {
            lines.add(current.toString().trim());
        }
        return lines.stream()
            .map(line -> Component.text(line, component.color() == null ? NamedTextColor.GRAY : component.color()))
            .collect(Collectors.toList());
    }

    private List<Component> wrapComponents(List<Component> components, int maxChars) {
        List<Component> lines = new ArrayList<>();
        Component current = Component.empty();
        int currentLength = 0;
        boolean firstInLine = true;
        for (Component component : components) {
            int length = plain(component).length();
            int separatorLength = firstInLine ? 0 : 2; // ", "
            if (currentLength + separatorLength + length > maxChars && !firstInLine) {
                lines.add(current);
                current = Component.empty();
                currentLength = 0;
                firstInLine = true;
                separatorLength = 0;
            }
            if (!firstInLine) {
                current = current.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            }
            current = current.append(component);
            currentLength += separatorLength + length;
            firstInLine = false;
        }
        if (!firstInLine) {
            lines.add(current);
        }
        return lines;
    }

    private String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String enchantDescription(EnchantDefinition definition, int level) {
        double percent = 0.0;
        if (definition.perLevelStats().containsKey(sh.harold.fulcrum.stats.core.StatIds.ATTACK_DAMAGE)) {
            percent = definition.levelCurve().value(definition.perLevelStats().get(sh.harold.fulcrum.stats.core.StatIds.ATTACK_DAMAGE), level) * 100.0;
            return "Increases melee damage by " + STAT_FORMAT.format(percent) + "%.";
        }
        if (definition.perLevelStats().containsKey(sh.harold.fulcrum.stats.core.StatIds.ARMOR)) {
            percent = definition.levelCurve().value(definition.perLevelStats().get(sh.harold.fulcrum.stats.core.StatIds.ARMOR), level);
            return "Increases defense by " + STAT_FORMAT.format(percent) + ".";
        }
        return "Enhances this item.";
    }

    private void addId(List<Component> lore, CustomItem definition) {
        String label = formattedId(definition);
        if (label.isBlank()) {
            return;
        }
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(noItalics(Component.text("ID: " + label, NamedTextColor.DARK_GRAY)));
    }
}
