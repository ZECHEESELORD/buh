package sh.harold.fulcrum.plugin.playermenu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.playerdata.PlayerSettingsService;
import sh.harold.fulcrum.plugin.scoreboard.ScoreboardFeature;
import sh.harold.fulcrum.plugin.stash.StashService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerMenuService {

    private static final int MENU_ROWS = 6;
    private static final int MENU_HEADLINE_SLOT = 22;
    private static final String DISPLAY_NAME = "&aPlayer Menu &7(Right Click)";
    private static final List<String> LORE_LINES = List.of("&e&lCLICK &eto open!");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final Logger logger;
    private final DocumentCollection players;
    private final MenuService menuService;
    private final StashService stashService;
    private final PlayerSettingsService settingsService;
    private final ScoreboardService scoreboardService;
    private final NamespacedKey markerKey;

    public PlayerMenuService(
        JavaPlugin plugin,
        DataApi dataApi,
        StashService stashService,
        MenuService menuService,
        PlayerSettingsService settingsService,
        ScoreboardService scoreboardService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.stashService = Objects.requireNonNull(stashService, "stashService");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.scoreboardService = Objects.requireNonNull(scoreboardService, "scoreboardService");
        this.markerKey = new NamespacedKey(plugin, "player_menu");
    }

    public CompletionStage<Void> distribute(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        return players.load(playerId.toString())
            .thenCompose(document -> resolveConfig(document)
            .thenCompose(config -> placeMenuItem(player, config)))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to prepare player menu for " + playerId, throwable);
                return null;
            });
    }

    public CompletionStage<Void> openMenu(Player player) {
        Objects.requireNonNull(player, "player");
        MenuDisplayItem headline = MenuDisplayItem.builder(Material.PLAYER_HEAD)
            .name("&aPlayer Menu")
            .secondary(player.getName())
            .description("&7Features unlock soon; stay tuned.")
            .slot(MENU_HEADLINE_SLOT)
            .build();

        int closeSlot = MenuButton.getCloseSlot(MENU_ROWS);
        MenuButton settingsButton = MenuButton.builder(Material.REDSTONE_TORCH)
            .name("&cView/Modify Settings")
            .secondary("Game Settings")
            .description("Adjust gameplay preferences and other options.")
            .slot(closeSlot + 1)
            .onClick(this::openSettingsMenu)
            .build();

        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        try {
            menuService.createMenuBuilder()
                .title("Player Menu")
                .rows(MENU_ROWS)
                .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                .addButton(MenuButton.createPositionedClose(MENU_ROWS))
                .addButton(settingsButton)
                .addItem(headline, MENU_HEADLINE_SLOT)
                .buildAsync(player)
                .whenComplete((menu, throwable) -> {
                    if (throwable != null) {
                        logger.log(Level.SEVERE, "Failed to open player menu for " + player.getUniqueId(), throwable);
                        openFuture.completeExceptionally(throwable);
                        return;
                    }
                    openFuture.complete(null);
                });
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "Failed to open player menu for " + player.getUniqueId(), throwable);
            openFuture.completeExceptionally(throwable);
        }
        return openFuture;
    }

    private void openSettingsMenu(Player player) {
        UUID playerId = player.getUniqueId();
        settingsService.isScoreboardEnabled(playerId)
            .whenComplete((enabled, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to load settings for " + playerId, throwable);
                    player.sendMessage("§cSettings are snoozing; try again soon.");
                    return;
                }

                int closeSlot = MenuButton.getCloseSlot(MENU_ROWS);
                MenuButton backButton = MenuButton.builder(Material.ARROW)
                    .name("&7Back")
                    .description("Return to the player menu.")
                    .slot(closeSlot - 1)
                    .onClick(this::openMenu)
                    .build();

                MenuButton scoreboardToggle = MenuButton.builder(Material.COMPARATOR)
                    .name(enabled ? "&aScoreboard: Enabled" : "&cScoreboard: Disabled")
                    .secondary("Display")
                    .description("Toggle the sidebar scoreboard on or off.")
                    .slot(10)
                    .onClick(viewer -> toggleScoreboard(viewer, !enabled))
                    .build();

                MenuButton relocateButton = MenuButton.builder(Material.ENDER_PEARL)
                    .name("&bRelocate Menu Item")
                    .secondary("Inventory Slot")
                    .description("Choose where the player menu item lives.")
                    .slot(28)
                    .onClick(this::openRelocateMenu)
                    .build();

                menuService.createMenuBuilder()
                    .title("Settings")
                    .rows(MENU_ROWS)
                    .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                    .addButton(MenuButton.createPositionedClose(MENU_ROWS))
                    .addButton(backButton)
                    .addButton(scoreboardToggle)
                    .addButton(relocateButton)
                    .buildAsync(player)
                    .exceptionally(openError -> {
                        logger.log(Level.SEVERE, "Failed to open settings menu for " + player.getUniqueId(), openError);
                        return null;
                    });
            });
    }

    private void toggleScoreboard(Player player, boolean enable) {
        UUID playerId = player.getUniqueId();
        settingsService.setScoreboardEnabled(playerId, enable)
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (enable) {
                    scoreboardService.showScoreboard(playerId, ScoreboardFeature.SCOREBOARD_ID);
                } else {
                    scoreboardService.hideScoreboard(playerId);
                }
                openSettingsMenu(player);
            }))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to toggle scoreboard for " + playerId, throwable);
                player.sendMessage("§cCould not update your scoreboard setting.");
                return null;
            });
    }

    private void openRelocateMenu(Player player) {
        UUID playerId = player.getUniqueId();
        loadConfig(playerId)
            .whenComplete((config, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to load menu item config for relocation for " + playerId, throwable);
                    player.sendMessage("§cCould not load your menu item.");
                    return;
                }

                int closeSlot = MenuButton.getCloseSlot(MENU_ROWS);
                int backSlot = closeSlot - 1;

                MenuButton backButton = MenuButton.builder(Material.ARROW)
                    .name("&7Back")
                    .description("Return to the settings menu.")
                    .slot(backSlot)
                    .onClick(this::openSettingsMenu)
                    .build();

                List<MenuItemPlacement> placements = buildMenuPlacements(player, config);

                var builder = menuService.createMenuBuilder()
                    .title("Relocate Menu Item")
                    .rows(MENU_ROWS)
                    .addButton(MenuButton.createPositionedClose(MENU_ROWS))
                    .addButton(backButton);

                placements.forEach(placement -> builder.addButton(placement.button(), placement.menuSlot()));

                fillDividerRow(builder);
                fillControlRow(builder, closeSlot, backSlot);

                builder.buildAsync(player)
                    .exceptionally(openError -> {
                        logger.log(Level.SEVERE, "Failed to open relocation menu for " + playerId, openError);
                        return null;
                    });
            });
    }

    private void updateMenuItemSlot(Player player, PlayerMenuItemConfig config) {
        UUID playerId = player.getUniqueId();
        persistConfig(playerId, config)
            .thenCompose(ignored -> placeMenuItem(player, config))
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, (Runnable) player::closeInventory))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to move player menu item for " + playerId, throwable);
                player.sendMessage("§cCould not move your menu item.");
                return null;
            });
    }

    private List<MenuItemPlacement> buildMenuPlacements(Player player, PlayerMenuItemConfig config) {
        PlayerInventory inventory = player.getInventory();
        List<MenuItemPlacement> placements = new ArrayList<>();

        // Inventory rows (slots 9-35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int playerSlot = 9 + row * 9 + col;
                int menuSlot = row * 9 + col;
                placements.add(createSlotButton(inventory, config, playerSlot, menuSlot));
            }
        }

        // Hotbar row (slots 0-8) at menu row index 4
        int hotbarRowIndex = 4;
        for (int col = 0; col < 9; col++) {
            int playerSlot = col;
            int menuSlot = hotbarRowIndex * 9 + col;
            placements.add(createSlotButton(inventory, config, playerSlot, menuSlot));
        }

        return placements;
    }

    private MenuItemPlacement createSlotButton(PlayerInventory inventory, PlayerMenuItemConfig config, int playerSlot, int menuSlot) {
        ItemStack occupant = inventory.getItem(playerSlot);
        boolean free = occupant == null || occupant.getType().isAir();

        MenuButton.Builder builder = MenuButton.builder(free ? Material.STONE_BUTTON : occupant.getType())
            .name(free ? "&aPlace menu item here" : "&7Select slot")
            .slot(menuSlot)
            .skipClickPrompt()
            .onClick(viewer -> {
                if (!free) {
                    return;
                }
                updateMenuItemSlot(viewer, config.withSlot(playerSlot));
            });

        if (free) {
            builder.description("Move the player menu item here.");
        } else {
            builder.lore("&cCannot move here!", "&cMove this item first.");
        }

        MenuButton button = builder.build();

        if (!free) {
            ItemStack clone = occupant.clone();
            button.setDisplayItem(clone);
        }

        return new MenuItemPlacement(menuSlot, button);
    }

    private void fillDividerRow(sh.harold.fulcrum.api.menu.CustomMenuBuilder builder) {
        int dividerRow = 3;
        for (int col = 0; col < 9; col++) {
            int slot = dividerRow * 9 + col;
            builder.addItem(MenuDisplayItem.builder(Material.GRAY_STAINED_GLASS_PANE).name("").slot(slot).build(), slot);
        }
    }

    private void fillControlRow(sh.harold.fulcrum.api.menu.CustomMenuBuilder builder, int closeSlot, int backSlot) {
        int controlRow = 5;
        for (int col = 0; col < 9; col++) {
            int slot = controlRow * 9 + col;
            if (slot == closeSlot || slot == backSlot) {
                continue;
            }
            builder.addItem(MenuDisplayItem.builder(Material.BLACK_STAINED_GLASS_PANE).name("").slot(slot).build(), slot);
        }
    }

    private CompletionStage<PlayerMenuItemConfig> loadConfig(UUID playerId) {
        return players.load(playerId.toString())
            .thenCompose(this::resolveConfig);
    }

    private CompletionStage<Void> persistConfig(UUID playerId, PlayerMenuItemConfig config) {
        return players.load(playerId.toString())
            .thenCompose(document -> document.set(PlayerMenuItemConfig.MATERIAL_PATH, config.material().name()).toCompletableFuture()
                .thenCompose(ignored -> document.set(PlayerMenuItemConfig.SLOT_PATH, config.slot()).toCompletableFuture())
                .thenApply(ignored -> null));
    }

    private CompletionStage<PlayerMenuItemConfig> resolveConfig(Document document) {
        Optional<String> rawMaterial = document.get(PlayerMenuItemConfig.MATERIAL_PATH, String.class);
        Optional<Integer> rawSlot = document.get(PlayerMenuItemConfig.SLOT_PATH, Integer.class);
        Material material = rawMaterial
            .map(this::materialFrom)
            .filter(candidate -> candidate != null && !candidate.isAir())
            .orElse(PlayerMenuItemConfig.DEFAULT.material());
        int slot = rawSlot.orElse(PlayerMenuItemConfig.DEFAULT.slot());

        PlayerMenuItemConfig resolved = new PlayerMenuItemConfig(material, slot);
        if (rawMaterial.isPresent() && rawSlot.isPresent()) {
            return CompletableFuture.completedFuture(resolved);
        }

        CompletionStage<Void> materialStage = rawMaterial.isPresent()
            ? CompletableFuture.completedFuture(null)
            : document.set(PlayerMenuItemConfig.MATERIAL_PATH, resolved.material().name()).toCompletableFuture();
        CompletionStage<Void> slotStage = rawSlot.isPresent()
            ? CompletableFuture.completedFuture(null)
            : document.set(PlayerMenuItemConfig.SLOT_PATH, resolved.slot()).toCompletableFuture();

        return CompletableFuture.allOf(materialStage.toCompletableFuture(), slotStage.toCompletableFuture())
            .thenApply(ignored -> resolved)
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to store menu item config", throwable);
            });
    }

    private CompletionStage<Void> placeMenuItem(Player player, PlayerMenuItemConfig config) {
        return CompletableFuture.runAsync(() -> placeMenuItemSync(player, config), plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
    }

    private void placeMenuItemSync(Player player, PlayerMenuItemConfig config) {
        if (!player.isOnline()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int existingMenuSlot = findMenuItemSlot(inventory);
        int targetSlot = config.slot();
        ItemStack current = inventory.getItem(targetSlot);
        ItemStack menuItem = buildMenuItem(config.material());

        if (existingMenuSlot == targetSlot) {
            inventory.setItem(targetSlot, menuItem);
            return;
        }

        ItemStack displaced = current == null ? null : current.clone();
        inventory.setItem(targetSlot, menuItem);

        if (existingMenuSlot >= 0) {
            if (displaced != null && !displaced.getType().isAir()) {
                inventory.setItem(existingMenuSlot, displaced);
            } else {
                inventory.setItem(existingMenuSlot, null);
            }
        } else if (displaced != null && !displaced.getType().isAir()) {
            inventory.setItem(targetSlot, menuItem);
            stashDisplaced(player, displaced);
        }
    }

    public boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(markerKey, PersistentDataType.BYTE);
    }

    private ItemStack buildMenuItem(Material material) {
        ItemStack stack = new ItemStack(material == null || material.isAir() ? PlayerMenuItemConfig.DEFAULT.material() : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            Component displayName = LEGACY.deserialize(DISPLAY_NAME).decoration(TextDecoration.ITALIC, false);
            List<Component> lore = LORE_LINES.stream()
                .map(line -> (Component) LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false))
                .toList();
            meta.displayName(displayName);
            meta.lore(lore);
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void stashDisplaced(Player player, ItemStack displaced) {
        stashService.stash(player, List.of(displaced))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to stash displaced hotbar item for " + player.getUniqueId(), throwable);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.getInventory().addItem(displaced);
                    }
                });
                return null;
            });
    }

    private Material materialFrom(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw);
    }

    private int findMenuItemSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isMenuItem(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private record MenuItemPlacement(int menuSlot, MenuButton button) {
    }
}
