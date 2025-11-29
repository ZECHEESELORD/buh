package sh.harold.fulcrum.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import sh.harold.fulcrum.api.message.scoreboard.impl.DefaultPlayerScoreboardManager;
import sh.harold.fulcrum.api.message.scoreboard.impl.SimpleScoreboardService;
import sh.harold.fulcrum.api.message.scoreboard.registry.DefaultScoreboardRegistry;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.ModuleActivation;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleLoader;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.permissions.StaffService;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;
import sh.harold.fulcrum.plugin.chat.ChatChannelService;
import sh.harold.fulcrum.plugin.chat.ChatModule;
import sh.harold.fulcrum.plugin.config.ModuleConfigService;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.economy.EconomyModule;
import sh.harold.fulcrum.plugin.economy.EconomyService;
import sh.harold.fulcrum.plugin.message.MessageModule;
import sh.harold.fulcrum.plugin.message.MessageService;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.fun.FunModule;
import sh.harold.fulcrum.plugin.playerdata.PlayerDataModule;
import sh.harold.fulcrum.plugin.scoreboard.ScoreboardFeature;
import sh.harold.fulcrum.plugin.menu.MenuModule;
import sh.harold.fulcrum.plugin.staff.StaffCommandsModule;
import sh.harold.fulcrum.plugin.stash.StashModule;
import sh.harold.fulcrum.plugin.stash.StashService;
import sh.harold.fulcrum.plugin.stats.StatsModule;
import sh.harold.fulcrum.plugin.datamigrator.DataMigratorModule;
import sh.harold.fulcrum.plugin.playermenu.PlayerMenuModule;
import sh.harold.fulcrum.plugin.playermenu.PlayerMenuService;
import sh.harold.fulcrum.plugin.unlockable.UnlockableModule;
import sh.harold.fulcrum.plugin.unlockable.UnlockableService;
import sh.harold.fulcrum.plugin.unlockable.CosmeticRegistry;
import sh.harold.fulcrum.plugin.version.PluginVersionService;
import sh.harold.fulcrum.plugin.version.VersionService;
import sh.harold.fulcrum.plugin.vote.FeatureVoteModule;
import sh.harold.fulcrum.plugin.vote.FeatureVoteService;
import sh.harold.fulcrum.plugin.tab.TabFeature;
import sh.harold.fulcrum.plugin.shutdown.ShutdownModule;
import sh.harold.fulcrum.plugin.osu.OsuLinkModule;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class BuhPlugin extends JavaPlugin {

    private static final Set<ModuleId> ALWAYS_ENABLED_MODULES = Set.of(
        ModuleId.of("data"),
        ModuleId.of("menu")
    );
    private static final Set<ModuleId> BASE_MODULES = Set.of(ModuleId.of("data-migrator"));

    private ModuleLoader moduleLoader;
    private ModuleConfigService moduleConfigService;
    private ModuleActivation moduleActivation;
    private List<ModuleDescriptor> moduleDescriptors;
    private DataModule dataModule;
    private DataMigratorModule dataMigratorModule;
    private PlayerDataModule playerDataModule;
    private EconomyModule economyModule;
    private LuckPermsModule luckPermsModule;
    private ChatModule chatModule;
    private MessageModule messageModule;
    private StashModule stashModule;
    private MenuModule menuModule;
    private PlayerMenuModule playerMenuModule;
    private FeatureVoteModule featureVoteModule;
    private FunModule funModule;
    private StaffCommandsModule staffCommandsModule;
    private StatsModule statsModule;
    private ShutdownModule shutdownModule;
    private OsuLinkModule osuLinkModule;
    private UnlockableModule unlockableModule;
    private ChatChannelService chatChannelService;
    private MessageService messageService;
    private VersionService versionService;
    private SimpleScoreboardService scoreboardService;
    private TabFeature tabFeature;
    private ScoreboardFeature scoreboardFeature;

    @Override
    public void onLoad() {
        ensureDataFolder();
        createModules();
    }

    @Override
    public void onEnable() {
        ensureDataFolder();
        if (moduleLoader == null) {
            createModules();
        }

        moduleActivation = moduleConfigService.load(moduleDescriptors);
        await(moduleLoader.enableAll(moduleActivation, this::logSkippedModule), "enable modules");
        getLogger().info("Fulcrum modules enabled");
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
    }

    @Override
    public void onDisable() {
        if (moduleLoader != null) {
            await(moduleLoader.disableAll(), "disable modules");
        }
    }

    public DataApi dataApi() {
        return dataModule == null ? null : dataModule.dataApi().orElse(null);
    }

    public Optional<StaffService> staffService() {
        return luckPermsModule == null ? Optional.empty() : luckPermsModule.staffService();
    }

    public Optional<EconomyService> economyService() {
        return economyModule == null ? Optional.empty() : economyModule.economyService();
    }

    public Optional<FormattedUsernameService> formattedUsernameService() {
        return luckPermsModule == null ? Optional.empty() : luckPermsModule.formattedUsernameService();
    }

    public Optional<SimpleScoreboardService> scoreboardService() {
        return Optional.ofNullable(scoreboardService);
    }

    public Optional<VersionService> versionService() {
        return Optional.ofNullable(versionService);
    }

    public Optional<StashService> stashService() {
        return stashModule == null ? Optional.empty() : stashModule.stashService();
    }

    public Optional<PlayerMenuService> playerMenuService() {
        return playerMenuModule == null ? Optional.empty() : Optional.ofNullable(playerMenuModule.playerMenuService());
    }

    public Optional<UnlockableService> unlockableService() {
        return unlockableModule == null ? Optional.empty() : Optional.ofNullable(unlockableModule.unlockableService());
    }

    public Optional<CosmeticRegistry> cosmeticRegistry() {
        return unlockableModule == null ? Optional.empty() : Optional.ofNullable(unlockableModule.cosmeticRegistry());
    }

    public StatsModule statsModule() {
        return statsModule;
    }

    public Optional<FeatureVoteService> featureVoteService() {
        return featureVoteModule == null ? Optional.empty() : Optional.ofNullable(featureVoteModule.voteService());
    }

    private void createModules() {
        versionService = new PluginVersionService(this);
        scoreboardService = new SimpleScoreboardService(
            this,
            new DefaultScoreboardRegistry(),
            new DefaultPlayerScoreboardManager()
        );
        dataModule = new DataModule(this);
        dataMigratorModule = new DataMigratorModule(this, dataModule);
        economyModule = new EconomyModule(this, dataModule);
        playerDataModule = new PlayerDataModule(this, dataModule);
        luckPermsModule = new LuckPermsModule(this);
        osuLinkModule = new OsuLinkModule(this, dataModule);
        chatChannelService = new ChatChannelService(this::staffService);
        messageService = new MessageService(
            this,
            () -> formattedUsernameService().orElseGet(this::noopFormattedUsernameService),
            () -> playerDataModule.usernameDisplayService().orElse(null)
        );
        chatModule = new ChatModule(this, luckPermsModule, chatChannelService, messageService, playerDataModule);
        messageModule = new MessageModule(this, luckPermsModule, chatChannelService, messageService);
        stashModule = new StashModule(this, dataModule);
        menuModule = new MenuModule(this);
        unlockableModule = new UnlockableModule(this, dataModule, economyModule);
        playerMenuModule = new PlayerMenuModule(this, dataModule, stashModule, menuModule, playerDataModule, scoreboardService, unlockableModule);
        featureVoteModule = new FeatureVoteModule(this, dataModule);
        funModule = new FunModule(this, luckPermsModule);
        staffCommandsModule = new StaffCommandsModule(this, luckPermsModule, dataModule);
        statsModule = new StatsModule(this);
        shutdownModule = new ShutdownModule(this, scoreboardService);
        tabFeature = new TabFeature(this);
        scoreboardFeature = new ScoreboardFeature(
            this,
            scoreboardService,
            versionService,
            playerDataModule,
            shutdownModule,
            economyModule
        );
        List<FulcrumModule> modules = List.of(
            shutdownModule,
            dataModule,
            dataMigratorModule,
            economyModule,
            playerDataModule,
            luckPermsModule,
            osuLinkModule,
            chatModule,
            messageModule,
            stashModule,
            menuModule,
            playerMenuModule,
            unlockableModule,
            featureVoteModule,
            funModule,
            staffCommandsModule,
            statsModule,
            tabFeature,
            scoreboardFeature
        );
        moduleDescriptors = modules.stream()
            .map(FulcrumModule::descriptor)
            .toList();
        moduleLoader = new ModuleLoader(modules);
        moduleConfigService = new ModuleConfigService(this, ALWAYS_ENABLED_MODULES, BASE_MODULES);
    }

    private void await(CompletionStage<Void> stage, String action) {
        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException runtimeException) {
            getLogger().log(Level.SEVERE, "Failed to " + action, runtimeException);
            throw runtimeException;
        }
    }

    private void ensureDataFolder() {
        getDataFolder().mkdirs();
    }

    private void logSkippedModule(ModuleLoader.SkippedModule skippedModule) {
        getLogger().info("Skipping module '" + skippedModule.moduleId().value() + "': " + skippedModule.reason());
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        ModulesCommand modulesCommand = new ModulesCommand(moduleLoader);
        PingCommand pingCommand = new PingCommand();
        registrar.register(getPluginMeta(), pingCommand.build(), "ping", java.util.List.of());
        registrar.register(getPluginMeta(), modulesCommand.build(), "module", java.util.List.of("modules"));
    }

    private FormattedUsernameService noopFormattedUsernameService() {
        return new FormattedUsernameService() {
            @Override
            public java.util.concurrent.CompletionStage<FormattedUsername> username(java.util.UUID playerId, String username) {
                return java.util.concurrent.CompletableFuture.completedFuture(new FormattedUsername(
                    net.kyori.adventure.text.Component.empty(),
                    net.kyori.adventure.text.Component.text(username, net.kyori.adventure.text.format.NamedTextColor.WHITE)
                ));
            }
        };
    }
}
