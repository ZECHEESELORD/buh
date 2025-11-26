package sh.harold.fulcrum.plugin.accountlink;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class AccountLinkModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;
    private final LuckPermsModule luckPermsModule;
    private final FeatureConfigService configService;
    private AccountLinkService accountLinkService;
    private AccountLinkListener listener;
    private AccountLinkConfig config;
    private AccountLinkHttpServer httpServer;

    public AccountLinkModule(JavaPlugin plugin, DataModule dataModule, LuckPermsModule luckPermsModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
        this.luckPermsModule = Objects.requireNonNull(luckPermsModule, "luckPermsModule");
        this.configService = new FeatureConfigService(plugin);
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            ModuleId.of("account-link"),
            java.util.Set.of(ModuleId.of("data"), ModuleId.of("luckperms")),
            ModuleCategory.UTILITY
        );
    }

    @Override
    public CompletionStage<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        config = AccountLinkConfig.from(configService.load(AccountLinkConfig.CONFIG_DEFINITION));
        DocumentCollection players = dataApi.collection("players");
        DocumentCollection tickets = dataApi.collection("link_tickets");
        DocumentCollection discordLinks = dataApi.collection("discord_links");

        accountLinkService = new AccountLinkService(plugin, players, tickets, discordLinks);
        listener = new AccountLinkListener(accountLinkService, plugin.getLogger());
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(listener, plugin);

        if (!config.hasSecret()) {
            plugin.getLogger().warning("Account link secret is empty; link callbacks will be rejected.");
        } else {
            StateCodec stateCodec = new StateCodec(config.hmacSecret(), config.secretVersion());
            OsuOAuthClient osuClient = new OsuOAuthClient(config, plugin.getLogger());
            httpServer = new AccountLinkHttpServer(accountLinkService, config, stateCodec, osuClient, plugin.getLogger());
            httpServer.start();
            plugin.getLogger().info("Account link HTTP server listening on " + config.bindAddress() + ":" + config.port());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (httpServer != null) {
            httpServer.close();
            httpServer = null;
        }
        accountLinkService = null;
        configService.close();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.Optional<AccountLinkService> accountLinkService() {
        return java.util.Optional.ofNullable(accountLinkService);
    }

    public java.util.Optional<AccountLinkConfig> accountLinkConfig() {
        return java.util.Optional.ofNullable(config);
    }
}
