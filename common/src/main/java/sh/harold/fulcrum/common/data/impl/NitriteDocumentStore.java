package sh.harold.fulcrum.common.data.impl;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.common.WriteResult;
import org.dizitart.no2.filters.FluentFilter;
import org.dizitart.no2.mvstore.MVStoreModule;
import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.DocumentStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public final class NitriteDocumentStore implements DocumentStore {

    private static final String ID_FIELD = "id";
    private static final String DATA_FIELD = "data";

    private final Path databasePath;
    private final Executor executor;
    private final Nitrite database;

    public NitriteDocumentStore(Path databasePath, Executor executor) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath");
        this.executor = executor;
        ensureParentDirectory(databasePath);
        MVStoreModule mvStore = MVStoreModule.withConfig()
            .filePath(databasePath.toFile())
            .build();
        this.database = Nitrite.builder()
            .loadModule(mvStore)
            .openOrCreate();
    }

    @Override
    public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        return CompletableFuture.supplyAsync(() -> {
            NitriteCollection collection = collection(key.collection());
            Document stored = collection.find(FluentFilter.where(ID_FIELD).eq(key.id())).firstOrNull();
            if (stored == null) {
                return new DocumentSnapshot(key, Map.of(), false);
            }
            Map<String, Object> data = toMap(fromNitriteValue(stored.get(DATA_FIELD)));
            return new DocumentSnapshot(key, data, true);
        }, executor());
    }

    @Override
    public CompletionStage<Void> write(DocumentKey key, Map<String, Object> data) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        Map<String, Object> snapshot = MapPath.deepCopy(data);
        return CompletableFuture.runAsync(() -> {
            NitriteCollection collection = collection(key.collection());
            Document document = createDocument(key.id(), snapshot);
            collection.update(FluentFilter.where(ID_FIELD).eq(key.id()), document, UpdateOptions.updateOptions(true));
        }, executor());
    }

    @Override
    public CompletionStage<Boolean> delete(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        return CompletableFuture.supplyAsync(() -> {
            NitriteCollection collection = collection(key.collection());
            WriteResult result = collection.remove(FluentFilter.where(ID_FIELD).eq(key.id()));
            return result.getAffectedCount() > 0;
        }, executor());
    }

    @Override
    public CompletionStage<List<DocumentSnapshot>> all(String collection) {
        Objects.requireNonNull(collection, "collection");
        return CompletableFuture.supplyAsync(() -> {
            NitriteCollection nitriteCollection = collection(collection);
            return nitriteCollection.find()
                .toList()
                .stream()
                .map(document -> {
                    Map<String, Object> data = toMap(fromNitriteValue(document.get(DATA_FIELD)));
                    return new DocumentSnapshot(
                        DocumentKey.of(collection, document.get(ID_FIELD, String.class)),
                        data,
                        true
                    );
                })
                .collect(Collectors.toUnmodifiableList());
        }, executor());
    }

    @Override
    public CompletionStage<Long> count(String collection) {
        Objects.requireNonNull(collection, "collection");
        return CompletableFuture.supplyAsync(() -> collection(collection).size(), executor());
    }

    @Override
    public CompletionStage<DocumentSnapshot> update(DocumentKey key, java.util.function.UnaryOperator<Map<String, Object>> mutator) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mutator, "mutator");
        return CompletableFuture.supplyAsync(() -> {
            NitriteCollection collection = collection(key.collection());
            Document existing = collection.find(FluentFilter.where(ID_FIELD).eq(key.id())).firstOrNull();
            Map<String, Object> current = existing == null
                ? Map.of()
                : toMap(fromNitriteValue(existing.get(DATA_FIELD)));

            Map<String, Object> working = MapPath.deepCopy(current);
            Map<String, Object> mutated = mutator.apply(working);
            if (mutated == null) {
                throw new IllegalStateException("Mutator returned null for " + key);
            }
            Map<String, Object> normalized = MapPath.deepCopy(mutated);
            Document document = createDocument(key.id(), normalized);
            collection.update(FluentFilter.where(ID_FIELD).eq(key.id()), document, UpdateOptions.updateOptions(true));
            return new DocumentSnapshot(key, normalized, true);
        }, executor());
    }

    public CompletionStage<Set<String>> collections() {
        return CompletableFuture.supplyAsync(() -> Set.copyOf(database.listCollectionNames()), executor());
    }

    @Override
    public void close() {
        database.close();
    }

    private NitriteCollection collection(String name) {
        return database.getCollection(name);
    }

    private Document createDocument(String id, Map<String, Object> data) {
        return Document.createDocument(ID_FIELD, id)
            .put(DATA_FIELD, toNitriteValue(data));
    }

    private Object fromNitriteValue(Object value) {
        if (value instanceof Document document) {
            Map<String, Object> map = new LinkedHashMap<>();
            document.forEach(pair -> map.put(pair.getFirst(), fromNitriteValue(pair.getSecond())));
            return map;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(fromNitriteValue(item));
            }
            return normalized;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, inner) -> copy.put(String.valueOf(key), fromNitriteValue(inner)));
            return copy;
        }
        return value;
    }

    private Map<String, Object> toMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, inner) -> copy.put(String.valueOf(key), fromNitriteValue(inner)));
        return copy;
    }

    private Object toNitriteValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Document nested = Document.createDocument();
            map.forEach((key, inner) -> nested.put(String.valueOf(key), toNitriteValue(inner)));
            return nested;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .map(this::toNitriteValue)
                .toList();
        }
        return value;
    }

    private Executor executor() {
        return executor != null ? executor : ForkJoinPool.commonPool();
    }

    private void ensureParentDirectory(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create storage directory " + parent, exception);
        }
    }
}
