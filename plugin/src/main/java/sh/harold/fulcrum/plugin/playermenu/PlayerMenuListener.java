package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.inventory.ClickType;

import java.util.Objects;

final class PlayerMenuListener implements Listener {

    private final PlayerMenuService menuService;

    PlayerMenuListener(PlayerMenuService menuService) {
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        menuService.distribute(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!menuService.isMenuItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        menuService.openMenu(event.getPlayer())
            .exceptionally(throwable -> {
                event.getPlayer().sendMessage("§cFailed to open the player menu; try again soon.");
                return null;
            });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (menuService.isMenuItem(current) || menuService.isMenuItem(cursor)) {
            event.setCancelled(true);
            if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
                menuService.openMenu(player)
                    .exceptionally(throwable -> {
                        player.sendMessage("§cFailed to open the player menu; try again soon.");
                        return null;
                    });
            }
            return;
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0) {
            PlayerInventory inventory = player.getInventory();
            if (menuService.isMenuItem(inventory.getItem(hotbarButton))) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getSlotType() == InventoryType.SlotType.QUICKBAR) {
            if (menuService.isMenuItem(event.getCurrentItem())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean touchesMenuSlot = event.getRawSlots().stream()
            .anyMatch(slot -> slot >= 0 && slot < inventory.getSize() && menuService.isMenuItem(inventory.getItem(slot)));
        if (touchesMenuSlot || menuService.isMenuItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (menuService.isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(menuService::isMenuItem);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRespawn(PlayerRespawnEvent event) {
        menuService.distribute(event.getPlayer());
    }
}
