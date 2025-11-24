package sh.harold.fulcrum.plugin.tab;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfiguration;

public record TabConfig(String serverName) {

    static final FeatureConfigOption<String> SERVER_NAME_OPTION = FeatureConfigOptions.stringOption("server_name", "");

    static final FeatureConfigDefinition CONFIG_DEFINITION = FeatureConfigDefinition.feature("tab")
        .option(SERVER_NAME_OPTION)
        .build();

    static TabConfig from(FeatureConfiguration configuration) {
        return new TabConfig(configuration.value(SERVER_NAME_OPTION));
    }
}
