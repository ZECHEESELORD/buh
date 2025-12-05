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
import sh.harold.fulcrum.stats.core.ConditionContext;
import sh.harold.fulcrum.stats.core.StatContainer;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.EnumSet;
import java.util.Locale;

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
        ConditionContext attackContext = buildAttackContext(attacker, defender, event.getCause());
        ConditionContext defenseContext = buildDefenseContext(defender, event.getCause());

        StatContainer defenderContainer = statService.getContainer(EntityKey.fromUuid(defender.getUniqueId()));

        double baseDamage = event.getDamage();
        if (attacker != null) {
            StatContainer attackerContainer = statService.getContainer(EntityKey.fromUuid(attacker.getUniqueId()));
            baseDamage = attackerContainer.getStat(StatIds.ATTACK_DAMAGE, attackContext);
            double critMultiplier = attackerContainer.getStat(StatIds.CRIT_DAMAGE);
            if (attacker instanceof Player player && isCritical(player)) {
                baseDamage *= critMultiplier;
            }
        }

        double armor = defenderContainer.getStat(StatIds.ARMOR, defenseContext);
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

    private ConditionContext buildAttackContext(LivingEntity attacker, LivingEntity defender, EntityDamageEvent.DamageCause cause) {
        ConditionContext context = ConditionContext.empty();
        if (defender != null) {
            String targetType = defender.getType().name().toLowerCase(Locale.ROOT);
            context = context.withTag("target:" + targetType);
            if (isArthropod(defender)) {
                context = context.withTag("target:arthropod");
            }
            if (isUndead(defender)) {
                context = context.withTag("target:undead");
            }
        }
        context = withCauseTags(context, cause);
        return context;
    }

    private ConditionContext buildDefenseContext(LivingEntity defender, EntityDamageEvent.DamageCause cause) {
        ConditionContext context = ConditionContext.empty();
        if (defender != null) {
            String type = defender.getType().name().toLowerCase(Locale.ROOT);
            context = context.withTag("self:" + type);
        }
        context = withCauseTags(context, cause);
        return context;
    }

    private ConditionContext withCauseTags(ConditionContext context, EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return context;
        }
        ConditionContext updated = context.withTag("cause:" + cause.name().toLowerCase(Locale.ROOT));
        if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            updated = updated.withTag("cause:projectile");
        }
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            updated = updated.withTag("cause:explosion");
        }
        if (EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.HOT_FLOOR,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.LIGHTNING
        ).contains(cause)) {
            updated = updated.withTag("cause:fire");
        }
        return updated;
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

    private boolean isArthropod(LivingEntity entity) {
        return ARTHROPODS.contains(entity.getType());
    }

    private boolean isUndead(LivingEntity entity) {
        return UNDEAD.contains(entity.getType());
    }

    private static final EnumSet<org.bukkit.entity.EntityType> ARTHROPODS = EnumSet.of(
        org.bukkit.entity.EntityType.SPIDER,
        org.bukkit.entity.EntityType.CAVE_SPIDER,
        org.bukkit.entity.EntityType.SILVERFISH,
        org.bukkit.entity.EntityType.ENDERMITE,
        org.bukkit.entity.EntityType.BEE
    );

    private static final EnumSet<org.bukkit.entity.EntityType> UNDEAD = EnumSet.of(
        org.bukkit.entity.EntityType.ZOMBIE,
        org.bukkit.entity.EntityType.DROWNED,
        org.bukkit.entity.EntityType.HUSK,
        org.bukkit.entity.EntityType.ZOMBIE_VILLAGER,
        org.bukkit.entity.EntityType.SKELETON,
        org.bukkit.entity.EntityType.STRAY,
        org.bukkit.entity.EntityType.WITHER_SKELETON,
        org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN,
        org.bukkit.entity.EntityType.WITHER,
        org.bukkit.entity.EntityType.PHANTOM,
        org.bukkit.entity.EntityType.ZOGLIN
    );
}
