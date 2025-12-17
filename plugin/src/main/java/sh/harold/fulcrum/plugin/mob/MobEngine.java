package sh.harold.fulcrum.plugin.mob;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.attribute.Attribute;
import sh.harold.fulcrum.plugin.mob.pdc.MobPdc;
import sh.harold.fulcrum.plugin.stats.StatMappingConfig;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.Objects;
import java.util.Optional;

public final class MobEngine {

    private final Plugin plugin;
    private final MobRegistry registry;
    private final MobPdc mobPdc;
    private final StatService statService;
    private final MobLifecycleService lifecycleService;
    private final MobDifficultyRater difficultyRater;
    private final MobNameplateService nameplateService;
    private final MobProvocationService provocationService;
    private final MobControllerService controllerService;

    public MobEngine(Plugin plugin, StatService statService, StatMappingConfig mappingConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = new MobRegistry();
        this.mobPdc = new MobPdc(plugin);
        this.statService = Objects.requireNonNull(statService, "statService");
        this.difficultyRater = new MobDifficultyRater(Objects.requireNonNull(mappingConfig, "mappingConfig"));
        this.lifecycleService = new MobLifecycleService(mobPdc, registry, this.statService);
        this.nameplateService = new MobNameplateService(plugin, mobPdc, registry, difficultyRater);
        this.provocationService = new MobProvocationService(plugin, lifecycleService, nameplateService);
        this.controllerService = new MobControllerService(plugin, mobPdc, registry);
    }

    public MobRegistry registry() {
        return registry;
    }

    public MobPdc mobPdc() {
        return mobPdc;
    }

    public StatService statService() {
        return statService;
    }

    public MobLifecycleService lifecycleService() {
        return lifecycleService;
    }

    public MobDifficultyRater difficultyRater() {
        return difficultyRater;
    }

    public MobNameplateService nameplateService() {
        return nameplateService;
    }

    public MobProvocationService provocationService() {
        return provocationService;
    }

    public MobControllerService controllerService() {
        return controllerService;
    }

    public boolean shouldShowNameplate(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return false;
        }
        String mobId = mobPdc.readId(entity).orElse(null);
        MobDefinition definition = mobId == null ? null : registry.get(mobId).orElse(null);
        MobTier tier = mobPdc.readTier(entity)
            .or(() -> definition == null ? Optional.empty() : Optional.of(definition.tier()))
            .orElse(MobTier.VANILLA);
        if (tier != MobTier.VANILLA) {
            return true;
        }
        if (lifecycleService.isHostile(entity)) {
            return true;
        }
        return provocationService.isProvoked(entity);
    }

    public Optional<LivingEntity> spawn(String mobId, Location location) {
        if (mobId == null || mobId.isBlank() || location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        MobDefinition definition = registry.get(mobId).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        Entity spawned = location.getWorld().spawnEntity(location, definition.baseType());
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            return Optional.empty();
        }
        if (!convert(living, mobId)) {
            living.remove();
            return Optional.empty();
        }
        return Optional.of(living);
    }

    public boolean convert(LivingEntity entity, String mobId) {
        if (entity == null || entity.isDead() || !entity.isValid() || mobId == null || mobId.isBlank()) {
            return false;
        }
        MobDefinition definition = registry.get(mobId).orElse(null);
        if (definition == null) {
            return false;
        }
        if (entity.getType() != definition.baseType()) {
            return false;
        }
        lifecycleService.assignCustomMob(entity, definition);
        healToMax(entity);
        nameplateService.refresh(entity, true, true);
        controllerService.ensureSpawned(entity);
        return true;
    }

    private void healToMax(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? entity.getHealth() : attribute.getValue();
        if (!Double.isFinite(max) || max <= 0.0) {
            return;
        }
        entity.setHealth(max);
    }
}
