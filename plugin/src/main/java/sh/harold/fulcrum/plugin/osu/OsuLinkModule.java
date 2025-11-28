package sh.harold.fulcrum.plugin.osu;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class OsuLinkModule implements FulcrumModule {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final DataModule dataModule;

    private OsuLinkService osuLinkService;
    private OsuLinkHttpServer httpServer;
    private FeatureConfigService configService;

    public OsuLinkModule(org.bukkit.plugin.java.JavaPlugin plugin, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("osu-link"), Set.of(ModuleId.of("data")), ModuleCategory.UTILITY);
    }

    @Override
    public CompletionStage<Void> enable() {
        return CompletableFuture.runAsync(() -> {
            configService = new FeatureConfigService(plugin);
            var dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
            DocumentCollection players = dataApi.collection("players");
            OsuLinkConfig config = OsuLinkConfig.load(configService);

            osuLinkService = new OsuLinkService(plugin, players, config);
            httpServer = new OsuLinkHttpServer(osuLinkService, config, plugin.getLogger());
            httpServer.start();
            plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        });
    }

    @Override
    public CompletionStage<Void> disable() {
        if (httpServer != null) {
            httpServer.close();
            httpServer = null;
        }
        if (configService != null) {
            configService.close();
            configService = null;
        }
        osuLinkService = null;
        return CompletableFuture.completedFuture(null);
    }

    public java.util.Optional<OsuLinkService> osuLinkService() {
        return java.util.Optional.ofNullable(osuLinkService);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        if (osuLinkService == null) {
            return;
        }
        Commands registrar = event.registrar();
        registrar.register(new OsuLinkCommand(osuLinkService).build(), "linkosuaccount", java.util.List.of());
    }
}
