package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.menu.MenuModule;
import sh.harold.fulcrum.plugin.playerdata.PlayerDataModule;
import sh.harold.fulcrum.plugin.playerdata.PlayerDirectoryService;
import sh.harold.fulcrum.plugin.playerdata.PlayerSettingsService;
import sh.harold.fulcrum.plugin.playerdata.UsernameDisplayService;
import sh.harold.fulcrum.plugin.stash.StashModule;
import sh.harold.fulcrum.plugin.stash.StashService;
import sh.harold.fulcrum.plugin.unlockable.UnlockableModule;
import sh.harold.fulcrum.plugin.unlockable.UnlockableRegistry;
import sh.harold.fulcrum.plugin.unlockable.UnlockableService;
import sh.harold.fulcrum.plugin.stats.StatsModule;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.command.brigadier.Commands;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PlayerMenuModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;
    private final StashModule stashModule;
    private final MenuModule menuModule;
    private final PlayerDataModule playerDataModule;
    private final ScoreboardService scoreboardService;
    private final UnlockableModule unlockableModule;
    private final StatsModule statsModule;
    private PlayerMenuService playerMenuService;

    public PlayerMenuModule(
        JavaPlugin plugin,
        DataModule dataModule,
        StashModule stashModule,
        MenuModule menuModule,
        PlayerDataModule playerDataModule,
        ScoreboardService scoreboardService,
        UnlockableModule unlockableModule,
        StatsModule statsModule
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
        this.stashModule = Objects.requireNonNull(stashModule, "stashModule");
        this.menuModule = Objects.requireNonNull(menuModule, "menuModule");
        this.playerDataModule = Objects.requireNonNull(playerDataModule, "playerDataModule");
        this.scoreboardService = Objects.requireNonNull(scoreboardService, "scoreboardService");
        this.unlockableModule = Objects.requireNonNull(unlockableModule, "unlockableModule");
        this.statsModule = Objects.requireNonNull(statsModule, "statsModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            ModuleId.of("player-menu"),
            Set.of(
                ModuleId.of("data"),
                ModuleId.of("stash"),
                ModuleId.of("player-data"),
                ModuleId.of("menu"),
                ModuleId.of("perks"),
                ModuleId.of("rpg-stats")
            ),
            ModuleCategory.HUD
        );
    }

    @Override
    public CompletionStage<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        StashService stashService = stashModule.stashService().orElseThrow(() -> new IllegalStateException("StashService not available"));
        MenuService menuService = menuModule.menuService().orElseThrow(() -> new IllegalStateException("MenuService not available"));
        PlayerSettingsService settingsService = playerDataModule.playerSettingsService()
            .orElseThrow(() -> new IllegalStateException("PlayerSettingsService not available"));
        UsernameDisplayService usernameDisplayService = playerDataModule.usernameDisplayService()
            .orElseThrow(() -> new IllegalStateException("UsernameDisplayService not available"));
        PlayerDirectoryService playerDirectoryService = playerDataModule.playerDirectoryService()
            .orElseThrow(() -> new IllegalStateException("PlayerDirectoryService not available"));
        UnlockableService unlockableService = java.util.Objects.requireNonNull(
            unlockableModule.unlockableService(),
            "UnlockableService not available"
        );
        UnlockableRegistry unlockableRegistry = unlockableModule.registry();
        var statService = Objects.requireNonNull(statsModule.statService(), "StatService not available");
        var statRegistry = Objects.requireNonNull(statsModule.statRegistry(), "StatRegistry not available");
        var sourceContextRegistry = Objects.requireNonNull(statsModule.statSourceContextRegistry(), "StatSourceContextRegistry not available");

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        playerMenuService = new PlayerMenuService(
            plugin,
            dataApi,
            stashService,
            menuService,
            settingsService,
            usernameDisplayService,
            playerDirectoryService,
            scoreboardService,
            unlockableService,
            unlockableRegistry,
            statService,
            statRegistry,
            sourceContextRegistry
        );

        pluginManager.registerEvents(new PlayerMenuListener(playerMenuService), plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);

        return CompletableFuture.completedFuture(null);
    }

    public PlayerMenuService playerMenuService() {
        return playerMenuService;
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        registrar.register(new PlayerMenuCommand(playerMenuService).build(), "menu", java.util.List.of());
        registrar.register(new PlayerSettingsCommand(playerMenuService).build(), "settings", java.util.List.of());
        registrar.register(new StatsMenuCommand(playerMenuService).build(), "stats", java.util.List.of());
        registrar.register(new ClearCommand(playerMenuService).build(), "clear", java.util.List.of());
    }
}
