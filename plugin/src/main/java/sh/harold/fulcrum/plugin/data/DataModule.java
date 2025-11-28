package sh.harold.fulcrum.plugin.data;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.DocumentStore;
import sh.harold.fulcrum.common.data.impl.JsonDocumentStore;
import sh.harold.fulcrum.common.data.ledger.LedgerRepository;
import sh.harold.fulcrum.common.data.ledger.SqliteLedgerRepository;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DataModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final Path storagePath;
    private ExecutorService executor;
    private DocumentStore store;
    private LedgerRepository ledgerRepository;
    private DataApi dataApi;
    private FeatureConfigService configService;

    public DataModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storagePath = plugin.getDataFolder().toPath().resolve("data");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("data"), ModuleCategory.API);
    }

    @Override
    public CompletionStage<Void> enable() {
        return CompletableFuture.runAsync(() -> {
            configService = new FeatureConfigService(plugin);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            DataConfig config = DataConfig.load(configService);
            store = createStore(config.store(), executor);
            ledgerRepository = createLedger(config, executor);
            dataApi = DataApi.using(store, executor, ledgerRepository);
        });
    }

    @Override
    public CompletionStage<Void> disable() {
        if (dataApi != null) {
            dataApi.close();
        }
        if (executor != null) {
            executor.close();
        }
        if (ledgerRepository != null) {
            ledgerRepository.close();
        }
        if (configService != null) {
            configService.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    public Optional<DataApi> dataApi() {
        return Optional.ofNullable(dataApi);
    }

    private DocumentStore createStore(DataConfig.DataStore selection, ExecutorService executor) {
        return switch (selection) {
            case JSON -> new JsonDocumentStore(storagePath, executor);
            case NITRITE -> new sh.harold.fulcrum.common.data.impl.NitriteDocumentStore(storagePath.resolve("nitrite.db"), executor);
        };
    }

    private LedgerRepository createLedger(DataConfig config, ExecutorService executor) {
        Path ledgerPath = plugin.getDataFolder().toPath().resolve(config.ledgerPath());
        String jdbcUrl = "jdbc:sqlite:" + ledgerPath.toAbsolutePath();
        return new SqliteLedgerRepository(jdbcUrl, executor, plugin.getLogger());
    }
}
