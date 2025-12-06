package sh.harold.fulcrum.plugin.unlockable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record PlayerCosmeticLoadout(Map<CosmeticSection, Set<UnlockableId>> equipped) {

    public PlayerCosmeticLoadout {
        Objects.requireNonNull(equipped, "equipped");
        equipped = equipped.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Set.copyOf(entry.getValue())
            ));
    }

    public Optional<UnlockableId> equipped(CosmeticSection section) {
        Objects.requireNonNull(section, "section");
        return equippedAll(section).stream().findFirst();
    }

    public Set<UnlockableId> equippedAll(CosmeticSection section) {
        Objects.requireNonNull(section, "section");
        return equipped.getOrDefault(section, Set.of());
    }

    public boolean isEquipped(CosmeticSection section, UnlockableId id) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(id, "id");
        return equippedAll(section).contains(id);
    }

    public static PlayerCosmeticLoadout empty() {
        return new PlayerCosmeticLoadout(Map.of());
    }
}
