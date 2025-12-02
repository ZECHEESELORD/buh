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
import org.bukkit.event.entity.EntityPickupItemEvent;
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
        sanitize(event.getPlayer());
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        sanitize(event.getPlayer());
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        sanitize(event.getPlayer());
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        sanitize(event.getPlayer());
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        sanitize(event.getPlayer());
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        sanitize(player);
        refreshLater(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        refreshLater(player);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        refreshLater(player);
    }

    private void sanitize(Player player) {
        var inventory = player.getInventory();
        inventory.setItemInMainHand(sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(inventory.getItemInMainHand()));
        inventory.setItemInOffHand(sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(inventory.getItemInOffHand()));
        inventory.setHelmet(sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(inventory.getHelmet()));
        inventory.setChestplate(sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(inventory.getChestplate()));
        inventory.setLeggings(sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(inventory.getLeggings()));
        inventory.setBoots(sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(inventory.getBoots()));
    }

    private void refreshLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            statBridge.refreshPlayer(player);
            player.updateInventory();
        });
    }
}
