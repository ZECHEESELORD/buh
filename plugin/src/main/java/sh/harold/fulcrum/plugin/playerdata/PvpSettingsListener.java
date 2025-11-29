package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PvpSettingsListener implements Listener {

    private static final Duration MESSAGE_COOLDOWN = Duration.ofSeconds(1);

    private final PlayerSettingsService settingsService;
    private final Logger logger;
    private final Map<UUID, BlockMessage> recentMessages = new ConcurrentHashMap<>();

    public PvpSettingsListener(PlayerSettingsService settingsService, Logger logger) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        warmSettings(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        settingsService.evictCachedSettings(playerId);
        recentMessages.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (attacker.getUniqueId().equals(target.getUniqueId())) {
            return;
        }

        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();

        boolean attackerPvp = settingsService.cachedPvpEnabled(attackerId);
        boolean targetPvp = settingsService.cachedPvpEnabled(targetId);

        if (!attackerPvp) {
            event.setCancelled(true);
            sendBlockMessage(attacker, BlockReason.ATTACKER_DISABLED, target.getName());
            return;
        }

        if (!targetPvp) {
            event.setCancelled(true);
            sendBlockMessage(attacker, BlockReason.TARGET_DISABLED, target.getName());
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player playerShooter) {
                return playerShooter;
            }
        }
        return null;
    }

    private void warmSettings(Player player) {
        UUID playerId = player.getUniqueId();
        long startedAt = System.nanoTime();
        logger.info(() -> "[login:data] warm player settings for " + playerId);
        settingsService.loadSettings(playerId)
            .whenComplete((ignored, throwable) -> {
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                if (throwable != null) {
                    logger.log(Level.WARNING, "[login:data] failed to warm settings for " + playerId + " after " + elapsedMillis + "ms", throwable);
                    return;
                }
                logger.info(() -> "[login:data] warmed player settings for " + playerId + " in " + elapsedMillis + "ms");
            });
    }

    private void sendBlockMessage(Player player, BlockReason reason, String targetName) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        BlockMessage last = recentMessages.get(playerId);
        if (last != null && last.reason() == reason && now - last.sentAtMillis() < MESSAGE_COOLDOWN.toMillis()) {
            return;
        }

        Component message = reason == BlockReason.ATTACKER_DISABLED
            ? Component.text("Your PvP switch is off; open settings when you crave a duel.", NamedTextColor.YELLOW)
            : Component.text(targetName + " is keeping PvP off right now.", NamedTextColor.YELLOW);
        player.sendMessage(message);
        recentMessages.put(playerId, new BlockMessage(reason, now));
    }

    private enum BlockReason {
        ATTACKER_DISABLED,
        TARGET_DISABLED
    }

    private record BlockMessage(BlockReason reason, long sentAtMillis) {
    }
}
