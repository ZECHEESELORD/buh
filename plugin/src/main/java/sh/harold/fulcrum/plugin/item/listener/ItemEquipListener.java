package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.stat.ItemStatBridge;

public final class ItemEquipListener implements Listener {

    private final Plugin plugin;
    private final ItemStatBridge statBridge;

    public ItemEquipListener(Plugin plugin, ItemStatBridge statBridge) {
        this.plugin = plugin;
        this.statBridge = statBridge;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        statBridge.refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        statBridge.refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        statBridge.refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        statBridge.refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        statBridge.refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.PLAYER) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> statBridge.refreshPlayer(player));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> statBridge.refreshPlayer(player));
    }
}
