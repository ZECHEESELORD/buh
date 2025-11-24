package sh.harold.fulcrum.plugin.stats;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.ConfigurableModule;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;
import sh.harold.fulcrum.plugin.stats.binding.ArmorVisualStatBinding;
import sh.harold.fulcrum.plugin.stats.binding.MaxHealthStatBinding;
import sh.harold.fulcrum.stats.binding.StatBindingManager;
import sh.harold.fulcrum.stats.core.StatRegistry;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class StatsModule implements FulcrumModule, ConfigurableModule {

    private final JavaPlugin plugin;
    private FeatureConfigService configService;
    private StatMappingConfig mappingConfig;
    private StatRegistry statRegistry;
    private StatService statService;
    private StatBindingManager bindingManager;
    private StatEntityListener statEntityListener;
    private StatDamageListener statDamageListener;

    public StatsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("stats"));
    }

    @Override
    public CompletionStage<Void> enable() {
        configService = new FeatureConfigService(plugin);
        statRegistry = StatRegistry.withDefaults();
        statService = new StatService(statRegistry);
        bindingManager = new StatBindingManager();
        refreshConfiguration(loadMappingConfig());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (statService != null && bindingManager != null) {
            statService.removeListener(bindingManager);
        }
        unregisterListeners();
        if (configService != null) {
            configService.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> reloadConfig() {
        if (configService == null || statService == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Stats module not initialized."));
        }
        refreshConfiguration(loadMappingConfig());
        return CompletableFuture.completedFuture(null);
    }

    public StatService statService() {
        return statService;
    }

    public StatRegistry statRegistry() {
        return statRegistry;
    }

    public StatMappingConfig mappingConfig() {
        return mappingConfig;
    }

    private void refreshConfiguration(StatMappingConfig newConfig) {
        mappingConfig = newConfig;
        rebuildBindings();
        registerListeners();
    }

    private void rebuildBindings() {
        if (bindingManager != null) {
            statService.removeListener(bindingManager);
        }

        bindingManager = new StatBindingManager();
        StatEntityResolver entityResolver = new StatEntityResolver(plugin.getServer());
        bindingManager.registerBinding(new MaxHealthStatBinding(entityResolver));
        if (mappingConfig.mirrorArmorAttributes()) {
            bindingManager.registerBinding(new ArmorVisualStatBinding(entityResolver));
        }
        statService.addListener(bindingManager);
    }

    private void registerListeners() {
        unregisterListeners();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        statEntityListener = new StatEntityListener(statService);
        statDamageListener = new StatDamageListener(statService, mappingConfig);
        pluginManager.registerEvents(statEntityListener, plugin);
        pluginManager.registerEvents(statDamageListener, plugin);
    }

    private void unregisterListeners() {
        if (statEntityListener != null) {
            HandlerList.unregisterAll(statEntityListener);
            statEntityListener = null;
        }
        if (statDamageListener != null) {
            HandlerList.unregisterAll(statDamageListener);
            statDamageListener = null;
        }
    }

    private StatMappingConfig loadMappingConfig() {
        return StatMappingConfig.from(configService.load(StatMappingConfig.CONFIG_DEFINITION));
    }
}
