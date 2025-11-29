package sh.harold.fulcrum.plugin.unlockable;

import java.util.Objects;
import java.util.Optional;

public record PlayerUnlockable(UnlockableDefinition definition, int tier, boolean enabled) {

    public PlayerUnlockable {
        Objects.requireNonNull(definition, "definition");
        if (tier < 0) {
            throw new IllegalArgumentException("Tier cannot be negative for " + definition.id());
        }
        int clampedTier = Math.min(tier, definition.maxTier());
        tier = clampedTier;
        boolean resolvedEnabled = clampedTier > 0 && (!definition.toggleable() || enabled);
        enabled = resolvedEnabled;
    }

    public boolean unlocked() {
        return tier > 0;
    }

    public Optional<UnlockableTier> activeTier() {
        if (!unlocked()) {
            return Optional.empty();
        }
        return definition.tier(tier());
    }

    public boolean canToggle() {
        return unlocked() && definition.toggleable();
    }
}
