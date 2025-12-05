package sh.harold.fulcrum.plugin.data;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.DocumentStore;
import sh.harold.fulcrum.common.data.impl.JsonDocumentStore;
import sh.harold.fulcrum.common.data.ledger.LedgerRepository;
import sh.harold.fulcrum.common.data.ledger.SqliteLedgerRepository;
import sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository;
import sh.harold.fulcrum.common.data.ledger.item.MySqlItemLedgerRepository;
import sh.harold.fulcrum.common.data.ledger.item.SqliteItemLedgerRepository;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class DataModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final Path storagePath;
    private ExecutorService executor;
    private DocumentStore store;
    private LedgerRepository ledgerRepository;
    private ItemLedgerRepository itemLedgerRepository;
    private DataApi dataApi;
    private FeatureConfigService configService;
    private DataConfig config;

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
            config = DataConfig.load(configService);
            executor = createExecutor(config);
            store = createStore(config.store(), executor);
            ledgerRepository = createLedger(config, executor);
            itemLedgerRepository = createItemLedger(config, executor);
            dataApi = DataApi.using(store, executor, ledgerRepository, itemLedgerRepository);
            plugin.getLogger().info(() -> "data store: " + config.store()
                + " at " + storagePath.toAbsolutePath()
                + " mysql=" + config.mysql().host() + ":" + config.mysql().port() + "/" + config.mysql().database()
                + " ledger=" + config.ledgerStore() + "@" + config.ledgerPath());
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
        if (itemLedgerRepository != null) {
            itemLedgerRepository.close();
        }
        if (configService != null) {
            configService.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    public Optional<DataApi> dataApi() {
        return Optional.ofNullable(dataApi);
    }

    public Optional<DocumentStore> documentStore() {
        return Optional.ofNullable(store);
    }

    private DocumentStore createStore(DataConfig.DataStore selection, ExecutorService executor) {
        return switch (selection) {
            case JSON -> new JsonDocumentStore(storagePath, executor);
            case NITRITE -> new sh.harold.fulcrum.common.data.impl.NitriteDocumentStore(storagePath.resolve("nitrite.db"), executor);
            case MYSQL -> new sh.harold.fulcrum.common.data.impl.MySqlDocumentStore(
                config.mysql().jdbcUrl(),
                config.mysql().username(),
                config.mysql().password(),
                config.mysql().maxPoolSize(),
                config.mysql().connectionTimeoutMillis(),
                plugin.getLogger(),
                executor
            );
        };
    }

    private ExecutorService createExecutor(DataConfig config) {
        int concurrency = switch (config.store()) {
            case MYSQL -> Math.max(2, config.mysql().maxPoolSize());
            default -> Math.max(4, Runtime.getRuntime().availableProcessors());
        };
        int queueSize = concurrency * 4;
        ThreadFactory threadFactory = Thread.ofVirtual().name("fulcrum-data-", 0).factory();
        return new ThreadPoolExecutor(
            concurrency,
            concurrency,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueSize),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private LedgerRepository createLedger(DataConfig config, ExecutorService executor) {
        if (config.ledgerStore() == DataConfig.LedgerStore.MYSQL) {
            return new sh.harold.fulcrum.common.data.ledger.MySqlLedgerRepository(
                config.mysql().jdbcUrl(),
                config.mysql().username(),
                config.mysql().password(),
                config.mysql().maxPoolSize(),
                config.mysql().connectionTimeoutMillis(),
                plugin.getLogger(),
                executor
            );
        }
        Path ledgerPath = plugin.getDataFolder().toPath().resolve(config.ledgerPath());
        String jdbcUrl = "jdbc:sqlite:" + ledgerPath.toAbsolutePath();
        return new SqliteLedgerRepository(jdbcUrl, executor, plugin.getLogger());
    }

    private ItemLedgerRepository createItemLedger(DataConfig config, ExecutorService executor) {
        if (config.ledgerStore() == DataConfig.LedgerStore.MYSQL) {
            return new MySqlItemLedgerRepository(
                config.mysql().jdbcUrl(),
                config.mysql().username(),
                config.mysql().password(),
                config.mysql().maxPoolSize(),
                config.mysql().connectionTimeoutMillis(),
                plugin.getLogger(),
                executor
            );
        }
        Path ledgerPath = plugin.getDataFolder().toPath().resolve(config.ledgerPath());
        String jdbcUrl = "jdbc:sqlite:" + ledgerPath.toAbsolutePath();
        return new SqliteItemLedgerRepository(jdbcUrl, executor, plugin.getLogger());
    }

    public DataConfig config() {
        return config;
    }
}
