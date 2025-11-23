package sh.harold.fulcrum.plugin.playerdata;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PlayerJoinListener implements Listener {

    private final Logger logger;
    private final DocumentCollection players;

    PlayerJoinListener(Logger logger, DataApi dataApi) {
        this.logger = logger;
        this.players = dataApi.collection("players");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Instant now = Instant.now();

        players.load(playerId.toString())
            .thenCompose(doc -> ensureDocument(doc, now))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to update player metadata for " + playerId, throwable);
                return null;
            });
    }

    private CompletableFuture<Void> ensureDocument(Document document, Instant now) {
        if (!document.exists()) {
            return document.overwrite(Map.of(
                "meta", Map.of(
                    "firstJoin", now.toString(),
                    "lastJoin", now.toString()
                )
            )).toCompletableFuture();
        }

        String timestamp = now.toString();
        Optional<String> firstJoin = document.get("meta.firstJoin", String.class);
        CompletableFuture<Void> firstJoinWrite = firstJoin.isEmpty()
            ? document.set("meta.firstJoin", timestamp).toCompletableFuture()
            : CompletableFuture.completedFuture(null);

        return firstJoinWrite.thenCompose(ignored -> document.set("meta.lastJoin", timestamp).toCompletableFuture());
    }
}
