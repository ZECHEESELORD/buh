package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
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
        ItemStack normalized = itemEngine.tagItem(stack, source);
        if (!normalized.equals(stack)) {
            writer.accept(normalized);
        }
    }
}
