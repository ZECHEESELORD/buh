package sh.harold.fulcrum.plugin.mob;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MobRegistry {

    private final Map<String, MobDefinition> definitions = new ConcurrentHashMap<>();

    public void register(MobDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        MobDefinition existing = definitions.putIfAbsent(definition.id(), definition);
        if (existing != null) {
            throw new IllegalArgumentException("Mob already registered: " + definition.id());
        }
    }

    public Optional<MobDefinition> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id));
    }
}

