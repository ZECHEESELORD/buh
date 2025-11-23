package sh.harold.fulcrum.stats.core;

import java.util.Map;

public record StatSnapshot(
    StatId statId,
    double baseValue,
    double finalValue,
    Map<ModifierOp, Map<StatSourceId, java.util.List<StatModifier>>> modifiers
) {
}
