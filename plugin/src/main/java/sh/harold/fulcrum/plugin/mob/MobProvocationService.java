package sh.harold.fulcrum.plugin.mob;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MobProvocationService {

    private static final long GRACE_WINDOW_MILLIS = 5_000L;
    private static final long TICK_PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final MobLifecycleService lifecycleService;
    private final MobNameplateService nameplateService;
    private final Map<UUID, Long> provokedUntilMillis = new ConcurrentHashMap<>();
    private BukkitTask task;

    MobProvocationService(Plugin plugin, MobLifecycleService lifecycleService, MobNameplateService nameplateService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.nameplateService = Objects.requireNonNull(nameplateService, "nameplateService");
    }

    void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID entityId : provokedUntilMillis.keySet()) {
            Entity resolved = Bukkit.getEntity(entityId);
            if (resolved instanceof LivingEntity living && !living.isDead() && living.isValid()) {
                nameplateService.restoreBaseName(living);
            }
        }
        provokedUntilMillis.clear();
    }

    void markProvoked(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        if (entity instanceof Player || lifecycleService.isHostile(entity)) {
            return;
        }
        lifecycleService.ensureParticipatingNeutral(entity);
        long now = System.currentTimeMillis();
        provokedUntilMillis.put(entity.getUniqueId(), now + GRACE_WINDOW_MILLIS);
        nameplateService.refresh(entity, true, true);
    }

    boolean isProvoked(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return false;
        }
        if (entity instanceof Player || lifecycleService.isHostile(entity)) {
            return false;
        }
        Long until = provokedUntilMillis.get(entity.getUniqueId());
        if (until == null) {
            return isAggroOnPlayer(entity);
        }
        return System.currentTimeMillis() < until || isAggroOnPlayer(entity);
    }

    void forget(LivingEntity entity) {
        if (entity != null) {
            provokedUntilMillis.remove(entity.getUniqueId());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : provokedUntilMillis.entrySet()) {
            UUID entityId = entry.getKey();
            long until = entry.getValue();
            Entity resolved = Bukkit.getEntity(entityId);
            if (!(resolved instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                provokedUntilMillis.remove(entityId);
                continue;
            }
            if (living instanceof Player || lifecycleService.isHostile(living)) {
                provokedUntilMillis.remove(entityId);
                continue;
            }
            if (isAggroOnPlayer(living)) {
                provokedUntilMillis.put(entityId, now + GRACE_WINDOW_MILLIS);
                continue;
            }
            if (now < until) {
                continue;
            }
            provokedUntilMillis.remove(entityId);
            nameplateService.restoreBaseName(living);
        }
    }

    private boolean isAggroOnPlayer(LivingEntity living) {
        if (living instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target instanceof Player) {
                return true;
            }
        }
        return false;
    }
}
