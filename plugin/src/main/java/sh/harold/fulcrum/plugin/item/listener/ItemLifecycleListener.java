package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.fulcrum.common.data.ledger.item.ItemCreationSource;
import sh.harold.fulcrum.plugin.item.ItemEngine;

import java.util.Objects;

/**
 * Normalizes items as they enter or leave the world so provenance, ids, and stats stay consistent for stacking.
 */
public final class ItemLifecycleListener implements Listener {

    private final ItemEngine itemEngine;
    public ItemLifecycleListener(ItemEngine itemEngine) {
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        normalizeEntityItem(event.getEntity().getItemStack(), ItemCreationSource.UNKNOWN, event.getEntity()::setItemStack);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        event.getItems().forEach(drop ->
            normalizeEntityItem(drop.getItemStack(), ItemCreationSource.UNKNOWN, drop::setItemStack)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        normalizeEntityItem(event.getItemDrop().getItemStack(), ItemCreationSource.UNKNOWN, event.getItemDrop()::setItemStack);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack dropStack = event.getItem().getItemStack();
            ItemStack sanitizedDrop = itemEngine.sanitizeStackable(dropStack);
            if (!sanitizedDrop.equals(dropStack)) {
                event.getItem().setItemStack(sanitizedDrop);
            }
            sanitizeInventoryStacks(player, sanitizedDrop);
        }
        normalizeEntityItem(event.getItem().getItemStack(), ItemCreationSource.UNKNOWN, event.getItem()::setItemStack);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        normalizeEntityItem(event.getItem().getItemStack(), ItemCreationSource.UNKNOWN, event.getItem()::setItemStack);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPick(io.papermc.paper.event.player.PlayerPickItemEvent event) {
        org.bukkit.inventory.PlayerInventory inventory = event.getPlayer().getInventory();
        int sourceSlot = event.getSourceSlot();
        if (sourceSlot >= 0) {
            ItemStack source = inventory.getItem(sourceSlot);
            normalizeEntityItem(source, ItemCreationSource.UNKNOWN, updated -> inventory.setItem(sourceSlot, updated));
        }
        int targetSlot = event.getTargetSlot();
        itemEngine.plugin().getServer().getScheduler().runTask(itemEngine.plugin(), () -> {
            ItemStack target = inventory.getItem(targetSlot);
            normalizeEntityItem(target, ItemCreationSource.UNKNOWN, updated -> inventory.setItem(targetSlot, updated));
        });
    }

    private void normalizeEntityItem(ItemStack stack, ItemCreationSource source, java.util.function.Consumer<ItemStack> writer) {
        if (stack == null || stack.getType().isAir() || writer == null) {
            return;
        }
        if (stack.getMaxStackSize() > 1) {
            ItemStack sanitized = itemEngine.sanitizeStackable(stack);
            if (!sanitized.equals(stack)) {
                writer.accept(sanitized);
            }
            return;
        }
        ItemStack normalized = itemEngine.tagItem(stack, source);
        if (!normalized.equals(stack)) {
            writer.accept(normalized);
        }
    }

    private void sanitizeInventoryStacks(Player player, ItemStack incoming) {
        if (incoming == null || incoming.getType().isAir() || incoming.getMaxStackSize() <= 1) {
            return;
        }
        Material type = incoming.getType();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != type || stack.getMaxStackSize() <= 1) {
                continue;
            }
            if (!isVanillaStackable(stack)) {
                continue;
            }
            ItemStack sanitized = itemEngine.sanitizeStackable(stack);
            ItemMeta meta = sanitized.getItemMeta();
            if (meta != null) {
                meta.displayName(null);
                meta.lore(null);
                sanitized.setItemMeta(meta);
            }
            if (!sanitized.equals(stack)) {
                inventory.setItem(slot, sanitized);
            }
        }
    }

    private boolean isVanillaStackable(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getMaxStackSize() <= 1) {
            return false;
        }
        String id = itemEngine.itemPdc().readId(stack).orElse(null);
        return id == null || id.startsWith("vanilla:");
    }
}
