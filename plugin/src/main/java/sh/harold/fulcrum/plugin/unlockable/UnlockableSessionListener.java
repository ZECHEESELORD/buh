package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UnlockableSessionListener implements Listener {

    private final UnlockableService unlockableService;
    private final Logger logger;

    public UnlockableSessionListener(UnlockableService unlockableService, Logger logger) {
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        unlockableService.loadState(playerId).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                logger.log(Level.SEVERE, "Failed to warm unlockable cache for " + playerId, throwable);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        evict(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        evict(event.getPlayer().getUniqueId());
    }

    private void evict(UUID playerId) {
        unlockableService.evict(playerId);
    }
}
