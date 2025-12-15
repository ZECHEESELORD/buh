package sh.harold.fulcrum.plugin.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

final class MobNameTagListener implements Listener {

    private final Plugin plugin;
    private final MobEngine engine;

    MobNameTagListener(Plugin plugin, MobEngine engine) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onNameTagAttempt(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity living)) {
            return;
        }
        ItemStack item = itemInHand(event.getHand(), event.getPlayer().getInventory().getItemInMainHand(), event.getPlayer().getInventory().getItemInOffHand());
        if (item == null || item.getType() != Material.NAME_TAG) {
            return;
        }

        if (!isMobPipelineEntity(living)) {
            return;
        }

        MobTier tier = engine.mobPdc().readTier(living).orElse(MobTier.VANILLA);
        if (tier != MobTier.VANILLA) {
            event.setCancelled(true);
            return;
        }

        String mobId = engine.mobPdc().readId(living).orElse(null);
        MobDefinition definition = mobId == null ? null : engine.registry().get(mobId).orElse(null);
        if (definition != null && definition.nameTagPolicy() == NameTagPolicy.DENY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onNameTagApplied(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity living)) {
            return;
        }
        ItemStack item = itemInHand(event.getHand(), event.getPlayer().getInventory().getItemInMainHand(), event.getPlayer().getInventory().getItemInOffHand());
        if (item == null || item.getType() != Material.NAME_TAG) {
            return;
        }

        if (!isMobPipelineEntity(living)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        Component displayName = meta == null ? null : meta.displayName();
        if (displayName == null || displayName.equals(Component.empty())) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(displayName).trim();
        if (plain.isBlank()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (living.isDead() || !living.isValid()) {
                return;
            }
            engine.mobPdc().writeNameBase(living, plain);
            if (engine.shouldShowNameplate(living)) {
                engine.nameplateService().refresh(living, true, true);
            }
        });
    }

    private boolean isMobPipelineEntity(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return false;
        }
        if (engine.mobPdc().readTier(entity).isPresent() || engine.mobPdc().readId(entity).isPresent()) {
            return true;
        }
        return engine.lifecycleService().isHostile(entity);
    }

    private ItemStack itemInHand(EquipmentSlot hand, ItemStack mainHand, ItemStack offHand) {
        if (hand == null) {
            return mainHand;
        }
        return hand == EquipmentSlot.OFF_HAND ? offHand : mainHand;
    }
}
