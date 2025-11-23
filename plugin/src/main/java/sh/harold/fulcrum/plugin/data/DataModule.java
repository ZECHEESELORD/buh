package sh.harold.fulcrum.plugin.data;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.DocumentStore;
import sh.harold.fulcrum.common.data.impl.JsonDocumentStore;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DataModule implements FulcrumModule {

    private final Path storagePath;
    private ExecutorService executor;
    private DocumentStore store;
    private DataApi dataApi;

    public DataModule(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("data"));
    }

    @Override
    public CompletionStage<Void> enable() {
        return CompletableFuture.runAsync(() -> {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            store = new JsonDocumentStore(storagePath, executor);
            dataApi = DataApi.using(store, executor);
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
        return CompletableFuture.completedFuture(null);
    }

    public Optional<DataApi> dataApi() {
        return Optional.ofNullable(dataApi);
    }
}
