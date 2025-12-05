package sh.harold.fulcrum.common.data;

import sh.harold.fulcrum.common.data.impl.DefaultDataApi;

import java.util.Objects;
import java.util.concurrent.Executor;

public interface DataApi extends AutoCloseable {

    static DataApi using(DocumentStore store) {
        return new DefaultDataApi(store, null, null);
    }

    static DataApi using(DocumentStore store, Executor executor) {
        return new DefaultDataApi(store, executor, null);
    }

    static DataApi using(
        DocumentStore store,
        Executor executor,
        sh.harold.fulcrum.common.data.ledger.LedgerRepository ledgerRepository
    ) {
        return new DefaultDataApi(store, executor, ledgerRepository, null);
    }

    static DataApi using(
        DocumentStore store,
        Executor executor,
        sh.harold.fulcrum.common.data.ledger.LedgerRepository ledgerRepository,
        sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository itemLedgerRepository
    ) {
        return new DefaultDataApi(store, executor, ledgerRepository, itemLedgerRepository);
    }

    DocumentCollection collection(String name);

    java.util.Optional<sh.harold.fulcrum.common.data.ledger.LedgerRepository> ledger();

    default java.util.Optional<sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository> itemLedger() {
        return java.util.Optional.empty();
    }

    default sh.harold.fulcrum.common.data.metrics.DataMetrics metrics() {
        return sh.harold.fulcrum.common.data.metrics.DataMetrics.noop();
    }

    @Override
    void close();
}
