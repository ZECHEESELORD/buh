package sh.harold.fulcrum.common.data.ledger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqliteLedgerRepository implements LedgerRepository {

    private static final String TABLE = "ledger";
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS ledger (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            player_uuid TEXT NOT NULL,
            type TEXT NOT NULL,
            amount INTEGER NOT NULL,
            balance INTEGER NOT NULL,
            source TEXT,
            created_at TEXT NOT NULL
        );
        """;
    private static final String CREATE_INDEX_SQL = "CREATE INDEX IF NOT EXISTS idx_ledger_player_created ON ledger(player_uuid, created_at DESC)";

    private final String jdbcUrl;
    private final Executor executor;
    private final Logger logger;

    public SqliteLedgerRepository(String jdbcUrl, Executor executor, Logger logger) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
        this.logger = logger != null ? logger : Logger.getLogger(SqliteLedgerRepository.class.getName());
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
            throw new IllegalStateException("Failed to initialize ledger schema: " + exception.getMessage(), exception);
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
            throw new IllegalStateException("Failed to create ledger directory " + parent, exception);
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
    public CompletionStage<Void> append(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + TABLE + " (player_uuid, type, amount, balance, source, created_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, entry.playerId().toString());
                statement.setString(2, entry.type().name());
                statement.setLong(3, entry.amount());
                statement.setLong(4, entry.resultingBalance());
                statement.setString(5, entry.source());
                statement.setString(6, entry.createdAt().toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to append ledger entry", exception);
            }
        }, executor);
    }

    @Override
    public CompletionStage<List<LedgerEntry>> recent(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT type, amount, balance, source, created_at FROM " + TABLE + " WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?";
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, normalizedLimit);
                List<LedgerEntry> entries = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        entries.add(mapEntry(playerId, rs));
                    }
                }
                return entries;
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to read recent ledger entries for " + playerId, exception);
                return List.of();
            }
        }, executor);
    }

    @Override
    public CompletionStage<Optional<LedgerEntry>> latest(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT type, amount, balance, source, created_at FROM " + TABLE + " WHERE player_uuid = ? ORDER BY created_at DESC LIMIT 1";
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapEntry(playerId, rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to read latest ledger entry for " + playerId, exception);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public void close() {
        // executor owned by caller
    }

    private LedgerEntry mapEntry(UUID playerId, ResultSet rs) throws SQLException {
        LedgerEntry.LedgerType type = LedgerEntry.LedgerType.valueOf(rs.getString("type"));
        long amount = rs.getLong("amount");
        long balance = rs.getLong("balance");
        String source = rs.getString("source");
        Instant createdAt = Instant.parse(rs.getString("created_at"));
        return new LedgerEntry(playerId, type, amount, balance, source, createdAt);
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
