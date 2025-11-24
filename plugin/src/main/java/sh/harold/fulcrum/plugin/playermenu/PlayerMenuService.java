package sh.harold.fulcrum.plugin.playermenu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.stash.StashService;

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

    private static final int HOTBAR_SLOT = 8;
    private static final String DISPLAY_NAME = "&aPlayer Menu &7(Right Click)";
    private static final List<String> LORE_LINES = List.of("&e&lCLICK &eto open!");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final Logger logger;
    private final DocumentCollection players;
    private final MenuService menuService;
    private final StashService stashService;
    private final NamespacedKey markerKey;

    public PlayerMenuService(JavaPlugin plugin, DataApi dataApi, StashService stashService, MenuService menuService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.stashService = Objects.requireNonNull(stashService, "stashService");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
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
        return CompletableFuture.runAsync(() -> {
            int rows = 3;
            MenuDisplayItem headline = MenuDisplayItem.builder(PlayerMenuItemConfig.DEFAULT.material())
                .name("&aPlayer Menu")
                .description("&7Features unlock soon; stay tuned.")
                .slot(13)
                .build();

            menuService.createMenuBuilder()
                .title("&aPlayer Menu")
                .rows(rows)
                .fillEmpty(Material.GRAY_STAINED_GLASS_PANE)
                .addButton(MenuButton.createPositionedClose(rows))
                .addItem(headline, 13)
                .buildAsync(player)
                .exceptionally(throwable -> {
                    logger.log(Level.SEVERE, "Failed to open player menu for " + player.getUniqueId(), throwable);
                    return null;
                });
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
    }

    private CompletionStage<PlayerMenuItemConfig> resolveConfig(Document document) {
        Optional<String> rawMaterial = document.get(PlayerMenuItemConfig.PATH, String.class);
        Material material = rawMaterial
            .map(this::materialFrom)
            .filter(candidate -> candidate != null && !candidate.isAir())
            .orElse(null);

        if (material != null) {
            return CompletableFuture.completedFuture(new PlayerMenuItemConfig(material));
        }

        PlayerMenuItemConfig fallback = PlayerMenuItemConfig.DEFAULT;
        if (!document.exists()) {
            return CompletableFuture.completedFuture(fallback);
        }

        return document.set(PlayerMenuItemConfig.PATH, fallback.material().name())
            .toCompletableFuture()
            .thenApply(ignored -> fallback)
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
        ItemStack current = inventory.getItem(HOTBAR_SLOT);
        ItemStack menuItem = buildMenuItem(config.material());

        if (isMenuItem(current)) {
            inventory.setItem(HOTBAR_SLOT, menuItem);
            return;
        }

        if (current != null && !current.getType().isAir()) {
            ItemStack displaced = current.clone();
            inventory.setItem(HOTBAR_SLOT, null);
            stashDisplaced(player, displaced);
        }

        inventory.setItem(HOTBAR_SLOT, menuItem);
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
            Component displayName = LEGACY.deserialize(DISPLAY_NAME);
            List<Component> lore = LORE_LINES.stream()
                .map(line -> (Component) LEGACY.deserialize(line))
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
}
