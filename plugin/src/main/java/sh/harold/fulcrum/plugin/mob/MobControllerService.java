package sh.harold.fulcrum.plugin.mob;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.plugin.mob.pdc.MobPdc;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MobControllerService {

    private final Plugin plugin;
    private final MobPdc mobPdc;
    private final MobRegistry registry;
    private final Map<UUID, MobController> controllers = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private long tick;

    MobControllerService(Plugin plugin, MobPdc mobPdc, MobRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mobPdc = Objects.requireNonNull(mobPdc, "mobPdc");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    void start() {
        stop();
        tick = 0L;
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickControllers, 1L, 1L);
    }

    void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Map.Entry<UUID, MobController> entry : controllers.entrySet()) {
            Entity resolved = Bukkit.getEntity(entry.getKey());
            if (resolved instanceof LivingEntity living && !living.isDead() && living.isValid()) {
                entry.getValue().onUnload(living);
            }
        }
        controllers.clear();
        tick = 0L;
    }

    void ensureSpawned(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        String mobId = mobPdc.readId(entity).orElse(null);
        if (mobId == null) {
            return;
        }
        MobDefinition definition = registry.get(mobId).orElse(null);
        if (definition == null || definition.controllerFactory() == null) {
            return;
        }
        controllers.computeIfAbsent(entity.getUniqueId(), ignored -> {
            MobController controller = definition.controllerFactory().create();
            controller.onSpawn(entity);
            return controller;
        });
    }

    void handleDamage(LivingEntity entity, org.bukkit.event.entity.EntityDamageEvent event) {
        if (entity == null || event == null) {
            return;
        }
        MobController controller = controllers.get(entity.getUniqueId());
        if (controller != null) {
            controller.onDamage(entity, event);
        }
    }

    void handleDeath(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        MobController controller = controllers.remove(entity.getUniqueId());
        if (controller != null) {
            controller.onDeath(entity);
        }
    }

    void handleUnload(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        MobController controller = controllers.remove(entity.getUniqueId());
        if (controller != null) {
            controller.onUnload(entity);
        }
    }

    private void tickControllers() {
        tick++;
        for (Map.Entry<UUID, MobController> entry : controllers.entrySet()) {
            Entity resolved = Bukkit.getEntity(entry.getKey());
            if (!(resolved instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                controllers.remove(entry.getKey());
                continue;
            }
            entry.getValue().tick(living, tick);
        }
    }
}

