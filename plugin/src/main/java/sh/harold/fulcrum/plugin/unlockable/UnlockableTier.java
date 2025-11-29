package sh.harold.fulcrum.plugin.unlockable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record UnlockableTier(int tier, String name, String description, long costInShards, Map<String, Object> attributes) {

    public UnlockableTier {
        if (tier < 1) {
            throw new IllegalArgumentException("Unlockable tiers are 1-based. Received: " + tier);
        }
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(attributes, "attributes");
        name = name.trim();
        description = description.trim();
        attributes = Map.copyOf(attributes);
        if (costInShards < 0) {
            throw new IllegalArgumentException("Cost cannot be negative for tier " + tier + ": " + costInShards);
        }
    }

    public UnlockableTier(int tier, String name, String description) {
        this(tier, name, description, 0L, Map.of());
    }

    public UnlockableTier withCost(long cost) {
        return new UnlockableTier(tier, name, description, cost, attributes);
    }

    public UnlockableTier withAttributes(Map<String, Object> newAttributes) {
        return new UnlockableTier(tier, name, description, costInShards, newAttributes);
    }

    public <T> Optional<T> attribute(String key, Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Object value = attributes.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
}
