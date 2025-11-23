package sh.harold.fulcrum.plugin.playerdata;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PlayerDataModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;

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
        PlayerJoinListener listener = new PlayerJoinListener(plugin.getLogger(), dataApi);
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(listener, plugin);
        return CompletableFuture.completedFuture(null);
    }
}
