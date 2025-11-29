package sh.harold.fulcrum.plugin.unlockable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class UnlockableCatalog {

    public static final UnlockableId POCKET_CRAFTER = UnlockableId.of("pocket-crafter");

    private UnlockableCatalog() {
    }

    public static void registerDefaults(UnlockableRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(new UnlockableDefinition(
            POCKET_CRAFTER,
            UnlockableType.PERK,
            "Pocket Crafter",
            "Summon a crafting grid without hunting for a table.",
            List.of(new UnlockableTier(
                1,
                "Pocket Crafter",
                "Unlock /craft.",
                300L,
                Map.of("command", "craft")
            )),
            org.bukkit.Material.CRAFTING_TABLE
        ));
    }
}
