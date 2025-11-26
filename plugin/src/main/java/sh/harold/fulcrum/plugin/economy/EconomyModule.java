package sh.harold.fulcrum.plugin.economy;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class EconomyModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;
    private EconomyService economyService;

    public EconomyModule(JavaPlugin plugin, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("economy"), Set.of(ModuleId.of("data")), ModuleCategory.ECONOMY);
    }

    @Override
    public CompletionStage<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        economyService = new EconomyService(dataApi, plugin.getLogger());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (economyService != null) {
            economyService.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    public Optional<EconomyService> economyService() {
        return Optional.ofNullable(economyService);
    }
}
