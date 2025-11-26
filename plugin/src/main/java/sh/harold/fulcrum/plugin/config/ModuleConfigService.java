package sh.harold.fulcrum.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.ModuleActivation;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Persists module activation with enough structure to stay readable as the list grows:
 * - API implementations stay on and never appear in the config.
 * - Base modules feed other features; they can be toggled and live together under base-modules.
 * - Regular modules live under the main "modules" bucket, grouped by category for scanning.
 */
public final class ModuleConfigService {

    private static final String BASE_MODULES_KEY = "base-modules";
    private static final String MODULES_KEY = "modules";
    private static final Set<ModuleId> OPTIONAL_DEPENDENT_MODULES = Set.of(
        ModuleId.of("account-link")
    );

    private final Path configPath;
    private final Logger logger;
    private final Set<ModuleId> alwaysEnabledModules;
    private final Set<ModuleId> baseModules;

    public ModuleConfigService(JavaPlugin plugin) {
        this(plugin, Set.of(), Set.of());
    }

    public ModuleConfigService(JavaPlugin plugin, Set<ModuleId> alwaysEnabledModules, Set<ModuleId> baseModules) {
        this(plugin.getDataFolder().toPath().resolve("modules.yml"), plugin.getLogger(), alwaysEnabledModules, baseModules);
    }

    public ModuleConfigService(Path configPath, Logger logger) {
        this(configPath, logger, Set.of(), Set.of());
    }

    public ModuleConfigService(Path configPath, Logger logger, Set<ModuleId> alwaysEnabledModules, Set<ModuleId> baseModules) {
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(alwaysEnabledModules, "alwaysEnabledModules");
        Objects.requireNonNull(baseModules, "baseModules");
        this.alwaysEnabledModules = Set.copyOf(alwaysEnabledModules);
        this.baseModules = Set.copyOf(baseModules);
    }

    public ModuleActivation load(Collection<ModuleDescriptor> descriptors) {
        try {
            Files.createDirectories(configPath.getParent());
            YamlConfiguration configuration = Files.exists(configPath)
                ? YamlConfiguration.loadConfiguration(configPath.toFile())
                : new YamlConfiguration();
            boolean updated = !Files.exists(configPath);

            Map<ModuleId, Set<ModuleId>> dependents = dependentsByModule(descriptors);
            Set<ModuleId> computedBaseModules = dependents.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            computedBaseModules.removeAll(OPTIONAL_DEPENDENT_MODULES);

            Set<ModuleId> baseModuleIds = new LinkedHashSet<>(computedBaseModules);
            baseModuleIds.addAll(baseModules);
            baseModuleIds.removeAll(alwaysEnabledModules);

            List<ModuleDescriptor> descriptorOrder = new ArrayList<>(descriptors);
            List<ModuleDescriptor> alwaysEnabled = descriptorOrder.stream()
                .filter(descriptor -> alwaysEnabledModules.contains(descriptor.id()))
                .toList();
            List<ModuleDescriptor> baseDescriptorList = descriptorOrder.stream()
                .filter(descriptor -> baseModuleIds.contains(descriptor.id()))
                .toList();
            List<ModuleDescriptor> optionalDescriptorList = descriptorOrder.stream()
                .filter(descriptor -> !alwaysEnabledModules.contains(descriptor.id()) && !baseModuleIds.contains(descriptor.id()))
                .toList();

            Map<ModuleId, Boolean> desiredStates = new LinkedHashMap<>();
            alwaysEnabled.forEach(descriptor -> desiredStates.put(descriptor.id(), true));

            for (ModuleDescriptor descriptor : baseDescriptorList) {
                ModuleId moduleId = descriptor.id();
                String key = BASE_MODULES_KEY + "." + moduleId.value();
                if (!configuration.contains(key)) {
                    updated = true;
                }
                boolean enabled = configuration.getBoolean(key, true);
                desiredStates.put(moduleId, enabled);
            }

            for (ModuleDescriptor descriptor : optionalDescriptorList) {
                ModuleId moduleId = descriptor.id();
                String key = optionalModuleKey(descriptor);
                boolean enabled;
                if (configuration.contains(key)) {
                    enabled = configuration.getBoolean(key, false);
                } else {
                    updated = true;
                    enabled = false;
                }
                desiredStates.put(moduleId, enabled);
            }

            if (updated) {
                writeConfig(alwaysEnabled, baseDescriptorList, optionalDescriptorList, desiredStates, dependents);
            }

            return new ModuleActivation(desiredStates);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load modules configuration from " + configPath, exception);
        }
    }

    private Map<ModuleId, Set<ModuleId>> dependentsByModule(Collection<ModuleDescriptor> descriptors) {
        Map<ModuleId, Set<ModuleId>> dependents = new LinkedHashMap<>();
        for (ModuleDescriptor descriptor : descriptors) {
            dependents.putIfAbsent(descriptor.id(), new LinkedHashSet<>());
        }
        for (ModuleDescriptor descriptor : descriptors) {
            for (ModuleId dependency : descriptor.dependencies()) {
                dependents.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(descriptor.id());
            }
        }
        return dependents;
    }

    private void writeConfig(
        List<ModuleDescriptor> alwaysEnabled,
        List<ModuleDescriptor> baseDescriptors,
        List<ModuleDescriptor> optionalDescriptors,
        Map<ModuleId, Boolean> desiredStates,
        Map<ModuleId, Set<ModuleId>> dependents
    ) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("# Buh module toggles. API implementations stay on and out of sight: ");
        builder.append(alwaysEnabled.isEmpty()
            ? "none"
            : alwaysEnabled.stream().map(descriptor -> descriptor.id().value()).collect(Collectors.joining(", ")));
        builder.append("\n");
        builder.append("# Base modules feed other features; toggle with care. Regular modules live below.\n");
        builder.append("# Modules are grouped by category to stay readable as the list grows.\n\n");

        builder.append(BASE_MODULES_KEY).append(":\n");
        if (baseDescriptors.isEmpty()) {
            builder.append("  # no base modules detected yet\n");
        } else {
            appendBaseModules(builder, baseDescriptors, desiredStates, dependents);
        }

        builder.append("\n").append(MODULES_KEY).append(":\n");
        if (optionalDescriptors.isEmpty()) {
            builder.append("  # no optional modules detected yet\n");
        } else {
            appendCategorizedModules(builder, optionalDescriptors, desiredStates, false);
        }

        Files.writeString(configPath, builder.toString(), StandardCharsets.UTF_8);
        logger.info("Saved module defaults to " + configPath);
    }

    private void appendBaseModules(
        StringBuilder builder,
        List<ModuleDescriptor> descriptors,
        Map<ModuleId, Boolean> desiredStates,
        Map<ModuleId, Set<ModuleId>> dependents
    ) {
        List<ModuleDescriptor> ordered = descriptors.stream()
            .sorted(Comparator.comparing(descriptor -> descriptor.id().value()))
            .toList();
        for (ModuleDescriptor descriptor : ordered) {
            Set<ModuleId> usedBy = dependents.getOrDefault(descriptor.id(), Set.of());
            builder.append("  # used by: ");
            builder.append(usedBy.isEmpty()
                ? "none"
                : usedBy.stream().map(ModuleId::value).sorted().collect(Collectors.joining(", ")));
            builder.append("\n");
            builder.append("  # depends on: ");
            builder.append(descriptor.dependencies().isEmpty()
                ? "none"
                : descriptor.dependencies().stream().map(ModuleId::value).sorted().collect(Collectors.joining(", ")));
            builder.append("\n");
            boolean enabled = desiredStates.getOrDefault(descriptor.id(), true);
            builder.append("  ").append(descriptor.id().value()).append(": ").append(enabled).append("\n");
        }
    }

    private void appendCategorizedModules(
        StringBuilder builder,
        List<ModuleDescriptor> descriptors,
        Map<ModuleId, Boolean> desiredStates,
        boolean defaultEnabled
    ) {
        Map<ModuleCategory, List<ModuleDescriptor>> byCategory = descriptors.stream()
            .collect(Collectors.groupingBy(ModuleDescriptor::category, LinkedHashMap::new, Collectors.toList()));

        LinkedHashMap<ModuleCategory, List<ModuleDescriptor>> orderedCategories = new LinkedHashMap<>();
        for (ModuleCategory category : ModuleCategory.values()) {
            List<ModuleDescriptor> inCategory = byCategory.get(category);
            if (inCategory != null) {
                orderedCategories.put(category, inCategory);
            }
        }

        int remaining = orderedCategories.size();
        for (Map.Entry<ModuleCategory, List<ModuleDescriptor>> entry : orderedCategories.entrySet()) {
            ModuleCategory category = entry.getKey();
            List<ModuleDescriptor> categoryDescriptors = entry.getValue().stream()
                .sorted(Comparator.comparing(descriptor -> descriptor.id().value()))
                .toList();
            builder.append("  ").append(category.configKey()).append(":\n");
            for (ModuleDescriptor descriptor : categoryDescriptors) {
                builder.append("    # depends on: ");
                builder.append(descriptor.dependencies().isEmpty()
                    ? "none"
                    : descriptor.dependencies().stream().map(ModuleId::value).sorted().collect(Collectors.joining(", ")));
                builder.append("\n");
                boolean enabled = desiredStates.getOrDefault(descriptor.id(), defaultEnabled);
                builder.append("    ").append(descriptor.id().value()).append(": ").append(enabled).append("\n");
            }
            remaining--;
            if (remaining > 0) {
                builder.append("\n");
            }
        }
    }

    private String optionalModuleKey(ModuleDescriptor descriptor) {
        return MODULES_KEY + "." + descriptor.category().configKey() + "." + descriptor.id().value();
    }

}
