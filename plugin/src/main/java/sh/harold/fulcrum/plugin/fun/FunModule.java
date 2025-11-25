package sh.harold.fulcrum.plugin.fun;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.fun.command.KaboomCommand;
import sh.harold.fulcrum.plugin.fun.quickmaths.QuickMathsCommand;
import sh.harold.fulcrum.plugin.fun.quickmaths.QuickMathsListener;
import sh.harold.fulcrum.plugin.fun.quickmaths.QuickMathsManager;
import sh.harold.fulcrum.plugin.fun.command.ShrugCommand;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class FunModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final LuckPermsModule luckPermsModule;
    private QuickMathsManager quickMathsManager;
    private StaffGuard staffGuard;

    public FunModule(JavaPlugin plugin, LuckPermsModule luckPermsModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPermsModule = Objects.requireNonNull(luckPermsModule, "luckPermsModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("fun"), Set.of(ModuleId.of("luckperms")));
    }

    @Override
    public CompletionStage<Void> enable() {
        staffGuard = new StaffGuard(luckPermsModule);
        quickMathsManager = new QuickMathsManager(plugin);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new QuickMathsListener(quickMathsManager), plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (quickMathsManager != null) {
            quickMathsManager.shutdown();
        }
        return CompletableFuture.completedFuture(null);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        registrar.register(new QuickMathsCommand(quickMathsManager, staffGuard).build(), "quickmaths", java.util.List.of());
        registrar.register(new ShrugCommand().build(), "shrug", java.util.List.of());
        registrar.register(new KaboomCommand(staffGuard).build(), "kaboom", java.util.List.of());
    }
}
