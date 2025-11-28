package sh.harold.fulcrum.plugin.osu;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record OsuLinkConfig(
    String bindAddress,
    int port,
    String publicBaseUrl,
    Duration tokenTtl,
    OAuthConfig oauth
) {

    private static final String FEATURE_NAME = "osu-link";
    private static final String BIND_KEY = "bind-address";
    private static final String PORT_KEY = "port";
    private static final String PUBLIC_BASE_URL_KEY = "public-base-url";
    private static final String TOKEN_TTL_SECONDS_KEY = "token-ttl-seconds";
    private static final String OAUTH_CLIENT_ID_KEY = "oauth.client-id";
    private static final String OAUTH_CLIENT_SECRET_KEY = "oauth.client-secret";
    private static final String OAUTH_AUTHORIZE_URL_KEY = "oauth.authorization-url";
    private static final String OAUTH_TOKEN_URL_KEY = "oauth.token-url";
    private static final String OAUTH_ME_URL_KEY = "oauth.me-url";
    private static final String OAUTH_CALLBACK_PATH_KEY = "oauth.callback-path";
    private static final String OAUTH_SCOPES_KEY = "oauth.scopes";
    private static final String DEFAULT_CALLBACK_PATH = "/osu-link/callback";

    private static final FeatureConfigOption<String> BIND_OPTION = FeatureConfigOptions.stringOption(BIND_KEY, "0.0.0.0");
    private static final FeatureConfigOption<Integer> PORT_OPTION = FeatureConfigOptions.intOption(PORT_KEY, 8081);
    private static final FeatureConfigOption<String> PUBLIC_BASE_OPTION = FeatureConfigOptions.stringOption(PUBLIC_BASE_URL_KEY, "http://localhost:8081");
    private static final FeatureConfigOption<Long> TOKEN_TTL_OPTION = FeatureConfigOptions.longOption(TOKEN_TTL_SECONDS_KEY, Duration.ofMinutes(15).toSeconds());
    private static final FeatureConfigOption<String> OAUTH_CLIENT_ID_OPTION = FeatureConfigOptions.stringOption(OAUTH_CLIENT_ID_KEY, "");
    private static final FeatureConfigOption<String> OAUTH_CLIENT_SECRET_OPTION = FeatureConfigOptions.stringOption(OAUTH_CLIENT_SECRET_KEY, "");
    private static final FeatureConfigOption<String> OAUTH_AUTHORIZE_URL_OPTION = FeatureConfigOptions.stringOption(OAUTH_AUTHORIZE_URL_KEY, "https://osu.ppy.sh/oauth/authorize");
    private static final FeatureConfigOption<String> OAUTH_TOKEN_URL_OPTION = FeatureConfigOptions.stringOption(OAUTH_TOKEN_URL_KEY, "https://osu.ppy.sh/oauth/token");
    private static final FeatureConfigOption<String> OAUTH_ME_URL_OPTION = FeatureConfigOptions.stringOption(OAUTH_ME_URL_KEY, "https://osu.ppy.sh/api/v2/me");
    private static final FeatureConfigOption<String> OAUTH_CALLBACK_PATH_OPTION = FeatureConfigOptions.stringOption(OAUTH_CALLBACK_PATH_KEY, DEFAULT_CALLBACK_PATH);
    private static final FeatureConfigOption<List<String>> OAUTH_SCOPES_OPTION = FeatureConfigOptions.stringListOption(OAUTH_SCOPES_KEY, List.of("identify", "public"));

    public static OsuLinkConfig load(FeatureConfigService configService) {
        Objects.requireNonNull(configService, "configService");
        FeatureConfigDefinition definition = FeatureConfigDefinition.feature(FEATURE_NAME)
            .option(BIND_OPTION)
            .option(PORT_OPTION)
            .option(PUBLIC_BASE_OPTION)
            .option(TOKEN_TTL_OPTION)
            .option(OAUTH_CLIENT_ID_OPTION)
            .option(OAUTH_CLIENT_SECRET_OPTION)
            .option(OAUTH_AUTHORIZE_URL_OPTION)
            .option(OAUTH_TOKEN_URL_OPTION)
            .option(OAUTH_ME_URL_OPTION)
            .option(OAUTH_CALLBACK_PATH_OPTION)
            .option(OAUTH_SCOPES_OPTION)
            .build();
        var config = configService.load(definition);

        String bindAddress = config.value(BIND_OPTION);
        int port = config.value(PORT_OPTION);
        String publicBaseUrl = config.value(PUBLIC_BASE_OPTION);
        long tokenTtlSeconds = config.value(TOKEN_TTL_OPTION);
        OAuthConfig oauthConfig = new OAuthConfig(
            config.value(OAUTH_CLIENT_ID_OPTION),
            config.value(OAUTH_CLIENT_SECRET_OPTION),
            config.value(OAUTH_AUTHORIZE_URL_OPTION),
            config.value(OAUTH_TOKEN_URL_OPTION),
            config.value(OAUTH_ME_URL_OPTION),
            normalizePath(config.value(OAUTH_CALLBACK_PATH_OPTION)),
            config.value(OAUTH_SCOPES_OPTION)
        );

        Duration tokenTtl = Duration.ofSeconds(Math.max(tokenTtlSeconds, 60));
        return new OsuLinkConfig(bindAddress, port, publicBaseUrl, tokenTtl, oauthConfig);
    }

    public String callbackUrl() {
        return normalizeBaseUrl(publicBaseUrl) + oauth.callbackPath();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return DEFAULT_CALLBACK_PATH;
        }
        String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    public record OAuthConfig(
        String clientId,
        String clientSecret,
        String authorizationUrl,
        String tokenUrl,
        String meUrl,
        String callbackPath,
        List<String> scopes
    ) {
        public OAuthConfig {
            clientId = Objects.requireNonNull(clientId, "clientId");
            clientSecret = Objects.requireNonNull(clientSecret, "clientSecret");
            authorizationUrl = Objects.requireNonNull(authorizationUrl, "authorizationUrl");
            tokenUrl = Objects.requireNonNull(tokenUrl, "tokenUrl");
            meUrl = Objects.requireNonNull(meUrl, "meUrl");
            callbackPath = Objects.requireNonNull(callbackPath, "callbackPath");
            Objects.requireNonNull(scopes, "scopes");
            scopes = List.copyOf(scopes);
        }
    }
}
