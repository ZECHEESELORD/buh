package sh.harold.fulcrum.plugin.stats;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.ConfigurableModule;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;
import sh.harold.fulcrum.plugin.stats.binding.ArmorVisualStatBinding;
import sh.harold.fulcrum.plugin.stats.binding.MaxHealthStatBinding;
import sh.harold.fulcrum.plugin.playerdata.PlayerDataModule;
import sh.harold.fulcrum.plugin.playerdata.PlayerSettingsService;
import sh.harold.fulcrum.stats.binding.StatBindingManager;
import sh.harold.fulcrum.stats.core.StatRegistry;
import sh.harold.fulcrum.stats.service.StatService;
import sh.harold.fulcrum.plugin.item.stat.StatSourceContextRegistry;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class StatsModule implements FulcrumModule, ConfigurableModule {

    private final JavaPlugin plugin;
    private final PlayerDataModule playerDataModule;
    private FeatureConfigService configService;
    private StatMappingConfig mappingConfig;
    private StatRegistry statRegistry;
    private StatService statService;
    private StatSourceContextRegistry statSourceContextRegistry;
    private StatBindingManager bindingManager;
    private PlayerSettingsService playerSettingsService;
    private DamageMarkerRenderer damageMarkerRenderer;
    private BowDrawTracker bowDrawTracker;
    private BowDrawListener bowDrawListener;
    private StatEntityListener statEntityListener;
    private StatDamageListener statDamageListener;
    private MovementSpeedListener movementSpeedListener;

    public StatsModule(JavaPlugin plugin, PlayerDataModule playerDataModule) {
        this.plugin = plugin;
        this.playerDataModule = playerDataModule;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("rpg-stats"), ModuleCategory.GAMEPLAY, ModuleId.of("player-data"));
    }

    @Override
    public CompletionStage<Void> enable() {
        configService = new FeatureConfigService(plugin);
        statRegistry = StatRegistry.withDefaults();
        statService = new StatService(statRegistry);
        statSourceContextRegistry = new StatSourceContextRegistry();
        bindingManager = new StatBindingManager();
        playerSettingsService = playerDataModule.playerSettingsService()
            .orElseThrow(() -> new IllegalStateException("PlayerSettingsService not available for stats"));
        damageMarkerRenderer = new DamageMarkerRenderer(plugin, playerSettingsService);
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

    public StatSourceContextRegistry statSourceContextRegistry() {
        return statSourceContextRegistry;
    }

    public StatMappingConfig mappingConfig() {
        return mappingConfig;
    }

    public PlayerSettingsService playerSettingsService() {
        return playerSettingsService;
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
        bindingManager.registerBinding(new sh.harold.fulcrum.plugin.stats.binding.AttackSpeedStatBinding(entityResolver));
        if (mappingConfig.mirrorArmorAttributes()) {
            bindingManager.registerBinding(new ArmorVisualStatBinding(entityResolver, mappingConfig));
        }
        statService.addListener(bindingManager);
    }

    private void registerListeners() {
        unregisterListeners();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        bowDrawTracker = new BowDrawTracker(plugin);
        bowDrawListener = new BowDrawListener(bowDrawTracker);
        statEntityListener = new StatEntityListener(statService);
        statDamageListener = new StatDamageListener(plugin, statService, mappingConfig, damageMarkerRenderer, bowDrawTracker);
        movementSpeedListener = new MovementSpeedListener(plugin, statService);
        pluginManager.registerEvents(bowDrawListener, plugin);
        pluginManager.registerEvents(statEntityListener, plugin);
        pluginManager.registerEvents(statDamageListener, plugin);
        pluginManager.registerEvents(movementSpeedListener, plugin);
    }

    private void unregisterListeners() {
        if (bowDrawListener != null) {
            HandlerList.unregisterAll(bowDrawListener);
            bowDrawListener = null;
            bowDrawTracker = null;
        }
        if (statEntityListener != null) {
            HandlerList.unregisterAll(statEntityListener);
            statEntityListener = null;
        }
        if (statDamageListener != null) {
            HandlerList.unregisterAll(statDamageListener);
            statDamageListener = null;
        }
        if (movementSpeedListener != null) {
            HandlerList.unregisterAll(movementSpeedListener);
            movementSpeedListener = null;
        }
    }

    private StatMappingConfig loadMappingConfig() {
        return StatMappingConfig.from(configService.load(StatMappingConfig.CONFIG_DEFINITION));
    }
}
