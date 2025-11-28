package sh.harold.fulcrum.plugin.osu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.StringJoiner;

final class OsuOAuthClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final OsuLinkConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    OsuOAuthClient(OsuLinkConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    String authorizationUrl(String state) {
        Objects.requireNonNull(state, "state");
        String scopes = encode(String.join(" ", config.oauth().scopes()));
        String encodedState = encode(state);
        String redirect = encode(config.callbackUrl());
        return config.oauth().authorizationUrl()
            + "?response_type=code"
            + "&client_id=" + encode(config.oauth().clientId())
            + "&redirect_uri=" + redirect
            + "&scope=" + scopes
            + "&state=" + encodedState;
    }

    OsuUserProfile exchangeCode(String code) throws IOException, InterruptedException {
        Objects.requireNonNull(code, "code");
        OAuthTokenResponse tokenResponse = requestToken(code);
        if (tokenResponse.accessToken == null || tokenResponse.accessToken.isBlank()) {
            throw new IOException("osu token response missing access token");
        }
        return fetchUser(tokenResponse.accessToken);
    }

    private OAuthTokenResponse requestToken(String code) throws IOException, InterruptedException {
        String body = buildFormBody(
            "grant_type", "authorization_code",
            "client_id", config.oauth().clientId(),
            "client_secret", config.oauth().clientSecret(),
            "code", code,
            "redirect_uri", config.callbackUrl()
        );
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.oauth().tokenUrl()))
            .timeout(HTTP_TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("osu token request failed with status " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), OAuthTokenResponse.class);
    }

    private OsuUserProfile fetchUser(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.oauth().meUrl()))
            .timeout(HTTP_TIMEOUT)
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("osu profile request failed with status " + response.statusCode());
        }
        OsuUserResponse userResponse = objectMapper.readValue(response.body(), OsuUserResponse.class);
        return new OsuUserProfile(
            userResponse.id,
            userResponse.username,
            userResponse.countryCode,
            userResponse.statistics == null ? null : userResponse.statistics.globalRank
        );
    }

    private String buildFormBody(String... parts) {
        StringJoiner joiner = new StringJoiner("&");
        for (int i = 0; i < parts.length; i += 2) {
            String key = parts[i];
            String value = parts[i + 1];
            joiner.add(encode(key) + "=" + encode(value));
        }
        return joiner.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record OAuthTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    record OsuUserResponse(
        long id,
        String username,
        @JsonProperty("country_code") String countryCode,
        Statistics statistics
    ) {
        record Statistics(@JsonProperty("global_rank") Integer globalRank) {
        }
    }

    record OsuUserProfile(long userId, String username, String countryCode, Integer globalRank) {
    }
}
