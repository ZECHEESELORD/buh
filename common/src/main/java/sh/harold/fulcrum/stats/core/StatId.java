package sh.harold.fulcrum.stats.core;

import java.util.Objects;

public record StatId(String value) implements Comparable<StatId> {

    public StatId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("StatId value must not be blank");
        }
    }

    @Override
    public int compareTo(StatId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
