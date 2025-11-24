package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

final class PlayerMenuListener implements Listener {

    private final PlayerMenuService menuService;

    PlayerMenuListener(PlayerMenuService menuService) {
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        menuService.distribute(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!menuService.isMenuItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        menuService.openMenu(event.getPlayer())
            .exceptionally(throwable -> {
                event.getPlayer().sendMessage("§cFailed to open the player menu; try again soon.");
                return null;
            });
    }
}
