package sh.harold.fulcrum.plugin.playerdata;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
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
    private PlayerSessionListener sessionListener;

    public PlayerDataModule(JavaPlugin plugin, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("player-data"), java.util.Set.of(ModuleId.of("data")));
    }

    @Override
    public CompletionStage<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        PlayerBiomeAggregator biomeAggregator = new PlayerBiomeAggregator(plugin.getLogger(), dataApi);
        PlayerSessionListener listener = new PlayerSessionListener(plugin.getLogger(), dataApi, biomeAggregator);
        sessionListener = listener;
        settingsService = new PlayerSettingsService(dataApi);
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(biomeAggregator, plugin);
        pluginManager.registerEvents(listener, plugin);
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
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to flush playtime on shutdown", throwable);
                return null;
            });
    }

    public java.util.Optional<PlayerSettingsService> playerSettingsService() {
        return java.util.Optional.ofNullable(settingsService);
    }
}
