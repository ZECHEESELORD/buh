package sh.harold.fulcrum.plugin.item.model;

import sh.harold.fulcrum.stats.core.StatId;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record StatsComponent(Map<StatId, Double> baseStats) implements ItemComponent {

    public StatsComponent {
        Objects.requireNonNull(baseStats, "baseStats");
    }

    public Map<StatId, Double> baseStats() {
        return Collections.unmodifiableMap(baseStats);
    }
}
