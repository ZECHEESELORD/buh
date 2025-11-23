Feature Configuration Primer:
Fulcrum now keeps feature settings tidy inside each plugin data folder. Every feature can declare its own options, and the service writes missing defaults to `config/<featureName>/config.yml` on first load.

Declaring options:
```java
import static sh.harold.fulcrum.plugin.config.FeatureConfigOptions.*;

private static final FeatureConfigOption<String> TITLE =
    stringOption("scoreboard-title", "&aFulcrum");
private static final FeatureConfigOption<Integer> UPDATE_TICKS =
    intOption("update-ticks", 20);

private static final FeatureConfigDefinition DEFINITION = FeatureConfigDefinition
    .feature("scoreboard")
    .option(TITLE)
    .option(UPDATE_TICKS)
    .build();
```

Loading configs:
```java
public final class ScoreboardFeature {
    private final FeatureConfiguration config;

    public ScoreboardFeature(BuhPlugin plugin) {
        this.config = plugin.featureConfigService().load(DEFINITION);
    }

    public String title() {
        return config.value(TITLE);
    }

    public int updateTicks() {
        return config.value(UPDATE_TICKS);
    }
}
```

Async loading:
`featureConfigService.loadAsync(definition)` returns a `CompletionStage<FeatureConfiguration>` when you need to stay fully off the primary thread.

Option helpers:
1) `stringOption`, `booleanOption`, `intOption`, `stringListOption` cover the usual cases.
2) For anything richer, build a `FeatureConfigOption` with a custom reader; keep serialization simple so the stored YAML stays readable.
3) Paths are written as provided, so pick short, human friendly names.

Operational notes:
1) The service writes defaults on first load, leaving existing player edits untouched later.
2) The plugin owns the shared service and closes it during disable. Do not close it from a feature.
3) If you compute expensive derived values, do it off thread and hop back to the server thread only for Bukkit work.
