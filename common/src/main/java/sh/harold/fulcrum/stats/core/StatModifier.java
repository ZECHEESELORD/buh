package sh.harold.fulcrum.stats.core;

import java.util.Objects;

public record StatModifier(StatId statId, StatSourceId sourceId, ModifierOp op, double value, int priority) {

    public StatModifier {
        Objects.requireNonNull(statId, "statId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(op, "op");
    }

    public StatModifier(StatId statId, StatSourceId sourceId, ModifierOp op, double value) {
        this(statId, sourceId, op, value, 0);
    }
}
