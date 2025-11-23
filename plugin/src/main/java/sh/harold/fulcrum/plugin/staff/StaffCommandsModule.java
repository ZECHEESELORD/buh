package sh.harold.fulcrum.plugin.staff;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.plugin.staff.command.LoopCommand;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class StaffCommandsModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final LuckPermsModule luckPermsModule;
    private StaffGuard staffGuard;

    public StaffCommandsModule(JavaPlugin plugin, LuckPermsModule luckPermsModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPermsModule = Objects.requireNonNull(luckPermsModule, "luckPermsModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("staff"), Set.of(ModuleId.of("luckperms")));
    }

    @Override
    public CompletionStage<Void> enable() {
        staffGuard = new StaffGuard(luckPermsModule);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        registrar.register(new LoopCommand(plugin, staffGuard).build(), "loop", java.util.List.of());
    }
}
