package sh.harold.fulcrum.common.data.ledger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
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

public final class MySqlLedgerRepository implements LedgerRepository {

    private static final String TABLE = "ledger";
    private final HikariDataSource dataSource;
    private final Executor executor;
    private final Logger logger;

    public MySqlLedgerRepository(String jdbcUrl, String username, String password, int maxPoolSize, long connectionTimeoutMillis, Logger logger, Executor executor) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.logger = logger != null ? logger : Logger.getLogger(MySqlLedgerRepository.class.getName());
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(Math.max(2, maxPoolSize));
        config.setMinimumIdle(Math.min(2, maxPoolSize));
        config.setConnectionTimeout(Math.max(1000L, connectionTimeoutMillis));
        config.setPoolName("fulcrum-ledger");
        config.setConnectionTestQuery("SELECT 1");
        this.dataSource = new HikariDataSource(config);
        initialize();
    }

    private void initialize() {
        String createTable = """
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT NOT NULL AUTO_INCREMENT,
                player_uuid VARCHAR(191) NOT NULL,
                type VARCHAR(64) NOT NULL,
                amount BIGINT NOT NULL,
                balance BIGINT NOT NULL,
                source VARCHAR(191),
                created_at TIMESTAMP NOT NULL,
                PRIMARY KEY (id),
                KEY idx_ledger_player_created (player_uuid, created_at)
            ) ENGINE=InnoDB;
            """.formatted(TABLE);
        runVoid(createTable);
    }

    @Override
    public CompletionStage<Void> append(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + TABLE + " (player_uuid, type, amount, balance, source, created_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, entry.playerId().toString());
                statement.setString(2, entry.type().name());
                statement.setLong(3, entry.amount());
                statement.setLong(4, entry.resultingBalance());
                statement.setString(5, entry.source());
                statement.setTimestamp(6, java.sql.Timestamp.from(entry.createdAt()));
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
            try (Connection connection = dataSource.getConnection();
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
            try (Connection connection = dataSource.getConnection();
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
        try {
            dataSource.close();
        } catch (Exception exception) {
            logger.log(Level.FINE, "Failed to close MySQL ledger datasource", exception);
        }
    }

    private LedgerEntry mapEntry(UUID playerId, ResultSet rs) throws SQLException {
        LedgerEntry.LedgerType type = LedgerEntry.LedgerType.valueOf(rs.getString("type"));
        long amount = rs.getLong("amount");
        long balance = rs.getLong("balance");
        String source = rs.getString("source");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        return new LedgerEntry(playerId, type, amount, balance, source, createdAt);
    }

    private void runVoid(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize MySQL ledger schema: " + exception.getMessage(), exception);
        }
    }
}
