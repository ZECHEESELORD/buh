package sh.harold.fulcrum.plugin.stats;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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

        StatContainer defenderContainer = statService.getContainer(EntityKey.fromUuid(defender.getUniqueId()));
        syncArmorStat(defender, defenderContainer);

        LivingEntity attacker = resolveAttacker(event);
        double baseDamage = event.getDamage();
        if (attacker != null) {
            StatContainer attackerContainer = statService.getContainer(EntityKey.fromUuid(attacker.getUniqueId()));
            attackerContainer.setBase(StatIds.ATTACK_DAMAGE, baseDamage);
            baseDamage = attackerContainer.getStat(StatIds.ATTACK_DAMAGE);
        }

        double armor = defenderContainer.getStat(StatIds.ARMOR);
        double finalDamage = applyArmor(baseDamage, armor);
        applyFinalDamage(event, Math.max(0.0, finalDamage));
    }

    private void syncArmorStat(LivingEntity defender, StatContainer container) {
        AttributeInstance armor = defender.getAttribute(Attribute.ARMOR);
        double armorValue = armor == null ? 0.0 : armor.getValue();
        container.setBase(StatIds.ARMOR, Math.max(0.0, armorValue));
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
}
