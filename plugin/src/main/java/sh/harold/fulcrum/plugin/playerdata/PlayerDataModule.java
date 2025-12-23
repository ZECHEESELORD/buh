package sh.harold.fulcrum.plugin.playerdata;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class PlayerDataModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;
    private PlayerSettingsService settingsService;
    private PlayerDirectoryService directoryService;
    private PlayerSessionListener sessionListener;
    private UsernameDisplayService usernameDisplayService;
    private PlayerLevelingService levelingService;

    public PlayerDataModule(JavaPlugin plugin, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("player-data"), java.util.Set.of(ModuleId.of("data")), ModuleCategory.PLAYER);
    }

    @Override
    public CompletionStage<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        PlayerBiomeAggregator biomeAggregator = new PlayerBiomeAggregator(plugin.getLogger(), dataApi);
        directoryService = new PlayerDirectoryService(dataApi, plugin.getLogger());
        PlayerSessionListener listener = new PlayerSessionListener(plugin.getLogger(), dataApi, biomeAggregator, directoryService);
        sessionListener = listener;
        settingsService = new PlayerSettingsService(dataApi);
        levelingService = new PlayerLevelingService(dataApi);
        usernameDisplayService = new UsernameDisplayService(plugin, dataApi, settingsService, levelingService);
        levelingService.addListener(usernameDisplayService::handleLevelUpdate);
        PvpSettingsListener pvpSettingsListener = new PvpSettingsListener(settingsService, plugin.getLogger());
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(biomeAggregator, plugin);
        pluginManager.registerEvents(listener, plugin);
        pluginManager.registerEvents(pvpSettingsListener, plugin);
        pluginManager.registerEvents(usernameDisplayService, plugin);
        plugin.getServer().getOnlinePlayers()
            .forEach(player -> settingsService.loadSettings(player.getUniqueId())
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "[startup:data] failed to warm settings for " + player.getUniqueId(), throwable);
                    return null;
                }));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (sessionListener == null) {
            return CompletableFuture.completedFuture(null);
        }
        var players = java.util.List.copyOf(plugin.getServer().getOnlinePlayers());
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Instant logoutTime = Instant.now();
        return sessionListener.flushSessions(players, logoutTime)
            .whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to flush playtime on shutdown", throwable);
                }
            });
    }

    public java.util.Optional<PlayerSettingsService> playerSettingsService() {
        return java.util.Optional.ofNullable(settingsService);
    }

    public java.util.Optional<PlayerDirectoryService> playerDirectoryService() {
        return java.util.Optional.ofNullable(directoryService);
    }

    public java.util.Optional<UsernameDisplayService> usernameDisplayService() {
        return java.util.Optional.ofNullable(usernameDisplayService);
    }

    public java.util.Optional<PlayerLevelingService> playerLevelingService() {
        return java.util.Optional.ofNullable(levelingService);
    }
}
