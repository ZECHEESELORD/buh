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
import java.time.Instant;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

final class OsuPublicApiClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_EXPIRY_SKEW = Duration.ofSeconds(30);

    private final LinkAccountConfig.OAuthConfig oauth;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final Object tokenLock = new Object();
    private volatile CachedToken cachedToken;
    private volatile CompletableFuture<CachedToken> tokenRequest;

    OsuPublicApiClient(LinkAccountConfig.OAuthConfig oauth) {
        this.oauth = Objects.requireNonNull(oauth, "oauth");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    CompletionStage<OsuUserProfile> fetchUserById(long userId) {
        if (userId <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return accessToken().thenCompose(token -> fetchUser(buildUserUri(String.valueOf(userId), null), token));
    }

    CompletionStage<OsuUserProfile> fetchUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String encodedUsername = encodePathSegment(username.trim());
        return accessToken().thenCompose(token -> fetchUser(buildUserUri(encodedUsername, "username"), token));
    }

    private CompletionStage<String> accessToken() {
        CachedToken token = cachedToken;
        if (token != null && !token.isExpired()) {
            return CompletableFuture.completedFuture(token.accessToken());
        }
        synchronized (tokenLock) {
            token = cachedToken;
            if (token != null && !token.isExpired()) {
                return CompletableFuture.completedFuture(token.accessToken());
            }
            CompletableFuture<CachedToken> inFlight = tokenRequest;
            if (inFlight != null) {
                return inFlight.thenApply(CachedToken::accessToken);
            }
            CompletableFuture<CachedToken> request = requestClientCredentialsToken().toCompletableFuture();
            tokenRequest = request;
            request.whenComplete((fresh, throwable) -> {
                synchronized (tokenLock) {
                    tokenRequest = null;
                    if (throwable == null && fresh != null && fresh.accessToken() != null && !fresh.accessToken().isBlank()) {
                        cachedToken = fresh;
                    }
                }
            });
            return request.thenApply(CachedToken::accessToken);
        }
    }

    private CompletionStage<CachedToken> requestClientCredentialsToken() {
        String body = buildFormBody(
            "grant_type", "client_credentials",
            "client_id", oauth.clientId(),
            "client_secret", oauth.clientSecret(),
            "scope", "public"
        );
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(oauth.tokenUrl()))
            .timeout(HTTP_TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() / 100 != 2) {
                    throw new CompletionException(new IOException("osu client token request failed with status " + response.statusCode()));
                }
                OAuthTokenResponse token;
                try {
                    token = objectMapper.readValue(response.body(), OAuthTokenResponse.class);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
                if (token.accessToken == null || token.accessToken.isBlank()) {
                    throw new CompletionException(new IOException("osu client token response missing access token"));
                }
                long expiresInSeconds = token.expiresIn == null ? 3600L : Math.max(60L, token.expiresIn);
                Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds).minus(TOKEN_EXPIRY_SKEW);
                return new CachedToken(token.accessToken, expiresAt);
            });
    }

    private CompletionStage<OsuUserProfile> fetchUser(URI uri, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(HTTP_TIMEOUT)
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() / 100 != 2) {
                    throw new CompletionException(new IOException("osu user request failed with status " + response.statusCode()));
                }
                OsuUserResponse user;
                try {
                    user = objectMapper.readValue(response.body(), OsuUserResponse.class);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
                return new OsuUserProfile(
                    user.id,
                    user.username,
                    user.countryCode,
                    user.statistics == null ? null : user.statistics.globalRank
                );
            });
    }

    private URI buildUserUri(String encodedUser, String key) {
        URI meUri = URI.create(oauth.meUrl());
        String query = key == null ? null : "key=" + encodeQueryParam(key);
        String path = "/api/v2/users/" + encodedUser + "/osu";
        try {
            return new URI(meUri.getScheme(), meUri.getAuthority(), path, query, null);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build osu user uri", exception);
        }
    }

    private String buildFormBody(String... parts) {
        StringJoiner joiner = new StringJoiner("&");
        for (int i = 0; i < parts.length; i += 2) {
            joiner.add(encodeQueryParam(parts[i]) + "=" + encodeQueryParam(parts[i + 1]));
        }
        return joiner.toString();
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }

    record OsuUserProfile(long userId, String username, String countryCode, Integer globalRank) {
    }

    private record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn
    ) {
    }

    private record OsuUserResponse(
        long id,
        String username,
        @JsonProperty("country_code") String countryCode,
        Statistics statistics
    ) {
        private record Statistics(@JsonProperty("global_rank") Integer globalRank) {
        }
    }
}

