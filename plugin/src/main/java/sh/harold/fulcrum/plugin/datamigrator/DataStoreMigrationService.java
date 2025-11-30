package sh.harold.fulcrum.plugin.datamigrator;

import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.DocumentStore;
import sh.harold.fulcrum.common.data.impl.NitriteDocumentStore;
import sh.harold.fulcrum.common.data.impl.MySqlDocumentStore;
import sh.harold.fulcrum.common.data.ledger.LedgerEntry;
import sh.harold.fulcrum.common.data.ledger.LedgerRepository;
import sh.harold.fulcrum.common.data.ledger.MySqlLedgerRepository;
import sh.harold.fulcrum.plugin.data.DataConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DataStoreMigrationService implements AutoCloseable {

    private final Path dataPath;
    private final DataConfig config;
    private final Logger logger;
    private final ExecutorService executor;

    DataStoreMigrationService(Path dataPath, DataConfig config, Logger logger) {
        this.dataPath = Objects.requireNonNull(dataPath, "dataPath");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    CompletionStage<DataStoreMigrationReport> migrateToMySql() {
        return CompletableFuture.supplyAsync(() -> {
            Path nitritePath = dataPath.getParent().resolve("data").resolve("nitrite.db");
            DocumentStore sourceStore = new NitriteDocumentStore(nitritePath, executor);
            DocumentStore targetStore = new MySqlDocumentStore(
                config.mysql().jdbcUrl(),
                config.mysql().username(),
                config.mysql().password(),
                config.mysql().maxPoolSize(),
                config.mysql().connectionTimeoutMillis(),
                logger,
                executor
            );
            LedgerRepository targetLedger = new MySqlLedgerRepository(
                config.mysql().jdbcUrl(),
                config.mysql().username(),
                config.mysql().password(),
                config.mysql().maxPoolSize(),
                config.mysql().connectionTimeoutMillis(),
                logger,
                executor
            );

            try (sourceStore; targetStore; targetLedger) {
                int collectionsMigrated = 0;
                int documentsMigrated = 0;
                int documentsSkipped = 0;

                for (String collection : determineCollections(sourceStore)) {
                    List<DocumentSnapshot> snapshots = sourceStore.all(collection).toCompletableFuture().join();
                    for (DocumentSnapshot snapshot : snapshots) {
                        DocumentKey key = snapshot.key();
                        DocumentSnapshot existing = targetStore.read(key).toCompletableFuture().join();
                        if (existing.exists()) {
                            documentsSkipped++;
                            logger.fine(() -> "[migrate] skipping existing document " + key.collection() + "/" + key.id());
                            continue;
                        }
                        targetStore.write(key, snapshot.copy()).toCompletableFuture().join();
                        documentsMigrated++;
                    }
                    collectionsMigrated++;
                    int finalDocumentsMigrated = documentsMigrated;
                    int finalDocumentsSkipped = documentsSkipped;
                    logger.info(() -> "[migrate] migrated collection " + collection + " (" + snapshots.size() + " docs; migrated=" + finalDocumentsMigrated + ", skipped=" + finalDocumentsSkipped + ")");
                }

                int ledgerMigrated = migrateLedger(targetLedger);

                return new DataStoreMigrationReport(collectionsMigrated, documentsMigrated, documentsSkipped, ledgerMigrated);
            }
        }, executor);
    }

    private List<String> determineCollections(DocumentStore store) {
        if (store instanceof NitriteDocumentStore nitrite) {
            try {
                Set<String> names = nitrite.collections().toCompletableFuture().join();
                if (!names.isEmpty()) {
                    return names.stream().toList();
                }
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Failed to enumerate nitrite collections, defaulting to players", exception);
            }
        }
        return List.of("players");
    }

    private int migrateLedger(LedgerRepository targetLedger) {
        Path ledgerPath = dataPath.getParent().resolve(config.ledgerPath());
        String jdbcUrl = "jdbc:sqlite:" + ledgerPath.toAbsolutePath();
        if (!Files.exists(ledgerPath)) {
            logger.info(() -> "[migrate] ledger file not found at " + ledgerPath + "; skipping ledger migration");
            return 0;
        }
        int migrated = 0;
        String sql = "SELECT player_uuid, type, amount, balance, source, created_at FROM ledger ORDER BY id ASC";
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            logger.log(Level.WARNING, "SQLite JDBC driver not found; skipping ledger migration from " + ledgerPath, exception);
            return 0;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                LedgerEntry.LedgerType type = LedgerEntry.LedgerType.valueOf(rs.getString("type").toUpperCase(Locale.ROOT));
                long amount = rs.getLong("amount");
                long balance = rs.getLong("balance");
                String source = rs.getString("source");
                Instant createdAt = Instant.parse(rs.getString("created_at"));
                LedgerEntry entry = new LedgerEntry(playerId, type, amount, balance, source, createdAt);
                targetLedger.append(entry).toCompletableFuture().join();
                migrated++;
            }
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to migrate ledger entries from " + ledgerPath, exception);
        }
        int migratedEntries = migrated;
        logger.info(() -> "[migrate] migrated " + migratedEntries + " ledger entries");
        return migrated;
    }

    @Override
    public void close() {
        executor.close();
    }

    record DataStoreMigrationReport(int collectionsMigrated, int documentsMigrated, int documentsSkipped, int ledgerEntriesMigrated) {
    }
}
