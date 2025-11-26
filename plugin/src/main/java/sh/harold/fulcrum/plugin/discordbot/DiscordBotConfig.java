package sh.harold.fulcrum.plugin.discordbot;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfiguration;

import java.util.Objects;

public record DiscordBotConfig(
    String token,
    long rulesChannelId,
    long linkChannelId,
    String rulesTemplate,
    String linkTemplate,
    String stateFile,
    String whitelistTemplate,
    String sourcesConfig,
    long reviewChannelId,
    long decisionChannelId,
    long generalChannelId,
    String reviewTemplate,
    String decisionTemplate,
    String sponsorDmTemplate,
    String sponsorPingTemplate
) {

    private static final FeatureConfigOption<String> TOKEN = FeatureConfigOptions.stringOption("token", "");
    private static final FeatureConfigOption<Long> RULES_CHANNEL = FeatureConfigOptions.longOption("rules-channel-id", 0L);
    private static final FeatureConfigOption<Long> LINK_CHANNEL = FeatureConfigOptions.longOption("link-channel-id", 0L);
    private static final FeatureConfigOption<String> RULES_TEMPLATE = FeatureConfigOptions.stringOption("rules-template", "rules.json");
    private static final FeatureConfigOption<String> LINK_TEMPLATE = FeatureConfigOptions.stringOption("link-template", "link.json");
    private static final FeatureConfigOption<String> STATE_FILE = FeatureConfigOptions.stringOption("state-file", "state.json");
    private static final FeatureConfigOption<String> WHITELIST_TEMPLATE = FeatureConfigOptions.stringOption("whitelist-template", "whitelist-ephemeral.json");
    private static final FeatureConfigOption<String> SOURCES_CONFIG = FeatureConfigOptions.stringOption("sources-config", "sources.yml");
    private static final FeatureConfigOption<Long> REVIEW_CHANNEL = FeatureConfigOptions.longOption("review-channel-id", 0L);
    private static final FeatureConfigOption<Long> DECISION_CHANNEL = FeatureConfigOptions.longOption("decision-channel-id", 0L);
    private static final FeatureConfigOption<Long> GENERAL_CHANNEL = FeatureConfigOptions.longOption("general-channel-id", 0L);
    private static final FeatureConfigOption<String> REVIEW_TEMPLATE = FeatureConfigOptions.stringOption("review-template", "review.json");
    private static final FeatureConfigOption<String> DECISION_TEMPLATE = FeatureConfigOptions.stringOption("decision-template", "decision.json");
    private static final FeatureConfigOption<String> SPONSOR_DM_TEMPLATE = FeatureConfigOptions.stringOption("sponsor-dm-template", "sponsor-dm.json");
    private static final FeatureConfigOption<String> SPONSOR_PING_TEMPLATE = FeatureConfigOptions.stringOption("sponsor-ping-template", "sponsor-ping.json");

    public static final FeatureConfigDefinition CONFIG_DEFINITION = FeatureConfigDefinition.feature("discord-bot")
        .option(TOKEN)
        .option(RULES_CHANNEL)
        .option(LINK_CHANNEL)
        .option(RULES_TEMPLATE)
        .option(LINK_TEMPLATE)
        .option(STATE_FILE)
        .option(WHITELIST_TEMPLATE)
        .option(SOURCES_CONFIG)
        .option(REVIEW_CHANNEL)
        .option(DECISION_CHANNEL)
        .option(GENERAL_CHANNEL)
        .option(REVIEW_TEMPLATE)
        .option(DECISION_TEMPLATE)
        .option(SPONSOR_DM_TEMPLATE)
        .option(SPONSOR_PING_TEMPLATE)
        .build();

    public static DiscordBotConfig from(FeatureConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new DiscordBotConfig(
            configuration.value(TOKEN),
            configuration.value(RULES_CHANNEL),
            configuration.value(LINK_CHANNEL),
            configuration.value(RULES_TEMPLATE),
            configuration.value(LINK_TEMPLATE),
            configuration.value(STATE_FILE),
            configuration.value(WHITELIST_TEMPLATE),
            configuration.value(SOURCES_CONFIG),
            configuration.value(REVIEW_CHANNEL),
            configuration.value(DECISION_CHANNEL),
            configuration.value(GENERAL_CHANNEL),
            configuration.value(REVIEW_TEMPLATE),
            configuration.value(DECISION_TEMPLATE),
            configuration.value(SPONSOR_DM_TEMPLATE),
            configuration.value(SPONSOR_PING_TEMPLATE)
        );
    }

    public boolean enabled() {
        return token != null && !token.isBlank();
    }

    public boolean hasChannels() {
        return rulesChannelId > 0 && linkChannelId > 0;
    }
}
