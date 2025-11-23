package sh.harold.fulcrum.stats.core;

import java.util.Objects;

public record StatSourceId(String value) implements Comparable<StatSourceId> {

    public StatSourceId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("StatSourceId value must not be blank");
        }
    }

    @Override
    public int compareTo(StatSourceId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
