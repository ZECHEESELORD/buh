package sh.harold.fulcrum.plugin.unlockable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PlayerCosmeticLoadout(Map<CosmeticSection, UnlockableId> equipped) {

    public PlayerCosmeticLoadout {
        Objects.requireNonNull(equipped, "equipped");
        equipped = Map.copyOf(equipped);
    }

    public Optional<UnlockableId> equipped(CosmeticSection section) {
        Objects.requireNonNull(section, "section");
        return Optional.ofNullable(equipped.get(section));
    }

    public boolean isEquipped(CosmeticSection section, UnlockableId id) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(id, "id");
        return id.equals(equipped.get(section));
    }

    public static PlayerCosmeticLoadout empty() {
        return new PlayerCosmeticLoadout(Map.of());
    }
}
