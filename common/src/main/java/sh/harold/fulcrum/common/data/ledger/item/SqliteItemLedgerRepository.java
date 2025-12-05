package sh.harold.fulcrum.common.data.ledger.item;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqliteItemLedgerRepository implements ItemLedgerRepository {

    private static final String TABLE = "item_instance_ledger";
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS item_instance_ledger (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            instance_uuid TEXT NOT NULL UNIQUE,
            item_id TEXT NOT NULL,
            player_uuid TEXT,
            source TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        """;
    private static final String CREATE_INDEX_SQL = "CREATE INDEX IF NOT EXISTS idx_item_instance_created ON item_instance_ledger(created_at DESC)";

    private final String jdbcUrl;
    private final Executor executor;
    private final Logger logger;

    public SqliteItemLedgerRepository(String jdbcUrl, Executor executor, Logger logger) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
        this.logger = logger != null ? logger : Logger.getLogger(SqliteItemLedgerRepository.class.getName());
        initialize();
    }

    private void initialize() {
        ensureDirectory();
        loadDriver();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE_SQL);
            statement.executeUpdate(CREATE_INDEX_SQL);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize item ledger schema: " + exception.getMessage(), exception);
        }
    }

    private void ensureDirectory() {
        int prefixIndex = jdbcUrl.indexOf(':');
        if (prefixIndex < 0) {
            return;
        }
        String pathPart = jdbcUrl.substring(jdbcUrl.indexOf(':', prefixIndex + 1) + 1);
        if (pathPart.isBlank()) {
            return;
        }
        java.nio.file.Path dbPath = java.nio.file.Paths.get(pathPart);
        java.nio.file.Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(parent);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to create item ledger directory " + parent, exception);
        }
    }

    private void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is not available on the classpath", exception);
        }
    }

    @Override
    public CompletionStage<Void> append(ItemInstanceRecord record) {
        Objects.requireNonNull(record, "record");
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + TABLE + " (instance_uuid, item_id, player_uuid, source, created_at) VALUES (?, ?, ?, ?, ?)";
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.instanceId().toString());
                statement.setString(2, record.itemId());
                statement.setString(3, record.creatorId() == null ? null : record.creatorId().toString());
                statement.setString(4, record.source().name());
                statement.setString(5, record.createdAt().toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to append item ledger entry", exception);
            }
        }, executor);
    }

    @Override
    public CompletionStage<Optional<ItemInstanceRecord>> find(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT item_id, player_uuid, source, created_at FROM " + TABLE + " WHERE instance_uuid = ? LIMIT 1";
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, instanceId.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRecord(instanceId, rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to read item ledger for " + instanceId, exception);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public void close() {
        // executor owned by caller
    }

    private ItemInstanceRecord mapRecord(UUID instanceId, ResultSet rs) throws SQLException {
        String itemId = rs.getString("item_id");
        String rawPlayer = rs.getString("player_uuid");
        UUID playerId = rawPlayer == null || rawPlayer.isBlank() ? null : UUID.fromString(rawPlayer);
        ItemCreationSource source = ItemCreationSource.valueOf(rs.getString("source"));
        Instant createdAt = Instant.parse(rs.getString("created_at"));
        return new ItemInstanceRecord(instanceId, itemId, source, playerId, createdAt);
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
