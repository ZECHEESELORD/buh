package sh.harold.fulcrum.plugin.playerdata;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerDirectoryService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final DocumentCollection players;
    private final Logger logger;
    private final Map<UUID, CachedEntry> cache = new ConcurrentHashMap<>();

    public PlayerDirectoryService(DataApi dataApi, Logger logger) {
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletionStage<PlayerDirectoryEntry> loadEntry(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CachedEntry cached = cache.get(playerId);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.entry());
        }
        return players.load(playerId.toString())
            .thenApply(PlayerDirectoryEntry::fromDocument)
            .thenApply(optional -> optional.map(entry -> {
                cache.put(playerId, new CachedEntry(entry, Instant.now()));
                return entry;
            }).orElse(null))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to load directory entry for " + playerId, throwable);
                return null;
            });
    }

    public void evict(UUID playerId) {
        cache.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    private record CachedEntry(PlayerDirectoryEntry entry, Instant fetchedAt) {
        boolean isExpired() {
            return fetchedAt.plus(CACHE_TTL).isBefore(Instant.now());
        }
    }
}
