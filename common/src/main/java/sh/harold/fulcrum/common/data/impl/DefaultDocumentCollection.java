package sh.harold.fulcrum.common.data.impl;

import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.DocumentStore;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

final class DefaultDocumentCollection implements DocumentCollection {

    private final String name;
    private final DocumentStore store;
    private final Executor executor;

    DefaultDocumentCollection(String name, DocumentStore store, Executor executor) {
        this.name = Objects.requireNonNull(name, "name");
        this.store = Objects.requireNonNull(store, "store");
        this.executor = executor;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CompletionStage<Document> load(String id) {
        DocumentKey key = DocumentKey.of(name, id);
        return store.read(key).thenApply(snapshot -> new StoredDocument(snapshot, store, executor));
    }

    @Override
    public CompletionStage<Document> create(String id, Map<String, Object> data) {
        DocumentKey key = DocumentKey.of(name, id);
        Map<String, Object> copy = MapPath.deepCopy(data);
        return store.write(key, copy)
            .thenCompose(ignored -> store.read(key))
            .thenApply(snapshot -> new StoredDocument(snapshot, store, executor));
    }

    @Override
    public CompletionStage<Boolean> delete(String id) {
        DocumentKey key = DocumentKey.of(name, id);
        return store.delete(key);
    }

    @Override
    public CompletionStage<List<Document>> all() {
        return store.all(name)
            .thenApply(list -> list.stream()
                .map(snapshot -> (Document) new StoredDocument(snapshot, store, executor))
                .toList());
    }

    @Override
    public CompletionStage<Long> count() {
        return store.count(name);
    }
}
