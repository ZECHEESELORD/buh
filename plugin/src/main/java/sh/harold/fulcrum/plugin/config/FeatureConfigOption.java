package sh.harold.fulcrum.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Objects;

/**
 * Defines a single typed configuration entry for a feature.
 */
public record FeatureConfigOption<T>(
    String path,
    T defaultValue,
    OptionReader<T> reader
) {

    public FeatureConfigOption {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reader, "reader");
        if (path.isBlank()) {
            throw new IllegalArgumentException("Feature config path cannot be blank");
        }
    }

    public T resolve(YamlConfiguration configuration) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }
        T value = reader.read(configuration, path);
        return value == null ? defaultValue : value;
    }

    @FunctionalInterface
    public interface OptionReader<T> {
        T read(YamlConfiguration configuration, String path);
    }
}
