package sh.harold.fulcrum.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.ModuleActivation;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class ModuleConfigService {

    private final Path configPath;
    private final Logger logger;

    public ModuleConfigService(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("modules.yml"), plugin.getLogger());
    }

    public ModuleConfigService(Path configPath, Logger logger) {
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public ModuleActivation load(Collection<ModuleDescriptor> descriptors) {
        try {
            Files.createDirectories(configPath.getParent());
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configPath.toFile());
            boolean updated = false;
            Map<ModuleId, Boolean> desiredStates = new LinkedHashMap<>();

            for (ModuleDescriptor descriptor : descriptors) {
                ModuleId moduleId = descriptor.id();
                String key = moduleId.value();
                if (!configuration.contains(key)) {
                    configuration.set(key, false);
                    updated = true;
                }
                boolean enabled = configuration.getBoolean(key, false);
                desiredStates.put(moduleId, enabled);
            }

            if (updated) {
                configuration.save(configPath.toFile());
                logger.info("Saved module defaults to " + configPath);
            }

            return new ModuleActivation(desiredStates);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load modules configuration from " + configPath, exception);
        }
    }
}
