package sh.harold.fulcrum.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.Objects;

public final class FeatureConfiguration {

    private final FeatureConfigDefinition definition;
    private final Path filePath;
    private final YamlConfiguration configuration;

    FeatureConfiguration(FeatureConfigDefinition definition, Path filePath, YamlConfiguration configuration) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public String featureName() {
        return definition.featureName();
    }

    public Path filePath() {
        return filePath;
    }

    public FeatureConfigDefinition definition() {
        return definition;
    }

    public <T> T value(FeatureConfigOption<T> option) {
        return option.resolve(configuration);
    }

    public YamlConfiguration raw() {
        return configuration;
    }
}
