package sh.harold.fulcrum.plugin.mob;

import sh.harold.fulcrum.plugin.stats.StatMappingConfig;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;

import java.util.Map;
import java.util.Objects;

public final class MobDifficultyRater {

    public record Weights(double effectiveHealth, double damagePerSecond, double movementSpeed, double levelScale) {
        public Weights {
            if (effectiveHealth < 0.0 || damagePerSecond < 0.0 || movementSpeed < 0.0 || levelScale <= 0.0) {
                throw new IllegalArgumentException("Mob difficulty weights must be non-negative and levelScale must be > 0");
            }
        }

        public static Weights defaults() {
            return new Weights(1.0, 1.0, 0.35, 7.0);
        }
    }

    private static final double EPSILON = 1.0E-9;
    private static final double BASE_HEALTH = 20.0;
    private static final double BASE_DAMAGE = 2.0;
    private static final double BASE_ATTACK_SPEED = 1.0;
    private static final double BASE_MOVEMENT_SPEED = 0.1;

    private final StatMappingConfig mappingConfig;
    private final Weights weights;

    public MobDifficultyRater(StatMappingConfig mappingConfig) {
        this(mappingConfig, Weights.defaults());
    }

    public MobDifficultyRater(StatMappingConfig mappingConfig, Weights weights) {
        this.mappingConfig = Objects.requireNonNull(mappingConfig, "mappingConfig");
        this.weights = weights == null ? Weights.defaults() : weights;
    }

    public int level(Map<StatId, Double> statBases) {
        Objects.requireNonNull(statBases, "statBases");

        double maxHealth = clampPositive(statBases.getOrDefault(StatIds.MAX_HEALTH, BASE_HEALTH), BASE_HEALTH);
        double attackDamage = clampPositive(statBases.getOrDefault(StatIds.ATTACK_DAMAGE, 1.0), 1.0);
        double movementSpeed = clampPositive(statBases.getOrDefault(StatIds.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED), BASE_MOVEMENT_SPEED);
        double armor = Math.max(0.0, statBases.getOrDefault(StatIds.ARMOR, 0.0));
        double attackSpeed = clampPositive(statBases.getOrDefault(StatIds.ATTACK_SPEED, BASE_ATTACK_SPEED), BASE_ATTACK_SPEED);

        double healthRatio = Math.max(EPSILON, maxHealth / BASE_HEALTH);
        double moveRatio = Math.max(EPSILON, movementSpeed / BASE_MOVEMENT_SPEED);
        double dpsRatio = Math.max(EPSILON, (attackDamage * attackSpeed) / (BASE_DAMAGE * BASE_ATTACK_SPEED));

        double reduction = mappingConfig.maxReduction() * (1.0 - Math.exp(-armor / mappingConfig.defenseScale()));
        double effectiveHealthRatio = Math.max(EPSILON, healthRatio / Math.max(EPSILON, 1.0 - reduction));

        double score = weights.effectiveHealth() * Math.log(effectiveHealthRatio)
            + weights.damagePerSecond() * Math.log(dpsRatio)
            + weights.movementSpeed() * Math.log(moveRatio);

        double raw = 1.0 + score * weights.levelScale();
        long rounded = Math.round(raw);
        return (int) Math.max(1L, rounded);
    }

    private double clampPositive(double value, double fallback) {
        if (!Double.isFinite(value) || Double.compare(value, 0.0) <= 0) {
            return fallback;
        }
        return value;
    }
}

