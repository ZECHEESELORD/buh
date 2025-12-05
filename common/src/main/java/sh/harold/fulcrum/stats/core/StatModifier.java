package sh.harold.fulcrum.stats.core;

import java.util.Objects;

public record StatModifier(StatId statId, StatSourceId sourceId, ModifierOp op, double value, int priority, StatCondition condition) {

    public StatModifier {
        Objects.requireNonNull(statId, "statId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(op, "op");
        condition = condition == null ? StatCondition.always() : condition;
    }

    public StatModifier(StatId statId, StatSourceId sourceId, ModifierOp op, double value) {
        this(statId, sourceId, op, value, 0, StatCondition.always());
    }

    public StatModifier(StatId statId, StatSourceId sourceId, ModifierOp op, double value, int priority) {
        this(statId, sourceId, op, value, priority, StatCondition.always());
    }
}
