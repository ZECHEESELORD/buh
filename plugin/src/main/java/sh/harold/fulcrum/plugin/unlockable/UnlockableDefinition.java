package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.Material;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record UnlockableDefinition(
    UnlockableId id,
    UnlockableType type,
    String name,
    String description,
    List<UnlockableTier> tiers,
    Material displayMaterial,
    boolean toggleable
) {

    public UnlockableDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(displayMaterial, "displayMaterial");
        name = name.trim();
        description = description.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Unlockable name must not be blank for " + id);
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("Unlockable description must not be blank for " + id);
        }
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("Unlockable must declare at least one tier: " + id);
        }

        Map<Integer, Long> duplicates = tiers.stream()
            .collect(Collectors.groupingBy(UnlockableTier::tier, Collectors.counting()))
            .entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Unlockable tiers must be unique per level: " + id + " duplicates=" + duplicates.keySet());
        }

        Set<Integer> tierNumbers = tiers.stream()
            .map(UnlockableTier::tier)
            .collect(Collectors.toSet());
        if (!tierNumbers.contains(1)) {
            throw new IllegalArgumentException("Unlockable must start at tier 1: " + id);
        }

        List<UnlockableTier> ordered = tiers.stream()
            .sorted(Comparator.comparingInt(UnlockableTier::tier))
            .toList();
        tiers = List.copyOf(ordered);

        if (displayMaterial.isAir()) {
            throw new IllegalArgumentException("Unlockable display material cannot be air: " + id);
        }
    }

    public UnlockableDefinition(
        UnlockableId id,
        UnlockableType type,
        String name,
        String description,
        List<UnlockableTier> tiers,
        Material displayMaterial
    ) {
        this(id, type, name, description, tiers, displayMaterial, true);
    }

    public Optional<UnlockableTier> tier(int tier) {
        return tiers.stream()
            .filter(candidate -> candidate.tier() == tier)
            .findFirst();
    }

    public boolean singleTier() {
        return tiers.size() == 1;
    }

    public int maxTier() {
        return tiers.getLast().tier();
    }
}
