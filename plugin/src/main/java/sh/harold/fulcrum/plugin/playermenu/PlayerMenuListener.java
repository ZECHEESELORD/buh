package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        PlayerInventory inventory = player.getInventory();
        ItemStack hotbarSwap = event.getHotbarButton() >= 0 ? inventory.getItem(event.getHotbarButton()) : null;

        boolean currentIsMenuItem = menuService.isMenuItem(current);
        boolean cursorIsMenuItem = menuService.isMenuItem(cursor);
        boolean hotbarSwapIsMenuItem = menuService.isMenuItem(hotbarSwap);

        if (!currentIsMenuItem && !cursorIsMenuItem && !hotbarSwapIsMenuItem) {
            return;
        }

        event.setCancelled(true);

        ClickType click = event.getClick();
        if ((click == ClickType.LEFT || click == ClickType.RIGHT) && (currentIsMenuItem || cursorIsMenuItem)) {
            menuService.openMenu(player)
                .exceptionally(throwable -> {
                    player.sendMessage("§cFailed to open the player menu; try again soon.");
                    return null;
                });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (menuService.isMenuItem(event.getMainHandItem()) || menuService.isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}
