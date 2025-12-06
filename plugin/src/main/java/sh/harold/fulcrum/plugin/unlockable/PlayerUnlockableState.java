package sh.harold.fulcrum.plugin.unlockable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record PlayerUnlockableState(Map<UnlockableId, PlayerUnlockable> unlockables, PlayerCosmeticLoadout cosmetics) {

    public PlayerUnlockableState {
        Objects.requireNonNull(unlockables, "unlockables");
        Objects.requireNonNull(cosmetics, "cosmetics");
        unlockables = Map.copyOf(unlockables);
    }

    public PlayerUnlockableState(Map<UnlockableId, PlayerUnlockable> unlockables) {
        this(unlockables, PlayerCosmeticLoadout.empty());
    }

    public Optional<PlayerUnlockable> unlockable(UnlockableId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(unlockables.get(id));
    }

    public boolean isEnabled(UnlockableId id) {
        return unlockable(id).map(PlayerUnlockable::enabled).orElse(false);
    }

    public List<PlayerUnlockable> enabled() {
        return unlockables.values().stream()
            .filter(PlayerUnlockable::enabled)
            .toList();
    }

    public List<PlayerUnlockable> unlocked() {
        return unlockables.values().stream()
            .filter(PlayerUnlockable::unlocked)
            .toList();
    }

    public Optional<UnlockableId> equippedCosmetic(CosmeticSection section) {
        return cosmetics.equipped(section);
    }

    public Set<UnlockableId> equippedCosmetics(CosmeticSection section) {
        return cosmetics.equippedAll(section);
    }
}
