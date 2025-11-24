package sh.harold.fulcrum.plugin.scoreboard;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfiguration;

public record ScoreboardConfig(String title, String headerPattern, String headerDateFormat, String footer) {

    static final FeatureConfigOption<String> TITLE_OPTION = FeatureConfigOptions.stringOption("title", "&bFulcrum");
    static final FeatureConfigOption<String> HEADER_PATTERN_OPTION = FeatureConfigOptions.stringOption("header.pattern", "&7{date} &8v{version}");
    static final FeatureConfigOption<String> HEADER_DATE_FORMAT_OPTION = FeatureConfigOptions.stringOption("header.date_format", "MM/dd/yy");
    static final FeatureConfigOption<String> FOOTER_OPTION = FeatureConfigOptions.stringOption("footer", "");

    static final FeatureConfigDefinition CONFIG_DEFINITION = FeatureConfigDefinition.feature("scoreboard")
        .option(TITLE_OPTION)
        .option(HEADER_PATTERN_OPTION)
        .option(HEADER_DATE_FORMAT_OPTION)
        .option(FOOTER_OPTION)
        .build();

    static ScoreboardConfig from(FeatureConfiguration configuration) {
        return new ScoreboardConfig(
            configuration.value(TITLE_OPTION),
            configuration.value(HEADER_PATTERN_OPTION),
            configuration.value(HEADER_DATE_FORMAT_OPTION),
            configuration.value(FOOTER_OPTION)
        );
    }
}
