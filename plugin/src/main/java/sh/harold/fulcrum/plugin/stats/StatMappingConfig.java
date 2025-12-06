package sh.harold.fulcrum.plugin.stats;

import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfiguration;

public record StatMappingConfig(double defenseScale, double maxReduction, boolean mirrorArmorAttributes) {

    static final FeatureConfigOption<Double> DEFENSE_SCALE_OPTION = FeatureConfigOptions.doubleOption("damage.defense_scale", 32.0);
    static final FeatureConfigOption<Double> MAX_REDUCTION_OPTION = FeatureConfigOptions.doubleOption("damage.max_reduction", 0.80);
    static final FeatureConfigOption<Boolean> MIRROR_ARMOR_OPTION = FeatureConfigOptions.booleanOption("visuals.mirror_armor_attributes", true);

    static final FeatureConfigDefinition CONFIG_DEFINITION = FeatureConfigDefinition.feature("stats")
        .option(DEFENSE_SCALE_OPTION)
        .option(MAX_REDUCTION_OPTION)
        .option(MIRROR_ARMOR_OPTION)
        .build();

    public StatMappingConfig {
        if (defenseScale <= 0) {
            throw new IllegalArgumentException("defenseScale must be > 0");
        }
        if (maxReduction <= 0 || maxReduction >= 1) {
            throw new IllegalArgumentException("maxReduction must be between 0 and 1 (exclusive)");
        }
    }

    static StatMappingConfig from(FeatureConfiguration configuration) {
        double defenseScale = configuration.value(DEFENSE_SCALE_OPTION);
        double maxReduction = configuration.value(MAX_REDUCTION_OPTION);
        boolean mirrorArmor = configuration.value(MIRROR_ARMOR_OPTION);
        return new StatMappingConfig(defenseScale, maxReduction, mirrorArmor);
    }
}
