package sh.harold.fulcrum.plugin.stats;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import sh.harold.fulcrum.stats.core.StatContainer;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

public final class StatDamageListener implements Listener {

    private final StatService statService;
    private final StatMappingConfig config;

    public StatDamageListener(StatService statService, StatMappingConfig config) {
        this.statService = statService;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity defender)) {
            return;
        }

        LivingEntity attacker = resolveAttacker(event);

        StatContainer defenderContainer = statService.getContainer(EntityKey.fromUuid(defender.getUniqueId()));

        double baseDamage = event.getDamage();
        if (attacker != null) {
            StatContainer attackerContainer = statService.getContainer(EntityKey.fromUuid(attacker.getUniqueId()));
            baseDamage = attackerContainer.getStat(StatIds.ATTACK_DAMAGE);
            double critMultiplier = 1.0 + attackerContainer.getStat(StatIds.CRIT_DAMAGE);
            if (attacker instanceof Player player && isCritical(player)) {
                baseDamage *= critMultiplier;
            }
        }

        double armor = defenderContainer.getStat(StatIds.ARMOR);
        double finalDamage = applyArmor(baseDamage, armor);
        applyFinalDamage(event, Math.max(0.0, finalDamage));

        if (event instanceof EntityDamageByEntityEvent byEntity && attacker != null) {
            attacker.getServer().getLogger().info(
                "[debug] damage=" + baseDamage
                    + " attacker=" + attacker.getType()
                    + " defender=" + defender.getType()
                    + " armor=" + armor
                    + " final=" + finalDamage
                    + " cause=" + byEntity.getCause()
                    + " critical=" + (attacker instanceof Player player && isCritical(player))
            );
        }
    }

    private LivingEntity resolveAttacker(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            LivingEntity direct = resolveAttacker(byEntity.getDamager());
            if (direct != null) {
                return direct;
            }
        }

        Entity causing = event.getDamageSource() == null ? null : event.getDamageSource().getCausingEntity();
        if (causing != null) {
            return resolveAttacker(causing);
        }
        return null;
    }

    private LivingEntity resolveAttacker(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof LivingEntity livingShooter) {
                return livingShooter;
            }
        }
        return null;
    }

    private double applyArmor(double baseDamage, double armor) {
        double clampedArmor = Math.max(0.0, armor);
        double raw = 1.0 - Math.exp(-clampedArmor / config.defenseScale());
        double reductionPercent = config.maxReduction() * raw;
        return baseDamage * (1.0 - reductionPercent);
    }

    private void applyFinalDamage(EntityDamageEvent event, double finalDamage) {
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (!event.isApplicable(modifier)) {
                continue;
            }
            if (modifier == EntityDamageEvent.DamageModifier.BASE) {
                event.setDamage(modifier, finalDamage);
            } else {
                event.setDamage(modifier, 0.0);
            }
        }
    }

    private boolean isCritical(Player player) {
        return !player.isOnGround()
            && !player.isSprinting()
            && !player.isSwimming()
            && !player.isInsideVehicle()
            && !player.isInWater()
            && player.getFallDistance() > 0.0f
            && player.getAttackCooldown() >= 0.9f
            && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
    }
}
