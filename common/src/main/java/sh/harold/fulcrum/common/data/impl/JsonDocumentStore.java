package sh.harold.fulcrum.common.data.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.DocumentStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class JsonDocumentStore implements DocumentStore {

    private final Path basePath;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final Map<DocumentKey, ReadWriteLock> documentLocks = new ConcurrentHashMap<>();

    public JsonDocumentStore(Path basePath) {
        this(basePath, null);
    }

    public JsonDocumentStore(Path basePath, Executor executor) {
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.executor = executor;
        this.mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create storage directory: " + basePath, e);
        }
    }

    @Override
    public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
        return CompletableFuture.supplyAsync(() -> {
            Path documentPath = documentPath(key);
            ReadWriteLock lock = lockFor(key);
            lock.readLock().lock();
            try {
                if (!Files.exists(documentPath)) {
                    return new DocumentSnapshot(key, Map.of(), false);
                }
                Map<String, Object> data = mapper.readValue(documentPath.toFile(), Map.class);
                return new DocumentSnapshot(key, data, true);
            } catch (IOException e) {
                return new DocumentSnapshot(key, Map.of(), false);
            } finally {
                lock.readLock().unlock();
            }
        }, executor());
    }

    @Override
    public CompletionStage<Void> write(DocumentKey key, Map<String, Object> data) {
        Map<String, Object> copy = MapPath.deepCopy(data);
        return CompletableFuture.runAsync(() -> {
            ReadWriteLock lock = lockFor(key);
            lock.writeLock().lock();
            try {
                Path collectionPath = basePath.resolve(key.collection());
                Files.createDirectories(collectionPath);

                Path documentPath = documentPath(key);
                Path tempPath = documentPath.resolveSibling(key.id() + ".tmp");

                String json = mapper.writeValueAsString(copy);
                Files.writeString(tempPath, json, StandardCharsets.UTF_8);
                Files.move(tempPath, documentPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write document " + key, e);
            } finally {
                lock.writeLock().unlock();
            }
        }, executor());
    }

    @Override
    public CompletionStage<Boolean> delete(DocumentKey key) {
        return CompletableFuture.supplyAsync(() -> {
            Path documentPath = documentPath(key);
            ReadWriteLock lock = lockFor(key);
            lock.writeLock().lock();
            try {
                if (!Files.exists(documentPath)) {
                    return false;
                }
                Files.delete(documentPath);
                return true;
            } catch (IOException e) {
                return false;
            } finally {
                lock.writeLock().unlock();
            }
        }, executor());
    }

    @Override
    public CompletionStage<List<DocumentSnapshot>> all(String collection) {
        return CompletableFuture.supplyAsync(() -> {
            Path collectionPath = basePath.resolve(collection);
            try {
                if (!Files.exists(collectionPath)) {
                    return List.of();
                }
                List<DocumentSnapshot> snapshots = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(collectionPath, "*.json")) {
                    for (Path entry : stream) {
                        String fileName = entry.getFileName().toString();
                        if (!fileName.endsWith(".json")) {
                            continue;
                        }
                        String id = fileName.substring(0, fileName.length() - 5);
                        DocumentKey key = DocumentKey.of(collection, id);
                        ReadWriteLock lock = lockFor(key);
                        lock.readLock().lock();
                        try {
                            Map<String, Object> data = mapper.readValue(entry.toFile(), Map.class);
                            snapshots.add(new DocumentSnapshot(key, data, true));
                        } finally {
                            lock.readLock().unlock();
                        }
                    }
                }
                return List.copyOf(snapshots);
            } catch (IOException e) {
                return List.of();
            }
        }, executor());
    }

    @Override
    public CompletionStage<Long> count(String collection) {
        return CompletableFuture.supplyAsync(() -> {
            Path collectionPath = basePath.resolve(collection);
            if (!Files.exists(collectionPath)) {
                return 0L;
            }
            try {
                return Files.list(collectionPath)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .count();
            } catch (IOException e) {
                return 0L;
            }
        }, executor());
    }

    @Override
    public void close() {
        // executor is owned by DataApi
    }

    private Executor executor() {
        return executor != null ? executor : ForkJoinPool.commonPool();
    }

    private Path documentPath(DocumentKey key) {
        return basePath.resolve(key.collection()).resolve(key.id() + ".json");
    }

    private ReadWriteLock lockFor(DocumentKey key) {
        return documentLocks.computeIfAbsent(key, ignored -> new ReentrantReadWriteLock());
    }
}
