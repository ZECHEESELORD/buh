package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import sh.harold.fulcrum.common.data.ledger.item.ItemCreationSource;
import sh.harold.fulcrum.plugin.item.ItemEngine;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;

import java.util.Objects;
import java.util.function.Consumer;

public final class SmithingListener implements Listener {

    private static final int RESULT_SLOT = 3;

    private final ItemEngine itemEngine;

    public SmithingListener(ItemEngine itemEngine) {
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareSmithingEvent event) {
        applyResult(event.getInventory(), event.getResult(), event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof SmithingInventory smithing)) {
            return;
        }
        if (event.getRawSlot() != RESULT_SLOT) {
            return;
        }
        applyResult(smithing, smithing.getResult(), updated -> smithing.setItem(RESULT_SLOT, updated));
    }

    private void applyResult(SmithingInventory inventory, ItemStack vanillaResult, Consumer<ItemStack> resultConsumer) {
        if (inventory == null || resultConsumer == null) {
            return;
        }
        if (vanillaResult == null || vanillaResult.getType().isAir()) {
            resultConsumer.accept(null);
            return;
        }
        ItemStack tagged = itemEngine.tagItem(vanillaResult, ItemCreationSource.CRAFT);
        ItemStack normalized = itemEngine.resolver()
            .resolve(tagged)
            .map(ItemInstance::stack)
            .orElse(tagged);
        resultConsumer.accept(normalized);
    }
}

