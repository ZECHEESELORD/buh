package sh.harold.fulcrum.common.data.impl;

import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.DocumentStore;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import java.util.WeakHashMap;

final class StoredDocument implements Document {

    private static final Map<DocumentKey, Object> KEY_LOCKS = Collections.synchronizedMap(new WeakHashMap<>());

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
        return mutate(working -> {
            MapPath.write(working, path, value);
            return working;
        });
    }

    @Override
    public CompletionStage<Void> remove(String path) {
        Objects.requireNonNull(path, "path");
        return mutate(working -> {
            MapPath.remove(working, path);
            return working;
        });
    }

    @Override
    public CompletionStage<Void> overwrite(Map<String, Object> data) {
        Map<String, Object> snapshot = MapPath.deepCopy(data);
        return mutate(ignored -> snapshot);
    }

    @Override
    public CompletionStage<Void> update(UnaryOperator<Map<String, Object>> mutator) {
        Objects.requireNonNull(mutator, "mutator");
        return mutate(current -> Objects.requireNonNull(mutator.apply(current), "mutation result"));
    }

    @Override
    public CompletionStage<Void> patch(Map<String, Object> setValues, Iterable<String> removePaths) {
        Map<String, Object> copy = MapPath.deepCopy(setValues);
        java.util.List<String> removals = new java.util.ArrayList<>();
        if (removePaths != null) {
            removePaths.forEach(removals::add);
        }
        return store.patch(key, copy, removals)
            .thenRun(() -> {
                synchronized (lock) {
                    Map<String, Object> working = MapPath.deepCopy(data);
                    copy.forEach((path, value) -> MapPath.write(working, path, value));
                    for (String path : removals) {
                        MapPath.remove(working, path);
                    }
                    this.data = MapPath.deepCopy(working);
                    this.exists = true;
                }
            });
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

    private CompletionStage<Void> mutate(UnaryOperator<Map<String, Object>> mutation) {
        Object keyLock = keyLock();
        return store.update(key, current -> {
            synchronized (keyLock) {
                Map<String, Object> working = MapPath.deepCopy(current);
                Map<String, Object> mutated = Objects.requireNonNull(mutation.apply(working), "mutation result");
                return MapPath.deepCopy(mutated);
            }
        }).thenAccept(snapshot -> {
            synchronized (lock) {
                data = MapPath.deepCopy(snapshot.data());
                exists = snapshot.exists();
            }
        });
    }

    private Object keyLock() {
        synchronized (KEY_LOCKS) {
            return KEY_LOCKS.computeIfAbsent(key, ignored -> new Object());
        }
    }

}
