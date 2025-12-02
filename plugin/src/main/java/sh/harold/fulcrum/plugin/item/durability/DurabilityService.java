package sh.harold.fulcrum.plugin.item.durability;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;
import sh.harold.fulcrum.plugin.item.runtime.DurabilityData;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.stat.ItemStatBridge;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class DurabilityService implements Listener {

    private static final String UNBREAKING_ID = "fulcrum:unbreaking";

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
            if (durability.defunct()) {
                return;
            }
            int baseDamage = event.getDamage();
            int level = instance.enchants().getOrDefault(UNBREAKING_ID, 0);
            int appliedDamage = applyUnbreaking(baseDamage, level);
            if (appliedDamage <= 0) {
                event.setDamage(0);
                event.setCancelled(true);
                return;
            }
            DurabilityData updated = durability.data().damage(appliedDamage);
            itemPdc.writeDurability(item, updated);
            event.setDamage(0);
            event.setCancelled(true);
            statBridge.refreshPlayer(event.getPlayer());
            if (updated.defunct()) {
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 1.0f);
                String label = label(instance.definition().category());
                event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text("Your " + label + " has lost all its durability, but it's not broken! Repair it to continue use!", net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }));
    }

    private String label(sh.harold.fulcrum.plugin.item.model.ItemCategory category) {
        return switch (category) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
            case SWORD -> "sword";
            case AXE -> "axe";
            case BOW -> "bow";
            case TRIDENT -> "trident";
            case SHOVEL -> "shovel";
            case HOE -> "hoe";
            case PICKAXE -> "pickaxe";
            default -> "item";
        };
    }

    private int applyUnbreaking(int damage, int level) {
        if (damage <= 0 || level <= 0) {
            return damage;
        }
        double chance = 1.0 / (level + 1.0);
        int applied = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < damage; i++) {
            if (random.nextDouble() < chance) {
                applied++;
            }
        }
        return applied;
    }
}
