package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.common.data.ledger.item.ItemCreationSource;
import sh.harold.fulcrum.plugin.item.ItemEngine;

import java.util.Objects;

public final class CraftProvenanceListener implements Listener {

    private final ItemEngine itemEngine;

    public CraftProvenanceListener(ItemEngine itemEngine) {
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        ItemStack tagged = itemEngine.tagItem(result, ItemCreationSource.CRAFT);
        event.getInventory().setResult(tagged);
    }
}
