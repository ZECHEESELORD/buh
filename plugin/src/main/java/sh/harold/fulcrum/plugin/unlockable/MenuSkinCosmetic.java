package sh.harold.fulcrum.plugin.unlockable;

import java.util.Objects;

public record MenuSkinCosmetic(UnlockableDefinition definition, String skinKey) implements Cosmetic {

    public MenuSkinCosmetic {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(skinKey, "skinKey");
        if (definition.type() != UnlockableType.COSMETIC) {
            throw new IllegalArgumentException("Menu skins must be cosmetics: " + definition.id());
        }
        skinKey = skinKey.trim();
        if (skinKey.isEmpty()) {
            throw new IllegalArgumentException("Menu skin key cannot be blank for " + definition.id());
        }
    }

    @Override
    public CosmeticSection section() {
        return CosmeticSection.PLAYER_MENU_SKIN;
    }
}
