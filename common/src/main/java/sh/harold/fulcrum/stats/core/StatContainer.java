package sh.harold.fulcrum.stats.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class StatContainer {

    private final StatRegistry registry;
    private final Map<StatId, StatInstance> stats = new HashMap<>();
    private final Consumer<StatValueChange> changeConsumer;

    public StatContainer(StatRegistry registry, Consumer<StatValueChange> changeConsumer) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.changeConsumer = changeConsumer == null ? change -> { } : changeConsumer;
    }

    public double getStat(StatId id) {
        Objects.requireNonNull(id, "id");
        return ensureInstance(id).getFinalValue();
    }

    public void setBase(StatId id, double baseValue) {
        Objects.requireNonNull(id, "id");
        StatInstance instance = ensureInstance(id);
        double oldValue = instance.getFinalValue();
        instance.setBaseValue(baseValue);
        double newValue = instance.getFinalValue();
        recordChange(id, oldValue, newValue);
    }

    public void addModifier(StatModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        StatInstance instance = ensureInstance(modifier.statId());
        double oldValue = instance.getFinalValue();
        instance.addModifier(modifier);
        double newValue = instance.getFinalValue();
        recordChange(modifier.statId(), oldValue, newValue);
    }

    public void removeModifier(StatId id, StatSourceId sourceId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");

        StatInstance instance = stats.get(id);
        if (instance == null) {
            return;
        }

        double oldValue = instance.getFinalValue();
        if (!instance.removeModifiersFromSource(sourceId)) {
            return;
        }

        double newValue = instance.getFinalValue();
        recordChange(id, oldValue, newValue);
    }

    public void clearSource(StatSourceId sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        for (Map.Entry<StatId, StatInstance> entry : stats.entrySet()) {
            StatInstance instance = entry.getValue();
            double oldValue = instance.getFinalValue();
            if (!instance.removeModifiersFromSource(sourceId)) {
                continue;
            }
            double newValue = instance.getFinalValue();
            recordChange(entry.getKey(), oldValue, newValue);
        }
    }

    public Collection<StatSnapshot> debugView() {
        return stats.values().stream()
            .map(StatInstance::snapshot)
            .toList();
    }

    public boolean isCustomized(StatId id) {
        StatInstance instance = stats.get(id);
        return instance != null && instance.hasCustomizations();
    }

    public boolean setBaseIfUncustomized(StatId id, double baseValue) {
        StatInstance instance = stats.get(id);
        if (instance != null && instance.hasCustomizations()) {
            return false;
        }
        setBase(id, baseValue);
        return true;
    }

    private StatInstance ensureInstance(StatId id) {
        return stats.computeIfAbsent(id, statId -> new StatInstance(registry.get(statId)));
    }

    private void recordChange(StatId statId, double oldValue, double newValue) {
        changeConsumer.accept(new StatValueChange(statId, oldValue, newValue));
    }
}
