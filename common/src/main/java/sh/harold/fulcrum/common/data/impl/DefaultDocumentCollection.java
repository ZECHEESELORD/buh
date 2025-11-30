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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

final class DefaultDocumentCollection implements DocumentCollection {

    private final String name;
    private final DocumentStore store;
    private final Executor executor;
    private final sh.harold.fulcrum.common.data.metrics.DataMetrics metrics;

    DefaultDocumentCollection(String name, DocumentStore store, Executor executor, sh.harold.fulcrum.common.data.metrics.DataMetrics metrics) {
        this.name = Objects.requireNonNull(name, "name");
        this.store = Objects.requireNonNull(store, "store");
        this.executor = executor;
        this.metrics = metrics;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CompletionStage<Document> load(String id) {
        long started = System.nanoTime();
        DocumentKey key = DocumentKey.of(name, id);
        return store.read(key)
            .<Document>thenApply(snapshot -> new StoredDocument(snapshot, store, executor))
            .whenComplete((ignored, throwable) -> record("load", started, throwable));
    }

    @Override
    public CompletionStage<Document> create(String id, Map<String, Object> data) {
        long started = System.nanoTime();
        DocumentKey key = DocumentKey.of(name, id);
        Map<String, Object> copy = MapPath.deepCopy(data);
        return store.update(key, ignored -> copy)
            .<Document>thenApply(snapshot -> new StoredDocument(snapshot, store, executor))
            .whenComplete((ignored, throwable) -> record("create", started, throwable));
    }

    @Override
    public CompletionStage<Boolean> delete(String id) {
        long started = System.nanoTime();
        DocumentKey key = DocumentKey.of(name, id);
        return store.delete(key).whenComplete((ignored, throwable) -> record("delete", started, throwable));
    }

    @Override
    public CompletionStage<List<Document>> all() {
        long started = System.nanoTime();
        return store.all(name)
            .thenApply(list -> list.stream()
                .map(snapshot -> (Document) new StoredDocument(snapshot, store, executor))
                .toList())
            .whenComplete((ignored, throwable) -> record("all", started, throwable));
    }

    @Override
    public CompletionStage<Long> count() {
        long started = System.nanoTime();
        return store.count(name).whenComplete((ignored, throwable) -> record("count", started, throwable));
    }

    @Override
    public CompletionStage<Map<String, Document>> loadAll(java.util.Collection<String> ids) {
        Objects.requireNonNull(ids, "ids");
        long started = System.nanoTime();
        List<String> idList = ids.stream().toList();
        List<CompletableFuture<Document>> futures = idList.stream()
            .map(this::load)
            .map(CompletionStage::toCompletableFuture)
            .toList();
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return all.thenApply(ignored -> {
            Map<String, Document> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < idList.size(); i++) {
                map.put(idList.get(i), futures.get(i).join());
            }
            return map;
        }).whenComplete((ignored, throwable) -> record("loadAll", started, throwable));
    }

    @Override
    public CompletionStage<Void> updateAll(Map<String, UnaryOperator<Map<String, Object>>> updates) {
        Objects.requireNonNull(updates, "updates");
        long started = System.nanoTime();
        List<CompletableFuture<DocumentSnapshot>> futures = updates.entrySet().stream()
            .map(entry -> store.update(DocumentKey.of(name, entry.getKey()), entry.getValue()).toCompletableFuture())
            .toList();
        CompletableFuture<Void> combined = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return combined.whenComplete((ignored, throwable) -> record("updateAll", started, throwable));
    }

    private void record(String operation, long startedNanos, Throwable throwable) {
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        metrics.record(operation, name, elapsedMillis, throwable == null);
    }
}
