package sh.harold.fulcrum.plugin.discordbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MojangClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";

    private final HttpClient httpClient;
    private final Logger logger;

    public MojangClient(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.httpClient = HttpClient.newHttpClient();
    }

    public CompletionStage<Optional<MojangProfile>> lookup(String username) {
        Objects.requireNonNull(username, "username");
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PROFILE_URL + username))
            .timeout(HTTP_TIMEOUT)
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    try {
                        MojangProfileResponse parsed = MAPPER.readValue(response.body(), MojangProfileResponse.class);
                        return Optional.of(new MojangProfile(parsed.id(), parsed.name()));
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, "Failed to parse Mojang response for " + username, exception);
                        return Optional.empty();
                    }
                }
                if (response.statusCode() == 404) {
                    return Optional.empty();
                }
                logger.warning("Mojang lookup failed for " + username + " with status " + response.statusCode());
                return Optional.empty();
            });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MojangProfileResponse(String id, String name) {
    }

    public record MojangProfile(String id, String name) {
    }
}
