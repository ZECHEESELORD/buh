package sh.harold.fulcrum.plugin.item.migration;

import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.item.ItemModule;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ItemMigrationModule implements FulcrumModule, Listener, Runnable {

    private static final int CONTAINERS_PER_TICK = 12;

    private final Plugin plugin;
    private final ItemModule itemModule;
    private final Deque<ContainerTarget> pending = new ArrayDeque<>();
    private ItemResolver resolver;
    private ItemPdc itemPdc;
    private BukkitTask pumpTask;

    public ItemMigrationModule(Plugin plugin, ItemModule itemModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemModule = Objects.requireNonNull(itemModule, "itemModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            ModuleId.of("item-migration"),
            java.util.Set.of(ModuleId.of("item-engine")),
            ModuleCategory.UTILITY
        );
    }

    @Override
    public CompletionStage<Void> enable() {
        resolver = itemModule.engine() == null ? null : itemModule.engine().resolver();
        itemPdc = itemModule.engine() == null ? null : itemModule.engine().itemPdc();
        if (resolver == null) {
            return CompletableFuture.failedStage(new IllegalStateException("Item engine not available for migration"));
        }
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        pumpTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 40L, 5L);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        if (pumpTask != null) {
            pumpTask.cancel();
        }
        pending.clear();
        return CompletableFuture.completedFuture(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scanInventory(player.getInventory());
        scanInventory(player.getEnderChest());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            return;
        }
        Chunk chunk = event.getChunk();
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder) {
                pending.add(new ContainerTarget(holder, () -> state.update(true, false)));
            }
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof StorageMinecart minecart) {
                pending.add(new ContainerTarget(minecart, () -> {
                }));
            }
        }
    }

    @Override
    public void run() {
        int processed = 0;
        while (processed < CONTAINERS_PER_TICK && !pending.isEmpty()) {
            ContainerTarget target = pending.poll();
            if (target == null) {
                break;
            }
            boolean changed = scanInventory(target.holder().getInventory());
            if (changed) {
                target.afterWrite().run();
            }
            processed++;
        }
    }

    private boolean scanInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemStack updated = processStack(stack);
            if (!Objects.equals(stack, updated)) {
                inventory.setItem(slot, updated);
                changed = true;
            }
        }
        return changed;
    }

    private ItemStack processStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        ItemStack working = processNestedContainers(stack);
        ItemStack normalized = resolver.resolve(working)
            .map(sh.harold.fulcrum.plugin.item.runtime.ItemInstance::stack)
            .orElse(working);
        if (itemPdc != null && normalized != null && normalized.getMaxStackSize() > 1) {
            normalized = stripStackableProvenance(normalized);
        }
        return normalized;
    }

    private ItemStack processNestedContainers(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        boolean changed = false;
        if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState state = blockStateMeta.getBlockState();
            if (state instanceof org.bukkit.block.ShulkerBox shulkerBox) {
                boolean nestedChanged = scanInventory(shulkerBox.getInventory());
                if (nestedChanged) {
                    shulkerBox.update();
                    blockStateMeta.setBlockState(shulkerBox);
                    changed = true;
                }
            }
        }
        if (meta instanceof BundleMeta bundleMeta) {
            List<ItemStack> items = new ArrayList<>(bundleMeta.getItems());
            boolean nestedChanged = false;
            for (int i = 0; i < items.size(); i++) {
                ItemStack inside = items.get(i);
                ItemStack updated = processStack(inside);
                if (!Objects.equals(inside, updated)) {
                    items.set(i, updated);
                    nestedChanged = true;
                }
            }
            if (nestedChanged) {
                bundleMeta.setItems(items);
                changed = true;
            }
        }
        if (changed) {
            stack.setItemMeta(meta);
        }
        if (stack.getMaxStackSize() <= 1) {
            return ItemSanitizer.normalize(stack);
        }
        return stripStackableProvenance(stack);
    }

    private ItemStack stripStackableProvenance(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getMaxStackSize() <= 1) {
            return stack;
        }
        String id = itemPdc.readId(stack).orElse(null);
        if (id != null && !id.startsWith("vanilla:")) {
            return stack;
        }
        ItemStack working = stack;
        ItemMeta meta = working.getItemMeta();
        if (meta == null) {
            return working;
        }
        // Remove per-item uniqueness and Fulcrum metadata to allow stacking for bulk items.
        meta.removeItemFlags(
            org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
            org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
            org.bukkit.inventory.ItemFlag.HIDE_ARMOR_TRIM
        );
        meta.displayName(null);
        meta.lore(null);
        working.setItemMeta(meta);
        working = itemPdc.clear(working, itemPdc.keys().itemId());
        working = itemPdc.clear(working, itemPdc.keys().version());
        working = itemPdc.clear(working, itemPdc.keys().stats());
        working = itemPdc.clear(working, itemPdc.keys().enchants());
        working = itemPdc.clear(working, itemPdc.keys().durabilityCurrent());
        working = itemPdc.clear(working, itemPdc.keys().durabilityMax());
        working = itemPdc.clear(working, itemPdc.keys().trimPattern());
        working = itemPdc.clear(working, itemPdc.keys().trimMaterial());
        working = itemPdc.clear(working, itemPdc.keys().createdAt());
        working = itemPdc.clear(working, itemPdc.keys().source());
        working = itemPdc.clear(working, itemPdc.keys().instanceId());
        return working;
    }

    private record ContainerTarget(InventoryHolder holder, Runnable afterWrite) {
    }
}
