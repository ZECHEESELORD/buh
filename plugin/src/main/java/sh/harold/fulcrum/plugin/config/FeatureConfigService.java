package sh.harold.fulcrum.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class FeatureConfigService implements AutoCloseable {

    private final Path configRoot;
    private final Logger logger;
    private final ExecutorService executor;

    public FeatureConfigService(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("config"), plugin.getLogger());
    }

    public FeatureConfigService(Path configRoot, Logger logger) {
        this.configRoot = Objects.requireNonNull(configRoot, "configRoot");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public FeatureConfiguration load(FeatureConfigDefinition definition) {
        Path featureDir = configRoot.resolve(definition.featureName());
        Path configPath = featureDir.resolve("config.yml");

        try {
            Files.createDirectories(featureDir);
            YamlConfiguration configuration = loadConfiguration(configPath.toFile());
            boolean updated = ensureDefaults(configuration, definition.options());
            if (updated) {
                configuration.save(configPath.toFile());
                logger.info("Saved defaults for feature config '" + definition.featureName() + "' to " + configPath);
            }
            return new FeatureConfiguration(definition, configPath, configuration);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load config for feature " + definition.featureName(), exception);
        }
    }

    public CompletionStage<FeatureConfiguration> loadAsync(FeatureConfigDefinition definition) {
        return CompletableFuture.supplyAsync(() -> load(definition), executor);
    }

    @Override
    public void close() {
        executor.close();
    }

    private YamlConfiguration loadConfiguration(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    private boolean ensureDefaults(YamlConfiguration configuration, List<FeatureConfigOption<?>> options) {
        boolean updated = false;
        for (FeatureConfigOption<?> option : options) {
            if (configuration.contains(option.path())) {
                continue;
            }
            configuration.set(option.path(), option.defaultValue());
            updated = true;
        }
        return updated;
    }
}
