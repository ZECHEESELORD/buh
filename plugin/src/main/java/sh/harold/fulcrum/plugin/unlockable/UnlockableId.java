package sh.harold.fulcrum.plugin.unlockable;

import java.util.Objects;
import java.util.regex.Pattern;

public record UnlockableId(String value) implements Comparable<UnlockableId> {

    private static final Pattern VALID_PATTERN = Pattern.compile("[a-z0-9_-]+");

    public UnlockableId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("UnlockableId value must not be blank");
        }
        if (value.indexOf('.') >= 0) {
            throw new IllegalArgumentException("UnlockableId may not contain dots: " + value);
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("UnlockableId must match " + VALID_PATTERN + ": " + value);
        }
    }

    public static UnlockableId of(String value) {
        return new UnlockableId(value);
    }

    @Override
    public int compareTo(UnlockableId other) {
        return value.compareTo(other.value());
    }

    @Override
    public String toString() {
        return value;
    }
}
