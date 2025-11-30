package sh.harold.fulcrum.plugin.playerdata;

import org.bukkit.Material;
import org.bukkit.entity.Player;
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
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PlayerSessionListener implements Listener {

    private final Logger logger;
    private final DocumentCollection players;
    private final PlayerBiomeAggregator biomeAggregator;
    private final PlayerDirectoryService directoryService;
    private final Map<UUID, Instant> sessionStarts = new ConcurrentHashMap<>();

    PlayerSessionListener(Logger logger, DataApi dataApi, PlayerBiomeAggregator biomeAggregator, PlayerDirectoryService directoryService) {
        this.logger = logger;
        this.players = dataApi.collection("players");
        this.biomeAggregator = biomeAggregator;
        this.directoryService = Objects.requireNonNull(directoryService, "directoryService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String username = event.getPlayer().getName();
        Instant now = Instant.now();
        sessionStarts.put(playerId, now);

        long startedAt = System.nanoTime();
        logger.info(() -> "[login:data] join metadata load for " + playerId + " (" + username + ")");
        players.load(playerId.toString())
            .thenCompose(document -> ensureJoinMetadata(document, now, username)
                .thenCompose(ignored -> biomeAggregator.recordInitialVisit(document, playerId, event.getPlayer().getLocation(), now)))
            .whenComplete((ignored, throwable) -> {
                directoryService.invalidateRoster();
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                if (throwable != null) {
                    logger.log(Level.SEVERE, "[login:data] join metadata update failed for " + playerId + " after " + elapsedMillis + "ms", throwable);
                    return;
                }
                logger.info(() -> "[login:data] join metadata update completed for " + playerId + " in " + elapsedMillis + "ms");
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

    CompletionStage<Void> flushSessions(Iterable<? extends Player> onlinePlayers, Instant logoutTime) {
        var writes = new ArrayList<CompletableFuture<Void>>();
        for (Player player : onlinePlayers) {
            UUID playerId = player.getUniqueId();
            Instant sessionStart = sessionStarts.remove(playerId);

            writes.add(players.load(playerId.toString())
                .thenCompose(document -> updatePlaytime(document, sessionStart, logoutTime, player.getName()))
                .toCompletableFuture());
        }
        if (writes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = writes.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    private CompletableFuture<Void> ensureJoinMetadata(Document document, Instant now, String username) {
        String timestamp = now.toString();
        CompletionStage<Void> firstJoinStage = ensureFirstJoin(document, timestamp);
        CompletionStage<Void> lastJoinStage = document.set("meta.lastJoin", timestamp);
        CompletionStage<Void> usernameStage = ensureUsername(document, username);
        CompletionStage<Void> playtimeStage = ensurePlaytimeCounter(document);
        CompletionStage<Void> menuStage = ensureMenuItemConfig(document);

        return CompletableFuture.allOf(
            firstJoinStage.toCompletableFuture(),
            lastJoinStage.toCompletableFuture(),
            usernameStage.toCompletableFuture(),
            playtimeStage.toCompletableFuture(),
            menuStage.toCompletableFuture()
        );
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
        Optional<String> materialName = document.get(PlayerMenuItemConfig.MATERIAL_PATH, String.class);
        Optional<Integer> slotValue = document.get(PlayerMenuItemConfig.SLOT_PATH, Integer.class);
        Optional<Boolean> enabledValue = document.get(PlayerMenuItemConfig.ENABLED_PATH, Boolean.class);

        Material material = materialName
            .map(this::parseMaterial)
            .filter(candidate -> candidate != null && !candidate.isAir())
            .orElse(PlayerMenuItemConfig.DEFAULT.material());
        int slot = slotValue.orElse(PlayerMenuItemConfig.DEFAULT.slot());
        boolean enabled = enabledValue.orElse(true);

        CompletionStage<Void> materialStage = materialName.isPresent()
            ? CompletableFuture.completedFuture(null)
            : document.set(PlayerMenuItemConfig.MATERIAL_PATH, material.name()).toCompletableFuture();
        CompletionStage<Void> slotStage = slotValue.isPresent()
            ? CompletableFuture.completedFuture(null)
            : document.set(PlayerMenuItemConfig.SLOT_PATH, slot).toCompletableFuture();
        CompletionStage<Void> enabledStage = enabledValue.isPresent()
            ? CompletableFuture.completedFuture(null)
            : document.set(PlayerMenuItemConfig.ENABLED_PATH, enabled).toCompletableFuture();

        return CompletableFuture.allOf(materialStage.toCompletableFuture(), slotStage.toCompletableFuture(), enabledStage.toCompletableFuture());
    }

    private CompletableFuture<Void> updatePlaytime(Document document, Instant sessionStart, Instant logoutTime, String username) {
        Instant effectiveStart = sessionStart != null
            ? sessionStart
            : document.get("meta.lastJoin", String.class)
                .map(this::parseInstant)
                .orElse(null);

        long sessionSeconds = computeSessionSeconds(effectiveStart, logoutTime);
        String logoutTimestamp = logoutTime.toString();

        Instant fallbackJoin = effectiveStart == null ? logoutTime : effectiveStart;
        CompletionStage<Void> firstJoinStage = ensureFirstJoin(document, fallbackJoin.toString());
        CompletionStage<Void> lastJoinStage = document.set("meta.lastJoin", fallbackJoin.toString());
        CompletionStage<Void> lastLeaveStage = document.set("meta.lastLeave", logoutTimestamp);
        CompletionStage<Void> usernameStage = ensureUsername(document, username);
        CompletionStage<Void> playtimeStage = ensurePlaytimeCounter(document)
            .thenCompose(ignored -> incrementPlaytime(document, sessionSeconds));
        CompletionStage<Void> menuStage = ensureMenuItemConfig(document);

        return CompletableFuture.allOf(
            firstJoinStage.toCompletableFuture(),
            lastJoinStage.toCompletableFuture(),
            lastLeaveStage.toCompletableFuture(),
            usernameStage.toCompletableFuture(),
            playtimeStage.toCompletableFuture(),
            menuStage.toCompletableFuture()
        );
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
