package sh.harold.fulcrum.plugin.playerdata;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LinkedAccountService {

    private final DocumentCollection players;
    private final Logger logger;
    private final Map<UUID, LinkedAccounts> cache = new ConcurrentHashMap<>();

    public LinkedAccountService(DataApi dataApi, Logger logger) {
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletionStage<LinkedAccounts> refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenApply(document -> updateCache(playerId, document))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to load linked accounts for " + playerId, throwable);
                return cache.get(playerId);
            });
    }

    public Optional<String> osuUsername(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(cache.get(playerId))
            .map(LinkedAccounts::osuUsername)
            .filter(LinkedAccountService::isPresent);
    }

    public Optional<String> discordDisplayName(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(cache.get(playerId))
            .map(LinkedAccounts::discordDisplayName)
            .filter(LinkedAccountService::isPresent);
    }

    public Optional<String> bestAlias(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        LinkedAccounts accounts = cache.get(playerId);
        if (accounts == null) {
            return Optional.empty();
        }
        if (isPresent(accounts.osuUsername())) {
            return Optional.of(accounts.osuUsername());
        }
        if (isPresent(accounts.discordDisplayName())) {
            return Optional.of(accounts.discordDisplayName());
        }
        return Optional.empty();
    }

    public void evict(UUID playerId) {
        cache.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    private LinkedAccounts updateCache(UUID playerId, Document document) {
        LinkedAccounts accounts = new LinkedAccounts(resolveOsu(document), resolveDiscord(document));
        cache.put(playerId, accounts);
        return accounts;
    }

    private String resolveOsu(Document document) {
        return document.get("linking.osu.username", String.class)
            .filter(LinkedAccountService::isPresent)
            .orElseGet(() -> document.get("osu.username", String.class)
                .filter(LinkedAccountService::isPresent)
                .orElse(null));
    }

    private String resolveDiscord(Document document) {
        return document.get("linking.discord.globalName", String.class)
            .filter(LinkedAccountService::isPresent)
            .orElseGet(() -> document.get("linking.discord.username", String.class)
                .filter(LinkedAccountService::isPresent)
                .orElse(null));
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    public record LinkedAccounts(String osuUsername, String discordDisplayName) {
        public LinkedAccounts {
            osuUsername = normalize(osuUsername);
            discordDisplayName = normalize(discordDisplayName);
        }

        private static String normalize(String raw) {
            return raw == null ? null : raw.trim();
        }
    }
}
