package sh.harold.fulcrum.stats.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StatRegistry {

    private final Map<StatId, StatDefinition> definitions = new HashMap<>();

    public void register(StatDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Stat already registered: " + definition.id());
        }
    }

    public void registerAll(Collection<StatDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        for (StatDefinition definition : definitions) {
            register(definition);
        }
    }

    public StatDefinition get(StatId id) {
        StatDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown stat id: " + id);
        }
        return definition;
    }

    public Collection<StatDefinition> getAll() {
        return List.copyOf(definitions.values());
    }

    public static StatRegistry withDefaults() {
        StatRegistry registry = new StatRegistry();
        registry.register(new StatDefinition(StatIds.MAX_HEALTH, 20.0, 0.0, Double.MAX_VALUE, StackingModel.DEFAULT, new StatVisual("<3", "#ff5555")));
        registry.register(new StatDefinition(StatIds.ATTACK_DAMAGE, 1.0, 0.0, Double.MAX_VALUE, StackingModel.DEFAULT, new StatVisual("DMG", "#ffb347")));
        registry.register(new StatDefinition(StatIds.ATTACK_SPEED, 1.0, 0.0, Double.MAX_VALUE, StackingModel.DEFAULT, new StatVisual("AS", "#ffd966")));
        registry.register(new StatDefinition(StatIds.MOVEMENT_SPEED, 0.1, 0.0, Double.MAX_VALUE, StackingModel.DEFAULT, new StatVisual("SPD", "#55ffff")));
        registry.register(new StatDefinition(StatIds.ARMOR, 0.0, 0.0, Double.MAX_VALUE, StackingModel.DEFAULT, new StatVisual("ARM", "#c0c0c0")));
        registry.register(new StatDefinition(StatIds.CRIT_DAMAGE, 0.0, 0.0, Double.MAX_VALUE, StackingModel.DEFAULT, new StatVisual("CRIT", "#ffcc00")));
        return registry;
    }
}
