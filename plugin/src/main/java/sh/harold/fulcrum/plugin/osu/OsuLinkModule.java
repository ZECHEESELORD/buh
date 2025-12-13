package sh.harold.fulcrum.plugin.osu;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.event.HandlerList;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;
import sh.harold.fulcrum.plugin.osu.LinkAccountConfig;

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
    private OsuVerificationService verificationService;
    private OsuRankRefreshListener rankRefreshListener;

    public OsuLinkModule(org.bukkit.plugin.java.JavaPlugin plugin, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("link-account"), Set.of(ModuleId.of("data")), ModuleCategory.UTILITY);
    }

    @Override
    public CompletionStage<Void> enable() {
        try {
            configService = new FeatureConfigService(plugin);
            var dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
            DocumentCollection players = dataApi.collection("players");
            LinkAccountConfig config = LinkAccountConfig.load(configService);

            if (config.osu().clientId().isBlank() || config.osu().clientSecret().isBlank() || config.discord().clientId().isBlank() || config.discord().clientSecret().isBlank()) {
                plugin.getLogger().warning("link-account module disabled: set osu/discord client id/secret in config/link-account/config.yml");
                return CompletableFuture.completedFuture(null);
            }

            VerificationWorld verificationWorld = new VerificationWorld(plugin);
            OsuVerificationService[] verificationRef = new OsuVerificationService[1];
            osuLinkService = new OsuLinkService(plugin, players, config, playerId -> {
                OsuVerificationService service = verificationRef[0];
                if (service != null) {
                    service.handleLinkCompleted(playerId);
                }
            });
            verificationService = new OsuVerificationService(plugin, players, verificationWorld, config.requireOsuLink(), osuLinkService);
            verificationRef[0] = verificationService;
            verificationService.registerListeners();
            rankRefreshListener = new OsuRankRefreshListener(osuLinkService);
            plugin.getServer().getPluginManager().registerEvents(rankRefreshListener, plugin);
            httpServer = new OsuLinkHttpServer(osuLinkService, config, plugin.getLogger());
            httpServer.start();
            plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
            return CompletableFuture.completedFuture(null);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<Void> disable() {
        if (httpServer != null) {
            httpServer.close();
            httpServer = null;
        }
        if (verificationService != null) {
            verificationService.close();
            verificationService = null;
        }
        if (rankRefreshListener != null) {
            HandlerList.unregisterAll(rankRefreshListener);
            rankRefreshListener = null;
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
        registrar.register(new DiscordLinkCommand(osuLinkService).build(), "linkdiscordaccount", java.util.List.of());
        registrar.register(new SkipOsuCommand().build(), "skiposu", java.util.List.of());
        if (verificationService != null) {
            registrar.register(new BypassRegistrationCommand(verificationService).build(), "bypassregistration", java.util.List.of());
        }
    }
}
