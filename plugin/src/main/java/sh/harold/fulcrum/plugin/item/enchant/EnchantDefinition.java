package sh.harold.fulcrum.plugin.item.enchant;

import net.kyori.adventure.text.Component;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record EnchantDefinition(
    String id,
    Component displayName,
    int maxLevel,
    Map<StatId, Double> perLevelStats
) {

    public EnchantDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(perLevelStats, "perLevelStats");
        if (maxLevel <= 0) {
            throw new IllegalArgumentException("maxLevel must be > 0");
        }
    }

    public Map<StatId, Double> bonusForLevel(int level) {
        int clamped = Math.max(0, level);
        Map<StatId, Double> bonuses = new HashMap<>();
        for (Map.Entry<StatId, Double> entry : perLevelStats.entrySet()) {
            bonuses.put(entry.getKey(), entry.getValue() * clamped);
        }
        return bonuses;
    }
}
