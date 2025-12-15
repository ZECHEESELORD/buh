package sh.harold.fulcrum.plugin.jukebox;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;
import sh.harold.fulcrum.plugin.config.FeatureConfiguration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record JukeboxConfig(
    String uploadUrlTemplate,
    Path tracksDirectory,
    Path tokenDirectory,
    Path slotsDirectory,
    int slotCount,
    Duration tokenTtl,
    int audibleRadiusBlocks,
    Duration fadeIn,
    Duration maxDuration
) {

    private static final String FEATURE_NAME = "jukebox";
    private static final FeatureConfigOption<String> UPLOAD_URL_TEMPLATE_OPTION = FeatureConfigOptions.stringOption(
        "upload.url-template",
        "http://localhost:8080/jukebox/upload?trackId={trackId}&token={token}"
    );
    private static final FeatureConfigOption<String> TRACKS_PATH_OPTION = FeatureConfigOptions.stringOption(
        "storage.tracks-path",
        "jukebox/tracks"
    );
    private static final FeatureConfigOption<String> TOKENS_PATH_OPTION = FeatureConfigOptions.stringOption(
        "storage.tokens-path",
        "jukebox/tokens"
    );
    private static final FeatureConfigOption<String> SLOTS_PATH_OPTION = FeatureConfigOptions.stringOption(
        "storage.slots-path",
        "jukebox/slots"
    );
    private static final FeatureConfigOption<Integer> SLOT_COUNT_OPTION = FeatureConfigOptions.intOption(
        "mint.slot-count",
        10
    );
    private static final FeatureConfigOption<Long> TOKEN_TTL_SECONDS_OPTION = FeatureConfigOptions.longOption(
        "mint.token-ttl-seconds",
        Duration.ofMinutes(15).toSeconds()
    );
    private static final FeatureConfigOption<Integer> AUDIBLE_RADIUS_OPTION = FeatureConfigOptions.intOption(
        "playback.audible-radius-blocks",
        48
    );
    private static final FeatureConfigOption<Long> FADE_IN_MILLIS_OPTION = FeatureConfigOptions.longOption(
        "playback.fade-in-millis",
        80
    );
    private static final FeatureConfigOption<Long> MAX_DURATION_SECONDS_OPTION = FeatureConfigOptions.longOption(
        "limits.max-duration-seconds",
        Duration.ofMinutes(6).toSeconds()
    );

    private static final FeatureConfigDefinition DEFINITION = FeatureConfigDefinition.feature(FEATURE_NAME)
        .option(UPLOAD_URL_TEMPLATE_OPTION)
        .option(TRACKS_PATH_OPTION)
        .option(TOKENS_PATH_OPTION)
        .option(SLOTS_PATH_OPTION)
        .option(SLOT_COUNT_OPTION)
        .option(TOKEN_TTL_SECONDS_OPTION)
        .option(AUDIBLE_RADIUS_OPTION)
        .option(FADE_IN_MILLIS_OPTION)
        .option(MAX_DURATION_SECONDS_OPTION)
        .build();

    public JukeboxConfig {
        Objects.requireNonNull(uploadUrlTemplate, "uploadUrlTemplate");
        Objects.requireNonNull(tracksDirectory, "tracksDirectory");
        Objects.requireNonNull(tokenDirectory, "tokenDirectory");
        Objects.requireNonNull(slotsDirectory, "slotsDirectory");
        Objects.requireNonNull(tokenTtl, "tokenTtl");
        Objects.requireNonNull(fadeIn, "fadeIn");
        Objects.requireNonNull(maxDuration, "maxDuration");
    }

    public static JukeboxConfig load(FeatureConfigService configService, Path pluginDataDirectory) {
        Objects.requireNonNull(configService, "configService");
        Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory");
        FeatureConfiguration configuration = configService.load(DEFINITION);
        Path tracksDirectory = resolvePath(pluginDataDirectory, configuration.value(TRACKS_PATH_OPTION));
        Path tokenDirectory = resolvePath(pluginDataDirectory, configuration.value(TOKENS_PATH_OPTION));
        Path slotsDirectory = resolvePath(pluginDataDirectory, configuration.value(SLOTS_PATH_OPTION));
        return new JukeboxConfig(
            configuration.value(UPLOAD_URL_TEMPLATE_OPTION),
            tracksDirectory,
            tokenDirectory,
            slotsDirectory,
            Math.max(1, configuration.value(SLOT_COUNT_OPTION)),
            Duration.ofSeconds(Math.max(1L, configuration.value(TOKEN_TTL_SECONDS_OPTION))),
            Math.max(1, configuration.value(AUDIBLE_RADIUS_OPTION)),
            Duration.ofMillis(Math.max(0L, configuration.value(FADE_IN_MILLIS_OPTION))),
            Duration.ofSeconds(Math.max(1L, configuration.value(MAX_DURATION_SECONDS_OPTION)))
        );
    }

    public List<FeatureConfigOption<?>> options() {
        return DEFINITION.options();
    }

    private static Path resolvePath(Path basePath, String configured) {
        if (configured == null || configured.isBlank()) {
            return basePath;
        }
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return basePath.resolve(path).normalize();
    }
}
