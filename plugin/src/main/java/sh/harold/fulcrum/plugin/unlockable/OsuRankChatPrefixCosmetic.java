package sh.harold.fulcrum.plugin.unlockable;

import java.util.Objects;

public record OsuRankChatPrefixCosmetic(UnlockableDefinition definition) implements Cosmetic {

    public OsuRankChatPrefixCosmetic {
        Objects.requireNonNull(definition, "definition");
        if (definition.type() != UnlockableType.COSMETIC) {
            throw new IllegalArgumentException("osu! rank chat prefix must be a cosmetic: " + definition.id());
        }
    }

    @Override
    public CosmeticSection section() {
        return CosmeticSection.CHAT_PREFIX;
    }
}

