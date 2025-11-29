package sh.harold.fulcrum.plugin.unlockable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticRegistry {

    private final Map<UnlockableId, Cosmetic> cosmetics = new ConcurrentHashMap<>();

    public Cosmetic register(Cosmetic cosmetic) {
        Objects.requireNonNull(cosmetic, "cosmetic");
        UnlockableDefinition definition = cosmetic.definition();
        if (definition.type() != UnlockableType.COSMETIC) {
            throw new IllegalArgumentException("Cosmetic definitions must use UnlockableType.COSMETIC: " + definition.id());
        }
        Cosmetic existing = cosmetics.putIfAbsent(definition.id(), cosmetic);
        if (existing != null) {
            throw new IllegalStateException("Cosmetic already registered: " + definition.id());
        }
        return cosmetic;
    }

    public Optional<Cosmetic> cosmetic(UnlockableId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(cosmetics.get(id));
    }

    public List<Cosmetic> cosmetics() {
        return cosmetics.values().stream()
            .sorted(Comparator.comparing(cosmetic -> cosmetic.id().value()))
            .toList();
    }

    public List<Cosmetic> cosmetics(CosmeticSection section) {
        Objects.requireNonNull(section, "section");
        return cosmetics.values().stream()
            .filter(cosmetic -> cosmetic.section() == section)
            .sorted(Comparator.comparing(cosmetic -> cosmetic.id().value()))
            .toList();
    }
}
