package sh.harold.fulcrum.plugin.unlockable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PlayerUnlockableState(Map<UnlockableId, PlayerUnlockable> unlockables) {

    public PlayerUnlockableState {
        Objects.requireNonNull(unlockables, "unlockables");
        unlockables = Map.copyOf(unlockables);
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
}
