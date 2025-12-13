package sh.harold.fulcrum.common.data.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import sh.harold.fulcrum.common.data.DocumentKey;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.DocumentStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MySqlDocumentStore implements DocumentStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String TABLE = "documents";
    private static final long SLOW_OP_THRESHOLD_MS = 250;
    private static final int MAX_LOCK_RETRIES = 3;
    private static final long LOCK_RETRY_BASE_DELAY_MS = 10;

    private final HikariDataSource dataSource;
    private final Executor executor;
    private final ObjectMapper objectMapper;
    private final Logger logger;

    public MySqlDocumentStore(String jdbcUrl, String username, String password, int maxPoolSize, long connectionTimeoutMillis, Logger logger, Executor executor) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.logger = logger != null ? logger : Logger.getLogger(MySqlDocumentStore.class.getName());
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setConnectionTimeout(Math.max(1000L, connectionTimeoutMillis));
        config.setPoolName("fulcrum-documents");
        config.setMaximumPoolSize(Math.max(2, maxPoolSize));
        config.setMinimumIdle(Math.min(2, maxPoolSize));
        config.setConnectionTestQuery("SELECT 1");
        this.dataSource = new HikariDataSource(config);
        this.objectMapper = new ObjectMapper();
        initialize();
    }

    private void initialize() {
        String createTable = """
            CREATE TABLE IF NOT EXISTS %s (
                collection VARCHAR(191) NOT NULL,
                id VARCHAR(191) NOT NULL,
                data JSON NOT NULL,
                PRIMARY KEY (collection, id)
            ) ENGINE=InnoDB;
            """.formatted(TABLE);
        runVoid(createTable);
    }

    @Override
    public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            String sql = "SELECT data FROM " + TABLE + " WHERE collection = ? AND id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, key.collection());
                statement.setString(2, key.id());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return new DocumentSnapshot(key, Map.of(), false);
                    }
                    String json = rs.getString(1);
                    Map<String, Object> map = parse(json);
                    return new DocumentSnapshot(key, map, true);
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "[data] read failed for " + key.collection() + "/" + key.id(), exception);
                throw new IllegalStateException("Failed to read document " + key, exception);
            } finally {
                logIfSlow("read", key, startedAt);
            }
        }, executor);
    }

    @Override
    public CompletionStage<Void> write(DocumentKey key, Map<String, Object> data) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        Map<String, Object> snapshot = MapPath.deepCopy(data);
        return CompletableFuture.runAsync(() -> {
            long startedAt = System.nanoTime();
            try (Connection connection = dataSource.getConnection()) {
                writeInternal(connection, key, snapshot);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "[data] write failed for " + key.collection() + "/" + key.id(), exception);
                throw new IllegalStateException("Failed to write document " + key, exception);
            } finally {
                logIfSlow("write", key, startedAt);
            }
        }, executor);
    }

    @Override
    public CompletionStage<DocumentSnapshot> update(DocumentKey key, java.util.function.UnaryOperator<Map<String, Object>> mutator) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mutator, "mutator");
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            try {
                for (int attempt = 0; attempt <= MAX_LOCK_RETRIES; attempt++) {
                    try (Connection connection = dataSource.getConnection()) {
                        boolean previousAutoCommit = connection.getAutoCommit();
                        connection.setAutoCommit(false);
                        try {
                            if (attempt > 0) {
                                lockOrCreateRow(connection, key);
                            }
                            Map<String, Object> current = readForUpdate(connection, key);
                            Map<String, Object> working = MapPath.deepCopy(current);
                            Map<String, Object> mutated = mutator.apply(working);
                            if (mutated == null) {
                                throw new IllegalStateException("Mutator returned null for " + key);
                            }
                            Map<String, Object> normalized = MapPath.deepCopy(mutated);
                            writeInternal(connection, key, normalized);
                            connection.commit();
                            return new DocumentSnapshot(key, normalized, true);
                        } catch (Exception exception) {
                            try {
                                connection.rollback();
                            } catch (SQLException rollbackException) {
                                exception.addSuppressed(rollbackException);
                            }
                            if (exception instanceof SQLException sqlException && attempt < MAX_LOCK_RETRIES && isRetryableLockFailure(sqlException)) {
                                delayRetry(attempt);
                                continue;
                            }
                            throw exception;
                        } finally {
                            connection.setAutoCommit(previousAutoCommit);
                        }
                    } catch (SQLException exception) {
                        if (attempt < MAX_LOCK_RETRIES && isRetryableLockFailure(exception)) {
                            delayRetry(attempt);
                            continue;
                        }
                        logger.log(Level.WARNING, "[data] update failed for " + key.collection() + "/" + key.id(), exception);
                        throw new IllegalStateException("Failed to update document " + key, exception);
                    }
                }
                throw new IllegalStateException("Failed to update document " + key + " after retries");
            } finally {
                logIfSlow("update", key, startedAt);
            }
        }, executor);
    }

    @Override
    public CompletionStage<Boolean> delete(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            String sql = "DELETE FROM " + TABLE + " WHERE collection = ? AND id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, key.collection());
                statement.setString(2, key.id());
                return statement.executeUpdate() > 0;
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "[data] delete failed for " + key.collection() + "/" + key.id(), exception);
                throw new IllegalStateException("Failed to delete document " + key, exception);
            } finally {
                logIfSlow("delete", key, startedAt);
            }
        }, executor);
    }

    @Override
    public CompletionStage<Void> patch(DocumentKey key, Map<String, Object> setValues, Iterable<String> removePaths) {
        Objects.requireNonNull(key, "key");
        Map<String, Object> sets = MapPath.deepCopy(setValues);
        java.util.List<String> removals = new java.util.ArrayList<>();
        if (removePaths != null) {
            removePaths.forEach(removals::add);
        }
        return update(key, current -> {
            Map<String, Object> working = MapPath.deepCopy(current);
            sets.forEach((path, value) -> MapPath.write(working, path, value));
            for (String path : removals) {
                MapPath.remove(working, path);
            }
            return working;
        }).thenApply(snapshot -> null);
    }

    @Override
    public CompletionStage<List<DocumentSnapshot>> all(String collection) {
        Objects.requireNonNull(collection, "collection");
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            String sql = "SELECT id, data FROM " + TABLE + " WHERE collection = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, collection);
                List<DocumentSnapshot> snapshots = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString(1);
                        String json = rs.getString(2);
                        Map<String, Object> map = parse(json);
                        snapshots.add(new DocumentSnapshot(DocumentKey.of(collection, id), map, true));
                    }
                }
                return List.copyOf(snapshots);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "[data] list failed for collection " + collection, exception);
                throw new IllegalStateException("Failed to list documents for " + collection, exception);
            } finally {
                logIfSlow("all", DocumentKey.of(collection, "*"), startedAt);
            }
        }, executor);
    }

    @Override
    public CompletionStage<Long> count(String collection) {
        Objects.requireNonNull(collection, "collection");
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.nanoTime();
            String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE collection = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, collection);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "[data] count failed for collection " + collection, exception);
                throw new IllegalStateException("Failed to count documents for " + collection, exception);
            } finally {
                logIfSlow("count", DocumentKey.of(collection, "*"), startedAt);
            }
        }, executor);
    }

    @Override
    public void close() {
        try {
            dataSource.close();
        } catch (Exception exception) {
            logger.log(Level.FINE, "Failed to close MySQL datasource", exception);
        }
    }

    private void runVoid(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize MySQL store: " + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> readForUpdate(Connection connection, DocumentKey key) throws SQLException {
        String sql = "SELECT data FROM " + TABLE + " WHERE collection = ? AND id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.collection());
            statement.setString(2, key.id());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Map.of();
                }
                String json = rs.getString(1);
                return parse(json);
            }
        }
    }

    private void lockOrCreateRow(Connection connection, DocumentKey key) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (collection, id, data) VALUES (?, ?, JSON_OBJECT()) "
            + "ON DUPLICATE KEY UPDATE data = data";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.collection());
            statement.setString(2, key.id());
            statement.executeUpdate();
        }
    }

    private boolean isRetryableLockFailure(SQLException exception) {
        if (exception == null) {
            return false;
        }
        int errorCode = exception.getErrorCode();
        if (errorCode == 1213 || errorCode == 1205) {
            return true;
        }
        String state = exception.getSQLState();
        if ("40001".equals(state)) {
            return true;
        }
        SQLException next = exception.getNextException();
        if (next != null && next != exception) {
            return isRetryableLockFailure(next);
        }
        Throwable cause = exception.getCause();
        if (cause instanceof SQLException sqlCause && cause != exception) {
            return isRetryableLockFailure(sqlCause);
        }
        return false;
    }

    private void delayRetry(int attempt) {
        long delayMillis = LOCK_RETRY_BASE_DELAY_MS * (1L << attempt);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeInternal(Connection connection, DocumentKey key, Map<String, Object> data) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (collection, id, data) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE data = VALUES(data)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.collection());
            statement.setString(2, key.id());
            statement.setString(3, toJson(data));
            statement.executeUpdate();
        }
    }

    private int applyPatch(Connection connection, DocumentKey key, Map<String, Object> setValues, java.util.List<String> removePaths) throws SQLException {
        String dataExpression = "data";
        java.util.List<Object> parameters = new java.util.ArrayList<>();

        if (!setValues.isEmpty()) {
            String setExpr = setValues.entrySet().stream()
                .map(entry -> {
                    parameters.add(pathParam(entry.getKey()));
                    parameters.add(toJsonValue(entry.getValue()));
                    return ", ?, JSON_EXTRACT(?, '$')";
                })
                .collect(Collectors.joining("", "JSON_SET(" + dataExpression, ")"));
            dataExpression = setExpr;
        }

        if (!removePaths.isEmpty()) {
            String removeExpr = removePaths.stream()
                .map(path -> {
                    parameters.add(pathParam(path));
                    return ", ?";
                })
                .collect(Collectors.joining("", "JSON_REMOVE(" + dataExpression, ")"));
            dataExpression = removeExpr;
        }

        if (parameters.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE " + TABLE + " SET data = " + dataExpression + " WHERE collection = ? AND id = ?";
        parameters.add(key.collection());
        parameters.add(key.id());

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object param : parameters) {
                statement.setObject(index++, param);
            }
            try {
                return statement.executeUpdate();
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "[data] mysql json patch failed sql=" + sql + " params=" + parameters, exception);
                throw exception;
            }
        }
    }

    private String pathParam(String path) {
        if (path == null || path.isBlank()) {
            return "$";
        }
        String[] parts = path.split("\\.");
        StringBuilder builder = new StringBuilder("$");
        for (String part : parts) {
            if (part.matches("[A-Za-z0-9_]+")) {
                builder.append(".").append(part);
            } else {
                String escaped = part
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
                builder.append(".\"").append(escaped).append("\"");
            }
        }
        return builder.toString();
    }

    private void logIfSlow(String operation, DocumentKey key, long startedAtNanos) {
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        if (elapsedMillis > SLOW_OP_THRESHOLD_MS) {
            logger.info(() -> "[data] slow " + operation + " for " + key.collection() + "/" + key.id() + " in " + elapsedMillis + "ms");
        }
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return new LinkedHashMap<>(parsed);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse document JSON", exception);
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize document", exception);
        }
    }

    private String toJsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize value", exception);
        }
    }
}
