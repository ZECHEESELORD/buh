package sh.harold.fulcrum.plugin.osu;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record LinkAccountConfig(
    String bindAddress,
    int port,
    String publicBaseUrl,
    Duration tokenTtl,
    boolean requireOsuLink,
    OAuthConfig osu,
    OAuthConfig discord
) {

    private static final String FEATURE_NAME = "link-account";
    private static final String BIND_KEY = "bind-address";
    private static final String PORT_KEY = "port";
    private static final String PUBLIC_BASE_URL_KEY = "public-base-url";
    private static final String TOKEN_TTL_SECONDS_KEY = "token-ttl-seconds";
    private static final String REQUIRE_OSU_LINK_KEY = "require-osu-link";

    private static final String OSU_CLIENT_ID_KEY = "osu.client-id";
    private static final String OSU_CLIENT_SECRET_KEY = "osu.client-secret";
    private static final String OSU_AUTHORIZE_URL_KEY = "osu.authorization-url";
    private static final String OSU_TOKEN_URL_KEY = "osu.token-url";
    private static final String OSU_ME_URL_KEY = "osu.me-url";
    private static final String OSU_CALLBACK_PATH_KEY = "osu.callback-path";
    private static final String OSU_SCOPES_KEY = "osu.scopes";
    private static final String OSU_DEFAULT_CALLBACK_PATH = "/link-account/osu-callback";

    private static final String DISCORD_CLIENT_ID_KEY = "discord.client-id";
    private static final String DISCORD_CLIENT_SECRET_KEY = "discord.client-secret";
    private static final String DISCORD_AUTHORIZE_URL_KEY = "discord.authorization-url";
    private static final String DISCORD_TOKEN_URL_KEY = "discord.token-url";
    private static final String DISCORD_ME_URL_KEY = "discord.me-url";
    private static final String DISCORD_CALLBACK_PATH_KEY = "discord.callback-path";
    private static final String DISCORD_SCOPES_KEY = "discord.scopes";
    private static final String DISCORD_DEFAULT_CALLBACK_PATH = "/link-account/discord-callback";

    private static final FeatureConfigOption<String> BIND_OPTION = FeatureConfigOptions.stringOption(BIND_KEY, "0.0.0.0");
    private static final FeatureConfigOption<Integer> PORT_OPTION = FeatureConfigOptions.intOption(PORT_KEY, 8081);
    private static final FeatureConfigOption<String> PUBLIC_BASE_OPTION = FeatureConfigOptions.stringOption(PUBLIC_BASE_URL_KEY, "http://localhost:8081");
    private static final FeatureConfigOption<Long> TOKEN_TTL_OPTION = FeatureConfigOptions.longOption(TOKEN_TTL_SECONDS_KEY, Duration.ofMinutes(15).toSeconds());
    private static final FeatureConfigOption<Boolean> REQUIRE_OSU_LINK_OPTION = FeatureConfigOptions.booleanOption(REQUIRE_OSU_LINK_KEY, false);

    private static final FeatureConfigOption<String> OSU_CLIENT_ID_OPTION = FeatureConfigOptions.stringOption(OSU_CLIENT_ID_KEY, "");
    private static final FeatureConfigOption<String> OSU_CLIENT_SECRET_OPTION = FeatureConfigOptions.stringOption(OSU_CLIENT_SECRET_KEY, "");
    private static final FeatureConfigOption<String> OSU_AUTHORIZE_URL_OPTION = FeatureConfigOptions.stringOption(OSU_AUTHORIZE_URL_KEY, "https://osu.ppy.sh/oauth/authorize");
    private static final FeatureConfigOption<String> OSU_TOKEN_URL_OPTION = FeatureConfigOptions.stringOption(OSU_TOKEN_URL_KEY, "https://osu.ppy.sh/oauth/token");
    private static final FeatureConfigOption<String> OSU_ME_URL_OPTION = FeatureConfigOptions.stringOption(OSU_ME_URL_KEY, "https://osu.ppy.sh/api/v2/me");
    private static final FeatureConfigOption<String> OSU_CALLBACK_PATH_OPTION = FeatureConfigOptions.stringOption(OSU_CALLBACK_PATH_KEY, OSU_DEFAULT_CALLBACK_PATH);
    private static final FeatureConfigOption<List<String>> OSU_SCOPES_OPTION = FeatureConfigOptions.stringListOption(OSU_SCOPES_KEY, List.of("identify", "public"));

    private static final FeatureConfigOption<String> DISCORD_CLIENT_ID_OPTION = FeatureConfigOptions.stringOption(DISCORD_CLIENT_ID_KEY, "");
    private static final FeatureConfigOption<String> DISCORD_CLIENT_SECRET_OPTION = FeatureConfigOptions.stringOption(DISCORD_CLIENT_SECRET_KEY, "");
    private static final FeatureConfigOption<String> DISCORD_AUTHORIZE_URL_OPTION = FeatureConfigOptions.stringOption(DISCORD_AUTHORIZE_URL_KEY, "https://discord.com/api/oauth2/authorize");
    private static final FeatureConfigOption<String> DISCORD_TOKEN_URL_OPTION = FeatureConfigOptions.stringOption(DISCORD_TOKEN_URL_KEY, "https://discord.com/api/oauth2/token");
    private static final FeatureConfigOption<String> DISCORD_ME_URL_OPTION = FeatureConfigOptions.stringOption(DISCORD_ME_URL_KEY, "https://discord.com/api/users/@me");
    private static final FeatureConfigOption<String> DISCORD_CALLBACK_PATH_OPTION = FeatureConfigOptions.stringOption(DISCORD_CALLBACK_PATH_KEY, DISCORD_DEFAULT_CALLBACK_PATH);
    private static final FeatureConfigOption<List<String>> DISCORD_SCOPES_OPTION = FeatureConfigOptions.stringListOption(DISCORD_SCOPES_KEY, List.of("identify"));

    public static LinkAccountConfig load(FeatureConfigService configService) {
        Objects.requireNonNull(configService, "configService");
        FeatureConfigDefinition definition = FeatureConfigDefinition.feature(FEATURE_NAME)
            .option(BIND_OPTION)
            .option(PORT_OPTION)
            .option(PUBLIC_BASE_OPTION)
            .option(TOKEN_TTL_OPTION)
            .option(REQUIRE_OSU_LINK_OPTION)
            .option(OSU_CLIENT_ID_OPTION)
            .option(OSU_CLIENT_SECRET_OPTION)
            .option(OSU_AUTHORIZE_URL_OPTION)
            .option(OSU_TOKEN_URL_OPTION)
            .option(OSU_ME_URL_OPTION)
            .option(OSU_CALLBACK_PATH_OPTION)
            .option(OSU_SCOPES_OPTION)
            .option(DISCORD_CLIENT_ID_OPTION)
            .option(DISCORD_CLIENT_SECRET_OPTION)
            .option(DISCORD_AUTHORIZE_URL_OPTION)
            .option(DISCORD_TOKEN_URL_OPTION)
            .option(DISCORD_ME_URL_OPTION)
            .option(DISCORD_CALLBACK_PATH_OPTION)
            .option(DISCORD_SCOPES_OPTION)
            .build();
        var config = configService.load(definition);

        String bindAddress = config.value(BIND_OPTION);
        int port = config.value(PORT_OPTION);
        String publicBaseUrl = config.value(PUBLIC_BASE_OPTION);
        long tokenTtlSeconds = config.value(TOKEN_TTL_OPTION);
        boolean requireOsuLink = config.value(REQUIRE_OSU_LINK_OPTION);

        OAuthConfig osu = new OAuthConfig(
            config.value(OSU_CLIENT_ID_OPTION),
            config.value(OSU_CLIENT_SECRET_OPTION),
            config.value(OSU_AUTHORIZE_URL_OPTION),
            config.value(OSU_TOKEN_URL_OPTION),
            config.value(OSU_ME_URL_OPTION),
            normalizePath(config.value(OSU_CALLBACK_PATH_OPTION), OSU_DEFAULT_CALLBACK_PATH),
            config.value(OSU_SCOPES_OPTION)
        );
        OAuthConfig discord = new OAuthConfig(
            config.value(DISCORD_CLIENT_ID_OPTION),
            config.value(DISCORD_CLIENT_SECRET_OPTION),
            config.value(DISCORD_AUTHORIZE_URL_OPTION),
            config.value(DISCORD_TOKEN_URL_OPTION),
            config.value(DISCORD_ME_URL_OPTION),
            normalizePath(config.value(DISCORD_CALLBACK_PATH_OPTION), DISCORD_DEFAULT_CALLBACK_PATH),
            config.value(DISCORD_SCOPES_OPTION)
        );

        Duration tokenTtl = Duration.ofSeconds(Math.max(tokenTtlSeconds, 60));
        return new LinkAccountConfig(bindAddress, port, publicBaseUrl, tokenTtl, requireOsuLink, osu, discord);
    }

    public String callbackUrl(OAuthConfig oauthConfig) {
        return normalizeBaseUrl(publicBaseUrl) + oauthConfig.callbackPath();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static String normalizePath(String rawPath, String defaultPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return defaultPath;
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
