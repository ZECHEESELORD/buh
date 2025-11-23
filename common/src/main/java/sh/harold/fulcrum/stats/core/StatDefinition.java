package sh.harold.fulcrum.stats.core;

import java.util.Objects;

public record StatDefinition(
    StatId id,
    double baseValue,
    double minValue,
    double maxValue,
    StackingModel stackingModel,
    StatVisual visual
) {

    public StatDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stackingModel, "stackingModel");
        visual = visual == null ? StatVisual.empty() : visual;
        if (minValue > maxValue) {
            throw new IllegalArgumentException("minValue must be <= maxValue");
        }
    }

    public StatDefinition(StatId id, double baseValue, double minValue, double maxValue, StackingModel stackingModel) {
        this(id, baseValue, minValue, maxValue, stackingModel, StatVisual.empty());
    }
}
