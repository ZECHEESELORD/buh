package sh.harold.fulcrum.plugin.staff;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.plugin.staff.command.OpenInventoryCommand;
import sh.harold.fulcrum.plugin.staff.VanishService;
import sh.harold.fulcrum.plugin.staff.command.LoopCommand;
import sh.harold.fulcrum.plugin.staff.command.SudoCommand;
import sh.harold.fulcrum.plugin.staff.command.VanishCommand;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class StaffCommandsModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final LuckPermsModule luckPermsModule;
    private final DataModule dataModule;
    private StaffGuard staffGuard;
    private VanishService vanishService;
    private OpenInventoryService openInventoryService;

    public StaffCommandsModule(JavaPlugin plugin, LuckPermsModule luckPermsModule, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPermsModule = Objects.requireNonNull(luckPermsModule, "luckPermsModule");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("staff"), Set.of(ModuleId.of("luckperms"), ModuleId.of("data")));
    }

    @Override
    public CompletionStage<Void> enable() {
        staffGuard = new StaffGuard(luckPermsModule);
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        vanishService = new VanishService(plugin, staffGuard, dataApi);
        openInventoryService = new OpenInventoryService(plugin, dataApi);
        plugin.getServer().getPluginManager().registerEvents(vanishService, plugin);
        openInventoryService.registerListeners();
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (vanishService != null) {
            vanishService.revealAll();
        }
        return CompletableFuture.completedFuture(null);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        registrar.register(new LoopCommand(plugin, staffGuard).build(), "loop", java.util.List.of());
        registrar.register(new SudoCommand(staffGuard).build(), "sudo", java.util.List.of());
        registrar.register(new VanishCommand(plugin, staffGuard, vanishService).build(), "vanish", java.util.List.of());
        registrar.register(new OpenInventoryCommand(staffGuard, openInventoryService).build(), "openinv", java.util.List.of("openinventory"));
    }
}
