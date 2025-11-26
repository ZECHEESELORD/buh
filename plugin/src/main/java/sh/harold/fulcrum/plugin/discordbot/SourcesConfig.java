package sh.harold.fulcrum.plugin.discordbot;

import org.bukkit.configuration.file.YamlConfiguration;
import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfiguration;

import java.util.List;
import java.util.Objects;

public record SourcesConfig(List<String> sources) {

    private static final FeatureConfigOption<List<String>> SOURCES = FeatureConfigOptions.stringListOption(
        "sources",
        List.of("6WC Canada", "6USC Server", "6WC USA")
    );

    public static final FeatureConfigDefinition CONFIG_DEFINITION = FeatureConfigDefinition.feature("discord-sources")
        .option(SOURCES)
        .build();

    public static SourcesConfig from(FeatureConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<String> values = configuration.value(SOURCES);
        return new SourcesConfig(values == null ? List.of() : List.copyOf(values));
    }

    public YamlConfiguration asYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sources", sources);
        return yaml;
    }
}
