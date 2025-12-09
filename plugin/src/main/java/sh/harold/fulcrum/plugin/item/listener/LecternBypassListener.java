package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.Material;
import org.bukkit.block.Lectern;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Ensures lectern interaction is not cancelled by ability handling or other interceptors.
 */
public final class LecternBypassListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            return; // allow sneak abilities to fire on lecterns if desired
        }
        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LECTERN) {
            return;
        }
        if (!(block.getState() instanceof Lectern lectern)) {
            return;
        }
        var book = lectern.getSnapshotInventory().getItem(0);
        if (book == null || book.getType().isAir()) {
            return;
        }

        event.setCancelled(false);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.ALLOW);
        event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
    }
}
