package sh.harold.fulcrum.api.message.scoreboard.registry;

import sh.harold.fulcrum.api.message.scoreboard.ScoreboardDefinition;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultScoreboardRegistry implements ScoreboardRegistry {

    private final ConcurrentHashMap<String, ScoreboardDefinition> definitions = new ConcurrentHashMap<>();

    @Override
    public void register(String id, ScoreboardDefinition definition) {
        Objects.requireNonNull(id, "Scoreboard ID cannot be null");
        Objects.requireNonNull(definition, "Scoreboard definition cannot be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Scoreboard ID cannot be blank");
        }
        if (definitions.putIfAbsent(id, definition) != null) {
            throw new IllegalStateException("Scoreboard with id " + id + " already registered");
        }
    }

    @Override
    public void unregister(String id) {
        Objects.requireNonNull(id, "Scoreboard ID cannot be null");
        definitions.remove(id);
    }

    @Override
    public Optional<ScoreboardDefinition> get(String id) {
        Objects.requireNonNull(id, "Scoreboard ID cannot be null");
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public boolean exists(String id) {
        Objects.requireNonNull(id, "Scoreboard ID cannot be null");
        return definitions.containsKey(id);
    }

    @Override
    public Set<String> ids() {
        return Set.copyOf(definitions.keySet());
    }
}
