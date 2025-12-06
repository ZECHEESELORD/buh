package sh.harold.fulcrum.plugin.unlockable;

import java.util.Objects;

public record ActionCosmetic(UnlockableDefinition definition, String actionKey) implements Cosmetic {

    public ActionCosmetic {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(actionKey, "actionKey");
        if (definition.type() != UnlockableType.COSMETIC) {
            throw new IllegalArgumentException("Actions must be cosmetics: " + definition.id());
        }
        actionKey = actionKey.trim();
        if (actionKey.isEmpty()) {
            throw new IllegalArgumentException("Action key cannot be blank for " + definition.id());
        }
    }

    @Override
    public CosmeticSection section() {
        return CosmeticSection.ACTIONS;
    }
}
