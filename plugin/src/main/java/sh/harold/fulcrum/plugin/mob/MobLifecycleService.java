package sh.harold.fulcrum.plugin.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import sh.harold.fulcrum.plugin.mob.pdc.MobPdc;
import sh.harold.fulcrum.stats.core.ModifierOp;
import sh.harold.fulcrum.stats.core.StatContainer;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.core.StatModifier;
import sh.harold.fulcrum.stats.core.StatSourceId;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class MobLifecycleService {

    private static final int DATA_VERSION = 1;
    private static final StatSourceId ATTACK_DAMAGE_SEED_SOURCE = new StatSourceId("mob:seed:attack_damage");

    private final MobPdc mobPdc;
    private final MobRegistry registry;
    private final StatService statService;
    private final VanillaMobStatSeeder vanillaSeeder = new VanillaMobStatSeeder();

    public MobLifecycleService(MobPdc mobPdc, MobRegistry registry, StatService statService) {
        this.mobPdc = Objects.requireNonNull(mobPdc, "mobPdc");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.statService = Objects.requireNonNull(statService, "statService");
    }

    public boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy;
    }

    public void ensureVanillaHostile(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        if (!isHostile(entity)) {
            return;
        }
        ensureIdentity(entity, entity.getType().getKey().toString(), MobTier.VANILLA);
        ensureNameBaseCaptured(entity);
        ensureStatBases(entity, null);
        applyStatBasesToContainer(entity);
        ensureMobAttackDamageCustomized(entity);
    }

    public void ensureParticipatingNeutral(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        if (isHostile(entity)) {
            return;
        }
        ensureIdentity(entity, entity.getType().getKey().toString(), MobTier.VANILLA);
        ensureNameBaseCaptured(entity);
        ensureStatBases(entity, null);
        applyStatBasesToContainer(entity);
        ensureMobAttackDamageCustomized(entity);
    }

    public void ensureCustomMob(LivingEntity entity, MobDefinition definition) {
        if (entity == null || entity.isDead() || !entity.isValid() || definition == null) {
            return;
        }
        ensureIdentity(entity, definition.id(), definition.tier());
        ensureNameBaseCaptured(entity);
        ensureStatBases(entity, definition);
        applyStatBasesToContainer(entity);
        ensureMobAttackDamageCustomized(entity);
    }

    public void assignCustomMob(LivingEntity entity, MobDefinition definition) {
        if (entity == null || entity.isDead() || !entity.isValid() || definition == null) {
            return;
        }
        mobPdc.writeVersion(entity, DATA_VERSION);
        mobPdc.writeId(entity, definition.id());
        mobPdc.writeTier(entity, definition.tier());
        ensureNameBaseCaptured(entity);
        Map<StatId, Double> bases = definition.statBases().isEmpty() ? vanillaSeeder.seedBases(entity) : definition.statBases();
        Map<StatId, Double> ensured = ensureCoreStats(entity, bases);
        mobPdc.writeStatBases(entity, ensured);
        applyStatBasesToContainer(entity);
        ensureMobAttackDamageCustomized(entity);
    }

    public void forgetEntity(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        statService.removeContainer(EntityKey.fromUuid(entity.getUniqueId()));
    }

    void ensureIdentity(LivingEntity entity, String mobId, MobTier tier) {
        mobPdc.readVersion(entity).orElseGet(() -> {
            mobPdc.writeVersion(entity, DATA_VERSION);
            return DATA_VERSION;
        });
        mobPdc.readId(entity).orElseGet(() -> {
            mobPdc.writeId(entity, mobId);
            return mobId;
        });
        mobPdc.readTier(entity).orElseGet(() -> {
            mobPdc.writeTier(entity, tier);
            return tier;
        });
    }

    void ensureNameBaseCaptured(LivingEntity entity) {
        if (mobPdc.readNameBase(entity).isPresent()) {
            return;
        }
        if (mobPdc.readNameMode(entity).orElse(null) == MobNameMode.ENGINE) {
            return;
        }
        Component current = entity.customName();
        if (current == null || current.equals(Component.empty())) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(current).trim();
        if (!plain.isBlank()) {
            mobPdc.writeNameBase(entity, plain);
        }
    }

    void ensureStatBases(LivingEntity entity, MobDefinition definition) {
        Map<StatId, Double> existing = mobPdc.readStatBases(entity).orElse(null);
        Map<StatId, Double> seeded = existing == null
            ? (definition == null || definition.statBases().isEmpty() ? vanillaSeeder.seedBases(entity) : definition.statBases())
            : existing;

        Map<StatId, Double> ensured = ensureCoreStats(entity, seeded);
        if (existing == null || !ensured.equals(existing)) {
            mobPdc.writeStatBases(entity, ensured);
        }
    }

    private Map<StatId, Double> ensureCoreStats(LivingEntity entity, Map<StatId, Double> bases) {
        Map<StatId, Double> ensured = new HashMap<>(bases == null ? Map.of() : bases);
        Map<StatId, Double> vanilla = vanillaSeeder.seedBases(entity);
        ensured.putIfAbsent(StatIds.MAX_HEALTH, vanilla.getOrDefault(StatIds.MAX_HEALTH, 20.0));
        ensured.putIfAbsent(StatIds.ATTACK_DAMAGE, vanilla.getOrDefault(StatIds.ATTACK_DAMAGE, 1.0));
        ensured.putIfAbsent(StatIds.ARMOR, vanilla.getOrDefault(StatIds.ARMOR, 0.0));
        ensured.putIfAbsent(StatIds.MOVEMENT_SPEED, vanilla.getOrDefault(StatIds.MOVEMENT_SPEED, 0.1));
        return Map.copyOf(ensured);
    }

    void applyStatBasesToContainer(LivingEntity entity) {
        Map<StatId, Double> bases = mobPdc.readStatBases(entity).orElse(null);
        if (bases == null || bases.isEmpty()) {
            return;
        }
        StatContainer container = statService.getContainer(EntityKey.fromUuid(entity.getUniqueId()));
        for (Map.Entry<StatId, Double> entry : bases.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            container.setBase(entry.getKey(), entry.getValue());
        }
    }

    void ensureMobAttackDamageCustomized(LivingEntity entity) {
        StatContainer container = statService.getContainer(EntityKey.fromUuid(entity.getUniqueId()));
        container.removeModifier(StatIds.ATTACK_DAMAGE, ATTACK_DAMAGE_SEED_SOURCE);
        container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, ATTACK_DAMAGE_SEED_SOURCE, ModifierOp.FLAT, 0.0));
    }
}
