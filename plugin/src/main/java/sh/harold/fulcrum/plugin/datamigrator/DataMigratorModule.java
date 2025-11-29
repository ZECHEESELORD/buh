package sh.harold.fulcrum.plugin.datamigrator;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class DataMigratorModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;
    private DataMigratorService migrator;

    public DataMigratorModule(JavaPlugin plugin, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            ModuleId.of("data-migrator"),
            Set.of(ModuleId.of("data")),
            ModuleCategory.UTILITY
        );
    }

    @Override
    public CompletionStage<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        Path dataPath = plugin.getDataFolder().toPath().resolve("data");
        migrator = new DataMigratorService(dataPath, plugin.getLogger(), dataApi);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new DataMigrationListener(plugin.getLogger(), migrator), plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (migrator != null) {
            migrator.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        registrar.register(new DataMigratorCommand(migrator).build(), "datamigrate", java.util.List.of("migratedata"));
    }
}
