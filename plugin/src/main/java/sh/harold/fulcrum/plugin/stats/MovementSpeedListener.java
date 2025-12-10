package sh.harold.fulcrum.plugin.stats;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import sh.harold.fulcrum.stats.core.ConditionContext;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MovementSpeedListener implements Listener {

    private static final EnumSet<Material> SANDY_MATERIALS = EnumSet.of(
        Material.SAND,
        Material.RED_SAND,
        Material.SUSPICIOUS_SAND,
        Material.GRAVEL,
        Material.SUSPICIOUS_GRAVEL
    );

    private final StatService statService;
    private final ItemPdc itemPdc;
    private final Map<UUID, Double> lastAppliedSpeed = new ConcurrentHashMap<>();

    MovementSpeedListener(Plugin plugin, StatService statService) {
        this.statService = Objects.requireNonNull(statService, "statService");
        this.itemPdc = new ItemPdc(plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        apply(event.getPlayer());
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastAppliedSpeed.remove(event.getPlayer().getUniqueId());
    }

    private void apply(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        boolean sprinting = player.isSprinting() && player.isOnGround();
        boolean sandySurface = isOnSandySurface(player);
        ConditionContext context = ConditionContext.empty();
        if (sandySurface) {
            context = context.withTag("terrain:sandy");
        }
        if (sprinting) {
            context = context.withTag("state:sprinting");
        }
        double baseSpeed = statService
            .getContainer(EntityKey.fromUuid(player.getUniqueId()))
            .getStat(StatIds.MOVEMENT_SPEED, context);
        double adjusted = applyDuneSpeed(baseSpeed, player, sandySurface, sprinting);
        double clamped = Math.max(0.0, adjusted);
        UUID playerId = player.getUniqueId();
        Double previous = lastAppliedSpeed.get(playerId);
        if (previous != null && Math.abs(previous - clamped) < 1.0E-6) {
            return;
        }
        attribute.setBaseValue(clamped);
        lastAppliedSpeed.put(playerId, clamped);
    }

    private double applyDuneSpeed(double baseSpeed, Player player, boolean sandySurface, boolean sprinting) {
        if (!sandySurface || !sprinting) {
            return baseSpeed;
        }
        ItemStack boots = player.getInventory().getBoots();
        int level = enchantLevel(boots, "dune_speed");
        if (level <= 0 || hasSoulSpeed(boots)) {
            return baseSpeed;
        }
        double multiplier = 1.0 + 0.10 * level;
        return baseSpeed * multiplier;
    }

    private boolean isOnSandySurface(Player player) {
        Block feet = player.getLocation().getBlock();
        Block beneath = feet == null ? null : feet.getRelative(BlockFace.DOWN);
        if (beneath == null) {
            return false;
        }
        Material type = beneath.getType();
        if (SANDY_MATERIALS.contains(type) || Tag.CONCRETE_POWDER.isTagged(type)) {
            return true;
        }
        return Tag.SAND.isTagged(type);
    }

    private int enchantLevel(ItemStack item, String key) {
        if (item == null || key == null || key.isBlank()) {
            return 0;
        }
        Map<String, Integer> stored = itemPdc.readEnchants(item).orElse(Map.of());
        String namespacedKey = key.contains(":") ? key : "fulcrum:" + key;
        Integer value = stored.get(namespacedKey);
        if (value == null && !key.contains(":")) {
            value = stored.get(key);
        }
        if (value != null) {
            return value;
        }
        String vanillaKey = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(vanillaKey));
        return enchantment == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    private boolean hasSoulSpeed(ItemStack item) {
        if (item == null) {
            return false;
        }
        Map<String, Integer> stored = itemPdc.readEnchants(item).orElse(Map.of());
        if (stored.containsKey("fulcrum:soul_speed")) {
            return true;
        }
        return item.getEnchantmentLevel(Enchantment.SOUL_SPEED) > 0;
    }
}
