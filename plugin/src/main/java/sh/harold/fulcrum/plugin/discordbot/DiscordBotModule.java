package sh.harold.fulcrum.plugin.discordbot;

import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.accountlink.AccountLinkConfig;
import sh.harold.fulcrum.plugin.accountlink.AccountLinkModule;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class DiscordBotModule implements FulcrumModule {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final AccountLinkModule accountLinkModule;
    private final DataModule dataModule;
    private final FeatureConfigService configService;
    private DiscordBotService botService;

    public DiscordBotModule(org.bukkit.plugin.java.JavaPlugin plugin, AccountLinkModule accountLinkModule, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.accountLinkModule = Objects.requireNonNull(accountLinkModule, "accountLinkModule");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
        this.configService = new FeatureConfigService(plugin);
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            ModuleId.of("discord-bot"),
            java.util.Set.of(ModuleId.of("account-link")),
            ModuleCategory.UTILITY
        );
    }

    @Override
    public CompletionStage<Void> enable() {
        DiscordBotConfig botConfig = DiscordBotConfig.from(configService.load(DiscordBotConfig.CONFIG_DEFINITION));
        AccountLinkConfig linkConfig = accountLinkModule.accountLinkConfig()
            .orElseThrow(() -> new IllegalStateException("Account link config missing; cannot start Discord bot."));
        var dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        accountLinkModule.reloadSourcesConfig();

        botService = DiscordBotService.withLinkFeature(
            plugin.getLogger(),
            botConfig,
            linkConfig,
            plugin.getDataFolder().toPath().resolve("config/discord-bot"),
            () -> accountLinkModule.sourcesConfig(),
            dataApi
        );
        return botService.start();
    }

    @Override
    public CompletionStage<Void> disable() {
        if (botService != null) {
            botService.close();
            botService = null;
        }
        configService.close();
        return CompletableFuture.completedFuture(null);
    }
}
