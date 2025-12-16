package sh.harold.fulcrum.plugin;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
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
import sh.harold.fulcrum.plugin.chat.UnsignedChatModule;
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
import sh.harold.fulcrum.plugin.jukebox.JukeboxModule;
import sh.harold.fulcrum.plugin.mob.MobModule;
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
import sh.harold.fulcrum.plugin.unlockable.ChatCosmeticPrefixService;
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
    private UnsignedChatModule unsignedChatModule;
    private MessageModule messageModule;
    private StashModule stashModule;
    private MenuModule menuModule;
    private JukeboxModule jukeboxModule;
    private PlayerMenuModule playerMenuModule;
    private FeatureVoteModule featureVoteModule;
    private FunModule funModule;
    private StaffCommandsModule staffCommandsModule;
    private StatsModule statsModule;
    private MobModule mobModule;
    private ShutdownModule shutdownModule;
    private OsuLinkModule osuLinkModule;
    private UnlockableModule unlockableModule;
    private sh.harold.fulcrum.plugin.item.ItemModule itemModule;
    private sh.harold.fulcrum.plugin.item.migration.ItemMigrationModule itemMigrationModule;
    private sh.harold.fulcrum.plugin.playerhead.PlayerHeadModule playerHeadModule;
    private ChatChannelService chatChannelService;
    private MessageService messageService;
    private VersionService versionService;
    private SimpleScoreboardService scoreboardService;
    private TabFeature tabFeature;
    private ScoreboardFeature scoreboardFeature;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI()
            .getSettings()
            .reEncodeByDefault(true)
            .checkForUpdates(false);
        PacketEvents.getAPI().load();
        ensureDataFolder();
        createModules();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();
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
        PacketEvents.getAPI().terminate();
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

    public Optional<sh.harold.fulcrum.plugin.mob.MobEngine> mobEngine() {
        return mobModule == null ? Optional.empty() : Optional.ofNullable(mobModule.mobEngine());
    }

    public Optional<FeatureVoteService> featureVoteService() {
        return featureVoteModule == null ? Optional.empty() : Optional.ofNullable(featureVoteModule.voteService());
    }

    public sh.harold.fulcrum.plugin.item.ItemModule itemModule() {
        return itemModule;
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
        ChatCosmeticPrefixService chatCosmeticPrefixService = new ChatCosmeticPrefixService(
            this::unlockableService,
            this::cosmeticRegistry,
            playerDataModule::playerDirectoryService,
            getLogger()
        );
        messageService = new MessageService(
            this,
            () -> formattedUsernameService().orElseGet(this::noopFormattedUsernameService),
            () -> playerDataModule.usernameDisplayService().orElse(null),
            chatCosmeticPrefixService
        );
        chatModule = new ChatModule(this, luckPermsModule, chatChannelService, messageService, playerDataModule, chatCosmeticPrefixService);
        unsignedChatModule = new UnsignedChatModule(this);
        messageModule = new MessageModule(this, luckPermsModule, chatChannelService, messageService);
        stashModule = new StashModule(this, dataModule);
        menuModule = new MenuModule(this);
        jukeboxModule = new JukeboxModule(this);
        unlockableModule = new UnlockableModule(this, dataModule, economyModule);
        statsModule = new StatsModule(this, playerDataModule);
        mobModule = new MobModule(this, statsModule);
        playerMenuModule = new PlayerMenuModule(this, dataModule, stashModule, menuModule, playerDataModule, scoreboardService, unlockableModule, statsModule);
        featureVoteModule = new FeatureVoteModule(this, dataModule);
        funModule = new FunModule(this, luckPermsModule);
        staffCommandsModule = new StaffCommandsModule(this, luckPermsModule, dataModule);
        itemModule = new sh.harold.fulcrum.plugin.item.ItemModule(this, statsModule, dataModule);
        playerHeadModule = new sh.harold.fulcrum.plugin.playerhead.PlayerHeadModule(this, itemModule);
        itemMigrationModule = new sh.harold.fulcrum.plugin.item.migration.ItemMigrationModule(this, itemModule);
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
            unsignedChatModule,
            messageModule,
            stashModule,
            menuModule,
            jukeboxModule,
            unlockableModule,
            statsModule,
            mobModule,
            playerMenuModule,
            featureVoteModule,
            funModule,
            staffCommandsModule,
            itemModule,
            playerHeadModule,
            itemMigrationModule,
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
        if (itemModule != null && itemModule.engine() != null && luckPermsModule != null) {
            sh.harold.fulcrum.plugin.permissions.StaffGuard staffGuard = new sh.harold.fulcrum.plugin.permissions.StaffGuard(luckPermsModule);
            sh.harold.fulcrum.plugin.item.command.EnchantCommand enchantCommand = new sh.harold.fulcrum.plugin.item.command.EnchantCommand(itemModule.engine(), staffGuard);
            registrar.register(getPluginMeta(), enchantCommand.build(), "enchant", java.util.List.of());
            sh.harold.fulcrum.plugin.item.command.DebugItemDataCommand debugItemDataCommand = new sh.harold.fulcrum.plugin.item.command.DebugItemDataCommand(staffGuard, itemModule.engine().resolver(), itemModule.engine().itemPdc());
            registrar.register(getPluginMeta(), debugItemDataCommand.build(), "debugitemdata", java.util.List.of("did"));
            sh.harold.fulcrum.plugin.item.command.DebugItemStatsCommand debugItemStatsCommand = new sh.harold.fulcrum.plugin.item.command.DebugItemStatsCommand(staffGuard, itemModule.engine().resolver());
            registrar.register(getPluginMeta(), debugItemStatsCommand.build(), "debugitemstats", java.util.List.of("dis"));
            sh.harold.fulcrum.plugin.item.command.SetItemDurabilityCommand setItemDurabilityCommand = new sh.harold.fulcrum.plugin.item.command.SetItemDurabilityCommand(staffGuard, itemModule.engine().resolver(), itemModule.engine().itemPdc(), itemModule.engine().statBridge());
            registrar.register(getPluginMeta(), setItemDurabilityCommand.build(), "setitemdurability", java.util.List.of("sidur"));
            sh.harold.fulcrum.plugin.stats.command.DebugStatsCommand debugStatsCommand = new sh.harold.fulcrum.plugin.stats.command.DebugStatsCommand(staffGuard, statsModule.statService());
            registrar.register(getPluginMeta(), debugStatsCommand.build(), "debugstats", java.util.List.of("ds"));
        }
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

    public MessageService messageService() {
        return messageService;
    }
}
