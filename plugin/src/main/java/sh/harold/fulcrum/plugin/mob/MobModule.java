package sh.harold.fulcrum.plugin.mob;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.common.loader.ConfigurableModule;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.stats.StatsModule;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class MobModule implements FulcrumModule, ConfigurableModule {

    private final JavaPlugin plugin;
    private final StatsModule statsModule;
    private MobEngine mobEngine;
    private Listener lifecycleListener;
    private Listener nameTagListener;
    private Listener provocationListener;
    private Listener healthListener;
    private Listener controllerListener;

    public MobModule(JavaPlugin plugin, StatsModule statsModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.statsModule = Objects.requireNonNull(statsModule, "statsModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("mob-engine"), ModuleCategory.GAMEPLAY, ModuleId.of("rpg-stats"));
    }

    @Override
    public CompletionStage<Void> enable() {
        mobEngine = new MobEngine(
            plugin,
            Objects.requireNonNull(statsModule.statService(), "StatService not available"),
            Objects.requireNonNull(statsModule.mappingConfig(), "StatMappingConfig not available")
        );
        registerListeners();
        mobEngine.provocationService().start();
        mobEngine.controllerService().start();
        bootstrapExistingEntities();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (mobEngine != null) {
            mobEngine.provocationService().stop();
            mobEngine.controllerService().stop();
        }
        unregisterListeners();
        mobEngine = null;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> reloadConfig() {
        return CompletableFuture.completedFuture(null);
    }

    public MobEngine mobEngine() {
        return mobEngine;
    }

    private void registerListeners() {
        unregisterListeners();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        lifecycleListener = new MobLifecycleListener(mobEngine);
        provocationListener = new MobProvocationListener(plugin, mobEngine);
        nameTagListener = new MobNameTagListener(plugin, mobEngine);
        healthListener = new MobHealthListener(plugin, mobEngine);
        controllerListener = new MobControllerListener(mobEngine);
        pluginManager.registerEvents(lifecycleListener, plugin);
        pluginManager.registerEvents(provocationListener, plugin);
        pluginManager.registerEvents(nameTagListener, plugin);
        pluginManager.registerEvents(healthListener, plugin);
        pluginManager.registerEvents(controllerListener, plugin);
    }

    private void unregisterListeners() {
        if (lifecycleListener != null) {
            HandlerList.unregisterAll(lifecycleListener);
            lifecycleListener = null;
        }
        if (nameTagListener != null) {
            HandlerList.unregisterAll(nameTagListener);
            nameTagListener = null;
        }
        if (provocationListener != null) {
            HandlerList.unregisterAll(provocationListener);
            provocationListener = null;
        }
        if (healthListener != null) {
            HandlerList.unregisterAll(healthListener);
            healthListener = null;
        }
        if (controllerListener != null) {
            HandlerList.unregisterAll(controllerListener);
            controllerListener = null;
        }
    }

    private void bootstrapExistingEntities() {
        if (mobEngine == null) {
            return;
        }
        for (var world : plugin.getServer().getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                if (living instanceof Player) {
                    continue;
                }
                String mobId = mobEngine.mobPdc().readId(living).orElse(null);
                MobDefinition definition = mobId == null ? null : mobEngine.registry().get(mobId).orElse(null);
                if (definition != null) {
                    mobEngine.lifecycleService().ensureCustomMob(living, definition);
                    mobEngine.nameplateService().refresh(living, true, true);
                    mobEngine.controllerService().ensureSpawned(living);
                    continue;
                }
                if (mobEngine.lifecycleService().isHostile(living)) {
                    mobEngine.lifecycleService().ensureVanillaHostile(living);
                    mobEngine.nameplateService().refresh(living, true, true);
                    continue;
                }
                if (living instanceof org.bukkit.entity.Mob mob && mob.getTarget() instanceof Player) {
                    mobEngine.provocationService().markProvoked(living);
                    continue;
                }
                if (mobEngine.mobPdc().readNameMode(living).orElse(MobNameMode.BASE) == MobNameMode.ENGINE) {
                    mobEngine.nameplateService().restoreBaseName(living);
                }
            }
        }
    }
}
