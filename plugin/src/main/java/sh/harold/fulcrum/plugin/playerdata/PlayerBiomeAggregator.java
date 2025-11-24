package sh.harold.fulcrum.plugin.playerdata;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PlayerBiomeAggregator implements Listener {

    private static final String PATH_PREFIX = "travel.biomes.";

    private final Logger logger;
    private final DocumentCollection players;
    private final Map<UUID, String> lastKnownBiomes = new ConcurrentHashMap<>();

    PlayerBiomeAggregator(Logger logger, DataApi dataApi) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
    }

    CompletionStage<Void> recordInitialVisit(Document document, UUID playerId, Location location, Instant timestamp) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(timestamp, "timestamp");

        String biomeKey = biomeKey(location);
        if (biomeKey == null) {
            return CompletableFuture.completedFuture(null);
        }

        lastKnownBiomes.put(playerId, biomeKey);
        return recordIfMissing(document, playerId, biomeKey, timestamp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Location location = event.getTo();
        if (location == null) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        String biomeKey = biomeKey(location);
        if (biomeKey == null || biomeKey.equals(lastKnownBiomes.get(playerId))) {
            return;
        }

        lastKnownBiomes.put(playerId, biomeKey);
        Instant timestamp = Instant.now();
        players.load(playerId.toString())
            .thenCompose(document -> recordIfMissing(document, playerId, biomeKey, timestamp))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to aggregate biome visit for player " + playerId, throwable);
                return null;
            });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Location location = event.getPlayer().getLocation();
        String biomeKey = biomeKey(location);
        if (biomeKey == null) {
            lastKnownBiomes.remove(playerId);
            return;
        }

        lastKnownBiomes.put(playerId, biomeKey);
        Instant timestamp = Instant.now();
        players.load(playerId.toString())
            .thenCompose(document -> recordIfMissing(document, playerId, biomeKey, timestamp))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to aggregate biome visit after world change for player " + playerId, throwable);
                return null;
            });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastKnownBiomes.remove(event.getPlayer().getUniqueId());
    }

    private CompletableFuture<Void> recordIfMissing(Document document, UUID playerId, String biomeKey, Instant timestamp) {
        Optional<String> stored = document.get(pathFor(biomeKey), String.class);
        if (stored.isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        return document.set(pathFor(biomeKey), timestamp.toString())
            .toCompletableFuture()
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to persist biome visit for player " + playerId + " in biome " + biomeKey, throwable);
                return null;
            });
    }

    private String biomeKey(Location location) {
        if (location == null) {
            return null;
        }
        return biomeKey(location.getBlock().getBiome());
    }

    private String biomeKey(Biome biome) {
        if (biome == null) {
            return null;
        }
        NamespacedKey key = biome.getKey();
        return key == null ? null : key.asString();
    }

    private String pathFor(String biomeKey) {
        return PATH_PREFIX + biomeKey;
    }
}
