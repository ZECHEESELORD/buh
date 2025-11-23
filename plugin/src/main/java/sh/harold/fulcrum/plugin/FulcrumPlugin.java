package sh.harold.fulcrum.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.ModuleLoader;
import sh.harold.fulcrum.common.permissions.StaffService;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.playerdata.PlayerDataModule;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class FulcrumPlugin extends JavaPlugin {

    private ModuleLoader moduleLoader;
    private DataModule dataModule;
    private PlayerDataModule playerDataModule;
    private LuckPermsModule luckPermsModule;

    @Override
    public void onLoad() {
        createModules();
    }

    @Override
    public void onEnable() {
        if (moduleLoader == null) {
            createModules();
        }

        await(moduleLoader.enableAll(), "enable modules");
        getLogger().info("Fulcrum modules enabled");

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

    private void createModules() {
        dataModule = new DataModule(dataPath());
        playerDataModule = new PlayerDataModule(this, dataModule);
        luckPermsModule = new LuckPermsModule(this);
        moduleLoader = new ModuleLoader(List.of(dataModule, playerDataModule, luckPermsModule));
    }

    private Path dataPath() {
        return getDataFolder().toPath().resolve("data");
    }

    private void await(CompletionStage<Void> stage, String action) {
        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException runtimeException) {
            getLogger().log(Level.SEVERE, "Failed to " + action, runtimeException);
            throw runtimeException;
        }
    }
}
