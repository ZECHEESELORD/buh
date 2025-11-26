package sh.harold.fulcrum.plugin.accountlink;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OsuOAuthClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final AccountLinkConfig config;
    private final Logger logger;

    public OsuOAuthClient(AccountLinkConfig config, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.httpClient = HttpClient.newHttpClient();
    }

    public CompletionStage<AccountLinkService.OsuProfile> exchangeForProfile(String code) {
        Objects.requireNonNull(code, "code");
        if (!config.hasOsuCredentials()) {
            return CompletableFuture.failedFuture(new IllegalStateException("osu credentials are not configured"));
        }

        return requestToken(code)
            .thenCompose(this::fetchProfile)
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed osu OAuth exchange", throwable);
                throw new IllegalStateException("Failed to fetch osu profile", throwable);
            });
    }

    private CompletionStage<OsuTokenResponse> requestToken(String code) {
        String body = form(Map.of(
            "client_id", Long.toString(config.osuClientId()),
            "client_secret", config.osuClientSecret(),
            "code", code,
            "grant_type", "authorization_code",
            "redirect_uri", config.publicCallbackUrl()
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.osuTokenUrl()))
            .timeout(HTTP_TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseToken(response.body());
                }
                throw new IllegalStateException("osu token exchange failed with status " + response.statusCode());
            });
    }

    private CompletionStage<AccountLinkService.OsuProfile> fetchProfile(OsuTokenResponse token) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.osuMeUrl()))
            .timeout(HTTP_TIMEOUT)
            .header("Authorization", token.tokenType() + " " + token.accessToken())
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseProfile(response.body());
                }
                throw new IllegalStateException("osu profile fetch failed with status " + response.statusCode());
            });
    }

    private OsuTokenResponse parseToken(String body) {
        try {
            return MAPPER.readValue(body, OsuTokenResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse osu token response", exception);
        }
    }

    private AccountLinkService.OsuProfile parseProfile(String body) {
        try {
            OsuUserResponse response = MAPPER.readValue(body, OsuUserResponse.class);
            int rank = response.statistics() != null && response.statistics().globalRank() != null
                ? response.statistics().globalRank()
                : 0;
            return new AccountLinkService.OsuProfile(
                response.id(),
                response.username(),
                rank,
                response.countryCode()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse osu profile response", exception);
        }
    }

    private String form(Map<String, String> params) {
        return params.entrySet().stream()
            .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsuTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsuUserResponse(long id, String username, @JsonProperty("country_code") String countryCode, Statistics statistics) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Statistics(@JsonProperty("global_rank") Integer globalRank) {
        }
    }
}
