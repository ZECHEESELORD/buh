package sh.harold.fulcrum.api.message.scoreboard.registry;

import sh.harold.fulcrum.api.message.scoreboard.ScoreboardDefinition;

import java.util.Optional;
import java.util.Set;

public interface ScoreboardRegistry {

    void register(String id, ScoreboardDefinition definition);

    void unregister(String id);

    Optional<ScoreboardDefinition> get(String id);

    boolean exists(String id);

    Set<String> ids();
}
