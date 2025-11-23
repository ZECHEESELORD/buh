package sh.harold.fulcrum.plugin.stats;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
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

public final class StatsModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private FeatureConfigService configService;
    private StatMappingConfig mappingConfig;
    private StatRegistry statRegistry;
    private StatService statService;
    private StatBindingManager bindingManager;

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
        mappingConfig = StatMappingConfig.from(configService.load(StatMappingConfig.CONFIG_DEFINITION));
        statRegistry = StatRegistry.withDefaults();
        statService = new StatService(statRegistry);
        bindingManager = new StatBindingManager();

        StatEntityResolver entityResolver = new StatEntityResolver(plugin.getServer());
        bindingManager.registerBinding(new MaxHealthStatBinding(entityResolver));
        if (mappingConfig.mirrorArmorAttributes()) {
            bindingManager.registerBinding(new ArmorVisualStatBinding(entityResolver));
        }
        statService.addListener(bindingManager);

        registerListeners();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (configService != null) {
            configService.close();
        }
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

    private void registerListeners() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new StatEntityListener(statService), plugin);
        pluginManager.registerEvents(new StatDamageListener(statService, mappingConfig), plugin);
    }
}
