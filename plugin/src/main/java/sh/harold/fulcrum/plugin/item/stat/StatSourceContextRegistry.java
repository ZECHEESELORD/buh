package sh.harold.fulcrum.plugin.item.stat;

import sh.harold.fulcrum.stats.core.StatSourceId;
import sh.harold.fulcrum.stats.service.EntityKey;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class StatSourceContextRegistry {

    private final Map<EntityKey, Map<StatSourceId, StatSourceContext>> contexts = new ConcurrentHashMap<>();

    public void put(EntityKey key, StatSourceId sourceId, StatSourceContext context) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sourceId, "sourceId");
        if (context == null) {
            return;
        }
        contexts.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(sourceId, context);
    }

    public Optional<StatSourceContext> get(EntityKey key, StatSourceId sourceId) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sourceId, "sourceId");
        return Optional.ofNullable(contexts.getOrDefault(key, Map.of()).get(sourceId));
    }

    public void clearPrefix(EntityKey key, String prefix) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(prefix, "prefix");
        Map<StatSourceId, StatSourceContext> map = contexts.get(key);
        if (map == null) {
            return;
        }
        map.keySet().removeIf(id -> id.value().startsWith(prefix));
    }

    public void clear(EntityKey key) {
        contexts.remove(key);
    }
}
