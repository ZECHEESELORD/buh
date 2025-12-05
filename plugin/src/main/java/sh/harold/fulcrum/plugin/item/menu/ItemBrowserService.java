package sh.harold.fulcrum.plugin.item.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.inventory.ClickType;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import sh.harold.fulcrum.api.menu.CustomMenuBuilder;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.api.menu.component.MenuItem;
import sh.harold.fulcrum.api.menu.impl.DefaultListMenu;
import sh.harold.fulcrum.plugin.item.ItemEngine;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.model.ItemRarity;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;

import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class ItemBrowserService {

    private static final int MENU_ROWS = 6;
    private static final int CLOSE_SLOT = MenuButton.getCloseSlot(MENU_ROWS);
    private static final int FILTER_SLOT = CLOSE_SLOT + 1;
    private static final int SORT_SLOT = CLOSE_SLOT + 2;
    private static final int SEARCH_SLOT = CLOSE_SLOT + 3;
    private static final String MENU_PROPERTY = "itemBrowserMenu";
    private static final String FILTER_MENU_PROPERTY = "itemBrowserFilterMenu";
    private static final String SESSION_KEY = "itemBrowserSession";
    private static final String SEARCH_INPUT_ID = "search";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final ItemEngine itemEngine;
    private final MenuService menuService;
    private final Map<UUID, ItemBrowserSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean vanillaSeeded = new AtomicBoolean(false);
    private final java.util.concurrent.Executor asyncPool = java.util.concurrent.Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        runnable -> {
            Thread thread = new Thread(runnable, "fulcrum-item-browser");
            thread.setDaemon(true);
            return thread;
        }
    );

    public ItemBrowserService(JavaPlugin plugin, ItemEngine itemEngine, MenuService menuService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    public java.util.concurrent.CompletionStage<Void> open(Player player) {
        ItemBrowserSession existing = currentSession(player);
        ItemBrowserState state = existing == null ? ItemBrowserState.defaultState() : existing.state();
        return buildSession(player, state)
            .thenCompose(session -> callSync(() -> open(player, session)))
            .thenCompose(Function.identity());
    }

    private java.util.concurrent.CompletionStage<Void> open(Player player, ItemBrowserSession session) {
        List<MenuItem> content = buildContent(session);

        return menuService.createListMenu()
            .title("Item Browser")
            .rows(MENU_ROWS)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .contentSlots(10, 43)
            .showPageIndicator(false)
            .addButton(MenuButton.createPositionedClose(MENU_ROWS))
            .addButton(buildFilterButton(session.state()))
            .addButton(buildSortButton(session.state()))
            .addButton(buildSearchButton(session.state()))
            .addItems(content)
            .buildAsync(player)
            .thenAccept(menu -> {
                menu.setProperty(MENU_PROPERTY, true);
                menu.getContext().setProperty(SESSION_KEY, session);
                storeSession(player, session);
                if (menu instanceof DefaultListMenu listMenu) {
                    refresh(listMenu, session);
                }
            })
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to open item browser for " + player.getUniqueId(), throwable);
                player.sendMessage(Component.text("Could not open the item browser right now.", NamedTextColor.RED));
                return null;
            });
    }

    private List<MenuItem> buildContent(ItemBrowserSession session) {
        return session.entries().stream()
            .filter(entry -> session.state().sourceFilter().matches(entry.vanilla()))
            .filter(entry -> session.state().rarityFilter().matches(entry.rarity()))
            .filter(entry -> matchesSearch(session.state(), entry))
            .sorted(session.state().sort().comparator())
            .map(this::toDisplayItem)
            .toList();
    }

    private List<ItemEntry> renderEntries(Player viewer) {
        ensureVanillaSeeded();
        return itemEngine.registry().definitions().stream()
            .map(item -> createEntry(item, viewer))
            .sorted(Comparator.comparing(ItemEntry::sortKey))
            .toList();
    }

    private void ensureVanillaSeeded() {
        if (vanillaSeeded.compareAndSet(false, true)) {
            itemEngine.ensureVanillaDefinitions(Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy())
                .toList());
        }
    }

    private ItemEntry createEntry(CustomItem item, Player viewer) {
        ItemStack base = itemEngine.resolver().initializeItem(item);
        ItemStack rendered = itemEngine.loreRenderer().render(base, viewer);
        ItemRarity rarity = item.component(ComponentType.VISUAL, VisualComponent.class)
            .map(VisualComponent::rarity)
            .orElse(ItemRarity.COMMON);
        ItemType type = ItemType.from(item.category());
        Component displayName = displayName(rendered, item);
        String sortKey = PLAIN.serialize(displayName).toLowerCase(Locale.ROOT);
        boolean vanilla = item.id().startsWith("vanilla:");
        return new ItemEntry(item, rendered, rarity, type, sortKey, displayName, vanilla);
    }

    private Component displayName(ItemStack stack, CustomItem item) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return meta.displayName();
        }
        return Component.text(item.id(), NamedTextColor.WHITE);
    }

    private MenuItem toDisplayItem(ItemEntry entry) {
        MenuButton button = MenuButton.builder(entry.rendered().getType())
            .onClick(player -> giveItem(player, entry))
            .build();
        button.setDisplayItem(entry.rendered());
        return button;
    }

    private void giveItem(Player player, ItemEntry entry) {
        ItemStack copy = entry.rendered().clone();
        var leftover = player.getInventory().addItem(copy);
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Component.text("Spawned " + copy.getAmount() + "x " + entry.definition().id() + ".", NamedTextColor.GREEN));
    }

    private MenuButton buildFilterButton(ItemBrowserState state) {
        return MenuButton.builder(Material.COMPARATOR)
            .name("&dFilters")
            .lore(filterSummaryLore(state).toArray(Component[]::new))
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(FILTER_SLOT)
            .onClick(this::openFilterMenu)
            .build();
    }

    private MenuButton buildSortButton(ItemBrowserState state) {
        return MenuButton.builder(Material.HOPPER)
            .name("&6Sort Order")
            .lore(sortLore(state.sort()).toArray(Component[]::new))
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(SORT_SLOT)
            .onClick(this::cycleSort)
            .build();
    }

    private MenuButton buildSourceFilterButton(ItemBrowserState state) {
        return MenuButton.builder(Material.NETHER_STAR)
            .name("&bItem Source")
            .description("Toggle between custom items, vanilla items, or both.")
            .lore(sourceLore(state.sourceFilter()).toArray(Component[]::new))
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(21)
            .onClick(player -> {
                updateSession(player, ItemBrowserState::nextSource);
                reopenFilterMenu(player);
            })
            .build();
    }

    private MenuButton buildRarityFilterButton(ItemBrowserState state) {
        return MenuButton.builder(Material.AMETHYST_SHARD)
            .name("&dRarity Filter")
            .description("Showing " + state.rarityFilter().label() + " items.")
            .lore(rarityLore(state.rarityFilter()).toArray(Component[]::new))
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(23)
            .onClick(player -> {
                updateSession(player, ItemBrowserState::nextRarity);
                reopenFilterMenu(player);
            })
            .build();
    }

    private MenuButton buildSearchButton(ItemBrowserState state) {
        String queryLabel = state.searchQuery().isBlank() ? "None" : state.searchQuery();
        return MenuButton.builder(Material.OAK_SIGN)
            .name("&bSearch Items")
            .description("Current query: " + queryLabel)
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(SEARCH_SLOT)
            .onClick(this::promptSearch)
            .onClick(ClickType.RIGHT, this::clearSearch)
            .build();
    }

    private void cycleSort(Player player) {
        updateSession(player, ItemBrowserState::nextSort);
    }

    private void promptSearch(Player player) {
        ItemBrowserSession session = currentSession(player);
        String initial = session == null ? "" : session.state().searchQuery();
        player.showDialog(buildSearchDialog(player, initial));
    }

    private void clearSearch(Player player) {
        applySearch(player, "");
    }

    private void openFilterMenu(Player player) {
        ItemBrowserSession session = currentSession(player);
        if (session == null) {
            buildSession(player, ItemBrowserState.defaultState())
                .thenComposeAsync(built -> callSync(() -> {
                    openFilterMenu(player, built);
                    return null;
                }), asyncPool)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to open filter menu for " + player.getUniqueId(), throwable);
                    player.sendMessage(Component.text("Filters are being shuffled right now; try again shortly.", NamedTextColor.RED));
                    return null;
                });
            return;
        }
        openFilterMenu(player, session);
    }

    private void reopenFilterMenu(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> openFilterMenu(player));
    }

    private void openFilterMenu(Player player, ItemBrowserSession session) {
        CustomMenuBuilder builder = menuService.createMenuBuilder()
            .title("Item Filters")
            .viewPort(6)
            .rows(6)
            .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
            .autoCloseButton(false);

        builder.addButton(buildSourceFilterButton(session.state()));
        builder.addButton(buildRarityFilterButton(session.state()));
        builder.addButton(buildBackButton());
        builder.addButton(MenuButton.createPositionedClose(6));

        builder.buildAsync(player)
            .thenAccept(menu -> {
                menu.setProperty(FILTER_MENU_PROPERTY, true);
                menu.getContext().setProperty(SESSION_KEY, session);
                storeSession(player, session);
            })
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to open filter menu for " + player.getUniqueId(), throwable);
                player.sendMessage(Component.text("Filters are being shuffled right now; try again shortly.", NamedTextColor.RED));
                return null;
            });
    }

    private MenuButton buildBackButton() {
        int slot = 48;
        return MenuButton.builder(Material.ARROW)
            .name("&aBack to Browser")
            .description("Return to the item list with these filters.")
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(slot)
            .onClick(this::reopenBrowser)
            .build();
    }

    private java.util.concurrent.CompletionStage<ItemBrowserSession> buildSession(Player player, ItemBrowserState state) {
        ItemBrowserSession cached = currentSession(player);
        if (cached != null) {
            return CompletableFuture.completedFuture(new ItemBrowserSession(cached.entries(), state));
        }
        return CompletableFuture.supplyAsync(() -> new ItemBrowserSession(renderEntries(player), state), asyncPool);
    }

    private void reopenBrowser(Player player) {
        ItemBrowserSession session = currentSession(player);
        ItemBrowserState state = session == null ? ItemBrowserState.defaultState() : session.state();
        buildSession(player, state)
            .thenCompose(built -> callSync(() -> open(player, built)))
            .thenCompose(Function.identity())
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to reopen item browser for " + player.getUniqueId(), throwable);
                player.sendMessage(Component.text("Could not reopen the item browser right now.", NamedTextColor.RED));
                return null;
            });
    }

    private void refresh(DefaultListMenu menu, ItemBrowserSession session) {
        menu.getContext().setProperty(SESSION_KEY, session);
        storeSession(menu.getContext().getViewer(), session);
        menu.clearContentItems();
        menu.addContentItems(buildContent(session));
        menu.setPersistentButton(buildFilterButton(session.state()), FILTER_SLOT);
        menu.setPersistentButton(buildSortButton(session.state()), SORT_SLOT);
        menu.setPersistentButton(buildSearchButton(session.state()), SEARCH_SLOT);
        menu.update();
    }

    private List<Component> filterSummaryLore(ItemBrowserState state) {
        Component source = Component.text("Source: ", NamedTextColor.GRAY)
            .append(sourceDisplay(state.sourceFilter()))
            .decoration(TextDecoration.ITALIC, false);
        Component rarity = Component.text("Rarity: ", NamedTextColor.GRAY)
            .append(rarityDisplay(state.rarityFilter()))
            .decoration(TextDecoration.ITALIC, false);
        return List.of(spacer(), source, rarity);
    }

    private List<Component> sourceLore(SourceFilter current) {
        return List.of(
            spacer(),
            sourceLoreLine(SourceFilter.BOTH, current),
            sourceLoreLine(SourceFilter.CUSTOM, current),
            sourceLoreLine(SourceFilter.VANILLA, current)
        );
    }

    private Component sourceLoreLine(SourceFilter filter, SourceFilter current) {
        boolean selected = filter == current;
        Component pointer = Component.text(selected ? "➜ " : "   ", NamedTextColor.YELLOW);
        Component display = sourceDisplay(filter);
        return pointer.append(display).decoration(TextDecoration.ITALIC, false);
    }

    private Component rarityLoreLine(ItemRarityFilter filter, ItemRarityFilter current) {
        boolean selected = filter == current;
        Component pointer = Component.text(selected ? "➜ " : "   ", NamedTextColor.YELLOW);
        Component display = rarityDisplay(filter);
        return pointer.append(display).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> rarityLore(ItemRarityFilter current) {
        return List.of(
            spacer(),
            rarityLoreLine(ItemRarityFilter.ANY, current),
            rarityLoreLine(ItemRarityFilter.COMMON, current),
            rarityLoreLine(ItemRarityFilter.UNCOMMON, current),
            rarityLoreLine(ItemRarityFilter.RARE, current),
            rarityLoreLine(ItemRarityFilter.EPIC, current),
            rarityLoreLine(ItemRarityFilter.LEGENDARY, current)
        );
    }

    private List<Component> sortLore(ItemSort current) {
        Component name = sortLine("Sort by Name", current == ItemSort.NAME);
        Component type = sortLine("Sort by Item Type", current == ItemSort.TYPE);
        return List.of(spacer(), name, type);
    }

    private Component sortLine(String label, boolean selected) {
        String pointer = selected ? "➜ " : "   ";
        NamedTextColor color = selected ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
        return Component.text(pointer + label, color)
            .decoration(TextDecoration.ITALIC, false);
    }

    private Component sourceDisplay(SourceFilter filter) {
        return Component.text(filter.label(), filter.color())
            .decoration(TextDecoration.ITALIC, false);
    }

    private Component rarityDisplay(ItemRarityFilter filter) {
        if (filter == ItemRarityFilter.ANY) {
            Component label = Component.text("ALL", NamedTextColor.DARK_GRAY);
            Component stars = Component.text("★★★★★", NamedTextColor.DARK_GRAY);
            return Component.text()
                .append(stars)
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(label)
                .append(Component.text(")", NamedTextColor.DARK_GRAY))
                .build()
                .decoration(TextDecoration.ITALIC, false);
        }
        ItemRarity rarity = filter.rarity;
        int filled = switch (rarity) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5;
        };
        NamedTextColor color = rarityColor(rarity);
        String filledStars = "★".repeat(filled);
        String emptyStars = "☆".repeat(5 - filled);
        Component stars = Component.text()
            .append(Component.text(filledStars, color))
            .append(Component.text(emptyStars, NamedTextColor.DARK_GRAY))
            .build();
        Component label = Component.text(rarity.name(), NamedTextColor.DARK_GRAY)
            .color(color);
        return Component.text()
            .append(stars)
            .append(Component.text(" (", NamedTextColor.DARK_GRAY))
            .append(label)
            .append(Component.text(")", NamedTextColor.DARK_GRAY))
            .build()
            .decoration(TextDecoration.ITALIC, false);
    }

    private boolean matchesSearch(ItemBrowserState state, ItemEntry entry) {
        if (state.searchQuery().isBlank()) {
            return true;
        }
        String needle = state.searchQuery().toLowerCase(Locale.ROOT);
        return entry.sortKey().contains(needle) || entry.definition().id().toLowerCase(Locale.ROOT).contains(needle);
    }

    private void applySearch(Player player, String query) {
        updateSession(player, state -> state.withSearchQuery(query));
    }

    private void updateSession(Player player, java.util.function.Function<ItemBrowserState, ItemBrowserState> transformer) {
        ItemBrowserSession session = currentSession(player);
        if (session == null) {
            buildSession(player, ItemBrowserState.defaultState())
                .thenCompose(built -> callSync(() -> {
                    ItemBrowserState nextState = transformer.apply(built.state());
                    ItemBrowserSession updated = new ItemBrowserSession(built.entries(), nextState);
                    return open(player, updated);
                }))
                .thenCompose(Function.identity())
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to update item browser for " + player.getUniqueId(), throwable);
                    player.sendMessage(Component.text("Could not update the item browser right now.", NamedTextColor.RED));
                    return null;
                });
            return;
        }
        ItemBrowserState next = transformer.apply(session.state());
        ItemBrowserSession updated = new ItemBrowserSession(session.entries(), next);
        storeSession(player, updated);
        if (!refreshOpenBrowser(player, updated)) {
            callSync(() -> open(player, updated))
                .thenCompose(Function.identity())
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to refresh item browser for " + player.getUniqueId(), throwable);
                    player.sendMessage(Component.text("Could not refresh the item browser right now.", NamedTextColor.RED));
                    return null;
                });
        }
    }

    private boolean refreshOpenBrowser(Player player, ItemBrowserSession session) {
        var menuOpt = menuService.getOpenMenu(player)
            .filter(menu -> menu.getProperty(MENU_PROPERTY, Boolean.class).orElse(false));
        menuOpt
            .filter(DefaultListMenu.class::isInstance)
            .map(DefaultListMenu.class::cast)
            .ifPresent(menu -> refresh(menu, session));
        return menuOpt.isPresent();
    }

    private ItemBrowserSession currentSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    private void storeSession(Player player, ItemBrowserSession session) {
        sessions.put(player.getUniqueId(), session);
    }

    private String rarityLabel(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> "Common";
            case UNCOMMON -> "Uncommon";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Legendary";
        };
    }

    private Component spacer() {
        return Component.text(" ").decoration(TextDecoration.ITALIC, false);
    }

    private NamedTextColor rarityColor(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
    }

    private enum ItemType {
        WEAPON("Weapon", 0),
        ARMOR("Armor", 1),
        UTILITY("Utility", 2),
        ACCESSORY("Accessory", 3),
        MATERIAL("Material", 4),
        CONSUMABLE("Consumable", 5);

        private final String label;
        private final int order;

        ItemType(String label, int order) {
            this.label = label;
            this.order = order;
        }

        public String label() {
            return label;
        }

        public int order() {
            return order;
        }

        public static ItemType from(ItemCategory category) {
            return switch (category) {
                case SWORD, AXE, BOW, WAND, TRIDENT -> WEAPON;
                case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> ARMOR;
                case ACCESSORY -> ACCESSORY;
                case CONSUMABLE -> CONSUMABLE;
                case MATERIAL -> MATERIAL;
                default -> UTILITY;
            };
        }
    }

    private enum SourceFilter {
        BOTH("All Items", true, true, NamedTextColor.GOLD),
        CUSTOM("Custom Only", false, true, NamedTextColor.AQUA),
        VANILLA("Vanilla Only", true, false, NamedTextColor.GREEN);

        private final String label;
        private final boolean includeVanilla;
        private final boolean includeCustom;
        private final NamedTextColor color;

        SourceFilter(String label, boolean includeVanilla, boolean includeCustom, NamedTextColor color) {
            this.label = label;
            this.includeVanilla = includeVanilla;
            this.includeCustom = includeCustom;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public NamedTextColor color() {
            return color;
        }

        public boolean matches(boolean vanilla) {
            return vanilla ? includeVanilla : includeCustom;
        }

        public SourceFilter next() {
            SourceFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private enum ItemSort {
        NAME("Name", Comparator.comparing(ItemEntry::sortKey)),
        TYPE("Item Type", Comparator.comparing((ItemEntry entry) -> entry.type().order()).thenComparing(ItemEntry::sortKey));

        private final String label;
        private final Comparator<ItemEntry> comparator;

        ItemSort(String label, Comparator<ItemEntry> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        public String label() {
            return label;
        }

        public Comparator<ItemEntry> comparator() {
            return comparator;
        }

        public ItemSort next() {
            ItemSort[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private enum ItemRarityFilter {
        ANY("ALL", null),
        COMMON("COMMON", ItemRarity.COMMON),
        UNCOMMON("UNCOMMON", ItemRarity.UNCOMMON),
        RARE("RARE", ItemRarity.RARE),
        EPIC("EPIC", ItemRarity.EPIC),
        LEGENDARY("LEGENDARY", ItemRarity.LEGENDARY);

        private final String label;
        private final ItemRarity rarity;

        ItemRarityFilter(String label, ItemRarity rarity) {
            this.label = label;
            this.rarity = rarity;
        }

        public String label() {
            return label;
        }

        public boolean matches(ItemRarity candidate) {
            return rarity == null || rarity == candidate;
        }

        public ItemRarityFilter next() {
            ItemRarityFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private record ItemBrowserState(SourceFilter sourceFilter, ItemRarityFilter rarityFilter, ItemSort sort, String searchQuery) {
        static ItemBrowserState defaultState() {
            return new ItemBrowserState(SourceFilter.BOTH, ItemRarityFilter.ANY, ItemSort.NAME, "");
        }

        ItemBrowserState nextSource() {
            return new ItemBrowserState(sourceFilter.next(), rarityFilter, sort, searchQuery);
        }

        ItemBrowserState nextRarity() {
            return new ItemBrowserState(sourceFilter, rarityFilter.next(), sort, searchQuery);
        }

        ItemBrowserState nextSort() {
            return new ItemBrowserState(sourceFilter, rarityFilter, sort.next(), searchQuery);
        }

        ItemBrowserState withSearchQuery(String query) {
            return new ItemBrowserState(sourceFilter, rarityFilter, sort, query == null ? "" : query.trim());
        }
    }

    private record ItemEntry(
        CustomItem definition,
        ItemStack rendered,
        ItemRarity rarity,
        ItemType type,
        String sortKey,
        Component displayName,
        boolean vanilla
    ) {
    }

    private record ItemBrowserSession(List<ItemEntry> entries, ItemBrowserState state) {
        private ItemBrowserSession {
            entries = List.copyOf(entries);
        }
    }

    private io.papermc.paper.dialog.Dialog buildSearchDialog(Player player, String initialQuery) {
        DialogInstancesProvider provider = DialogInstancesProvider.instance();

        TextDialogInput queryInput = provider.textBuilder(SEARCH_INPUT_ID, Component.text("Search Query"))
            .initial(initialQuery == null ? "" : initialQuery)
            .width(240)
            .maxLength(64)
            .labelVisible(true)
            .build();

        DialogAction applyAction = provider.register(searchCallback(), ClickCallback.Options.builder().uses(1).build());

        ActionButton applyButton = provider.actionButtonBuilder(Component.text("Apply"))
            .tooltip(Component.text("Update the item list search.", NamedTextColor.GRAY))
            .action(applyAction)
            .build();

        DialogAction cancelAction = provider.register((response, audience) -> { }, ClickCallback.Options.builder().uses(1).build());
        ActionButton cancelButton = provider.actionButtonBuilder(Component.text("Cancel"))
            .tooltip(Component.text("Close without changing search.", NamedTextColor.GRAY))
            .action(cancelAction)
            .build();

        DialogBase base = provider.dialogBaseBuilder(Component.text("Search Items"))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogAfterAction.CLOSE)
            .body(List.of(provider.plainMessageDialogBody(Component.text("Enter text to filter items by name or id.", NamedTextColor.GRAY))))
            .inputs(List.of(queryInput))
            .build();

        io.papermc.paper.registry.data.dialog.type.MultiActionType type = DialogType.multiAction(List.of(applyButton, cancelButton))
            .exitAction(cancelButton)
            .columns(2)
            .build();

        return io.papermc.paper.dialog.Dialog.create(factory -> {
            io.papermc.paper.registry.data.dialog.DialogRegistryEntry.Builder builder = factory.empty();
            builder.base(base);
            builder.type(type);
        });
    }

    private DialogActionCallback searchCallback() {
        return (response, audience) -> {
            if (!(audience instanceof Player player)) {
                return;
            }
            String query = Optional.ofNullable(response.getText(SEARCH_INPUT_ID)).orElse("");
            plugin.getServer().getScheduler().runTask(plugin, () -> applySearch(player, query));
        };
    }

    private <T> java.util.concurrent.CompletionStage<T> callSync(java.util.concurrent.Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
