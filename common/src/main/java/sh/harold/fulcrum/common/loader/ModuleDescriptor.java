package sh.harold.fulcrum.common.loader;

import java.util.Objects;
import java.util.Set;

public record ModuleDescriptor(ModuleId id, Set<ModuleId> dependencies) {

    public ModuleDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dependencies, "dependencies");
        dependencies = Set.copyOf(dependencies);
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("Module cannot depend on itself: " + id);
        }
    }

    public static ModuleDescriptor of(ModuleId id, ModuleId... dependencies) {
        return new ModuleDescriptor(id, Set.of(dependencies));
    }
}
