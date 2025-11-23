package sh.harold.fulcrum.common.loader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ModuleGraph {

    private final Map<ModuleId, Set<ModuleId>> dependencies;
    private final List<ModuleId> loadOrder;

    private ModuleGraph(Map<ModuleId, Set<ModuleId>> dependencies, List<ModuleId> loadOrder) {
        this.dependencies = dependencies;
        this.loadOrder = loadOrder;
    }

    public static ModuleGraph build(Collection<ModuleDescriptor> descriptors) {
        Map<ModuleId, Set<ModuleId>> dependencies = new HashMap<>();
        for (ModuleDescriptor descriptor : descriptors) {
            if (dependencies.putIfAbsent(descriptor.id(), new HashSet<>(descriptor.dependencies())) != null) {
                throw new ModuleGraphException("Duplicate module id encountered: " + descriptor.id());
            }
        }

        Set<ModuleId> missingModules = dependencies.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
                .filter(dependency -> !dependencies.containsKey(dependency)))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!missingModules.isEmpty()) {
            throw new ModuleGraphException("Missing dependencies for modules: " + missingModules);
        }

        Map<ModuleId, Integer> incomingEdgeCount = new HashMap<>();
        Map<ModuleId, Set<ModuleId>> dependents = new HashMap<>();
        dependencies.forEach((id, moduleDependencies) -> {
            incomingEdgeCount.put(id, moduleDependencies.size());
            for (ModuleId dependency : moduleDependencies) {
                dependents.computeIfAbsent(dependency, ignored -> new HashSet<>()).add(id);
            }
        });

        Deque<ModuleId> readyModules = new ArrayDeque<>();
        incomingEdgeCount.forEach((id, edgeCount) -> {
            if (edgeCount == 0) {
                readyModules.add(id);
            }
        });

        List<ModuleId> loadOrder = new ArrayList<>();
        while (!readyModules.isEmpty()) {
            ModuleId module = readyModules.removeFirst();
            loadOrder.add(module);
            for (ModuleId dependent : dependents.getOrDefault(module, Set.of())) {
                int updatedEdges = incomingEdgeCount.computeIfPresent(dependent, (ignored, edges) -> edges - 1);
                if (updatedEdges == 0) {
                    readyModules.add(dependent);
                }
            }
        }

        if (loadOrder.size() != dependencies.size()) {
            throw new ModuleGraphException("Circular dependency detected across modules: " + dependencies.keySet());
        }

        Map<ModuleId, Set<ModuleId>> immutableDependencies = dependencies.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        return new ModuleGraph(immutableDependencies, List.copyOf(loadOrder));
    }

    public List<ModuleId> loadOrder() {
        return loadOrder;
    }

    public Set<ModuleId> dependenciesFor(ModuleId moduleId) {
        return dependencies.getOrDefault(moduleId, Set.of());
    }
}
