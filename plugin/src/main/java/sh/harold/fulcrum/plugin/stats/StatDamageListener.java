package sh.harold.fulcrum.plugin.stats;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WaterMob;
import sh.harold.fulcrum.stats.core.ConditionContext;
import sh.harold.fulcrum.stats.core.ModifierOp;
import sh.harold.fulcrum.stats.core.StatContainer;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.core.StatModifier;
import sh.harold.fulcrum.stats.core.StatSnapshot;
import sh.harold.fulcrum.stats.core.StatSourceId;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import java.util.concurrent.ThreadLocalRandom;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.function.Predicate;

public final class StatDamageListener implements Listener {

    private final StatService statService;
    private final StatMappingConfig config;
    private final DamageMarkerRenderer damageMarkerRenderer;
    private final ItemPdc itemPdc;
    private final BowDrawTracker bowDrawTracker;

    public StatDamageListener(Plugin plugin, StatService statService, StatMappingConfig config, DamageMarkerRenderer damageMarkerRenderer, BowDrawTracker bowDrawTracker) {
        Objects.requireNonNull(plugin, "plugin");
        this.statService = statService;
        this.config = config;
        this.damageMarkerRenderer = damageMarkerRenderer;
        this.itemPdc = new ItemPdc(plugin);
        this.bowDrawTracker = bowDrawTracker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity defender)) {
            return;
        }
        if (defender.getNoDamageTicks() > defender.getMaximumNoDamageTicks() / 2) {
            return; // Honor vanilla invulnerability frames to avoid rapid-fire hits (lava, magma, melee multi-hit).
        }

        LivingEntity attacker = resolveAttacker(event);
        MaceSmash maceSmash = attacker == null ? MaceSmash.inactive() : computeMaceSmash(event, attacker);
        ConditionContext attackContext = buildAttackContext(attacker, defender, event.getCause());
        ConditionContext defenseContext = buildDefenseContext(defender, event.getCause());

        StatContainer defenderContainer = statService.getContainer(EntityKey.fromUuid(defender.getUniqueId()));

        double baseDamage = event.getDamage();
        boolean critical = false;
        AbstractArrow arrow = arrowFromEvent(event);
        boolean fireworkDamage = isFireworkRocketDamage(event);
        boolean useStatDamage = attacker != null
            && !fireworkDamage
            && (event instanceof EntityDamageByEntityEvent
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE);
        if (useStatDamage) {
            StatContainer attackerContainer = statService.getContainer(EntityKey.fromUuid(attacker.getUniqueId()));
            boolean hasCustomAttack = attackerContainer.isCustomized(StatIds.ATTACK_DAMAGE) || attackerContainer.isCustomized(StatIds.CRIT_DAMAGE);
            if (!hasCustomAttack && !(attacker instanceof Player)) {
                baseDamage = event.getDamage();
            } else {
                double attackDamage = attackerContainer.getStat(StatIds.ATTACK_DAMAGE, attackContext);
                if (arrow != null) {
                    if (arrow instanceof Trident trident) {
                        baseDamage = computeTridentDamage(trident, defender, attackDamage, event.getDamage());
                    } else {
                        double drawForce = drawForce(arrow);
                        double rangedBase = scaleForBowDraw(attackDamage, drawForce);
                        baseDamage = applyArrowCritical(arrow, rangedBase);
                        critical = critical || arrow.isCritical();
                    }
                } else {
                    double nonEnchantDamage = attackDamageExcludingEnchants(attackerContainer, attackContext);
                    double enchantDamage = Math.max(0.0, attackDamage - nonEnchantDamage);
                    double basePortion = nonEnchantDamage + maceSmash.bonusDamage();
                    baseDamage = scaleForAttackCooldown(basePortion, enchantDamage, attackStrength(attacker));
                    double critMultiplier = attackerContainer.getStat(StatIds.CRIT_DAMAGE);
                    if (attacker instanceof Player player && isCritical(player)) {
                        baseDamage *= critMultiplier;
                        critical = true;
                    }
                }
            }
        }
        CriticalStrikeResult criticalStrike = rollCriticalStrike(event, attacker, baseDamage);
        baseDamage = criticalStrike.damage();
        critical = critical || criticalStrike.triggered();

        double armor = defenderContainer.getStat(StatIds.ARMOR, defenseContext);
        if (maceSmash.active() && maceSmash.breachLevel() > 0) {
            double effectiveness = Math.max(0.0, 1.0 - 0.15 * maceSmash.breachLevel());
            armor *= effectiveness;
        }
        double finalDamage = applyArmor(baseDamage, armor);
        double clampedDamage = Math.max(0.0, finalDamage);
        applyFinalDamage(event, clampedDamage);
        if (maceSmash.active()) {
            applyMaceAfterEffects(attacker, defender, maceSmash);
        }
        if (attacker instanceof Player playerAttacker && damageMarkerRenderer != null && clampedDamage > 0.0) {
            damageMarkerRenderer.render(playerAttacker, defender, clampedDamage, critical);
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
        event.setDamage(finalDamage);
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

    private MaceSmash computeMaceSmash(EntityDamageEvent event, LivingEntity attacker) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return MaceSmash.inactive();
        }
        if (!(byEntity.getDamager() instanceof LivingEntity directAttacker) || directAttacker != attacker) {
            return MaceSmash.inactive();
        }
        ItemStack heldItem = attacker.getEquipment() == null ? null : attacker.getEquipment().getItem(EquipmentSlot.HAND);
        if (heldItem == null || heldItem.getType() != Material.MACE) {
            return MaceSmash.inactive();
        }
        if (attacker.isGliding()) {
            return MaceSmash.inactive();
        }
        double fallDistance = attacker.getFallDistance();
        if (Double.compare(fallDistance, 1.5) <= 0) {
            return MaceSmash.inactive();
        }
        int densityLevel = enchantLevel(heldItem, "density");
        int breachLevel = enchantLevel(heldItem, "breach");
        int windBurstLevel = enchantLevel(heldItem, "wind_burst");
        double damageBonus = maceDamageBonus(fallDistance, densityLevel);
        if (Double.compare(damageBonus, 0.0) <= 0) {
            return MaceSmash.inactive();
        }
        return new MaceSmash(true, damageBonus, fallDistance, breachLevel, windBurstLevel);
    }

    private double maceDamageBonus(double fallDistance, int densityLevel) {
        double baseBonus;
        if (fallDistance <= 3.0) {
            baseBonus = 4.0 * fallDistance;
        } else if (fallDistance <= 8.0) {
            baseBonus = 12.0 + 2.0 * (fallDistance - 3.0);
        } else {
            baseBonus = 22.0 + fallDistance - 8.0;
        }
        double enchantPerBlock = 0.5 * Math.max(0, densityLevel);
        return baseBonus + enchantPerBlock * fallDistance;
    }

    private void applyMaceAfterEffects(LivingEntity attacker, LivingEntity defender, MaceSmash maceSmash) {
        attacker.setFallDistance(0.0f);
        applyMaceShockwave(attacker, defender, maceSmash.fallDistance());
        playMaceSound(attacker, defender, maceSmash.fallDistance());
        if (maceSmash.windBurstLevel() > 0) {
            applyWindBurst(attacker, maceSmash.windBurstLevel());
        }
    }

    private void applyWindBurst(LivingEntity attacker, int level) {
        if (attacker == null) {
            return;
        }
        double radius = 3.5;
        double basePower = switch (level) {
            case 1 -> 1.2;
            case 2 -> 1.75;
            case 3 -> 2.2;
            default -> 1.5 + 0.35 * level;
        };
        Vector upward = new Vector(0.0, Math.max(0.6, 0.6 + 0.2 * level), 0.0);
        attacker.setVelocity(attacker.getVelocity().add(upward));
        attacker.getWorld().playSound(attacker.getLocation(), "minecraft:wind_charge.burst", org.bukkit.SoundCategory.PLAYERS, 1.0f, 1.0f);
        Collection<LivingEntity> nearby = attacker.getWorld().getNearbyLivingEntities(attacker.getLocation(), radius, entity ->
            shouldAffectWithShockwave(attacker, attacker, entity, radius)
        );
        for (LivingEntity living : nearby) {
            Vector offset = living.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (offset.lengthSquared() == 0.0) {
                continue;
            }
            double distance = offset.length();
            AttributeInstance resistance = living.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            double resistanceScale = resistance == null ? 0.0 : resistance.getValue();
            double knockbackPower = (radius - distance) / radius * basePower * (1.0 - resistanceScale);
            if (Double.compare(knockbackPower, 0.0) <= 0) {
                continue;
            }
            Vector impulse = offset.normalize().multiply(knockbackPower).setY(0.25 + 0.1 * level);
            living.setVelocity(living.getVelocity().add(impulse));
        }
    }

    private void applyMaceShockwave(LivingEntity attacker, LivingEntity target, double fallDistance) {
        if (attacker == null || target == null) {
            return;
        }
        double radius = 3.5;
        double strengthMultiplier = fallDistance > 5.0 ? 2.0 : 1.0;
        Collection<LivingEntity> nearby = attacker.getWorld().getNearbyLivingEntities(target.getLocation(), radius, entity ->
            shouldAffectWithShockwave(attacker, target, entity, radius)
        );
        for (LivingEntity living : nearby) {
            Vector offset = living.getLocation().toVector().subtract(target.getLocation().toVector());
            if (offset.lengthSquared() == 0.0) {
                continue;
            }
            double horizontalDistance = offset.length();
            AttributeInstance resistance = living.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            double resistanceScale = resistance == null ? 0.0 : resistance.getValue();
            double knockbackPower = (radius - horizontalDistance) * 0.7 * strengthMultiplier * (1.0 - resistanceScale);
            if (Double.compare(knockbackPower, 0.0) <= 0) {
                continue;
            }
            Vector impulse = offset.normalize().multiply(knockbackPower).setY(0.7);
            living.setVelocity(living.getVelocity().add(impulse));
        }
    }

    private boolean shouldAffectWithShockwave(LivingEntity attacker, LivingEntity target, LivingEntity candidate, double radius) {
        if (candidate == null || candidate.equals(attacker) || candidate.equals(target)) {
            return false;
        }
        if (candidate instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (candidate instanceof ArmorStand armorStand && armorStand.isMarker()) {
            return false;
        }
        if (candidate instanceof Tameable tameable && tameable.isTamed() && target instanceof org.bukkit.entity.AnimalTamer tamer) {
            if (Objects.equals(tameable.getOwnerUniqueId(), tamer.getUniqueId())) {
                return false;
            }
        }
        if (candidate.getWorld() != target.getWorld()) {
            return false;
        }
        return candidate.getLocation().distanceSquared(target.getLocation()) <= radius * radius;
    }

    private void playMaceSound(LivingEntity attacker, LivingEntity target, double fallDistance) {
        if (attacker == null || target == null) {
            return;
        }
        String sound = target.isOnGround()
            ? (fallDistance > 5.0 ? "minecraft:item.mace.smash_ground_heavy" : "minecraft:item.mace.smash_ground")
            : "minecraft:item.mace.smash_air";
        attacker.getWorld().playSound(target.getLocation(), sound, org.bukkit.SoundCategory.PLAYERS, 1.0f, 1.0f);
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
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(vanillaKey));
        return enchantment == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    private CriticalStrikeResult rollCriticalStrike(EntityDamageEvent event, LivingEntity attacker, double baseDamage) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return new CriticalStrikeResult(false, baseDamage);
        }
        if (byEntity.getDamager() instanceof Projectile) {
            return new CriticalStrikeResult(false, baseDamage);
        }
        if (attacker == null || attacker.getEquipment() == null) {
            return new CriticalStrikeResult(false, baseDamage);
        }
        ItemStack weapon = attacker.getEquipment().getItem(EquipmentSlot.HAND);
        if (weapon == null || weapon.getType().isAir()) {
            return new CriticalStrikeResult(false, baseDamage);
        }
        Material type = weapon.getType();
        boolean isWeapon = type.name().endsWith("_SWORD") || type.name().endsWith("_AXE");
        if (!isWeapon) {
            return new CriticalStrikeResult(false, baseDamage);
        }
        int level = enchantLevel(weapon, "critical_strike");
        if (level <= 0) {
            return new CriticalStrikeResult(false, baseDamage);
        }
        double chance = Math.max(0.0, 0.05 * level);
        boolean triggered = ThreadLocalRandom.current().nextDouble() < chance;
        return new CriticalStrikeResult(triggered, triggered ? baseDamage * 2.0 : baseDamage);
    }

    private boolean isArthropod(LivingEntity entity) {
        return ARTHROPODS.contains(entity.getType());
    }

    private boolean isUndead(LivingEntity entity) {
        return UNDEAD.contains(entity.getType());
    }

    private double attackStrength(LivingEntity attacker) {
        if (attacker instanceof Player player) {
            return clamp(player.getAttackCooldown(), 0.0, 1.0);
        }
        return 1.0;
    }

    private double scaleForAttackCooldown(double baseDamage, double enchantDamage, double attackStrength) {
        double clampedStrength = clamp(attackStrength, 0.0, 1.0);
        double baseMultiplier = 0.2 + 0.8 * clampedStrength * clampedStrength;
        double enchantMultiplier = 0.2 + 0.8 * clampedStrength;
        return baseDamage * baseMultiplier + enchantDamage * enchantMultiplier;
    }

    private double scaleForBowDraw(double attackDamage, double drawForce) {
        double clampedForce = clamp(drawForce, 0.0, 1.0);
        double velocity = clampedForce * 3.0;
        return Math.max(0.0, attackDamage * velocity);
    }

    private double attackDamageExcludingEnchants(StatContainer container, ConditionContext context) {
        return computeAttackDamage(container, context, sourceId -> !isEnchantSource(sourceId));
    }

    private double computeAttackDamage(StatContainer container, ConditionContext context, Predicate<StatSourceId> sourceFilter) {
        StatSnapshot snapshot = container.debugView().stream()
            .filter(entry -> StatIds.ATTACK_DAMAGE.equals(entry.statId()))
            .findFirst()
            .orElse(null);
        if (snapshot == null) {
            return container.getStat(StatIds.ATTACK_DAMAGE, context);
        }
        return computeDamageFromSnapshot(snapshot, context, sourceFilter);
    }

    private double computeDamageFromSnapshot(StatSnapshot snapshot, ConditionContext context, Predicate<StatSourceId> sourceFilter) {
        double flatSum = snapshot.baseValue() + sumModifiers(snapshot, ModifierOp.FLAT, context, sourceFilter);
        double percentAddFactor = 1.0 + sumModifiers(snapshot, ModifierOp.PERCENT_ADD, context, sourceFilter);
        double percentMultFactor = productModifiers(snapshot, ModifierOp.PERCENT_MULT, context, sourceFilter);
        return Math.max(0.0, flatSum * percentAddFactor * percentMultFactor);
    }

    private double sumModifiers(StatSnapshot snapshot, ModifierOp op, ConditionContext context, Predicate<StatSourceId> sourceFilter) {
        Map<StatSourceId, List<StatModifier>> grouped = snapshot.modifiers().get(op);
        if (grouped == null || grouped.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map.Entry<StatSourceId, List<StatModifier>> entry : grouped.entrySet()) {
            if (!sourceFilter.test(entry.getKey())) {
                continue;
            }
            for (StatModifier modifier : entry.getValue()) {
                if (modifier.condition() == null || modifier.condition().test(context)) {
                    sum += modifier.value();
                }
            }
        }
        return sum;
    }

    private double productModifiers(StatSnapshot snapshot, ModifierOp op, ConditionContext context, Predicate<StatSourceId> sourceFilter) {
        Map<StatSourceId, List<StatModifier>> grouped = snapshot.modifiers().get(op);
        if (grouped == null || grouped.isEmpty()) {
            return 1.0;
        }
        double product = 1.0;
        for (Map.Entry<StatSourceId, List<StatModifier>> entry : grouped.entrySet()) {
            if (!sourceFilter.test(entry.getKey())) {
                continue;
            }
            for (StatModifier modifier : entry.getValue()) {
                if (modifier.condition() == null || modifier.condition().test(context)) {
                    product *= 1.0 + modifier.value();
                }
            }
        }
        return product;
    }

    private AbstractArrow arrowFromEvent(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof AbstractArrow arrow) {
            return arrow;
        }
        return null;
    }

    private boolean isFireworkRocketDamage(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent byEntity
            && byEntity.getDamager() instanceof Firework;
    }

    private double drawForce(AbstractArrow arrow) {
        double stored = bowDrawTracker == null ? -1.0 : bowDrawTracker.readForce(arrow);
        if (stored >= 0.0) {
            return stored;
        }
        double inferred = arrow.getVelocity() == null ? -1.0 : arrow.getVelocity().length() / 3.0;
        return clamp(inferred, 0.0, 1.0);
    }

    private double applyArrowCritical(AbstractArrow arrow, double baseDamage) {
        if (arrow == null || !arrow.isCritical()) {
            return baseDamage;
        }
        double maxBonus = Math.max(0.0, baseDamage / 2.0 + 2.0);
        double bonus = ThreadLocalRandom.current().nextDouble(0.0, maxBonus);
        return baseDamage + bonus;
    }

    private double computeTridentDamage(Trident trident, LivingEntity defender, double attackDamage, double fallbackDamage) {
        double scaledBase = attackDamage > 0.0
            ? attackDamage * TRIDENT_THROWN_SCALE
            : Math.max(0.0, fallbackDamage);
        double impalingBonus = impalingBonus(trident, defender);
        return Math.max(0.0, scaledBase + impalingBonus);
    }

    private double impalingBonus(Trident trident, LivingEntity defender) {
        if (trident == null || defender == null || !isImpalingTarget(defender)) {
            return 0.0;
        }
        ItemStack stack = trident.getItemStack();
        int impalingLevel = enchantLevel(stack, "impaling");
        if (impalingLevel <= 0) {
            return 0.0;
        }
        return impalingLevel * IMPALING_PER_LEVEL;
    }

    private boolean isImpalingTarget(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof WaterMob;
    }

    private boolean isEnchantSource(StatSourceId sourceId) {
        return sourceId != null && sourceId.value().contains(":enchant:");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final double TRIDENT_MELEE_BASE_DAMAGE = 9.0;
    private static final double TRIDENT_THROWN_BASE_DAMAGE = 8.0;
    private static final double TRIDENT_THROWN_SCALE = TRIDENT_THROWN_BASE_DAMAGE / TRIDENT_MELEE_BASE_DAMAGE;
    private static final double IMPALING_PER_LEVEL = 2.5;

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

    private record CriticalStrikeResult(boolean triggered, double damage) {
    }

    private record MaceSmash(boolean active, double bonusDamage, double fallDistance, int breachLevel, int windBurstLevel) {
        static MaceSmash inactive() {
            return new MaceSmash(false, 0.0, 0.0, 0, 0);
        }
    }
}
