package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;

import java.util.Objects;

public final class NullItemPlacementListener implements Listener {

    private static final String NULL_ITEM_ID = "fulcrum:null_item";

    private final ItemPdc itemPdc;

    public NullItemPlacementListener(ItemPdc itemPdc) {
        this.itemPdc = Objects.requireNonNull(itemPdc, "itemPdc");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        ItemStack inHand = event.getItemInHand();
        if (inHand == null || inHand.getType().isAir()) {
            return;
        }
        String itemId = itemPdc.readId(inHand).orElse(null);
        if (NULL_ITEM_ID.equals(itemId)) {
            event.setCancelled(true);
        }
    }
}
