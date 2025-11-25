package sh.harold.fulcrum.api.menu.impl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.api.menu.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simplified implementation of MenuService focusing on basic menu functionality.
 * This implementation removes the complex navigation system in favor of the new
 * parentMenu() builder approach for handling menu relationships.
 */
public class DefaultMenuService implements MenuService {

    private final Plugin plugin;
    private final MenuRegistry menuRegistry;
    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();
    private final Map<Plugin, Set<String>> pluginMenus = new ConcurrentHashMap<>();
    private final Executor mainThreadExecutor;
    private final Logger logger;

    // NEW: Menu instance registry for ID-based menu storage and retrieval
    private final Map<String, Menu> menuInstances = new ConcurrentHashMap<>();

    public DefaultMenuService(Plugin plugin, MenuRegistry menuRegistry) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.menuRegistry = Objects.requireNonNull(menuRegistry, "MenuRegistry cannot be null");
        this.mainThreadExecutor = plugin.getServer().getScheduler().getMainThreadExecutor(plugin);
        this.logger = plugin.getLogger();
    }

    @Override
    public ListMenuBuilder createListMenu() {
        return new DefaultListMenuBuilder(this);
    }

    @Override
    public CustomMenuBuilder createMenuBuilder() {
        return new DefaultCustomMenuBuilder(this);
    }

    @Override
    public TabbedMenuBuilder createTabbedMenu() {
        return new DefaultTabbedMenuBuilder(this);
    }

    @Override
    public CompletableFuture<Void> openMenu(Menu menu, Player player) {
        Objects.requireNonNull(menu, "Menu cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");

        long started = System.nanoTime();
        String requestId = UUID.randomUUID().toString();

        CompletableFuture<Void> openFuture = CompletableFuture.runAsync(() -> {
            openMenus.put(player.getUniqueId(), menu);
            trackMenuForPlugin(menu);
            player.openInventory(menu.getInventory());
            menu.update();
        }, mainThreadExecutor);

        // Warn if the open request stalls
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!openFuture.isDone()) {
                logger.log(Level.WARNING, "Menu open still pending after 5s requestId={0} menu={1} player={2}",
                        new Object[]{requestId, menu.getId(), player.getUniqueId()});
            }
        }, 100L);

        openFuture.whenComplete((ignored, throwable) -> {
            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            if (throwable != null) {
                logger.log(Level.SEVERE, "Failed to open menu requestId=" + requestId + " menu=" + menu.getId()
                        + " player=" + player.getUniqueId() + " after " + durationMs + "ms", throwable);
                return;
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Opened menu requestId=" + requestId + " menu=" + menu.getId()
                        + " player=" + player.getUniqueId() + " in " + durationMs + "ms");
            }
        });

        return openFuture;
    }

    @Override
    public boolean closeMenu(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");

        Menu menu = openMenus.remove(player.getUniqueId());
        if (menu != null) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
            return true;
        }
        return false;
    }

    @Override
    public int closeAllMenus() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (closeMenu(player)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Optional<Menu> getOpenMenu(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        return Optional.ofNullable(openMenus.get(player.getUniqueId()));
    }

    @Override
    public boolean hasMenuOpen(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        return openMenus.containsKey(player.getUniqueId());
    }

    @Override
    public boolean refreshMenu(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        Menu menu = openMenus.get(player.getUniqueId());
        if (menu != null) {
            menu.update();
            return true;
        }
        return false;
    }

    @Override
    public MenuRegistry getMenuRegistry() {
        return menuRegistry;
    }

    @Override
    public void registerPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin cannot be null");
        pluginMenus.computeIfAbsent(plugin, k -> ConcurrentHashMap.newKeySet());
    }

    @Override
    public void unregisterPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin cannot be null");

        Set<String> menuIds = pluginMenus.remove(plugin);
        if (menuIds != null) {
            // Close all menus for all players
            int closed = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                Menu currentMenu = openMenus.get(player.getUniqueId());
                if (currentMenu != null && currentMenu.getOwnerPlugin().equals(plugin)) {
                    closeMenu(player);
                    closed++;
                }
            }

            if (closed > 0) {
                plugin.getLogger().info("Closed " + closed + " menus during plugin unregister");
            }
        }

        // Also unregister from menu registry
        menuRegistry.unregisterTemplates(plugin);
    }

    @Override
    public int getOpenMenuCount() {
        return openMenus.size();
    }

    /**
     * Gets the main plugin instance.
     *
     * @return the plugin
     */
    public Plugin getPlugin() {
        return plugin;
    }
    
    Executor getMainThreadExecutor() {
        return mainThreadExecutor;
    }

    /**
     * Registers a menu instance with a custom ID for later retrieval.
     * This allows any menu to be referenced by children via .parentMenu(id).
     *
     * @param menuId the custom menu ID
     * @param menu   the menu instance to register
     */
    @Override
    public void registerMenuInstance(String menuId, Menu menu) {
        Objects.requireNonNull(menuId, "Menu ID cannot be null");
        Objects.requireNonNull(menu, "Menu cannot be null");

        menuInstances.put(menuId, menu);
    }

    /**
     * Gets a menu instance by its custom ID.
     *
     * @param menuId the menu ID
     * @return an Optional containing the menu if found, empty otherwise
     */
    @Override
    public Optional<Menu> getMenuInstance(String menuId) {
        return Optional.ofNullable(menuInstances.get(menuId));
    }

    /**
     * Checks if a menu instance is registered with the given ID.
     *
     * @param menuId the menu ID
     * @return true if the menu instance exists
     */
    @Override
    public boolean hasMenuInstance(String menuId) {
        return menuInstances.containsKey(menuId);
    }

    /**
     * Opens a registered menu instance by ID.
     *
     * @param menuId the menu ID
     * @param player the player to open the menu for
     * @return CompletableFuture that completes when the menu is opened
     */
    @Override
    public CompletableFuture<Void> openMenuInstance(String menuId, Player player) {
        Optional<Menu> menu = getMenuInstance(menuId);
        if (menu.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Menu instance not found: " + menuId));
        }
        return openMenu(menu.get(), player);
    }

    @Override
    public boolean hasMenuTemplate(String templateId) {
        return menuRegistry.hasTemplate(templateId);
    }

    @Override
    public CompletableFuture<Menu> openMenuTemplate(String templateId, Player player) {
        return menuRegistry.openTemplate(templateId, player);
    }

    /**
     * Shuts down the menu service.
     */
    public void shutdown() {
        // Close all menus
        closeAllMenus();

        // Clear all plugin registrations
        pluginMenus.clear();
        openMenus.clear();

        // Clear registry
        menuRegistry.clearRegistry();
    }

    /**
     * Removes a menu from tracking when closed externally.
     * Called by inventory close event handler.
     *
     * @param player the player whose menu closed
     */
    public void handleMenuClosed(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    /**
     * Tracks a menu for plugin ownership.
     *
     * @param menu the menu to track
     */
    private void trackMenuForPlugin(Menu menu) {
        Plugin owner = menu.getOwnerPlugin();
        if (owner != null) {
            pluginMenus.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet())
                    .add(menu.getId());
        }
    }
}
