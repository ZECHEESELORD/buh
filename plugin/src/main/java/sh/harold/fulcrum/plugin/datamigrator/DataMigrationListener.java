package sh.harold.fulcrum.plugin.datamigrator;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DataMigrationListener implements Listener {

    private final Logger logger;
    private final DataMigratorService migrator;

    DataMigrationListener(Logger logger, DataMigratorService migrator) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.migrator = Objects.requireNonNull(migrator, "migrator");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        long startedAt = System.nanoTime();
        logger.info(() -> "[login:data] migrate on join check for " + playerId);
        migrator.migrateOnJoin(playerId)
            .whenComplete((updated, throwable) -> {
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                if (throwable != null) {
                    logger.log(Level.WARNING, "[login:data] join migration failed for " + playerId + " after " + elapsedMillis + "ms", throwable);
                    return;
                }
                logger.info(() -> "[login:data] join migration completed for " + playerId + " (updated=" + updated + ") in " + elapsedMillis + "ms");
            });
    }
}
