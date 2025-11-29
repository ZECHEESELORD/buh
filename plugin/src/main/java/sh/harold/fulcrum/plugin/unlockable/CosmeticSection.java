package sh.harold.fulcrum.plugin.unlockable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public enum CosmeticSection {
    PLAYER_MENU_SKIN("menu-skin"),
    PARTICLE_TRAIL("particle-trail"),
    CHAT_PREFIX("chat-prefix"),
    STATUS("status");

    private final String dataKey;

    CosmeticSection(String dataKey) {
        this.dataKey = dataKey;
    }

    public String dataKey() {
        return dataKey;
    }

    public static Optional<CosmeticSection> fromKey(String key) {
        Objects.requireNonNull(key, "key");
        return Arrays.stream(values())
            .filter(section -> section.dataKey.equalsIgnoreCase(key) || section.name().equalsIgnoreCase(key))
            .findFirst();
    }
}
