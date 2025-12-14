package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer;

import java.util.Objects;

public final class AnvilListener implements Listener {

    private static final double LOG_BASE = Math.log(2.0);
    private static final double REPAIR_COST_MULTIPLIER = 5.0;

    private final Plugin plugin;
    private final ItemResolver resolver;

    public AnvilListener(Plugin plugin, ItemResolver resolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        ItemStack updated = computeResult(event.getInventory(), event.getResult());
        event.setResult(updated);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        if (event.getRawSlot() != 2) { // result slot
            return;
        }
        ItemStack updated = computeResult(anvil, anvil.getResult());
        event.setCurrentItem(updated);
        anvil.setItem(2, updated);
    }

    private ItemStack computeResult(AnvilInventory inventory, ItemStack vanillaResult) {
        if (vanillaResult == null || vanillaResult.getType().isAir()) {
            inventory.setRepairCost(0);
            return null;
        }
        int linearRepairs = readLinearRepairCount(inventory.getFirstItem());
        int uiCost = computeRepairCost(linearRepairs);
        inventory.setRepairCost(uiCost);
        boolean hasRightItem = inventory.getSecondItem() != null && !inventory.getSecondItem().getType().isAir();
        ItemStack working = vanillaResult.clone();
        ItemResolver.AnvilEnchantMerge anvilMerge = resolver.applyAnvilEnchantMerge(
            working,
            inventory.getFirstItem(),
            inventory.getSecondItem()
        );
        working = anvilMerge.stack();
        if (anvilMerge.incompatibleMerge() && hasRightItem) {
            return null;
        }
        ItemStack normalized = resolver.resolve(working).map(ItemInstance::stack).orElse(working.clone());
        normalized = setLinearRepairCost(normalized, linearRepairs + 1);
        normalized = ItemSanitizer.normalize(normalized);
        plugin.getServer().getScheduler().runTask(plugin, () ->
            inventory.getViewers().forEach(viewer -> {
                if (viewer instanceof org.bukkit.entity.Player player) {
                    player.updateInventory();
                }
            })
        );
        return normalized;
    }

    private int readLinearRepairCount(ItemStack base) {
        if (base == null) {
            return 0;
        }
        ItemMeta meta = base.getItemMeta();
        if (!(meta instanceof Repairable repairable)) {
            return 0;
        }
        int stored = Math.max(0, repairable.getRepairCost());
        if (stored <= 1) {
            return stored;
        }
        double inferred = Math.log(stored + 1.0) / LOG_BASE;
        return Math.max(1, (int) Math.floor(inferred));
    }

    private ItemStack setLinearRepairCost(ItemStack stack, int cost) {
        if (stack == null || cost < 0) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Repairable repairable)) {
            return stack;
        }
        repairable.setRepairCost(cost);
        stack.setItemMeta((ItemMeta) repairable);
        return stack;
    }

    private int computeRepairCost(int linearRepairs) {
        double scaled = REPAIR_COST_MULTIPLIER * (Math.log(linearRepairs + 1.0) / LOG_BASE);
        int floored = (int) Math.floor(scaled);
        return Math.max(1, floored);
    }
}
