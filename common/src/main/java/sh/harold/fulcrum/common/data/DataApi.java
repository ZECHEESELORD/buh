package sh.harold.fulcrum.common.data;

import sh.harold.fulcrum.common.data.impl.DefaultDataApi;

import java.util.Objects;
import java.util.concurrent.Executor;

public interface DataApi extends AutoCloseable {

    static DataApi using(DocumentStore store) {
        return new DefaultDataApi(store, null);
    }

    static DataApi using(DocumentStore store, Executor executor) {
        return new DefaultDataApi(store, executor);
    }

    DocumentCollection collection(String name);

    @Override
    void close();
}
