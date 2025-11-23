package sh.harold.fulcrum.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Objects;

public final class FeatureConfigOptions {

    private FeatureConfigOptions() {
    }

    public static FeatureConfigOption<String> stringOption(String path, String defaultValue) {
        return new FeatureConfigOption<>(path, defaultValue, YamlConfiguration::getString);
    }

    public static FeatureConfigOption<Boolean> booleanOption(String path, boolean defaultValue) {
        return new FeatureConfigOption<>(path, defaultValue, YamlConfiguration::getBoolean);
    }

    public static FeatureConfigOption<Integer> intOption(String path, int defaultValue) {
        return new FeatureConfigOption<>(path, defaultValue, YamlConfiguration::getInt);
    }

    public static FeatureConfigOption<Double> doubleOption(String path, double defaultValue) {
        return new FeatureConfigOption<>(path, defaultValue, YamlConfiguration::getDouble);
    }

    public static FeatureConfigOption<List<String>> stringListOption(String path, List<String> defaultValue) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        List<String> safeDefault = List.copyOf(defaultValue);
        return new FeatureConfigOption<>(path, safeDefault,
            (configuration, key) -> List.copyOf(configuration.getStringList(key)));
    }
}
