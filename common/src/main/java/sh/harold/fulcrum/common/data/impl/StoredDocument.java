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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

final class StoredDocument implements Document {

    private static final ConcurrentHashMap<DocumentKey, Object> KEY_LOCKS = new ConcurrentHashMap<>();

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
        return CompletableFuture.runAsync(() -> {
            synchronized (keyLock()) {
                DocumentSnapshot snapshot = store.read(key).toCompletableFuture().join();
                Map<String, Object> working = MapPath.deepCopy(snapshot.data());
                MapPath.write(working, path, value);
                store.write(key, working).toCompletableFuture().join();
                synchronized (lock) {
                    data = MapPath.deepCopy(working);
                    exists = true;
                }
            }
        }, executor());
    }

    @Override
    public CompletionStage<Void> remove(String path) {
        Objects.requireNonNull(path, "path");
        return CompletableFuture.runAsync(() -> {
            synchronized (keyLock()) {
                DocumentSnapshot snapshot = store.read(key).toCompletableFuture().join();
                Map<String, Object> working = MapPath.deepCopy(snapshot.data());
                MapPath.remove(working, path);
                store.write(key, working).toCompletableFuture().join();
                synchronized (lock) {
                    data = MapPath.deepCopy(working);
                    exists = true;
                }
            }
        }, executor());
    }

    @Override
    public CompletionStage<Void> overwrite(Map<String, Object> data) {
        Map<String, Object> snapshot = MapPath.deepCopy(data);
        return CompletableFuture.runAsync(() -> {
            synchronized (keyLock()) {
                store.write(key, snapshot).toCompletableFuture().join();
                synchronized (lock) {
                    this.data = MapPath.deepCopy(snapshot);
                    this.exists = true;
                }
            }
        }, executor());
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

    private Object keyLock() {
        return KEY_LOCKS.computeIfAbsent(key, ignored -> new Object());
    }

    private Executor executor() {
        return executor != null ? executor : ForkJoinPool.commonPool();
    }
}
