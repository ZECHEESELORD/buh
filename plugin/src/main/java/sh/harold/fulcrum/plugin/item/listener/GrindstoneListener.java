package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class GrindstoneListener implements Listener {

    private static final int RESULT_SLOT = 2;
    private static final Set<String> CURSE_IDS = Set.of(
        "fulcrum:curse_of_binding",
        "fulcrum:curse_of_vanishing"
    );

    private final ItemPdc itemPdc;

    public GrindstoneListener(ItemPdc itemPdc) {
        this.itemPdc = Objects.requireNonNull(itemPdc, "itemPdc");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareGrindstoneEvent event) {
        applyResult(event.getInventory(), event.getResult(), event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory grindstone)) {
            return;
        }
        if (event.getRawSlot() != RESULT_SLOT) {
            return;
        }
        applyResult(grindstone, grindstone.getItem(RESULT_SLOT), updated -> grindstone.setItem(RESULT_SLOT, updated));
    }

    private void applyResult(GrindstoneInventory inventory, ItemStack vanillaResult, Consumer<ItemStack> resultConsumer) {
        if (inventory == null) {
            return;
        }
        ItemStack updated = vanillaResult;
        if (updated == null || updated.getType().isAir()) {
            updated = buildCustomDisenchantResult(inventory);
        }
        if (updated == null || updated.getType().isAir()) {
            resultConsumer.accept(null);
            return;
        }
        ItemStack stripped = stripNonCurseEnchants(updated.clone());
        resultConsumer.accept(stripped);
    }

    private ItemStack buildCustomDisenchantResult(GrindstoneInventory inventory) {
        ItemStack upper = inventory.getItem(0);
        ItemStack lower = inventory.getItem(1);
        boolean upperPresent = upper != null && upper.getType() != Material.AIR;
        boolean lowerPresent = lower != null && lower.getType() != Material.AIR;
        if (upperPresent == lowerPresent) {
            return null;
        }
        ItemStack base = upperPresent ? upper : lower;
        Map<String, Integer> enchants = itemPdc.readEnchants(base).orElse(Map.of());
        if (enchants.isEmpty()) {
            return null;
        }
        Map<String, Integer> kept = keepCurses(enchants);
        if (kept.equals(enchants)) {
            return null;
        }
        ItemStack result = base.clone();
        result = kept.isEmpty() ? itemPdc.clearEnchants(result) : itemPdc.writeEnchants(result, kept);
        resetRepairCost(result);
        stripNonCurseVanillaEnchants(result);
        return result;
    }

    private Map<String, Integer> keepCurses(Map<String, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> kept = new HashMap<>();
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String id = entry.getKey();
            int level = entry.getValue() == null ? 0 : entry.getValue();
            if (level <= 0 || id == null) {
                continue;
            }
            if (CURSE_IDS.contains(id)) {
                kept.put(id, level);
            }
        }
        return Map.copyOf(kept);
    }

    private ItemStack stripNonCurseEnchants(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        Map<String, Integer> enchants = itemPdc.readEnchants(stack).orElse(Map.of());
        if (enchants.isEmpty()) {
            return stack;
        }
        Map<String, Integer> kept = keepCurses(enchants);
        if (kept.equals(enchants)) {
            return stack;
        }
        ItemStack updated = kept.isEmpty() ? itemPdc.clearEnchants(stack) : itemPdc.writeEnchants(stack, kept);
        resetRepairCost(updated);
        return updated;
    }

    private void resetRepairCost(ItemStack stack) {
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Repairable repairable)) {
            return;
        }
        if (repairable.getRepairCost() == 0) {
            return;
        }
        repairable.setRepairCost(0);
        stack.setItemMeta((ItemMeta) repairable);
    }

    private void stripNonCurseVanillaEnchants(ItemStack stack) {
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.getEnchants().isEmpty()) {
            return;
        }
        boolean metaChanged = false;
        for (Enchantment enchantment : Set.copyOf(meta.getEnchants().keySet())) {
            if (enchantment == null || enchantment.isCursed()) {
                continue;
            }
            metaChanged |= meta.removeEnchant(enchantment);
        }
        if (metaChanged) {
            stack.setItemMeta(meta);
        }
    }
}

