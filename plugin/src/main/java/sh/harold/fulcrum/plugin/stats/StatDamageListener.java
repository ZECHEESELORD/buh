package sh.harold.fulcrum.plugin.stats;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageType;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.runtime.StatContribution;
import sh.harold.fulcrum.plugin.item.runtime.VanillaStatResolver;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final VanillaStatResolver vanillaStatResolver = new VanillaStatResolver();
    private final Map<UUID, KnownSpeedSample> knownSpeeds = new ConcurrentHashMap<>();
    private volatile ItemResolver itemResolver;

    public StatDamageListener(Plugin plugin, StatService statService, StatMappingConfig config, DamageMarkerRenderer damageMarkerRenderer, BowDrawTracker bowDrawTracker) {
        Objects.requireNonNull(plugin, "plugin");
        this.statService = statService;
        this.config = config;
        this.damageMarkerRenderer = damageMarkerRenderer;
        this.itemPdc = new ItemPdc(plugin);
        this.bowDrawTracker = bowDrawTracker;
    }

    public void setItemResolver(ItemResolver itemResolver) {
        this.itemResolver = itemResolver;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null) {
            return;
        }
        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            knownSpeeds.remove(playerId);
            return;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        if (Double.compare(dx, 0.0) == 0 && Double.compare(dy, 0.0) == 0 && Double.compare(dz, 0.0) == 0) {
            return;
        }
        Vector perTickSpeed = new Vector(dx, dy, dz);
        if (perTickSpeed.lengthSquared() > MAX_KNOWN_SPEED_PER_TICK_SQUARED) {
            knownSpeeds.remove(playerId);
            return;
        }
        knownSpeeds.put(playerId, new KnownSpeedSample(perTickSpeed, System.nanoTime()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            return;
        }
        knownSpeeds.remove(playerId);
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
        DamageType damageType = event.getDamageSource() == null ? null : event.getDamageSource().getDamageType();
        SpearCharge spearCharge = attacker == null ? SpearCharge.inactive() : computeSpearCharge(event, attacker, defender, damageType);
        MaceSmash maceSmash = attacker == null ? MaceSmash.inactive() : computeMaceSmash(event, attacker, damageType);
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
                } else if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                    baseDamage = computeSweepDamage(attacker, attackDamage);
                } else {
                    double nonEnchantDamage;
                    double enchantDamage;
                    double basePortion;
                    double strength;

                    if (spearCharge.active()) {
                        strength = 1.0;
                        if (spearCharge.hand() == EquipmentSlot.OFF_HAND) {
                            double spearNonEnchantFlat = spearAttackDamageFromNonEnchants(spearCharge.spearStack(), attackContext);
                            double spearEnchantFlat = spearAttackDamageFromEnchants(spearCharge.spearStack(), attackContext);
                            nonEnchantDamage = computeAttackDamageWithExtraFlat(
                                attackerContainer,
                                attackContext,
                                sourceId -> !isEnchantSource(sourceId) && !isMainHandSource(sourceId),
                                spearNonEnchantFlat
                            );
                            attackDamage = computeAttackDamageWithExtraFlat(
                                attackerContainer,
                                attackContext,
                                sourceId -> !isMainHandSource(sourceId),
                                spearNonEnchantFlat + spearEnchantFlat
                            );
                            enchantDamage = Math.max(0.0, attackDamage - nonEnchantDamage);
                            basePortion = nonEnchantDamage + spearCharge.bonusDamage();
                        } else {
                            nonEnchantDamage = attackDamageExcludingEnchants(attackerContainer, attackContext);
                            enchantDamage = Math.max(0.0, attackDamage - nonEnchantDamage);
                            basePortion = nonEnchantDamage + spearCharge.bonusDamage();
                        }
                    } else {
                        nonEnchantDamage = attackDamageExcludingEnchants(attackerContainer, attackContext);
                        enchantDamage = Math.max(0.0, attackDamage - nonEnchantDamage);
                        basePortion = nonEnchantDamage + maceSmash.bonusDamage();
                        strength = attackStrength(attacker);
                    }

                    baseDamage = scaleForAttackCooldown(basePortion, enchantDamage, strength);
                    double critMultiplier = attackerContainer.getStat(StatIds.CRIT_DAMAGE);
                    if (attacker instanceof Player player && isCritical(player)) {
                        baseDamage *= critMultiplier;
                        critical = true;
                    }
                }
            }
        }
        CriticalStrikeResult criticalStrike = rollCriticalStrike(event, attacker, damageType, baseDamage);
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

    private double computeSweepDamage(LivingEntity attacker, double attackDamage) {
        ItemStack weapon = attacker.getEquipment() == null ? null : attacker.getEquipment().getItem(EquipmentSlot.HAND);
        int level = enchantLevel(weapon, "sweeping_edge");
        double ratio = level <= 0 ? 0.0 : (double) level / (level + 1.0);
        double sweepDamage = 1.0 + attackDamage * ratio;
        return Math.max(0.0, Math.round(sweepDamage)); // Vanilla: round(1 + AD * (L / (L + 1)))
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

    private MaceSmash computeMaceSmash(EntityDamageEvent event, LivingEntity attacker, DamageType damageType) {
        if (!isDamageType(damageType, "mace_smash")) {
            return MaceSmash.inactive();
        }
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
        double damageBonus = maceDamageBonus(fallDistance, densityLevel);
        if (Double.compare(damageBonus, 0.0) <= 0) {
            return MaceSmash.inactive();
        }
        return new MaceSmash(true, damageBonus, fallDistance, breachLevel);
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

    private CriticalStrikeResult rollCriticalStrike(EntityDamageEvent event, LivingEntity attacker, DamageType damageType, double baseDamage) {
        if (event != null && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return new CriticalStrikeResult(false, baseDamage);
        }
        if (isDamageType(damageType, "spear")) {
            return new CriticalStrikeResult(false, baseDamage);
        }
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
        if (weapon == null || isAir(weapon.getType())) {
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

    private double attackDamageExcludingMainHand(StatContainer container, ConditionContext context) {
        return computeAttackDamage(container, context, sourceId -> !isMainHandSource(sourceId));
    }

    private double attackDamageExcludingEnchantsAndMainHand(StatContainer container, ConditionContext context) {
        return computeAttackDamage(container, context, sourceId -> !isEnchantSource(sourceId) && !isMainHandSource(sourceId));
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

    private double computeAttackDamageWithExtraFlat(StatContainer container, ConditionContext context, Predicate<StatSourceId> sourceFilter, double extraFlat) {
        if (Double.compare(extraFlat, 0.0) == 0) {
            return computeAttackDamage(container, context, sourceFilter);
        }
        StatSnapshot snapshot = container.debugView().stream()
            .filter(entry -> StatIds.ATTACK_DAMAGE.equals(entry.statId()))
            .findFirst()
            .orElse(null);
        if (snapshot == null) {
            return container.getStat(StatIds.ATTACK_DAMAGE, context) + extraFlat;
        }
        return computeDamageFromSnapshot(snapshot, context, sourceFilter, extraFlat);
    }

    private double computeDamageFromSnapshot(StatSnapshot snapshot, ConditionContext context, Predicate<StatSourceId> sourceFilter) {
        return computeDamageFromSnapshot(snapshot, context, sourceFilter, 0.0);
    }

    private double computeDamageFromSnapshot(StatSnapshot snapshot, ConditionContext context, Predicate<StatSourceId> sourceFilter, double extraFlat) {
        double flatSum = snapshot.baseValue() + sumModifiers(snapshot, ModifierOp.FLAT, context, sourceFilter) + extraFlat;
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

    private boolean isMainHandSource(StatSourceId sourceId) {
        return sourceId != null && sourceId.value() != null && sourceId.value().startsWith("item:main_hand");
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

    private SpearCharge computeSpearCharge(EntityDamageEvent event, LivingEntity attacker, LivingEntity defender, DamageType damageType) {
        if (!isDamageType(damageType, "spear")) {
            return SpearCharge.inactive();
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return SpearCharge.inactive();
        }
        if (!(byEntity.getDamager() instanceof LivingEntity directAttacker) || directAttacker != attacker) {
            return SpearCharge.inactive();
        }
        SpearUse spearUse = resolveSpearUse(attacker);
        if (spearUse == null || spearUse.stack() == null || isAir(spearUse.stack().getType())) {
            return SpearCharge.inactive();
        }
        double multiplier = spearChargeMultiplier(spearUse.stack().getType());
        if (Double.compare(multiplier, 0.0) <= 0) {
            return SpearCharge.inactive();
        }
        double relativeSpeed = relativeSpeedAlongLook(attacker, defender);
        double bonusDamage = Math.floor(Math.max(0.0, relativeSpeed) * multiplier);
        return new SpearCharge(true, spearUse.hand(), spearUse.stack().clone(), bonusDamage);
    }

    private SpearUse resolveSpearUse(LivingEntity attacker) {
        if (attacker == null) {
            return null;
        }
        ItemStack active = attacker.hasActiveItem() ? attacker.getActiveItem() : null;
        boolean activeIsSpear = active != null && !isAir(active.getType()) && isSpear(active.getType());

        ItemStack mainHand = attacker.getEquipment() == null ? null : attacker.getEquipment().getItem(EquipmentSlot.HAND);
        ItemStack offHand = attacker.getEquipment() == null ? null : attacker.getEquipment().getItem(EquipmentSlot.OFF_HAND);
        boolean mainIsSpear = mainHand != null && !isAir(mainHand.getType()) && isSpear(mainHand.getType());
        boolean offIsSpear = offHand != null && !isAir(offHand.getType()) && isSpear(offHand.getType());

        if (activeIsSpear) {
            if (mainIsSpear && !offIsSpear) {
                return new SpearUse(EquipmentSlot.HAND, mainHand);
            }
            if (offIsSpear && !mainIsSpear) {
                return new SpearUse(EquipmentSlot.OFF_HAND, offHand);
            }
            EquipmentSlot activeHand = attacker.getActiveItemHand();
            if (activeHand == EquipmentSlot.OFF_HAND && offIsSpear) {
                return new SpearUse(EquipmentSlot.OFF_HAND, offHand);
            }
            if (activeHand == EquipmentSlot.HAND && mainIsSpear) {
                return new SpearUse(EquipmentSlot.HAND, mainHand);
            }
            return new SpearUse(activeHand == null ? EquipmentSlot.HAND : activeHand, active);
        }

        if (mainIsSpear && !offIsSpear) {
            return new SpearUse(EquipmentSlot.HAND, mainHand);
        }
        if (offIsSpear && !mainIsSpear) {
            return new SpearUse(EquipmentSlot.OFF_HAND, offHand);
        }
        if (mainIsSpear && offIsSpear) {
            return new SpearUse(EquipmentSlot.HAND, mainHand);
        }
        return null;
    }

    private boolean isDamageType(DamageType damageType, String minecraftKey) {
        if (damageType == null || minecraftKey == null || minecraftKey.isBlank()) {
            return false;
        }
        NamespacedKey key = damageType.getKey();
        return key != null && key.equals(NamespacedKey.minecraft(minecraftKey));
    }

    private boolean isAir(Material material) {
        if (material == null) {
            return true;
        }
        return switch (material) {
            case AIR, CAVE_AIR, VOID_AIR -> true;
            default -> false;
        };
    }

    private boolean isSpear(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.equals("SPEAR") || name.endsWith("_SPEAR");
    }

    private double spearChargeMultiplier(Material material) {
        if (material == null) {
            return 0.0;
        }
        return switch (material) {
            case WOODEN_SPEAR, GOLDEN_SPEAR -> 0.7;
            case STONE_SPEAR, COPPER_SPEAR -> 0.82;
            case IRON_SPEAR -> 0.95;
            case DIAMOND_SPEAR -> 1.075;
            case NETHERITE_SPEAR -> 1.2;
            default -> 0.0;
        };
    }

    private double relativeSpeedAlongLook(LivingEntity attacker, LivingEntity defender) {
        if (attacker == null || defender == null) {
            return 0.0;
        }
        Vector look = attacker.getLocation().getDirection();
        if (look == null || look.lengthSquared() == 0.0) {
            return 0.0;
        }
        Vector direction = look.normalize();
        Vector attackerMotion = motion(attacker);
        attackerMotion.multiply(20.0);
        Vector defenderMotion = motion(defender);
        defenderMotion.multiply(20.0);
        double attackerSpeed = direction.dot(attackerMotion);
        double defenderSpeed = direction.dot(defenderMotion);
        return Math.max(0.0, attackerSpeed - defenderSpeed);
    }

    private Vector motion(LivingEntity entity) {
        if (entity == null) {
            return new Vector();
        }
        if (entity instanceof Player player) {
            Vector known = knownSpeed(player);
            if (known != null) {
                return known;
            }
        }
        if (entity.isInsideVehicle() && entity.getVehicle() != null) {
            Entity root = entity;
            while (root.getVehicle() != null) {
                root = root.getVehicle();
            }
            Vector velocity = root.getVelocity();
            return velocity == null ? new Vector() : velocity.clone();
        }
        Vector velocity = entity.getVelocity();
        return velocity == null ? new Vector() : velocity.clone();
    }

    private Vector knownSpeed(Player player) {
        if (player == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            return null;
        }
        KnownSpeedSample sample = knownSpeeds.get(playerId);
        if (sample == null) {
            return null;
        }
        long ageNanos = System.nanoTime() - sample.updatedAtNanos();
        if (ageNanos > KNOWN_SPEED_STALE_NANOS) {
            return null;
        }
        Vector perTick = sample.perTickSpeed();
        return perTick == null ? null : perTick.clone();
    }

    private double spearAttackDamageFromNonEnchants(ItemStack stack, ConditionContext context) {
        if (stack == null || isAir(stack.getType())) {
            return 0.0;
        }
        ItemResolver resolver = itemResolver;
        if (resolver != null) {
            ItemInstance instance = resolver.resolve(stack).orElse(null);
            if (instance != null) {
                double total = 0.0;
                for (Map.Entry<String, Map<sh.harold.fulcrum.stats.core.StatId, StatContribution>> entry : instance.statSources().entrySet()) {
                    if (entry.getKey().startsWith("enchant:")) {
                        continue;
                    }
                    StatContribution contribution = entry.getValue().get(StatIds.ATTACK_DAMAGE);
                    if (contribution == null) {
                        continue;
                    }
                    if (contribution.condition() == null || contribution.condition().test(context)) {
                        total += contribution.value();
                    }
                }
                return total;
            }
        }
        return baseAttackDamageForEnchantScaling(stack);
    }

    private double spearAttackDamageFromEnchants(ItemStack stack, ConditionContext context) {
        if (stack == null || isAir(stack.getType())) {
            return 0.0;
        }
        ItemResolver resolver = itemResolver;
        if (resolver != null) {
            ItemInstance instance = resolver.resolve(stack).orElse(null);
            if (instance != null) {
                double total = 0.0;
                for (Map.Entry<String, Map<sh.harold.fulcrum.stats.core.StatId, StatContribution>> entry : instance.statSources().entrySet()) {
                    if (!entry.getKey().startsWith("enchant:")) {
                        continue;
                    }
                    StatContribution contribution = entry.getValue().get(StatIds.ATTACK_DAMAGE);
                    if (contribution == null) {
                        continue;
                    }
                    if (contribution.condition() == null || contribution.condition().test(context)) {
                        total += contribution.value();
                    }
                }
                return total;
            }
        }
        return fallbackSpearEnchantDamage(stack, context);
    }

    private double fallbackSpearEnchantDamage(ItemStack stack, ConditionContext context) {
        double baseAttackDamage = baseAttackDamageForEnchantScaling(stack);
        if (Double.compare(baseAttackDamage, 0.0) <= 0) {
            return 0.0;
        }
        int sharpnessLevel = enchantLevel(stack, "sharpness");
        int smiteLevel = enchantLevel(stack, "smite");
        int baneLevel = enchantLevel(stack, "bane_of_arthropods");
        double bonus = 0.0;
        if (sharpnessLevel > 0) {
            bonus += baseAttackDamage * sharpnessCurveValue(sharpnessLevel);
        }
        if (smiteLevel > 0 && context != null && context.hasTag("target:undead")) {
            bonus += baseAttackDamage * 0.05 * smiteLevel;
        }
        if (baneLevel > 0 && context != null && context.hasTag("target:arthropod")) {
            bonus += baseAttackDamage * 0.05 * baneLevel;
        }
        return bonus;
    }

    private double baseAttackDamageForEnchantScaling(ItemStack stack) {
        if (stack == null || isAir(stack.getType())) {
            return 0.0;
        }
        ItemResolver resolver = itemResolver;
        if (resolver != null) {
            ItemInstance instance = resolver.resolve(stack).orElse(null);
            if (instance != null) {
                Map<sh.harold.fulcrum.stats.core.StatId, StatContribution> base = instance.statSources().get("base");
                if (base != null) {
                    StatContribution contribution = base.get(StatIds.ATTACK_DAMAGE);
                    if (contribution != null) {
                        return contribution.value();
                    }
                }
            }
        }
        try {
            return vanillaStatResolver.statsFor(stack.getType()).getOrDefault(StatIds.ATTACK_DAMAGE, 0.0);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private double sharpnessCurveValue(int level) {
        if (level <= 0) {
            return 0.0;
        }
        if (level <= 4) {
            return level * 0.05;
        }
        double bonus = 0.20; // level 4 value
        double extra = ((level - 1.0) * (level - 4.0)) / 2.0; // sum of (level-3) from 5..level
        return bonus + 0.05 * extra;
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
    private static final long KNOWN_SPEED_STALE_NANOS = 250_000_000L;
    private static final double MAX_KNOWN_SPEED_PER_TICK_SQUARED = 16.0;

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

    private record KnownSpeedSample(Vector perTickSpeed, long updatedAtNanos) {
    }

    private record SpearUse(EquipmentSlot hand, ItemStack stack) {
    }

    private record SpearCharge(boolean active, EquipmentSlot hand, ItemStack spearStack, double bonusDamage) {
        static SpearCharge inactive() {
            return new SpearCharge(false, EquipmentSlot.HAND, null, 0.0);
        }
    }

    private record MaceSmash(boolean active, double bonusDamage, double fallDistance, int breachLevel) {
        static MaceSmash inactive() {
            return new MaceSmash(false, 0.0, 0.0, 0);
        }
    }
}
