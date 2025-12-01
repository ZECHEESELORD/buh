package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.stat.ItemStatBridge;

public final class HelmetEquipListener implements Listener {

    private final ItemResolver resolver;
    private final ItemStatBridge statBridge;

    public HelmetEquipListener(ItemResolver resolver, ItemStatBridge statBridge) {
        this.resolver = resolver;
        this.statBridge = statBridge;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack == null) {
            return;
        }

        resolver.resolve(stack).ifPresent(instance -> {
            if (instance.definition().category() != ItemCategory.HELMET) {
                return;
            }
            Player player = event.getPlayer();
            ItemStack previous = player.getInventory().getHelmet();

            ItemStack toEquip = stack.clone();
            toEquip.setAmount(1);
            sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(toEquip);
            player.getInventory().setHelmet(toEquip);

            if (stack.getAmount() > 1) {
                ItemStack remaining = stack.clone();
                remaining.setAmount(stack.getAmount() - 1);
                player.getInventory().setItemInMainHand(remaining);
                if (previous != null) {
                    player.getInventory().addItem(previous);
                }
            } else {
                player.getInventory().setItemInMainHand(previous == null ? null : previous);
            }
            statBridge.refreshPlayer(player);
            event.setCancelled(true);
        });
    }
}
