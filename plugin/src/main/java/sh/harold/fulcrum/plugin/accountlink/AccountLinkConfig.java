package sh.harold.fulcrum.plugin.accountlink;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfiguration;

import java.time.Duration;
import java.util.Objects;

public record AccountLinkConfig(
    String bindAddress,
    int port,
    String publicCallbackUrl,
    String hmacSecret,
    String secretVersion,
    Duration stateTtl,
    long osuClientId,
    String osuClientSecret,
    String osuAuthorizeUrl,
    String osuTokenUrl,
    String osuMeUrl
) {

    private static final FeatureConfigOption<String> BIND_ADDRESS = FeatureConfigOptions.stringOption("http.bind-address", "0.0.0.0");
    private static final FeatureConfigOption<Integer> PORT = FeatureConfigOptions.intOption("http.port", 8151);
    private static final FeatureConfigOption<String> PUBLIC_CALLBACK = FeatureConfigOptions.stringOption("http.public-callback-url", "http://localhost:8151/callback");
    private static final FeatureConfigOption<String> HMAC_SECRET = FeatureConfigOptions.stringOption("state.hmac-secret", "");
    private static final FeatureConfigOption<String> SECRET_VERSION = FeatureConfigOptions.stringOption("state.secret-version", "v1");
    private static final FeatureConfigOption<Integer> STATE_TTL_SECONDS = FeatureConfigOptions.intOption("state.ttl-seconds", 900);
    private static final FeatureConfigOption<Integer> OSU_CLIENT_ID = FeatureConfigOptions.intOption("osu.client-id", 0);
    private static final FeatureConfigOption<String> OSU_CLIENT_SECRET = FeatureConfigOptions.stringOption("osu.client-secret", "");
    private static final FeatureConfigOption<String> OSU_AUTHORIZE_URL = FeatureConfigOptions.stringOption("osu.authorize-url", "https://osu.ppy.sh/oauth/authorize");
    private static final FeatureConfigOption<String> OSU_TOKEN_URL = FeatureConfigOptions.stringOption("osu.token-url", "https://osu.ppy.sh/oauth/token");
    private static final FeatureConfigOption<String> OSU_ME_URL = FeatureConfigOptions.stringOption("osu.me-url", "https://osu.ppy.sh/api/v2/me");

    public static final FeatureConfigDefinition CONFIG_DEFINITION = FeatureConfigDefinition.feature("account-link")
        .option(BIND_ADDRESS)
        .option(PORT)
        .option(PUBLIC_CALLBACK)
        .option(HMAC_SECRET)
        .option(SECRET_VERSION)
        .option(STATE_TTL_SECONDS)
        .option(OSU_CLIENT_ID)
        .option(OSU_CLIENT_SECRET)
        .option(OSU_AUTHORIZE_URL)
        .option(OSU_TOKEN_URL)
        .option(OSU_ME_URL)
        .build();

    public static AccountLinkConfig from(FeatureConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new AccountLinkConfig(
            configuration.value(BIND_ADDRESS),
            configuration.value(PORT),
            configuration.value(PUBLIC_CALLBACK),
            configuration.value(HMAC_SECRET),
            configuration.value(SECRET_VERSION),
            Duration.ofSeconds(configuration.value(STATE_TTL_SECONDS)),
            configuration.value(OSU_CLIENT_ID).longValue(),
            configuration.value(OSU_CLIENT_SECRET),
            configuration.value(OSU_AUTHORIZE_URL),
            configuration.value(OSU_TOKEN_URL),
            configuration.value(OSU_ME_URL)
        );
    }

    public boolean hasSecret() {
        return hmacSecret != null && !hmacSecret.isBlank();
    }

    public boolean hasOsuCredentials() {
        return osuClientId > 0 && osuClientSecret != null && !osuClientSecret.isBlank();
    }
}
