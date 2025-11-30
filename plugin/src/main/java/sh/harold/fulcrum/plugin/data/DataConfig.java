package sh.harold.fulcrum.plugin.data;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record DataConfig(
    DataStore store,
    LedgerStore ledgerStore,
    String ledgerPath,
    MySqlOptions mysql,
    boolean migrationBlockLogins
) {

    private static final String FEATURE_NAME = "data";
    private static final FeatureConfigOption<String> STORE_OPTION = FeatureConfigOptions.stringOption("store", "mysql");
    private static final FeatureConfigOption<String> LEDGER_STORE_OPTION = FeatureConfigOptions.stringOption("ledger.store", "mysql");
    private static final FeatureConfigOption<String> LEDGER_PATH_OPTION = FeatureConfigOptions.stringOption("ledger.path", "data/ledger.db");
    private static final FeatureConfigOption<Boolean> MIGRATION_BLOCK_LOGINS_OPTION = FeatureConfigOptions.booleanOption("migration.block-logins", true);

    private static final FeatureConfigOption<String> MYSQL_HOST_OPTION = FeatureConfigOptions.stringOption("mysql.host", "localhost");
    private static final FeatureConfigOption<Integer> MYSQL_PORT_OPTION = FeatureConfigOptions.intOption("mysql.port", 3306);
    private static final FeatureConfigOption<String> MYSQL_DATABASE_OPTION = FeatureConfigOptions.stringOption("mysql.database", "fulcrum");
    private static final FeatureConfigOption<String> MYSQL_USERNAME_OPTION = FeatureConfigOptions.stringOption("mysql.username", "root");
    private static final FeatureConfigOption<String> MYSQL_PASSWORD_OPTION = FeatureConfigOptions.stringOption("mysql.password", "");
    private static final FeatureConfigOption<Integer> MYSQL_POOL_SIZE_OPTION = FeatureConfigOptions.intOption("mysql.pool-size", 5);
    private static final FeatureConfigOption<Long> MYSQL_CONNECTION_TIMEOUT_OPTION = FeatureConfigOptions.longOption("mysql.connection-timeout-millis", 3000L);

    public static DataConfig load(FeatureConfigService configService) {
        Objects.requireNonNull(configService, "configService");
        FeatureConfigDefinition definition = new FeatureConfigDefinition(
            FEATURE_NAME,
            List.of(
                STORE_OPTION,
                LEDGER_STORE_OPTION,
                LEDGER_PATH_OPTION,
                MIGRATION_BLOCK_LOGINS_OPTION,
                MYSQL_HOST_OPTION,
                MYSQL_PORT_OPTION,
                MYSQL_DATABASE_OPTION,
                MYSQL_USERNAME_OPTION,
                MYSQL_PASSWORD_OPTION,
                MYSQL_POOL_SIZE_OPTION,
                MYSQL_CONNECTION_TIMEOUT_OPTION
            )
        );
        var config = configService.load(definition);
        DataStore store = DataStore.from(config.value(STORE_OPTION));
        LedgerStore ledgerStore = LedgerStore.from(config.value(LEDGER_STORE_OPTION));
        String ledgerPath = config.value(LEDGER_PATH_OPTION);
        MySqlOptions mysql = new MySqlOptions(
            config.value(MYSQL_HOST_OPTION),
            config.value(MYSQL_PORT_OPTION),
            config.value(MYSQL_DATABASE_OPTION),
            config.value(MYSQL_USERNAME_OPTION),
            config.value(MYSQL_PASSWORD_OPTION),
            config.value(MYSQL_POOL_SIZE_OPTION),
            config.value(MYSQL_CONNECTION_TIMEOUT_OPTION)
        );
        boolean migrationBlockLogins = config.value(MIGRATION_BLOCK_LOGINS_OPTION);
        return new DataConfig(store, ledgerStore, ledgerPath, mysql, migrationBlockLogins);
    }

    public enum DataStore {
        NITRITE,
        JSON,
        MYSQL;

        static DataStore from(String raw) {
            if (raw == null || raw.isBlank()) {
                return NITRITE;
            }
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "json" -> JSON;
                case "mysql" -> MYSQL;
                default -> NITRITE;
            };
        }
    }

    public enum LedgerStore {
        SQLITE,
        MYSQL;

        static LedgerStore from(String raw) {
            if (raw == null || raw.isBlank()) {
                return SQLITE;
            }
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "mysql" -> MYSQL;
                default -> SQLITE;
            };
        }
    }

    public record MySqlOptions(
        String host,
        int port,
        String database,
        String username,
        String password,
        int maxPoolSize,
        long connectionTimeoutMillis
    ) {
        public MySqlOptions {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(database, "database");
            Objects.requireNonNull(username, "username");
            if (port <= 0) {
                port = 3306;
            }
            if (maxPoolSize <= 0) {
                maxPoolSize = 5;
            }
            if (connectionTimeoutMillis <= 0) {
                connectionTimeoutMillis = 3000L;
            }
        }

        public String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC";
        }
    }
}
