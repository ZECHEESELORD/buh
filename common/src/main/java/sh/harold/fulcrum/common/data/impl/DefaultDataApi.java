package sh.harold.fulcrum.common.data.impl;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.data.DocumentStore;
import sh.harold.fulcrum.common.data.ledger.LedgerRepository;
import sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository;
import sh.harold.fulcrum.common.data.metrics.DataMetrics;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Optional;

public final class DefaultDataApi implements DataApi {

    private final DocumentStore store;
    private final Executor executor;
    private final Map<String, DocumentCollection> collections = new ConcurrentHashMap<>();
    private final ExecutorService ownedExecutor;
    private final LedgerRepository ledgerRepository;
    private final ItemLedgerRepository itemLedgerRepository;
    private final DataMetrics metrics;

    public DefaultDataApi(DocumentStore store, Executor executor, LedgerRepository ledgerRepository) {
        this(store, executor, ledgerRepository, null);
    }

    public DefaultDataApi(DocumentStore store, Executor executor, LedgerRepository ledgerRepository, ItemLedgerRepository itemLedgerRepository) {
        this.store = Objects.requireNonNull(store, "store");
        if (executor == null) {
            this.ownedExecutor = Executors.newVirtualThreadPerTaskExecutor();
            this.executor = ownedExecutor;
        } else {
            this.executor = executor;
            this.ownedExecutor = null;
        }
        this.ledgerRepository = ledgerRepository;
        this.itemLedgerRepository = itemLedgerRepository;
        this.metrics = new DataMetrics();
    }

    @Override
    public DocumentCollection collection(String name) {
        Objects.requireNonNull(name, "name");
        return collections.computeIfAbsent(name, value -> new DefaultDocumentCollection(value, store, executor, metrics));
    }

    @Override
    public Optional<LedgerRepository> ledger() {
        return Optional.ofNullable(ledgerRepository);
    }

    @Override
    public Optional<ItemLedgerRepository> itemLedger() {
        return Optional.ofNullable(itemLedgerRepository);
    }

    @Override
    public DataMetrics metrics() {
        return metrics;
    }

    @Override
    public void close() {
        store.close();
        if (ownedExecutor != null) {
            ownedExecutor.close();
        }
        if (ledgerRepository != null) {
            ledgerRepository.close();
        }
        if (itemLedgerRepository != null) {
            itemLedgerRepository.close();
        }
    }
}
