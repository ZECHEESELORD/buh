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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JsonDocumentStore implements DocumentStore {

    private static final long SLOW_OP_THRESHOLD_MS = 250;

    private final Path basePath;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final Logger logger;
    private final Map<DocumentKey, ReadWriteLock> documentLocks = new ConcurrentHashMap<>();

    public JsonDocumentStore(Path basePath) {
        this(basePath, null);
    }

    public JsonDocumentStore(Path basePath, Executor executor) {
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.executor = executor;
        this.logger = Logger.getLogger(JsonDocumentStore.class.getName());
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
            long startedAt = System.nanoTime();
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
                logger.log(Level.WARNING, "[data] read failed for " + key.collection() + "/" + key.id(), e);
                throw new IllegalStateException("Failed to read document " + key, e);
            } finally {
                lock.readLock().unlock();
                logIfSlow("read", key, startedAt);
            }
        }, executor());
    }

    @Override
    public CompletionStage<Void> write(DocumentKey key, Map<String, Object> data) {
        Map<String, Object> copy = MapPath.deepCopy(data);
        return CompletableFuture.runAsync(() -> {
            long startedAt = System.nanoTime();
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
                logger.log(Level.WARNING, "[data] write failed for " + key.collection() + "/" + key.id(), e);
                throw new IllegalStateException("Failed to write document " + key, e);
            } finally {
                lock.writeLock().unlock();
                logIfSlow("write", key, startedAt);
            }
        }, executor());
    }

    @Override
    public CompletionStage<DocumentSnapshot> update(DocumentKey key, java.util.function.UnaryOperator<Map<String, Object>> mutator) {
        Objects.requireNonNull(mutator, "mutator");
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            ReadWriteLock lock = lockFor(key);
            lock.writeLock().lock();
            try {
                Path collectionPath = basePath.resolve(key.collection());
                Files.createDirectories(collectionPath);

                Path documentPath = documentPath(key);
                Map<String, Object> current = Files.exists(documentPath)
                    ? mapper.readValue(documentPath.toFile(), Map.class)
                    : Map.of();
                Map<String, Object> working = MapPath.deepCopy(current);
                Map<String, Object> mutated = mutator.apply(working);
                if (mutated == null) {
                    throw new IllegalStateException("Mutator returned null for " + key);
                }
                Map<String, Object> normalized = MapPath.deepCopy(mutated);

                Path tempPath = documentPath.resolveSibling(key.id() + ".tmp");
                String json = mapper.writeValueAsString(normalized);
                Files.writeString(tempPath, json, StandardCharsets.UTF_8);
                Files.move(tempPath, documentPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

                return new DocumentSnapshot(key, normalized, true);
            } catch (IOException e) {
                logger.log(Level.WARNING, "[data] update failed for " + key.collection() + "/" + key.id(), e);
                throw new IllegalStateException("Failed to update document " + key, e);
            } finally {
                lock.writeLock().unlock();
                logIfSlow("update", key, startedAt);
            }
        }, executor());
    }

    @Override
    public CompletionStage<Boolean> delete(DocumentKey key) {
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
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
                logger.log(Level.WARNING, "[data] delete failed for " + key.collection() + "/" + key.id(), e);
                throw new IllegalStateException("Failed to delete document " + key, e);
            } finally {
                lock.writeLock().unlock();
                logIfSlow("delete", key, startedAt);
            }
        }, executor());
    }

    @Override
    public CompletionStage<List<DocumentSnapshot>> all(String collection) {
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
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
                logger.log(Level.WARNING, "[data] list failed for collection " + collection, e);
                throw new IllegalStateException("Failed to list documents for " + collection, e);
            } finally {
                logIfSlow("all", DocumentKey.of(collection, "*"), startedAt);
            }
        }, executor());
    }

    @Override
    public CompletionStage<Long> count(String collection) {
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            Path collectionPath = basePath.resolve(collection);
            if (!Files.exists(collectionPath)) {
                return 0L;
            }
            try {
                return Files.list(collectionPath)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .count();
            } catch (IOException e) {
                logger.log(Level.WARNING, "[data] count failed for collection " + collection, e);
                throw new IllegalStateException("Failed to count documents for " + collection, e);
            } finally {
                logIfSlow("count", DocumentKey.of(collection, "*"), startedAt);
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

    private void logIfSlow(String operation, DocumentKey key, long startedAtNanos) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        if (elapsedMillis > SLOW_OP_THRESHOLD_MS) {
            logger.info(() -> "[data] slow " + operation + " for " + key.collection() + "/" + key.id() + " in " + elapsedMillis + "ms");
        }
    }
}
