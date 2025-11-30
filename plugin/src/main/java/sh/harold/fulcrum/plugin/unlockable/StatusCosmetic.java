package sh.harold.fulcrum.plugin.unlockable;

import java.util.Objects;

public record StatusCosmetic(UnlockableDefinition definition, String status) implements Cosmetic {

    public StatusCosmetic {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(status, "status");
        if (definition.type() != UnlockableType.COSMETIC) {
            throw new IllegalArgumentException("Statuses must be cosmetics: " + definition.id());
        }
        status = status.trim();
        if (status.isEmpty()) {
            throw new IllegalArgumentException("Status cannot be blank for " + definition.id());
        }
    }

    @Override
    public CosmeticSection section() {
        return CosmeticSection.STATUS;
    }
}
