package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.block.Block;
import sh.harold.fulcrum.plugin.staff.StaffCreativeService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static sh.harold.fulcrum.plugin.osu.VerificationConstants.REGISTRATION_TAG;

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
        if (action == Action.RIGHT_CLICK_BLOCK && !event.getPlayer().isSneaking()) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable()) {
                return; // let vanilla block interactions (like lecterns) proceed
            }
        }
        if (isRegistrationLocked(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (event.getPlayer().getScoreboardTags().contains(StaffCreativeService.CREATIVE_TAG)) {
            event.getPlayer().performCommand("item");
            return;
        }
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

        boolean interactingWithMenuItem = currentIsMenuItem || cursorIsMenuItem;
        boolean swappingIntoMenuSlot = hotbarSwapIsMenuItem;

        if (!interactingWithMenuItem && !swappingIntoMenuSlot) {
            return;
        }

        boolean creativePickupIntoMenuSlot = swappingIntoMenuSlot
            && event.getView().getType() == InventoryType.CREATIVE;

        if (interactingWithMenuItem) {
            event.setCancelled(true);

            if (isRegistrationLocked(player)) {
                return;
            }

            ClickType click = event.getClick();
            if ((click == ClickType.LEFT || click == ClickType.RIGHT) && (currentIsMenuItem || cursorIsMenuItem)) {
                menuService.openMenu(player)
                    .exceptionally(throwable -> {
                        player.sendMessage("§cFailed to open the player menu; try again soon.");
                        return null;
                    });
            }
            return;
        }

        if (creativePickupIntoMenuSlot) {
            menuService.distribute(player);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (menuService.isMenuItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int menuSlot = findMenuSlot(inventory);
        if (menuSlot < 0) {
            return;
        }

        InventoryView view = event.getView();
        Map<Integer, ItemStack> allowedPlacements = new HashMap<>();
        boolean touchesMenuSlot = false;
        int placedAmount = 0;

        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int rawSlot = entry.getKey();
            Integer playerSlot = mapToPlayerSlot(rawSlot, view);
            if (playerSlot != null && playerSlot == menuSlot) {
                touchesMenuSlot = true;
                continue;
            }
            allowedPlacements.put(rawSlot, entry.getValue());
        }

        if (!touchesMenuSlot) {
            return;
        }

        event.setCancelled(true);
        for (Map.Entry<Integer, ItemStack> entry : allowedPlacements.entrySet()) {
            int rawSlot = entry.getKey();
            ItemStack newStack = entry.getValue();
            ItemStack existing = view.getItem(rawSlot);
            int existingAmount = existing == null ? 0 : existing.getAmount();
            int delta = Math.max(0, newStack.getAmount() - existingAmount);
            placedAmount += delta;
            view.setItem(rawSlot, newStack);
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            int remaining = Math.max(0, cursor.getAmount() - placedAmount);
            ItemStack updatedCursor = remaining <= 0 ? null : cursor.clone();
            if (updatedCursor != null) {
                updatedCursor.setAmount(remaining);
            }
            view.setCursor(updatedCursor);
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

    private boolean isRegistrationLocked(Player player) {
        return player.getScoreboardTags().contains(REGISTRATION_TAG);
    }

    private int findMenuSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (menuService.isMenuItem(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private Integer mapToPlayerSlot(int rawSlot, InventoryView view) {
        int topSize = view.getTopInventory().getSize();
        if (rawSlot < topSize) {
            return null;
        }
        if (!(view.getBottomInventory() instanceof PlayerInventory bottom)) {
            return null;
        }
        int bottomIndex = rawSlot - topSize;
        if (bottomIndex < 0 || bottomIndex >= bottom.getSize()) {
            return null;
        }
        if (bottomIndex >= 27 && bottomIndex <= 35) {
            return bottomIndex - 27; // hotbar 0-8
        }
        if (bottomIndex <= 26) {
            return bottomIndex + 9; // main inventory 9-35
        }
        return null;
    }
}
