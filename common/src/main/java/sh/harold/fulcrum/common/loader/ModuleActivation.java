package sh.harold.fulcrum.common.loader;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ModuleActivation(Map<ModuleId, Boolean> desiredStates) {

    public ModuleActivation {
        Objects.requireNonNull(desiredStates, "desiredStates");
        desiredStates = Map.copyOf(desiredStates);
    }

    public boolean enabled(ModuleId moduleId) {
        return desiredStates.getOrDefault(moduleId, false);
    }

    public static ModuleActivation enableAll(Collection<ModuleId> moduleIds) {
        Map<ModuleId, Boolean> enabled = moduleIds.stream()
            .collect(Collectors.toMap(Function.identity(), ignored -> Boolean.TRUE, (first, second) -> first, LinkedHashMap::new));
        return new ModuleActivation(enabled);
    }
}
