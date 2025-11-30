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
import java.util.LinkedHashMap;
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
        Map<String, Object> updates = new LinkedHashMap<>();
        if (document.get("meta.firstJoin", String.class).isEmpty()) {
            updates.put("meta.firstJoin", timestamp);
        }
        updates.put("meta.lastJoin", timestamp);
        if (document.get("meta.username", String.class).filter(username::equals).isEmpty()) {
            updates.put("meta.username", username);
        }
        if (document.get("statistics.playtimeSeconds", Number.class).isEmpty()) {
            updates.put("statistics.playtimeSeconds", 0L);
        }
        seedMenuItemDefaults(document, updates);
        return updates.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : document.patch(updates, java.util.List.of()).toCompletableFuture();
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
        Map<String, Object> updates = new LinkedHashMap<>();
        if (document.get("meta.firstJoin", String.class).isEmpty()) {
            updates.put("meta.firstJoin", fallbackJoin.toString());
        }
        updates.put("meta.lastJoin", fallbackJoin.toString());
        updates.put("meta.lastLeave", logoutTimestamp);
        if (document.get("meta.username", String.class).filter(username::equals).isEmpty()) {
            updates.put("meta.username", username);
        }

        Optional<Number> playtime = document.get("statistics.playtimeSeconds", Number.class);
        long current = playtime.map(Number::longValue).orElse(0L);
        if (sessionSeconds > 0 || playtime.isEmpty()) {
            long updated = current + sessionSeconds;
            updates.put("statistics.playtimeSeconds", updated);
        }

        seedMenuItemDefaults(document, updates);

        return updates.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : document.patch(updates, java.util.List.of()).toCompletableFuture();
    }

    private long computeSessionSeconds(Instant start, Instant end) {
        if (start == null) {
            return 0L;
        }
        long seconds = Duration.between(start, end).getSeconds();
        return Math.max(seconds, 0L);
    }

    private void seedMenuItemDefaults(Document document, Map<String, Object> updates) {
        Optional<String> materialName = document.get(PlayerMenuItemConfig.MATERIAL_PATH, String.class);
        Optional<Integer> slotValue = document.get(PlayerMenuItemConfig.SLOT_PATH, Integer.class);
        Optional<Boolean> enabledValue = document.get(PlayerMenuItemConfig.ENABLED_PATH, Boolean.class);

        Material material = materialName
            .map(this::parseMaterial)
            .filter(candidate -> candidate != null && !candidate.isAir())
            .orElse(PlayerMenuItemConfig.DEFAULT.material());
        if (materialName.isEmpty()) {
            updates.put(PlayerMenuItemConfig.MATERIAL_PATH, material.name());
        }
        if (slotValue.isEmpty()) {
            updates.put(PlayerMenuItemConfig.SLOT_PATH, PlayerMenuItemConfig.DEFAULT.slot());
        }
        if (enabledValue.isEmpty()) {
            updates.put(PlayerMenuItemConfig.ENABLED_PATH, true);
        }
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
