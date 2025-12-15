package sh.harold.fulcrum.plugin.mob;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Objects;

final class MobProvocationListener implements Listener {

    private final Plugin plugin;
    private final MobEngine engine;

    MobProvocationListener(Plugin plugin, MobEngine engine) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (!(event.getTarget() instanceof Player)) {
            return;
        }
        engine.provocationService().markProvoked(living);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity victim = event.getEntity() instanceof LivingEntity living ? living : null;
        LivingEntity attacker = resolveDamager(event.getDamager());
        if (victim == null || attacker == null) {
            return;
        }

        if (victim instanceof Player) {
            engine.provocationService().markProvoked(attacker);
        }

        if (attacker instanceof Player && !engine.lifecycleService().isHostile(victim) && !(victim instanceof Player)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (victim.isDead() || !victim.isValid()) {
                    return;
                }
                if (victim instanceof org.bukkit.entity.Mob mob && mob.getTarget() instanceof Player) {
                    engine.provocationService().markProvoked(victim);
                }
            });
        }
    }

    private LivingEntity resolveDamager(Entity damager) {
        if (damager == null) {
            return null;
        }
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof LivingEntity livingShooter) {
                return livingShooter;
            }
        }
        return null;
    }
}
