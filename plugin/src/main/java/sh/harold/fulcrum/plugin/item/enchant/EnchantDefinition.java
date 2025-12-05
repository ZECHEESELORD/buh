package sh.harold.fulcrum.plugin.item.enchant;

import net.kyori.adventure.text.Component;
import sh.harold.fulcrum.stats.core.StatId;

import sh.harold.fulcrum.stats.core.StatIds;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public record EnchantDefinition(
    String id,
    Component displayName,
    Component description,
    int maxLevel,
    Map<StatId, Double> perLevelStats,
    LevelCurve levelCurve,
    boolean scaleAttackDamage,
    java.util.Set<String> incompatibleWith,
    sh.harold.fulcrum.stats.core.StatCondition condition
) {

    public EnchantDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        description = description == null ? Component.empty() : description;
        Objects.requireNonNull(perLevelStats, "perLevelStats");
        levelCurve = levelCurve == null ? LevelCurve.linear() : levelCurve;
        incompatibleWith = incompatibleWith == null ? java.util.Set.of() : java.util.Set.copyOf(incompatibleWith);
        if (maxLevel <= 0) {
            throw new IllegalArgumentException("maxLevel must be > 0");
        }
        condition = condition == null ? sh.harold.fulcrum.stats.core.StatCondition.always() : condition;
    }

    public EnchantDefinition(String id, Component displayName, int maxLevel, Map<StatId, Double> perLevelStats) {
        this(id, displayName, Component.empty(), maxLevel, perLevelStats, LevelCurve.linear(), false, java.util.Set.of(), sh.harold.fulcrum.stats.core.StatCondition.always());
    }

    public Map<StatId, Double> bonusForLevel(int level, double baseAttackDamage) {
        int clamped = Math.max(0, level);
        Map<StatId, Double> bonuses = new HashMap<>();
        for (Map.Entry<StatId, Double> entry : perLevelStats.entrySet()) {
            double value = levelCurve.value(entry.getValue(), clamped);
            if (scaleAttackDamage && entry.getKey().equals(StatIds.ATTACK_DAMAGE)) {
                bonuses.put(entry.getKey(), baseAttackDamage * value);
            } else {
                bonuses.put(entry.getKey(), value);
            }
        }
        return bonuses;
    }

    @FunctionalInterface
    public interface LevelCurve {
        double value(double perLevel, int level);

        static LevelCurve linear() {
            return (perLevel, level) -> perLevel * level;
        }
    }
}
