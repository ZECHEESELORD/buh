package sh.harold.fulcrum.common.data.impl;

import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.DocumentStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

final class StoredDocument implements Document {

    private final DocumentKey key;
    private final DocumentStore store;
    private final Executor executor;
    private final Object lock = new Object();
    private Map<String, Object> data;
    private boolean exists;

    StoredDocument(DocumentSnapshot snapshot, DocumentStore store, Executor executor) {
        this.key = snapshot.key();
        this.store = Objects.requireNonNull(store, "store");
        this.executor = executor;
        this.data = MapPath.deepCopy(snapshot.data());
        this.exists = snapshot.exists();
    }

    @Override
    public DocumentKey key() {
        return key;
    }

    @Override
    public boolean exists() {
        synchronized (lock) {
            return exists;
        }
    }

    @Override
    public <T> Optional<T> get(String path, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value;
        synchronized (lock) {
            value = MapPath.read(data, path);
        }
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    @Override
    public CompletionStage<Void> set(String path, Object value) {
        Objects.requireNonNull(path, "path");
        Map<String, Object> snapshot;
        synchronized (lock) {
            Map<String, Object> working = MapPath.deepCopy(data);
            MapPath.write(working, path, value);
            data = working;
            exists = true;
            snapshot = MapPath.deepCopy(data);
        }
        return store.write(key, snapshot);
    }

    @Override
    public CompletionStage<Void> remove(String path) {
        Objects.requireNonNull(path, "path");
        Map<String, Object> snapshot;
        synchronized (lock) {
            Map<String, Object> working = MapPath.deepCopy(data);
            MapPath.remove(working, path);
            data = working;
            snapshot = MapPath.deepCopy(data);
        }
        return store.write(key, snapshot);
    }

    @Override
    public CompletionStage<Void> overwrite(Map<String, Object> data) {
        Map<String, Object> snapshot = MapPath.deepCopy(data);
        synchronized (lock) {
            this.data = MapPath.deepCopy(data);
            this.exists = true;
        }
        return store.write(key, snapshot);
    }

    @Override
    public Map<String, Object> snapshot() {
        synchronized (lock) {
            return MapPath.deepCopy(data);
        }
    }

    @Override
    public CompletionStage<Map<String, Object>> snapshotAsync() {
        Executor targetExecutor = executor != null ? executor : Runnable::run;
        return CompletableFuture.supplyAsync(this::snapshot, targetExecutor);
    }
}
