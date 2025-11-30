package sh.harold.fulcrum.linkservice.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import sh.harold.fulcrum.common.data.impl.OAuthProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.StringJoiner;

final class OAuthResult {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final boolean success;
    private final String accountUsername;
    private final String providerAccountId;
    private final String message;

    private OAuthResult(boolean success, String accountUsername, String providerAccountId, String message) {
        this.success = success;
        this.accountUsername = accountUsername;
        this.providerAccountId = providerAccountId;
        this.message = message;
    }

    static OAuthResult success(String accountUsername, String providerAccountId) {
        return new OAuthResult(true, accountUsername, providerAccountId, "Linked");
    }

    static OAuthResult failure(String message) {
        return new OAuthResult(false, null, null, message);
    }

    boolean success() {
        return success;
    }

    String accountUsername() {
        return accountUsername;
    }

    String providerAccountId() {
        return providerAccountId;
    }

    String message() {
        return message;
    }

    static OAuthResult exchange(OAuthProvider provider, String code, Duration timeout, String redirectUri) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
        String body = form(
            "grant_type", "authorization_code",
            "client_id", provider.clientId(),
            "client_secret", provider.clientSecret(),
            "code", code,
            "redirect_uri", redirectUri
        );
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(provider.tokenUrl()))
            .timeout(timeout)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> tokenResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (tokenResponse.statusCode() / 100 != 2) {
            return failure("token exchange failed: " + tokenResponse.statusCode());
        }
        OAuthTokenResponse token = OBJECT_MAPPER.readValue(tokenResponse.body(), OAuthTokenResponse.class);
        if (token.accessToken == null || token.accessToken.isBlank()) {
            return failure("missing access token");
        }
        HttpRequest meRequest = HttpRequest.newBuilder()
            .uri(URI.create(provider.meUrl()))
            .timeout(timeout)
            .header("Authorization", "Bearer " + token.accessToken)
            .GET()
            .build();
        HttpResponse<String> meResponse = client.send(meRequest, HttpResponse.BodyHandlers.ofString());
        if (meResponse.statusCode() / 100 != 2) {
            return failure("profile fetch failed: " + meResponse.statusCode());
        }
        ProviderUser user = OBJECT_MAPPER.readValue(meResponse.body(), ProviderUser.class);
        return success(user.username(), user.id());
    }

    private static String form(String... parts) {
        StringJoiner joiner = new StringJoiner("&");
        for (int i = 0; i < parts.length; i += 2) {
            joiner.add(encode(parts[i]) + "=" + encode(parts[i + 1]));
        }
        return joiner.toString();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record OAuthTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record ProviderUser(String id, String username) {
    }
}
