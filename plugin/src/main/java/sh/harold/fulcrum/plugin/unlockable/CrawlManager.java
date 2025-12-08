package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.Location;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

final class CrawlManager {

    private static final long FOLLOW_INTERVAL_TICKS = 1L;
    private static final double HEAD_OFFSET = 0.5;
    private static final double HOP_EXTRA_OFFSET = 0.6;
    private static final double HOP_VELOCITY = 0.42;
    private static final int HOP_TICKS = 6;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<UUID, CrawlSession> sessions = new HashMap<>();

    CrawlManager(JavaPlugin plugin, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    CrawlToggleResult toggle(Player player) {
        UUID playerId = player.getUniqueId();
        if (sessions.containsKey(playerId)) {
            stop(player);
            return CrawlToggleResult.STOPPED;
        }
        return start(player) ? CrawlToggleResult.STARTED : CrawlToggleResult.FAILED;
    }

    boolean start(Player player) {
        UUID playerId = player.getUniqueId();
        if (sessions.containsKey(playerId)) {
            return true;
        }
        if (!player.isOnline() || player.isDead() || player.isInsideVehicle() || player.isGliding()) {
            return false;
        }
        Shulker helper = spawnHelper(player);
        if (helper == null) {
            return false;
        }
        applyCrawlPose(player);
        nudgePlayerDown(player);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player, helper), 0L, FOLLOW_INTERVAL_TICKS);
        sessions.put(playerId, new CrawlSession(helper, task));
        return true;
    }

    void stop(Player player) {
        stop(player.getUniqueId(), player);
    }

    void stop(UUID playerId) {
        stop(playerId, plugin.getServer().getPlayer(playerId));
    }

    void hopIfCrawling(Player player) {
        CrawlSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.setHopTicks(HOP_TICKS);
        session.helper().teleport(helperLocation(player, HEAD_OFFSET + HOP_EXTRA_OFFSET));
        applyCrawlPose(player);
        applyHopVelocity(player);
    }

    void stopAll() {
        new ArrayList<>(sessions.keySet()).forEach(this::stop);
    }

    boolean isCrawling(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    private void tick(Player player, Shulker helper) {
        CrawlSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            stop(player);
            return;
        }
        if (!player.isOnline() || player.isDead() || player.isInsideVehicle() || player.isFlying() || player.isGliding()) {
            stop(player);
            return;
        }
        if (!Objects.equals(player.getWorld(), helper.getWorld())) {
            stop(player);
            return;
        }
        if (helper.isDead()) {
            stop(player);
            return;
        }
        double offset = HEAD_OFFSET;
        if (session.hopTicks() > 0) {
            offset += HOP_EXTRA_OFFSET;
            session.decrementHopTicks();
        }
        Location target = helperLocation(player, offset);
        if (helper.getLocation().distanceSquared(target) > 0.001) {
            helper.teleport(target);
        }
        applyCrawlPose(player);
    }

    private Shulker spawnHelper(Player player) {
        Location headLocation = helperLocation(player);
        try {
            return player.getWorld().spawn(headLocation, Shulker.class, shulker -> {
                shulker.setAI(false);
                shulker.setGravity(false);
                shulker.setInvisible(true);
                shulker.setInvulnerable(true);
                shulker.setSilent(true);
                shulker.setPersistent(false);
                shulker.setRemoveWhenFarAway(true);
            });
        } catch (Throwable throwable) {
            logger.fine(() -> "Failed to spawn crawl helper for " + player.getUniqueId() + ": " + throwable.getMessage());
            return null;
        }
    }

    private void stop(UUID playerId, Player player) {
        CrawlSession session = sessions.remove(playerId);
        if (session != null) {
            session.followTask().cancel();
            Shulker helper = session.helper();
            if (!helper.isDead()) {
                helper.remove();
            }
        }
        if (player != null && player.isOnline()) {
            resetPose(player);
        }
    }

    private void applyCrawlPose(Player player) {
        try {
            player.setSwimming(true);
            player.setPose(Pose.SWIMMING);
            player.setSprinting(false);
        } catch (Throwable throwable) {
            logger.fine(() -> "Failed to force crawl pose for " + player.getUniqueId() + ": " + throwable.getMessage());
        }
    }

    private void resetPose(Player player) {
        try {
            player.setSwimming(false);
            player.setPose(Pose.STANDING);
            player.setSneaking(false);
            player.setSprinting(false);
        } catch (Throwable throwable) {
            logger.fine(() -> "Failed to reset crawl pose for " + player.getUniqueId() + ": " + throwable.getMessage());
        }
    }

    private void nudgePlayerDown(Player player) {
        try {
            player.setVelocity(player.getVelocity().setY(-0.08));
        } catch (Throwable throwable) {
            logger.fine(() -> "Failed to nudge player down for crawl start " + player.getUniqueId() + ": " + throwable.getMessage());
        }
    }

    private void applyHopVelocity(Player player) {
        try {
            Vector velocity = player.getVelocity();
            player.setVelocity(new Vector(velocity.getX(), HOP_VELOCITY, velocity.getZ()));
        } catch (Throwable throwable) {
            logger.fine(() -> "Failed to apply crawl hop for " + player.getUniqueId() + ": " + throwable.getMessage());
        }
    }

    private Location helperLocation(Player player) {
        return helperLocation(player, HEAD_OFFSET);
    }

    private Location helperLocation(Player player, double offset) {
        BoundingBox box = player.getBoundingBox();
        double centerX = (box.getMinX() + box.getMaxX()) * 0.5;
        double centerZ = (box.getMinZ() + box.getMaxZ()) * 0.5;
        double centerY = box.getMaxY() + offset;
        return new Location(player.getWorld(), centerX, centerY, centerZ);
    }

    private static final class CrawlSession {
        private final Shulker helper;
        private final BukkitTask followTask;
        private int hopTicks;

        private CrawlSession(Shulker helper, BukkitTask followTask) {
            this.helper = helper;
            this.followTask = followTask;
        }

        Shulker helper() {
            return helper;
        }

        BukkitTask followTask() {
            return followTask;
        }

        int hopTicks() {
            return hopTicks;
        }

        void setHopTicks(int hopTicks) {
            this.hopTicks = hopTicks;
        }

        void decrementHopTicks() {
            if (hopTicks > 0) {
                hopTicks--;
            }
        }
    }

    enum CrawlToggleResult {
        STARTED,
        STOPPED,
        FAILED
    }
}
