package sh.harold.fulcrum.linkservice.link;

import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.DocumentStore;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

public final class LinkStateRepository {

    private static final String COLLECTION = "link_states";

    private final DocumentStore store;
    private final Logger logger;

    public LinkStateRepository(DocumentStore store, Logger logger) {
        this.store = Objects.requireNonNull(store, "store");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletionStage<Optional<LinkState>> load(String stateToken) {
        DocumentKey key = DocumentKey.of(COLLECTION, stateToken);
        return store.read(key).thenApply(snapshot -> {
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            Map<String, Object> data = snapshot.copy();
            try {
                return Optional.of(LinkState.fromMap(data));
            } catch (Exception exception) {
                logger.warning("Failed to parse link state " + stateToken + ": " + exception.getMessage());
                return Optional.empty();
            }
        });
    }

    public CompletionStage<Void> persistResult(String stateToken, LinkState state, LinkResult result) {
        DocumentKey key = DocumentKey.of(COLLECTION, stateToken);
        LinkState updated = state.complete(result.accountUsername(), result.providerAccountId(), result.message());
        return store.write(key, updated.toMap());
    }

    public CompletionStage<Void> markConsumed(String stateToken) {
        return store.delete(DocumentKey.of(COLLECTION, stateToken)).thenApply(ignored -> null);
    }

    public record LinkState(
        String stateToken,
        String provider,
        String playerId,
        String playerUsername,
        Instant expiresAt,
        boolean completed,
        String accountUsername,
        String providerAccountId,
        String failureMessage
    ) {
        public static LinkState fromMap(Map<String, Object> map) {
            String state = string(map, "state");
            String provider = string(map, "provider");
            String playerId = string(map, "playerId");
            String playerUsername = string(map, "playerUsername");
            String expiresRaw = string(map, "expiresAt");
            boolean completed = booleanVal(map, "completed");
            String accountUsername = string(map, "accountUsername");
            String providerAccountId = string(map, "providerAccountId");
            String failureMessage = string(map, "failureMessage");
            Instant expiresAt = expiresRaw == null ? Instant.EPOCH : Instant.parse(expiresRaw);
            return new LinkState(state, provider, playerId, playerUsername, expiresAt, completed, accountUsername, providerAccountId, failureMessage);
        }

        public Map<String, Object> toMap() {
            return Map.of(
                "state", stateToken,
                "provider", provider,
                "playerId", playerId,
                "playerUsername", playerUsername,
                "expiresAt", expiresAt.toString(),
                "completed", completed,
                "accountUsername", accountUsername == null ? "" : accountUsername,
                "providerAccountId", providerAccountId == null ? "" : providerAccountId,
                "failureMessage", failureMessage == null ? "" : failureMessage
            );
        }

        public LinkState complete(String accountUsername, String providerAccountId, String failureMessage) {
            return new LinkState(
                stateToken,
                provider,
                playerId,
                playerUsername,
                expiresAt,
                true,
                accountUsername,
                providerAccountId,
                failureMessage
            );
        }

        private static String string(Map<String, Object> map, String key) {
            Object value = map.get(key);
            return value == null ? null : value.toString();
        }

        private static boolean booleanVal(Map<String, Object> map, String key) {
            Object value = map.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String string) {
                return Boolean.parseBoolean(string);
            }
            return false;
        }
    }

    public record LinkResult(boolean success, String accountUsername, String providerAccountId, String message) {
        public static LinkResult success(String accountUsername, String providerAccountId) {
            return new LinkResult(true, accountUsername, providerAccountId, "Linked");
        }

        public static LinkResult failure(String message) {
            return new LinkResult(false, null, null, message);
        }
    }
}
