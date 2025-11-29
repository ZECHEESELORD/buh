package sh.harold.fulcrum.plugin.datamigrator;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.impl.JsonDocumentStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DataMigratorService implements AutoCloseable {

    private final Path storagePath;
    private final Logger logger;
    private final ExecutorService executor;
    private final JsonDocumentStore legacyStore;
    private final DocumentCollection players;

    DataMigratorService(Path storagePath, Logger logger, DataApi dataApi) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.legacyStore = new JsonDocumentStore(storagePath, executor);
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
    }

    CompletionStage<Boolean> migrateOnJoin(UUID playerId) {
        DocumentKey key = DocumentKey.of("players", playerId.toString());
        return legacyStore.read(key)
            .thenCompose(snapshot -> {
                if (!snapshot.exists()) {
                    return CompletableFuture.completedFuture(false);
                }
                return players.load(playerId.toString())
                    .thenCompose(current -> mergeSnapshot(current, snapshot));
            })
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to migrate legacy data for " + playerId, throwable);
                return false;
            });
    }

    CompletionStage<DataMigrationReport> migrateAllPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            List<Path> legacyFiles = listLegacyFiles();
            int migrated = 0;
            int unchanged = 0;
            int missing = 0;

            for (Path file : legacyFiles) {
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith(".json")) {
                    continue;
                }
                String id = fileName.substring(0, fileName.length() - 5);
                DocumentKey key = DocumentKey.of("players", id);
                DocumentSnapshot snapshot = legacyStore.read(key).toCompletableFuture().join();
                if (!snapshot.exists()) {
                    missing++;
                    continue;
                }
                Document current = players.load(id).toCompletableFuture().join();
                boolean updated = mergeSnapshot(current, snapshot).toCompletableFuture().join();
                if (updated) {
                    migrated++;
                } else {
                    unchanged++;
                }
            }

            return new DataMigrationReport(legacyFiles.size(), migrated, unchanged, missing);
        }, executor);
    }

    private List<Path> listLegacyFiles() {
        Path playersDir = storagePath.resolve("players");
        if (!Files.isDirectory(playersDir)) {
            return List.of();
        }
        try {
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(playersDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(files::add);
            }
            return files;
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Failed to list legacy JSON files", exception);
            return List.of();
        }
    }

    private CompletionStage<Boolean> mergeSnapshot(Document current, DocumentSnapshot snapshot) {
        Map<String, Object> legacyData = snapshot.copy();
        Map<String, Object> currentSnapshot = current.snapshot();

        Map<String, Object> merged = new LinkedHashMap<>(legacyData);
        merged.putAll(currentSnapshot);

        if (merged.equals(currentSnapshot)) {
            return CompletableFuture.completedFuture(false);
        }
        return current.overwrite(merged).thenApply(ignored -> true);
    }

    @Override
    public void close() {
        try {
            legacyStore.close();
        } catch (Exception exception) {
            logger.log(Level.FINE, "Failed to close legacy JSON store", exception);
        }
        executor.close();
    }

    private Path legacyFile(UUID playerId) {
        return storagePath.resolve("players").resolve(playerId.toString() + ".json");
    }

    record DataMigrationReport(int legacyFiles, int migrated, int unchanged, int missing) {
    }
}
