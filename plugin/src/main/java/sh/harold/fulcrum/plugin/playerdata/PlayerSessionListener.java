package sh.harold.fulcrum.plugin.playerdata;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.playermenu.PlayerMenuItemConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PlayerSessionListener implements Listener {

    private final Logger logger;
    private final DocumentCollection players;
    private final Map<UUID, Instant> sessionStarts = new ConcurrentHashMap<>();

    PlayerSessionListener(Logger logger, DataApi dataApi) {
        this.logger = logger;
        this.players = dataApi.collection("players");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String username = event.getPlayer().getName();
        Instant now = Instant.now();
        sessionStarts.put(playerId, now);

        players.load(playerId.toString())
            .thenCompose(document -> ensureJoinMetadata(document, now, username))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to update player metadata for " + playerId, throwable);
                return null;
            });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String username = event.getPlayer().getName();
        Instant logoutTime = Instant.now();
        Instant sessionStart = sessionStarts.remove(playerId);

        players.load(playerId.toString())
            .thenCompose(document -> updatePlaytime(document, sessionStart, logoutTime, username))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to update playtime for " + playerId, throwable);
                return null;
            });
    }

    private CompletableFuture<Void> ensureJoinMetadata(Document document, Instant now, String username) {
        String timestamp = now.toString();
        if (!document.exists()) {
            return document.overwrite(Map.of(
                "meta", Map.of(
                    "firstJoin", timestamp,
                    "lastJoin", timestamp,
                    "username", username
                ),
                "statistics", Map.of(
                    "playtimeSeconds", 0L
                ),
                "inventory", Map.of(
                    "menuItem", Map.of(
                        "material", PlayerMenuItemConfig.DEFAULT.material().name()
                    )
                )
            )).toCompletableFuture();
        }

        return ensureFirstJoin(document, timestamp)
            .thenCompose(ignored -> ensurePlaytimeCounter(document))
            .thenCompose(ignored -> ensureUsername(document, username))
            .thenCompose(ignored -> ensureMenuItemConfig(document))
            .thenCompose(ignored -> document.set("meta.lastJoin", timestamp).toCompletableFuture());
    }

    private CompletableFuture<Void> ensureFirstJoin(Document document, String timestamp) {
        Optional<String> firstJoin = document.get("meta.firstJoin", String.class);
        if (firstJoin.isEmpty()) {
            return document.set("meta.firstJoin", timestamp).toCompletableFuture();
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> ensurePlaytimeCounter(Document document) {
        Optional<Number> playtime = document.get("statistics.playtimeSeconds", Number.class);
        if (playtime.isEmpty()) {
            return document.set("statistics.playtimeSeconds", 0L).toCompletableFuture();
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> ensureUsername(Document document, String username) {
        Optional<String> stored = document.get("meta.username", String.class);
        if (stored.isPresent() && stored.get().equals(username)) {
            return CompletableFuture.completedFuture(null);
        }
        return document.set("meta.username", username).toCompletableFuture();
    }

    private CompletableFuture<Void> ensureMenuItemConfig(Document document) {
        Optional<String> materialName = document.get(PlayerMenuItemConfig.PATH, String.class);
        Material material = materialName
            .map(this::parseMaterial)
            .filter(candidate -> candidate != null && !candidate.isAir())
            .orElse(null);

        if (material != null) {
            return CompletableFuture.completedFuture(null);
        }

        return document.set(PlayerMenuItemConfig.PATH, PlayerMenuItemConfig.DEFAULT.material().name()).toCompletableFuture();
    }

    private CompletableFuture<Void> updatePlaytime(Document document, Instant sessionStart, Instant logoutTime, String username) {
        Instant effectiveStart = sessionStart != null
            ? sessionStart
            : document.get("meta.lastJoin", String.class)
                .map(this::parseInstant)
                .orElse(null);

        long sessionSeconds = computeSessionSeconds(effectiveStart, logoutTime);
        String logoutTimestamp = logoutTime.toString();

        if (!document.exists()) {
            Instant fallbackJoin = effectiveStart == null ? logoutTime : effectiveStart;
            return document.overwrite(Map.of(
                "meta", Map.of(
                    "firstJoin", fallbackJoin.toString(),
                    "lastJoin", fallbackJoin.toString(),
                    "lastLeave", logoutTimestamp,
                    "username", username
                ),
                "statistics", Map.of(
                    "playtimeSeconds", sessionSeconds
                ),
                "inventory", Map.of(
                    "menuItem", Map.of(
                        "material", PlayerMenuItemConfig.DEFAULT.material().name()
                    )
                )
            )).toCompletableFuture();
        }

        return ensurePlaytimeCounter(document)
            .thenCompose(ignored -> ensureUsername(document, username))
            .thenCompose(ignored -> document.set("meta.lastLeave", logoutTimestamp).toCompletableFuture())
            .thenCompose(ignored -> incrementPlaytime(document, sessionSeconds));
    }

    private long computeSessionSeconds(Instant start, Instant end) {
        if (start == null) {
            return 0L;
        }
        long seconds = Duration.between(start, end).getSeconds();
        return Math.max(seconds, 0L);
    }

    private CompletableFuture<Void> incrementPlaytime(Document document, long sessionSeconds) {
        if (sessionSeconds <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        long current = document.get("statistics.playtimeSeconds", Number.class)
            .map(Number::longValue)
            .orElse(0L);
        long updated = current + sessionSeconds;
        return document.set("statistics.playtimeSeconds", updated).toCompletableFuture();
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException exception) {
            logger.log(Level.WARNING, "Unable to parse instant '" + raw + '\'', exception);
            return null;
        }
    }

    private Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw);
    }
}
