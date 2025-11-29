package sh.harold.fulcrum.plugin.data;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record DataConfig(DataStore store, String ledgerPath) {

    private static final String FEATURE_NAME = "data";
    private static final FeatureConfigOption<String> STORE_OPTION = FeatureConfigOptions.stringOption("store", "nitrite");
    private static final FeatureConfigOption<String> LEDGER_PATH_OPTION = FeatureConfigOptions.stringOption("ledger.path", "data/ledger.db");

    public static DataConfig load(FeatureConfigService configService) {
        Objects.requireNonNull(configService, "configService");
        FeatureConfigDefinition definition = new FeatureConfigDefinition(
            FEATURE_NAME,
            List.of(STORE_OPTION, LEDGER_PATH_OPTION)
        );
        var config = configService.load(definition);
        String selected = config.value(STORE_OPTION);
        DataStore store = DataStore.from(selected);
        String ledgerPath = config.value(LEDGER_PATH_OPTION);
        return new DataConfig(store, ledgerPath);
    }

    public enum DataStore {
        NITRITE,
        JSON;

        static DataStore from(String raw) {
            if (raw == null || raw.isBlank()) {
                return NITRITE;
            }
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "json" -> JSON;
                default -> NITRITE;
            };
        }
    }
}
