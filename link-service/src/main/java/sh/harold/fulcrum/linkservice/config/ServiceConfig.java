package sh.harold.fulcrum.linkservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record ServiceConfig(
    HttpConfig http,
    MysqlConfig mysql,
    OAuthConfig oauth
) {

    public static ServiceConfig load() {
        Path path = Path.of("link-service.yml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .registerModule(new SimpleModule().addDeserializer(Duration.class, new TimeSuffixDurationDeserializer()));
        try (InputStream input = Files.newInputStream(path)) {
            return mapper.readValue(input, ServiceConfig.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load link-service.yml", exception);
        }
    }

    public record HttpConfig(String bindAddress, int port, Duration timeout) {
        public HttpConfig {
            Objects.requireNonNull(bindAddress, "bindAddress");
            if (port <= 0) {
                port = 8081;
            }
            timeout = normalizeDuration(timeout, Duration.ofSeconds(15));
        }
    }

    public record MysqlConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        @JsonAlias("poolSize") int maxPoolSize,
        long connectionTimeoutMillis
    ) {
        public MysqlConfig {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(database, "database");
            Objects.requireNonNull(username, "username");
            if (port <= 0) {
                port = 3306;
            }
            if (maxPoolSize <= 0) {
                maxPoolSize = 5;
            }
            if (connectionTimeoutMillis <= 0) {
                connectionTimeoutMillis = 3000L;
            }
        }

        public String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC";
        }
    }

    public record OAuthConfig(
        ProviderConfig osu,
        ProviderConfig discord
    ) {
        public OAuthConfig {
            Objects.requireNonNull(osu, "osu");
            Objects.requireNonNull(discord, "discord");
        }
    }

    public record ProviderConfig(
        @JsonAlias({"client-id", "clientId"}) String clientId,
        @JsonAlias({"client-secret", "clientSecret"}) String clientSecret,
        @JsonAlias({"authorize-url", "authorization-url", "authorizeUrl", "authorizationUrl"}) String authorizeUrl,
        @JsonAlias({"token-url", "tokenUrl"}) String tokenUrl,
        @JsonAlias({"me-url", "meUrl"}) String meUrl,
        @JsonAlias({"callback-path", "callbackPath"}) String callbackPath,
        List<String> scopes
    ) {
        public ProviderConfig {
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(clientSecret, "clientSecret");
            Objects.requireNonNull(authorizeUrl, "authorizeUrl");
            Objects.requireNonNull(tokenUrl, "tokenUrl");
            Objects.requireNonNull(meUrl, "meUrl");
            Objects.requireNonNull(callbackPath, "callbackPath");
            Objects.requireNonNull(scopes, "scopes");
        }
    }

    private static Duration normalizeDuration(Duration raw, Duration defaultDuration) {
        if (raw == null) {
            return defaultDuration;
        }
        long seconds = raw.getSeconds();
        if (seconds <= 0) {
            return defaultDuration;
        }
        return Duration.ofSeconds(seconds);
    }
}
