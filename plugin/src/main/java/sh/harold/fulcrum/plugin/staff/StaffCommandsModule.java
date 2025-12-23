package sh.harold.fulcrum.plugin.staff;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.plugin.staff.command.OpenInventoryCommand;
import sh.harold.fulcrum.plugin.staff.VanishService;
import sh.harold.fulcrum.plugin.staff.command.DumpDataCommand;
import sh.harold.fulcrum.plugin.staff.command.EmergencyCreativeCommand;
import sh.harold.fulcrum.plugin.staff.command.FeedCommand;
import sh.harold.fulcrum.plugin.staff.command.HealCommand;
import sh.harold.fulcrum.plugin.staff.command.LevelCommand;
import sh.harold.fulcrum.plugin.staff.command.LoopCommand;
import sh.harold.fulcrum.plugin.staff.command.StaffGamemodeCommand;
import sh.harold.fulcrum.plugin.staff.command.SudoCommand;
import sh.harold.fulcrum.plugin.staff.command.VanishCommand;
import sh.harold.fulcrum.plugin.staff.StaffCreativeService;
import sh.harold.fulcrum.plugin.playerdata.PlayerDataModule;
import sh.harold.fulcrum.plugin.playerdata.PlayerLevelingService;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class StaffCommandsModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final LuckPermsModule luckPermsModule;
    private final DataModule dataModule;
    private final PlayerDataModule playerDataModule;
    private StaffGuard staffGuard;
    private VanishService vanishService;
    private OpenInventoryService openInventoryService;
    private StaffCreativeService staffCreativeService;
    private PlayerLevelingService levelingService;

    public StaffCommandsModule(
        JavaPlugin plugin,
        LuckPermsModule luckPermsModule,
        DataModule dataModule,
        PlayerDataModule playerDataModule
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPermsModule = Objects.requireNonNull(luckPermsModule, "luckPermsModule");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
        this.playerDataModule = Objects.requireNonNull(playerDataModule, "playerDataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            ModuleId.of("staff"),
            Set.of(ModuleId.of("luckperms"), ModuleId.of("data"), ModuleId.of("player-data")),
            ModuleCategory.STAFF
        );
    }

    @Override
    public CompletionStage<Void> enable() {
        staffGuard = new StaffGuard(luckPermsModule);
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        vanishService = new VanishService(plugin, staffGuard, dataApi);
        openInventoryService = new OpenInventoryService(plugin, dataApi);
        staffCreativeService = new StaffCreativeService(plugin, staffGuard);
        levelingService = playerDataModule.playerLevelingService()
            .orElseThrow(() -> new IllegalStateException("PlayerLevelingService not available"));
        plugin.getServer().getPluginManager().registerEvents(vanishService, plugin);
        plugin.getServer().getPluginManager().registerEvents(staffCreativeService, plugin);
        openInventoryService.registerListeners();
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (vanishService != null) {
            vanishService.revealAll();
        }
        if (staffCreativeService != null) {
            staffCreativeService.disableAll();
        }
        return CompletableFuture.completedFuture(null);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        registrar.register(new LoopCommand(plugin, staffGuard).build(), "loop", java.util.List.of());
        registrar.register(new SudoCommand(staffGuard).build(), "sudo", java.util.List.of());
        registrar.register(new VanishCommand(plugin, staffGuard, vanishService).build(), "vanish", java.util.List.of());
        registrar.register(new OpenInventoryCommand(staffGuard, openInventoryService).build(), "openinv", java.util.List.of("openinventory"));
        registrar.register(new DumpDataCommand(plugin, staffGuard, dataModule).build(), "dumpdata", java.util.List.of());
        registrar.register(new HealCommand(staffGuard).build(), "heal", java.util.List.of());
        registrar.register(new FeedCommand(staffGuard).build(), "feed", java.util.List.of());
        registrar.register(new LevelCommand(staffGuard, levelingService, luckPermsModule).build(), "level", java.util.List.of());
        StaffGamemodeCommand gmCommand = new StaffGamemodeCommand(staffGuard, staffCreativeService);
        registrar.register(gmCommand.build(), "gamemode", java.util.List.of());
        registrar.register(gmCommand.alias("gmc", "creative"), "gmc", java.util.List.of());
        registrar.register(gmCommand.alias("gms", "survival"), "gms", java.util.List.of());
        registrar.register(gmCommand.alias("gmsp", "spectator"), "gmsp", java.util.List.of());
        registrar.register(new EmergencyCreativeCommand(staffGuard).build(), "ireallywantcreativemode", java.util.List.of());
    }
}
