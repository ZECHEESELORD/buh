package sh.harold.fulcrum.plugin.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record FeatureConfigDefinition(String featureName, List<FeatureConfigOption<?>> options) {

    public FeatureConfigDefinition {
        Objects.requireNonNull(featureName, "featureName");
        Objects.requireNonNull(options, "options");
        if (featureName.isBlank()) {
            throw new IllegalArgumentException("Feature name cannot be blank");
        }
        validateUniquePaths(options);
        options = List.copyOf(options);
    }

    public static Builder feature(String featureName) {
        return new Builder(featureName);
    }

    private static void validateUniquePaths(List<FeatureConfigOption<?>> options) {
        Set<String> paths = new HashSet<>();
        for (FeatureConfigOption<?> option : options) {
            if (!paths.add(option.path())) {
                throw new IllegalArgumentException("Duplicate feature config path detected: " + option.path());
            }
        }
    }

    public static final class Builder {

        private final String featureName;
        private final List<FeatureConfigOption<?>> options = new ArrayList<>();

        private Builder(String featureName) {
            this.featureName = Objects.requireNonNull(featureName, "featureName");
            if (featureName.isBlank()) {
                throw new IllegalArgumentException("Feature name cannot be blank");
            }
        }

        public Builder option(FeatureConfigOption<?> option) {
            options.add(Objects.requireNonNull(option, "option"));
            return this;
        }

        public FeatureConfigDefinition build() {
            return new FeatureConfigDefinition(featureName, options);
        }
    }
}
