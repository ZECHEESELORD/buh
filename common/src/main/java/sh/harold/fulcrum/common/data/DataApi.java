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

    static DataApi using(DocumentStore store, Executor executor, sh.harold.fulcrum.common.data.ledger.LedgerRepository ledgerRepository) {
        return new DefaultDataApi(store, executor, ledgerRepository);
    }

    DocumentCollection collection(String name);

    java.util.Optional<sh.harold.fulcrum.common.data.ledger.LedgerRepository> ledger();

    default sh.harold.fulcrum.common.data.metrics.DataMetrics metrics() {
        return sh.harold.fulcrum.common.data.metrics.DataMetrics.noop();
    }

    @Override
    void close();
}
