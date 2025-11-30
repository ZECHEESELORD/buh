package sh.harold.fulcrum.plugin.datamigrator;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
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
    private DataStoreMigrationService storeMigrator;
    private MigrationGate migrationGate;

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
        storeMigrator = new DataStoreMigrationService(dataPath, dataModule.config(), plugin.getLogger());
        migrationGate = new MigrationGate(plugin, dataModule.config());

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new DataMigrationListener(plugin.getLogger(), migrator), plugin);
        pluginManager.registerEvents(migrationGate, plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (migrator != null) {
            migrator.close();
        }
        if (storeMigrator != null) {
            storeMigrator.close();
        }
        if (migrationGate != null) {
            migrationGate.disable();
        }
        return CompletableFuture.completedFuture(null);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        registrar.register(new DataMigrationCommands(migrator, storeMigrator, migrationGate).build(), "datamigrate", java.util.List.of("migratedata"));
    }

    /**
     * Blocks logins while migration is required.
     */
    static final class MigrationGate implements Listener {

        private final java.util.concurrent.atomic.AtomicBoolean migrationRequired;
        private final JavaPlugin plugin;

        MigrationGate(JavaPlugin plugin, sh.harold.fulcrum.plugin.data.DataConfig config) {
            this.plugin = plugin;
            this.migrationRequired = new java.util.concurrent.atomic.AtomicBoolean(config.migrationBlockLogins());
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPreLogin(AsyncPlayerPreLoginEvent event) {
            if (!migrationRequired.get()) {
                return;
            }
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Data migration in progress. Please try again shortly.");
        }

        void markComplete() {
            migrationRequired.set(false);
        }

        void disable() {
            migrationRequired.set(false);
            AsyncPlayerPreLoginEvent.getHandlerList().unregister(this);
        }
    }
}
