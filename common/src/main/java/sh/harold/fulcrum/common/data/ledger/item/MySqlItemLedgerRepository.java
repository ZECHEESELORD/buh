package sh.harold.fulcrum.common.data.ledger.item;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
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

public final class MySqlItemLedgerRepository implements ItemLedgerRepository {

    private static final String TABLE = "item_instance_ledger";
    private final HikariDataSource dataSource;
    private final Executor executor;
    private final Logger logger;

    public MySqlItemLedgerRepository(
        String jdbcUrl,
        String username,
        String password,
        int maxPoolSize,
        long connectionTimeoutMillis,
        Logger logger,
        Executor executor
    ) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.logger = logger != null ? logger : Logger.getLogger(MySqlItemLedgerRepository.class.getName());
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(Math.max(2, maxPoolSize));
        config.setMinimumIdle(Math.min(2, maxPoolSize));
        config.setConnectionTimeout(Math.max(1000L, connectionTimeoutMillis));
        config.setPoolName("fulcrum-item-ledger");
        config.setConnectionTestQuery("SELECT 1");
        this.dataSource = new HikariDataSource(config);
        initialize();
    }

    private void initialize() {
        String createTable = """
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT NOT NULL AUTO_INCREMENT,
                instance_uuid VARCHAR(191) NOT NULL,
                item_id VARCHAR(191) NOT NULL,
                player_uuid VARCHAR(191),
                source VARCHAR(64) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uq_item_instance (instance_uuid),
                KEY idx_item_instance_created (created_at)
            ) ENGINE=InnoDB;
            """.formatted(TABLE);
        runVoid(createTable);
    }

    @Override
    public CompletionStage<Void> append(ItemInstanceRecord record) {
        Objects.requireNonNull(record, "record");
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + TABLE + " (instance_uuid, item_id, player_uuid, source, created_at) VALUES (?, ?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.instanceId().toString());
                statement.setString(2, record.itemId());
                statement.setString(3, record.creatorId() == null ? null : record.creatorId().toString());
                statement.setString(4, record.source().name());
                statement.setTimestamp(5, java.sql.Timestamp.from(record.createdAt()));
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
            try (Connection connection = dataSource.getConnection();
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
        try {
            dataSource.close();
        } catch (Exception exception) {
            logger.log(Level.FINE, "Failed to close MySQL item ledger datasource", exception);
        }
    }

    private ItemInstanceRecord mapRecord(UUID instanceId, ResultSet rs) throws SQLException {
        String itemId = rs.getString("item_id");
        String rawPlayer = rs.getString("player_uuid");
        UUID playerId = rawPlayer == null || rawPlayer.isBlank() ? null : UUID.fromString(rawPlayer);
        ItemCreationSource source = ItemCreationSource.valueOf(rs.getString("source"));
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        return new ItemInstanceRecord(instanceId, itemId, source, playerId, createdAt);
    }

    private void runVoid(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize MySQL item ledger schema: " + exception.getMessage(), exception);
        }
    }
}
