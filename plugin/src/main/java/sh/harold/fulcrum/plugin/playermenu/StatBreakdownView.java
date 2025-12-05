package sh.harold.fulcrum.plugin.playermenu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.stats.core.ModifierOp;
import sh.harold.fulcrum.stats.core.StatDefinition;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.core.StatRegistry;
import sh.harold.fulcrum.stats.core.StatSnapshot;
import sh.harold.fulcrum.stats.core.StatSourceId;
import sh.harold.fulcrum.stats.core.StatModifier;
import sh.harold.fulcrum.stats.core.StatVisual;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.plugin.item.stat.StatSourceContext;
import sh.harold.fulcrum.plugin.item.stat.StatSourceContextRegistry;
import sh.harold.fulcrum.plugin.item.stat.SourceCategory;
import sh.harold.fulcrum.stats.service.StatService;
import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

final class StatBreakdownView {

    private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("#.##");
    private static final int ROWS = 6;
    private static final Map<String, Material> STAT_ICONS = Map.of(
        sh.harold.fulcrum.stats.core.StatIds.MAX_HEALTH.value(), Material.GOLDEN_APPLE,
        sh.harold.fulcrum.stats.core.StatIds.ATTACK_DAMAGE.value(), Material.IRON_SWORD,
        sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED.value(), Material.CLOCK,
        sh.harold.fulcrum.stats.core.StatIds.MOVEMENT_SPEED.value(), Material.FEATHER,
        sh.harold.fulcrum.stats.core.StatIds.ARMOR.value(), Material.IRON_CHESTPLATE,
        sh.harold.fulcrum.stats.core.StatIds.CRIT_DAMAGE.value(), Material.GOLDEN_SWORD
    );
    private static final List<String> SLOT_ORDER = List.of(
        "HELMET",
        "CHESTPLATE",
        "LEGGINGS",
        "BOOTS",
        "MAIN_HAND",
        "OFF_HAND",
        "ACCESSORY",
        "PERK",
        "BONUS",
        "DEFAULT",
        "MISC",
        ""
    );
    private static final List<SourceCategory> CATEGORY_ORDER = List.of(
        SourceCategory.BASE,
        SourceCategory.ENCHANT,
        SourceCategory.PERK,
        SourceCategory.BONUS,
        SourceCategory.DEFAULT,
        SourceCategory.UNKNOWN
    );

    private final Plugin plugin;
    private final MenuService menuService;
    private final StatService statService;
    private final StatRegistry statRegistry;
    private final StatSourceContextRegistry contextRegistry;
    private final Consumer<Player> backToMenu;

    StatBreakdownView(Plugin plugin, MenuService menuService, StatService statService, StatRegistry statRegistry, StatSourceContextRegistry contextRegistry, Consumer<Player> backToMenu) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.statService = Objects.requireNonNull(statService, "statService");
        this.statRegistry = Objects.requireNonNull(statRegistry, "statRegistry");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry");
        this.backToMenu = Objects.requireNonNull(backToMenu, "backToMenu");
    }

    void open(Player player) {
        open(player, StatViewState.defaultState());
    }

    private void open(Player player, StatViewState state) {
        Objects.requireNonNull(player, "player");
        try {
            var container = statService.getContainer(EntityKey.fromUuid(player.getUniqueId()));
            Map<StatId, StatSnapshot> snapshots = container.debugView().stream()
                .collect(java.util.stream.Collectors.toMap(StatSnapshot::statId, snapshot -> snapshot));
            List<StatEntry> entries = statRegistry.getAll().stream()
                .map(def -> {
                    StatSnapshot snapshot = snapshots.get(def.id());
                    double baseValue = snapshot != null ? snapshot.baseValue() : def.baseValue();
                    double finalValue = snapshot != null ? snapshot.finalValue() : container.getStat(def.id());
                    return new StatEntry(def, baseValue, finalValue);
                })
                .filter(entry -> !state.positiveOnly() || entry.finalValue() > 0.0)
                .sorted(Comparator.comparing(entry -> entry.definition().id().value()))
                .toList();

            menuService.createListMenu()
                .title("Stat Breakdown")
                .rows(ROWS)
                .addBorder(Material.BLACK_STAINED_GLASS_PANE)
                .showPageIndicator(false)
                .addButton(MenuButton.createPositionedClose(ROWS))
                .addButton(backButton())
                .addButton(filterToggle(state, s -> open(player, s)))
                .addItems(entries, entry -> buildStatButton(entry, state))
                .emptyMessage(Component.text("No stats to display.", NamedTextColor.GRAY))
                .buildAsync(player)
                .thenAccept(menu -> menu.setProperty("statViewState", state))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to open stat breakdown for " + player.getUniqueId(), throwable);
                    player.sendMessage("§cStats are snoozing; try again soon.");
                    return null;
                });
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open stat breakdown for " + player.getUniqueId(), throwable);
            player.sendMessage("§cStats are snoozing; try again soon.");
        }
    }

    private MenuButton buildStatButton(StatEntry entry, StatViewState state) {
        StatId id = entry.definition().id();
        String label = humanize(id.value());
        double displayBase = scale(id, entry.baseValue(), true);
        double displayCurrent = scale(id, entry.finalValue(), true);
        String base = VALUE_FORMAT.format(displayBase);
        String current = VALUE_FORMAT.format(displayCurrent);
        String icon = visualIcon(id);
        String color = legacyColor(id);
        String suffix = StatIds.CRIT_DAMAGE.equals(id) ? "%" : "";
        return MenuButton.builder(materialFor(entry.definition()))
            .name(color + (icon.isBlank() ? "" : icon + " ") + label + " &f" + current + suffix)
            .secondary("Stat Overview")
            .description(statDescription(id, color, icon, base + suffix))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> openSources(viewer, entry.definition(), state))
            .build();
    }

    private MenuButton backButton() {
        return MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Player Menu")
            .description("Return to the player menu.")
            .slot(MenuButton.getBackSlot(ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(backToMenu)
            .build();
    }

    private void openSources(Player player, StatDefinition definition) {
        openSources(player, definition, resolveState(player));
    }

    private void openSources(Player player, StatDefinition definition, StatViewState state) {
        Objects.requireNonNull(player, "player");
        if (definition == null) {
            return;
        }
        try {
            var container = statService.getContainer(EntityKey.fromUuid(player.getUniqueId()));
            StatSnapshot snapshot = container.debugView().stream()
                .filter(snap -> snap.statId().equals(definition.id()))
                .findFirst()
                .orElse(null);
            double baseValue = snapshot != null ? snapshot.baseValue() : definition.baseValue();
            double finalValue = snapshot != null ? snapshot.finalValue() : container.getStat(definition.id());
            List<SourceEntry> sources = snapshot == null
                ? List.of()
                : computeSources(definition.id(), snapshot, state, player);

            List<sh.harold.fulcrum.api.menu.component.MenuItem> items = new ArrayList<>();
            items.add(baseInnateItem(player, definition.id(), baseValue));
            items.addAll(sources.stream().map(entry -> buildSourceItem(player, entry, state)).toList());

            menuService.createListMenu()
                .title(humanize(definition.id().value()) + " Sources")
                .rows(ROWS)
                .addBorder(Material.BLACK_STAINED_GLASS_PANE)
                .showPageIndicator(false)
                .addButton(MenuButton.createPositionedClose(ROWS))
                .addButton(statBackButton(definition, state))
                .addButton(groupingToggle(state, s -> openSources(player, definition, s)))
                .addButton(flattenToggle(state, s -> openSources(player, definition, s)))
                .addItems(items)
                .emptyMessage(Component.text("No sources found.", NamedTextColor.GRAY))
                .buildAsync(player)
                .thenAccept(menu -> menu.setProperty("statViewState", state))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to open stat sources for " + player.getUniqueId(), throwable);
                    player.sendMessage("§cSources are snoozing; try again soon.");
                    return null;
                });
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open stat sources for " + player.getUniqueId(), throwable);
            player.sendMessage("§cSources are snoozing; try again soon.");
        }
    }

    private MenuDisplayItem baseInnateItem(Player player, StatId statId, double baseValue) {
        double displayBase = scale(statId, baseValue, true);
        TextColor color = colorFor(statId);
        String icon = visualIcon(statId);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        Component valueLine = Component.text()
            .append(Component.text("Value: ", NamedTextColor.GRAY))
            .append(Component.text("+" + VALUE_FORMAT.format(displayBase) + (icon.isBlank() ? "" : icon), color))
            .decoration(TextDecoration.ITALIC, false)
            .build();
        lore.add(valueLine);
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        wrap("This is the base value of this stat which everyone starts off with.", 40)
            .forEach(line -> lore.add(Component.text(line, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        MenuDisplayItem item = MenuDisplayItem.builder(Material.PLAYER_HEAD)
            .name(toLegacy(color) + (icon.isBlank() ? "" : icon + " ") + "Base Value")
            .secondary("&8Innate")
            .lore(lore.toArray(new Component[0]))
            .build();
        ItemStack stack = item.getDisplayItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(plugin.getServer().getOfflinePlayer(player.getUniqueId()));
            stack.setItemMeta(skullMeta);
            item.setDisplayItem(stack);
        }
        return item;
    }

    private MenuDisplayItem buildSourceItem(Player player, SourceEntry entry, StatViewState state) {
        double displayFlat = scale(entry.statId(), entry.totalFlat(), false);
        var key = EntityKey.fromUuid(player.getUniqueId());
        var context = contextRegistry.get(key, entry.sourceId()).orElse(entry.context());
        SourceDisplay display = displayMetadata(entry, context);
        TextColor color = colorFor(entry.statId());
        String icon = visualIcon(entry.statId());
        List<Component> lore = new ArrayList<>();
        Component valueLine = Component.text()
            .append(Component.text("Value: ", NamedTextColor.GRAY))
            .append(Component.text(formattedSigned(displayFlat) + (icon.isBlank() ? "" : icon), color))
            .decoration(TextDecoration.ITALIC, false)
            .build();
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        lore.add(valueLine);
        if (state.flattenEnabled() && !state.groupingEnabled() && entry.view() == SourceView.INDIVIDUAL) {
            lore.addAll(flattenedFrom(display.displayItem()));
        }
        if (!entry.children().isEmpty()) {
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Flat Bonuses:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            entry.children().forEach(child -> lore.add(flatContributionLine(child)));
        }
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        wrap(display.description(), 40).forEach(line -> lore.add(Component.text(line, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        MenuDisplayItem item = MenuDisplayItem.builder(materialForSource(entry.sourceId()))
            .name(toLegacy(color) + (icon.isBlank() ? "" : icon + " ") + display.title())
            .secondary(display.secondary())
            .lore(lore.toArray(new Component[0]))
            .build();
        if (display.displayItem() != null) {
            item.setDisplayItem(display.displayItem());
        }
        return item;
    }

    private SourceDisplay displayMetadata(SourceEntry entry, StatSourceContext context) {
        String title = context != null && !context.name().isBlank()
            ? context.name()
            : humanizeSource(entry.sourceId().value());
        String secondary = context != null && !context.secondary().isBlank()
            ? context.secondary()
            : defaultSecondary(entry);
        String description = context != null && !context.description().isBlank()
            ? context.description()
            : defaultDescription(entry);
        ItemStack displayItem = context == null ? null : context.displayItem();
        return new SourceDisplay(title, secondary, description, displayItem);
    }

    private String defaultSecondary(SourceEntry entry) {
        return switch (entry.view()) {
            case SLOT -> "&8" + displaySlotName(resolveSlot(entry));
            case CATEGORY -> "&8" + displayCategoryName(categoryOf(entry));
            case SLOT_CATEGORY -> "&8" + displayCategoryName(categoryOf(entry));
            case INDIVIDUAL -> {
                SourceCategory category = categoryOf(entry);
                boolean isEnchant = category == SourceCategory.ENCHANT || entry.sourceId().value().toLowerCase(Locale.ROOT).contains("enchant");
                yield "&8" + (isEnchant ? "Enchantment" : "Source");
            }
        };
    }

    private String defaultDescription(SourceEntry entry) {
        SourceCategory category = categoryOf(entry);
        return switch (entry.view()) {
            case SLOT -> "All sources from " + displaySlotName(resolveSlot(entry)).toLowerCase(Locale.ROOT) + ".";
            case CATEGORY -> "Combined " + displayCategoryName(category).toLowerCase(Locale.ROOT) + " sources.";
            case SLOT_CATEGORY -> "Grouped " + displayCategoryName(category).toLowerCase(Locale.ROOT) + " sources on " + displaySlotName(resolveSlot(entry)).toLowerCase(Locale.ROOT) + ".";
            case INDIVIDUAL -> category == SourceCategory.ENCHANT
                ? "Stats coming from enchantments on the item."
                : "Contribution from this source.";
        };
    }

    private Component flatContributionLine(SourceEntry entry) {
        TextColor color = colorFor(entry.statId());
        String icon = visualIcon(entry.statId());
        double displayFlat = scale(entry.statId(), entry.totalFlat(), false);
        String title = displayMetadata(entry, entry.context()).title();
        return Component.text()
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(Component.text(formattedSigned(displayFlat) + (icon.isBlank() ? "" : icon) + " ", color))
            .append(Component.text(title, NamedTextColor.WHITE))
            .decoration(TextDecoration.ITALIC, false)
            .build();
    }

    private MenuButton statBackButton(StatDefinition definition, StatViewState state) {
        return MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Stat List")
            .description("Return to all stats.")
            .slot(MenuButton.getBackSlot(ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(player -> open(player, state))
            .build();
    }

    private MenuButton filterToggle(StatViewState state, java.util.function.Consumer<StatViewState> action) {
        boolean positiveOnly = state.positiveOnly();
        return MenuButton.builder(Material.PUFFERFISH)
            .name(positiveOnly ? "&aView: Your Stats" : "&aView: All Stats")
            .secondary(positiveOnly ? "Stats > 0" : "Every Stat")
            .description(positiveOnly
                ? "Show only stats where you have a value above zero."
                : "Show the full stat list.")
            .slot(50)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(player -> action.accept(state.toggleFilter()))
            .build();
    }

    private List<SourceEntry> computeSources(StatId statId, StatSnapshot snapshot, StatViewState state, Player player) {
        EntityKey key = EntityKey.fromUuid(player.getUniqueId());
        Map<StatSourceId, Contribution> contributions = new LinkedHashMap<>();
        Map<ModifierOp, Map<StatSourceId, List<StatModifier>>> modifiers = snapshot.modifiers();
        for (ModifierOp op : ModifierOp.values()) {
            Map<StatSourceId, List<StatModifier>> bySource = modifiers.getOrDefault(op, Map.of());
            for (Map.Entry<StatSourceId, List<StatModifier>> entry : bySource.entrySet()) {
                Contribution contribution = contributions.computeIfAbsent(entry.getKey(), ignored -> new Contribution());
                double sum = entry.getValue().stream().mapToDouble(StatModifier::value).sum();
                switch (op) {
                    case FLAT -> contribution.flat += sum;
                    case PERCENT_ADD -> contribution.percentAdd += sum;
                    case PERCENT_MULT -> contribution.percentMultFactor *= (1.0 + sum);
                }
            }
        }
        List<SourceEntry> entries = contributions.entrySet().stream()
            .map(entry -> {
                StatSourceContext ctx = contextRegistry.get(key, entry.getKey()).orElse(null);
                return new SourceEntry(
                    statId,
                    entry.getKey(),
                    entry.getValue().flat,
                    entry.getValue().percentAdd,
                    entry.getValue().percentMultFactor - 1.0,
                    ctx,
                    List.of(),
                    SourceView.INDIVIDUAL
                );
            })
            .toList();
        return groupEntries(entries, state);
    }

    private List<SourceEntry> groupEntries(List<SourceEntry> entries, StatViewState state) {
        if (entries.isEmpty()) {
            return entries;
        }
        // Flattened keeps individual entries; unflattened aggregates by slot/category depending on grouping.
        boolean flattened = state.flattenEnabled();
        boolean grouped = state.groupingEnabled();
        if (flattened && !grouped) {
            return entries;
        }
        if (flattened) {
            return aggregateByCategory(entries);
        }
        if (!grouped) {
            return aggregateBySlot(entries);
        }
        return aggregateBySlotAndCategory(entries);
    }

    private List<SourceEntry> aggregateByCategory(List<SourceEntry> entries) {
        Map<SourceCategory, List<SourceEntry>> grouped = new EnumMap<>(SourceCategory.class);
        for (SourceEntry entry : entries) {
            SourceCategory category = categoryOf(entry);
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(entry);
        }
        List<SourceEntry> result = new ArrayList<>();
        for (SourceCategory category : CATEGORY_ORDER) {
            List<SourceEntry> group = grouped.get(category);
            if (group == null || group.isEmpty()) {
                continue;
            }
            result.add(mergeGroup(
                "category:" + category.name().toLowerCase(Locale.ROOT),
                categoryContext(category, ""),
                SourceView.CATEGORY,
                group
            ));
        }
        return result;
    }

    private List<SourceEntry> aggregateBySlot(List<SourceEntry> entries) {
        Map<String, List<SourceEntry>> grouped = new LinkedHashMap<>();
        for (SourceEntry entry : entries) {
            String slot = resolveSlot(entry);
            grouped.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(entry);
        }
        List<SourceEntry> result = new ArrayList<>();
        for (String slot : sortedSlots(grouped.keySet())) {
            List<SourceEntry> group = grouped.get(slot);
            if (group == null || group.isEmpty()) {
                continue;
            }
            result.add(mergeGroup(
                "slot:" + slot.toLowerCase(Locale.ROOT),
                slotContext(slot, slotDisplayItem(group)),
                SourceView.SLOT,
                group
            ));
        }
        return result;
    }

    private List<SourceEntry> aggregateBySlotAndCategory(List<SourceEntry> entries) {
        Map<String, Map<SourceCategory, List<SourceEntry>>> grouped = new LinkedHashMap<>();
        for (SourceEntry entry : entries) {
            String slot = resolveSlot(entry);
            SourceCategory category = categoryOf(entry);
            grouped.computeIfAbsent(slot, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(category, ignored -> new ArrayList<>())
                .add(entry);
        }
        List<SourceEntry> result = new ArrayList<>();
        for (String slot : sortedSlots(grouped.keySet())) {
            Map<SourceCategory, List<SourceEntry>> byCategory = grouped.getOrDefault(slot, Map.of());
            for (SourceCategory category : CATEGORY_ORDER) {
                List<SourceEntry> group = byCategory.get(category);
                if (group == null || group.isEmpty()) {
                    continue;
                }
                result.add(mergeGroup(
                    "slot:" + slot.toLowerCase(Locale.ROOT) + ":category:" + category.name().toLowerCase(Locale.ROOT),
                    categoryContext(category, slot),
                    SourceView.SLOT_CATEGORY,
                    group
                ));
            }
        }
        return result;
    }

    private SourceEntry mergeGroup(String key, StatSourceContext context, SourceView view, List<SourceEntry> children) {
        Contribution contribution = new Contribution();
        for (SourceEntry child : children) {
            contribution.flat += child.totalFlat();
            contribution.percentAdd += child.percentAdd();
            contribution.percentMultFactor *= (1.0 + child.percentMult());
        }
        List<SourceEntry> orderedChildren = children.stream()
            .sorted(childComparator(view))
            .toList();
        return new SourceEntry(
            children.getFirst().statId(),
            new StatSourceId(key),
            contribution.flat,
            contribution.percentAdd,
            contribution.percentMultFactor - 1.0,
            context,
            orderedChildren,
            view
        );
    }

    private String resolveSlot(SourceEntry entry) {
        String slotTag = entry.context() == null ? "" : entry.context().slotTag();
        if (!slotTag.isBlank()) {
            return slotTag.toUpperCase(Locale.ROOT);
        }
        SourceCategory category = categoryOf(entry);
        if (category == SourceCategory.PERK) {
            return "PERK";
        }
        if (category == SourceCategory.BONUS) {
            return "BONUS";
        }
        if (category == SourceCategory.DEFAULT) {
            return "DEFAULT";
        }
        return "MISC";
    }

    private int slotIndex(String slotTag) {
        int index = SLOT_ORDER.indexOf(slotTag);
        return index < 0 ? SLOT_ORDER.size() : index;
    }

    private int categoryIndex(SourceCategory category) {
        int index = CATEGORY_ORDER.indexOf(category);
        return index < 0 ? CATEGORY_ORDER.size() : index;
    }

    private List<String> sortedSlots(Iterable<String> slots) {
        List<String> sorted = new ArrayList<>();
        for (String slot : slots) {
            sorted.add(slot);
        }
        sorted.sort(Comparator.comparingInt(this::slotIndex).thenComparing(slot -> slot, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private SourceCategory categoryOf(SourceEntry entry) {
        return entry.context() == null ? SourceCategory.UNKNOWN : entry.context().category();
    }

    private Comparator<SourceEntry> childComparator(SourceView view) {
        return switch (view) {
            case CATEGORY -> Comparator
                .comparing((SourceEntry entry) -> slotIndex(resolveSlot(entry)))
                .thenComparing(entry -> displayMetadata(entry, entry.context()).title(), String.CASE_INSENSITIVE_ORDER);
            case SLOT -> Comparator
                .comparing((SourceEntry entry) -> categoryIndex(categoryOf(entry)))
                .thenComparing(entry -> displayMetadata(entry, entry.context()).title(), String.CASE_INSENSITIVE_ORDER);
            case SLOT_CATEGORY, INDIVIDUAL -> Comparator.comparing(
                entry -> displayMetadata(entry, entry.context()).title(),
                String.CASE_INSENSITIVE_ORDER
            );
        };
    }

    private StatSourceContext slotContext(String slotTag, ItemStack display) {
        String displaySlot = displaySlotName(slotTag);
        String description = "All sources from " + displaySlot.toLowerCase(Locale.ROOT) + ".";
        return new StatSourceContext(
            displaySlot,
            "&8" + displaySlot,
            description,
            display != null ? display : new ItemStack(slotMaterial(slotTag)),
            SourceCategory.UNKNOWN,
            slotTag
        );
    }

    private ItemStack slotDisplayItem(List<SourceEntry> entries) {
        for (SourceEntry entry : entries) {
            StatSourceContext ctx = entry.context();
            if (ctx != null && ctx.displayItem() != null) {
                return ctx.displayItem();
            }
        }
        return null;
    }

    private StatSourceContext categoryContext(SourceCategory category, String slotTag) {
        String categoryName = displayCategoryName(category);
        boolean hasSlot = slotTag != null && !slotTag.isBlank();
        String displaySlot = hasSlot ? displaySlotName(slotTag) : "";
        String title = hasSlot ? displaySlot + " " + categoryName : categoryName + " Category";
        String secondary = "&8" + categoryName;
        String description = hasSlot
            ? "Grouped " + categoryName.toLowerCase(Locale.ROOT) + " sources on " + displaySlot.toLowerCase(Locale.ROOT) + "."
            : "All " + categoryName.toLowerCase(Locale.ROOT) + " sources together.";
        return new StatSourceContext(
            title,
            secondary,
            description,
            new ItemStack(categoryMaterial(category)),
            category,
            hasSlot ? slotTag : ""
        );
    }

    private Material categoryMaterial(SourceCategory category) {
        return switch (category) {
            case BASE -> Material.GRASS_BLOCK;
            case ENCHANT -> Material.ENCHANTING_TABLE;
            case PERK -> Material.NETHER_STAR;
            case BONUS -> Material.EMERALD_BLOCK;
            case DEFAULT -> Material.BARRIER;
            case UNKNOWN -> Material.BOOK;
        };
    }

    private Material slotMaterial(String slotTag) {
        String slot = slotTag == null ? "" : slotTag.toUpperCase(Locale.ROOT);
        return switch (slot) {
            case "HELMET" -> Material.NETHERITE_HELMET;
            case "CHESTPLATE" -> Material.NETHERITE_CHESTPLATE;
            case "LEGGINGS" -> Material.NETHERITE_LEGGINGS;
            case "BOOTS" -> Material.NETHERITE_BOOTS;
            case "MAIN_HAND" -> Material.NETHERITE_SWORD;
            case "OFF_HAND" -> Material.SHIELD;
            case "ACCESSORY" -> Material.TOTEM_OF_UNDYING;
            case "PERK" -> Material.NETHER_STAR;
            case "BONUS" -> Material.EMERALD;
            case "DEFAULT" -> Material.PAPER;
            default -> Material.BOOK;
        };
    }

    private String displaySlotName(String slotTag) {
        String slot = slotTag == null ? "" : slotTag.toUpperCase(Locale.ROOT);
        return switch (slot) {
            case "HELMET" -> "Helmet";
            case "CHESTPLATE" -> "Chestplate";
            case "LEGGINGS" -> "Leggings";
            case "BOOTS" -> "Boots";
            case "MAIN_HAND" -> "Main Hand";
            case "OFF_HAND" -> "Off Hand";
            case "ACCESSORY" -> "Accessory";
            case "PERK" -> "Perk";
            case "BONUS" -> "Bonus";
            case "DEFAULT" -> "Default";
            default -> "Misc";
        };
    }

    private String displayCategoryName(SourceCategory category) {
        return switch (category) {
            case BASE -> "Basic";
            case ENCHANT -> "Enchant";
            case PERK -> "Perk";
            case BONUS -> "Bonus";
            case DEFAULT -> "Default";
            case UNKNOWN -> "Misc";
        };
    }

    private List<Component> flattenedFrom(ItemStack displayItem) {
        if (displayItem == null) {
            return List.of();
        }
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        Component name = meta.hasDisplayName()
            ? meta.displayName()
            : Component.text(humanize(displayItem.getType().name()), NamedTextColor.DARK_GRAY);
        name = name == null ? Component.text(humanize(displayItem.getType().name()), NamedTextColor.DARK_GRAY) : name;
        name = name.decoration(TextDecoration.ITALIC, false);
        Component spacer = Component.empty().decoration(TextDecoration.ITALIC, false);
        Component label = Component.text("Flattened from:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
        return List.of(spacer, label, name);
    }

    private double scale(StatId id, double rawValue, boolean allowPercentFallback) {
        if (sh.harold.fulcrum.stats.core.StatIds.MOVEMENT_SPEED.equals(id)) {
            return rawValue * 1000.0;
        }
        if (sh.harold.fulcrum.stats.core.StatIds.CRIT_DAMAGE.equals(id)) {
            return rawValue * 100.0;
        }
        return rawValue;
    }

    private Material materialFor(StatDefinition definition) {
        return Optional.ofNullable(STAT_ICONS.get(definition.id().value())).orElse(Material.PAPER);
    }

    private Material materialForSource(StatSourceId sourceId) {
        String value = sourceId.value().toLowerCase(Locale.ROOT);
        if (value.startsWith("item:")) {
            return Material.DIAMOND_SWORD;
        }
        if (value.contains("perk") || value.contains("unlock")) {
            return Material.ENCHANTED_BOOK;
        }
        return Material.BOOK;
    }

    private String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] tokens = raw.split("[_\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
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

    private String humanizeSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>(Arrays.asList(raw.split(":")));
        if (!parts.isEmpty() && parts.get(0).equalsIgnoreCase("item")) {
            parts.remove(0);
        }
        String slot = !parts.isEmpty() ? parts.get(0) : "";
        boolean isDefault = slot.equalsIgnoreCase("default");
        boolean isEnchant = parts.stream().anyMatch(part -> part.equalsIgnoreCase("enchant"));
        int enchantIndex = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).equalsIgnoreCase("enchant")) {
                enchantIndex = i;
                break;
            }
        }
        List<String> nameParts = new ArrayList<>();
        if (enchantIndex >= 0 && enchantIndex + 1 < parts.size()) {
            nameParts.addAll(parts.subList(enchantIndex + 1, parts.size()));
        } else if (!parts.isEmpty()) {
            nameParts.addAll(parts.subList(isDefault ? 1 : 0, parts.size()));
        }
        String slotLabel = slot.isBlank() || slot.equalsIgnoreCase("default") ? "" : humanize(slot);
        String nameLabel = nameParts.isEmpty() ? "Source" : humanize(String.join(" ", nameParts));
        StringBuilder builder = new StringBuilder();
        if (!slotLabel.isBlank()) {
            builder.append(slotLabel).append(": ");
        }
        builder.append(nameLabel);
        if (isEnchant) {
            builder.append(" (Enchant)");
        }
        if (isDefault && builder.isEmpty()) {
            return "Default";
        }
        return builder.toString();
    }

    private String formattedSigned(double value) {
        String label = VALUE_FORMAT.format(Math.abs(value));
        return (value >= 0 ? "+" : "-") + label;
    }

    private String formattedPercent(double value) {
        return formattedSigned(value * 100.0) + "%";
    }

    private TextColor colorFor(StatId id) {
        StatVisual visual = visualFor(id);
        if (!visual.hasColor()) {
            return NamedTextColor.WHITE;
        }
        TextColor parsed = TextColor.fromCSSHexString(visual.color());
        return parsed == null ? NamedTextColor.WHITE : parsed;
    }

    private String toLegacy(TextColor color) {
        if (color == null) {
            return "&7";
        }
        if (color.equals(NamedTextColor.RED)) return "&c";
        if (color.equals(NamedTextColor.YELLOW)) return "&e";
        if (color.equals(NamedTextColor.GREEN)) return "&a";
        if (color.equals(NamedTextColor.BLUE)) return "&9";
        if (color.equals(NamedTextColor.WHITE)) return "&f";
        if (color.equals(NamedTextColor.GRAY)) return "&7";
        if (color.equals(NamedTextColor.DARK_GRAY)) return "&8";
        return "&f";
    }

    private String statDescription(StatId id, String color, String icon, String base) {
        String prefix = color;
        return switch (id.value()) {
            case "max_health" -> prefix + "Your Health stat increases your maximum health. Base: &f" + base;
            case "attack_damage" -> prefix + "Your Attack Damage raises the damage dealt on hits.";
            case "attack_speed" -> prefix + "Attack Speed affects swing cadence and DPS.";
            case "movement_speed" -> prefix + "Movement Speed makes you sprint and strafe faster.";
            case "armor" -> prefix + "Defense reduces incoming damage.";
            case "crit_damage" -> prefix + "Crit Damage adds bonus damage when you land a crit.";
            default -> prefix + "Influences your power.";
        };
    }

    private String visualIcon(StatId id) {
        StatVisual visual = visualFor(id);
        return visual.hasIcon() ? visual.icon() : "";
    }

    private StatViewState resolveState(Player player) {
        return menuService.getOpenMenu(player)
            .flatMap(menu -> menu.getProperty("statViewState", StatViewState.class))
            .orElse(StatViewState.defaultState());
    }

    private MenuButton groupingToggle(StatViewState state, java.util.function.Consumer<StatViewState> action) {
        boolean grouping = state.groupingEnabled();
        return MenuButton.builder(Material.COMPARATOR)
            .name(grouping ? "&bGrouping: On" : "&bGrouping: Off")
            .secondary("View Mode")
            .description(grouping
                ? "Bucket sources by category."
                : "Show sources without category grouping.")
            .slot(50)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(player -> action.accept(state.toggleGrouping()))
            .build();
    }

    private MenuButton flattenToggle(StatViewState state, java.util.function.Consumer<StatViewState> action) {
        boolean flattened = state.flattenEnabled();
        return MenuButton.builder(Material.COBBLESTONE_SLAB)
            .name(flattened ? "&eFlatten: On" : "&eFlatten: Off")
            .secondary("Layout")
            .description(flattened
                ? "Show individual source cards."
                : "Collapse sources inside their slots.")
            .slot(51)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(player -> action.accept(state.toggleFlatten()))
            .build();
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

    private String legacyColor(StatId id) {
        if (StatIds.MAX_HEALTH.equals(id) || StatIds.ATTACK_DAMAGE.equals(id)) {
            return "&c";
        }
        if (StatIds.ATTACK_SPEED.equals(id)) {
            return "&e";
        }
        if (StatIds.MOVEMENT_SPEED.equals(id)) {
            return "&f";
        }
        if (StatIds.ARMOR.equals(id)) {
            return "&a";
        }
        if (StatIds.CRIT_DAMAGE.equals(id)) {
            return "&9";
        }
        return "&7";
    }

    private List<String> wrap(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        List<String> tokens = List.of(text.split(" "));
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
        return lines;
    }

    private record StatEntry(StatDefinition definition, double baseValue, double finalValue) {
    }

    private record SourceDisplay(String title, String secondary, String description, ItemStack displayItem) {
    }

    private record SourceEntry(StatId statId, StatSourceId sourceId, double totalFlat, double percentAdd, double percentMult, StatSourceContext context, List<SourceEntry> children, SourceView view) {
        SourceEntry {
            children = children == null ? List.of() : List.copyOf(children);
            view = view == null ? SourceView.INDIVIDUAL : view;
        }
    }

    private enum SourceView {
        INDIVIDUAL,
        SLOT,
        CATEGORY,
        SLOT_CATEGORY
    }

    private static final class Contribution {
        double flat = 0.0;
        double percentAdd = 0.0;
        double percentMultFactor = 1.0;
    }
}
