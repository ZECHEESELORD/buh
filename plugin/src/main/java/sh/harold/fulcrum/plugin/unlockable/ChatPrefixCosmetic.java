package sh.harold.fulcrum.plugin.unlockable;

import java.util.Objects;

public record ChatPrefixCosmetic(UnlockableDefinition definition, String prefix) implements Cosmetic {

    public ChatPrefixCosmetic {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(prefix, "prefix");
        if (definition.type() != UnlockableType.COSMETIC) {
            throw new IllegalArgumentException("Chat prefixes must be cosmetics: " + definition.id());
        }
        prefix = prefix.trim();
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("Chat prefix cannot be blank for " + definition.id());
        }
    }

    @Override
    public CosmeticSection section() {
        return CosmeticSection.CHAT_PREFIX;
    }
}
