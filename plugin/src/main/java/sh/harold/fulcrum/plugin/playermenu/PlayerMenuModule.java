package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.stash.StashModule;
import sh.harold.fulcrum.plugin.stash.StashService;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PlayerMenuModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;
    private final StashModule stashModule;
    private PlayerMenuService menuService;

    public PlayerMenuModule(JavaPlugin plugin, DataModule dataModule, StashModule stashModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
        this.stashModule = Objects.requireNonNull(stashModule, "stashModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            ModuleId.of("player-menu"),
            Set.of(ModuleId.of("data"), ModuleId.of("stash"), ModuleId.of("player-data"))
        );
    }

    @Override
    public CompletionStage<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        StashService stashService = stashModule.stashService().orElseThrow(() -> new IllegalStateException("StashService not available"));
        menuService = new PlayerMenuService(plugin, dataApi, stashService);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerMenuListener(menuService), plugin);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        return CompletableFuture.completedFuture(null);
    }

    public PlayerMenuService menuService() {
        return menuService;
    }
}
