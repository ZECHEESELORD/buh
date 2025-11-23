package sh.harold.fulcrum.common.loader;

import java.util.Objects;

public record ModuleId(String value) {

    public ModuleId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Module id cannot be blank");
        }
    }

    public static ModuleId of(String value) {
        return new ModuleId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
