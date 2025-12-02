package sh.harold.fulcrum.plugin.item.durability;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.runtime.DurabilityData;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.stat.ItemStatBridge;

import java.util.Objects;

public final class DurabilityService implements Listener {

    private final ItemResolver resolver;
    private final ItemPdc itemPdc;
    private final ItemStatBridge statBridge;

    public DurabilityService(ItemResolver resolver, ItemPdc itemPdc, ItemStatBridge statBridge) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.itemPdc = Objects.requireNonNull(itemPdc, "itemPdc");
        this.statBridge = Objects.requireNonNull(statBridge, "statBridge");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        resolver.resolve(item).ifPresent(instance -> instance.durability().ifPresent(durability -> {
            DurabilityData updated = durability.data().damage(event.getDamage());
            itemPdc.writeDurability(item, updated);
            event.setDamage(0);
            event.setCancelled(true);
            statBridge.refreshPlayer(event.getPlayer());
        }));
    }
}
