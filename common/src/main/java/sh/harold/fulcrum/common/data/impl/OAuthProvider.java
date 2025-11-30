package sh.harold.fulcrum.common.data.impl;

import java.util.List;
import java.util.Objects;

public record OAuthProvider(
    String clientId,
    String clientSecret,
    String authorizeUrl,
    String tokenUrl,
    String meUrl,
    String callbackPath,
    List<String> scopes
) {
    public OAuthProvider {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecret, "clientSecret");
        Objects.requireNonNull(authorizeUrl, "authorizeUrl");
        Objects.requireNonNull(tokenUrl, "tokenUrl");
        Objects.requireNonNull(meUrl, "meUrl");
        Objects.requireNonNull(callbackPath, "callbackPath");
        Objects.requireNonNull(scopes, "scopes");
    }
}
