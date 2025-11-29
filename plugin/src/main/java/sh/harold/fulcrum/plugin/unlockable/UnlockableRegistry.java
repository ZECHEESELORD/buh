package sh.harold.fulcrum.plugin.unlockable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class UnlockableRegistry {

    private final Map<UnlockableId, UnlockableDefinition> definitions = new ConcurrentHashMap<>();

    public UnlockableDefinition register(UnlockableDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        UnlockableDefinition existing = definitions.putIfAbsent(definition.id(), definition);
        if (existing != null) {
            throw new IllegalStateException("Unlockable already registered: " + definition.id());
        }
        return definition;
    }

    public Optional<UnlockableDefinition> definition(UnlockableId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(definitions.get(id));
    }

    public List<UnlockableDefinition> definitions() {
        return definitions.values().stream()
            .sorted(Comparator.comparing(UnlockableDefinition::id))
            .toList();
    }

    public List<UnlockableDefinition> definitions(UnlockableType type) {
        return definitions.values().stream()
            .filter(definition -> definition.type() == type)
            .sorted(Comparator.comparing(UnlockableDefinition::id))
            .toList();
    }
}
