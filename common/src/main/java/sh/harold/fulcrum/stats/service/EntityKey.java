package sh.harold.fulcrum.stats.service;

import java.util.Objects;
import java.util.UUID;

public record EntityKey(String value) implements Comparable<EntityKey> {

    public EntityKey {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("EntityKey value must not be blank");
        }
    }

    public static EntityKey fromUuid(UUID id) {
        Objects.requireNonNull(id, "id");
        return new EntityKey(id.toString());
    }

    @Override
    public int compareTo(EntityKey other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
