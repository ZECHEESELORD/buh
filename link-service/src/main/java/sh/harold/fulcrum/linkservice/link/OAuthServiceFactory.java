package sh.harold.fulcrum.linkservice.link;

import sh.harold.fulcrum.common.data.impl.OAuthProvider;
import sh.harold.fulcrum.linkservice.config.ServiceConfig;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class OAuthServiceFactory {

    private final Map<String, OAuthProvider> providers;

    public OAuthServiceFactory(ServiceConfig.OAuthConfig config, Logger logger) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(logger, "logger");
        providers = Map.of(
            "osu", new OAuthProvider(
                config.osu().clientId(),
                config.osu().clientSecret(),
                config.osu().authorizeUrl(),
                config.osu().tokenUrl(),
                config.osu().meUrl(),
                config.osu().callbackPath(),
                config.osu().scopes()
            ),
            "discord", new OAuthProvider(
                config.discord().clientId(),
                config.discord().clientSecret(),
                config.discord().authorizeUrl(),
                config.discord().tokenUrl(),
                config.discord().meUrl(),
                config.discord().callbackPath(),
                config.discord().scopes()
            )
        );
    }

    public OAuthProvider provider(String name) {
        return providers.get(name);
    }
}
