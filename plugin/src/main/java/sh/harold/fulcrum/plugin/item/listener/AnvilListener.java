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
import java.util.function.Consumer;

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
        applyResult(event.getInventory(), event.getResult(), event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        if (event.getRawSlot() != 2) { // result slot
            return;
        }
        applyResult(anvil, anvil.getResult(), updated -> anvil.setItem(2, updated));
    }

    private void applyResult(AnvilInventory inventory, ItemStack vanillaResult, Consumer<ItemStack> resultConsumer) {
        if (vanillaResult == null || vanillaResult.getType().isAir()) {
            resultConsumer.accept(null);
            inventory.setRepairCost(0);
            return;
        }
        int linearRepairs = readLinearRepairCount(inventory.getFirstItem());
        int uiCost = computeRepairCost(linearRepairs);
        inventory.setRepairCost(uiCost);
        ItemStack working = vanillaResult.clone();
        ItemResolver.EnchantMerge merge = resolver.mergeEnchants(working);
        boolean hasRightItem = inventory.getSecondItem() != null && !inventory.getSecondItem().getType().isAir();
        if (merge.removedIncompatibles() && hasRightItem) {
            resultConsumer.accept(null);
            return;
        }
        ItemStack normalized = resolver.resolve(working).map(ItemInstance::stack).orElse(working.clone());
        normalized = setLinearRepairCost(normalized, linearRepairs + 1);
        normalized = ItemSanitizer.normalize(normalized);
        resultConsumer.accept(normalized);
        plugin.getServer().getScheduler().runTask(plugin, () ->
            inventory.getViewers().forEach(viewer -> {
                if (viewer instanceof org.bukkit.entity.Player player) {
                    player.updateInventory();
                }
            })
        );
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
