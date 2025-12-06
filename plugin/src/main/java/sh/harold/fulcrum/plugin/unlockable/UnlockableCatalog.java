package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class UnlockableCatalog {

    public static final UnlockableId POCKET_CRAFTER = UnlockableId.of("pocket-crafter");
    public static final UnlockableId SIT_ACTION = UnlockableId.of("action-sit");
    public static final UnlockableId CRAWL_ACTION = UnlockableId.of("action-crawl");
    public static final UnlockableId RIDE_ACTION = UnlockableId.of("action-ride");

    private UnlockableCatalog() {
    }

    public static void registerDefaults(UnlockableRegistry registry, CosmeticRegistry cosmeticRegistry) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(cosmeticRegistry, "cosmeticRegistry");
        registerPerks(registry);
        registerCosmetics(registry, cosmeticRegistry);
    }

    private static void registerPerks(UnlockableRegistry registry) {
        registry.register(new UnlockableDefinition(
            POCKET_CRAFTER,
            UnlockableType.PERK,
            "Pocket Crafter",
            "Summon a crafting grid without hunting for a table.",
            List.of(new UnlockableTier(
                1,
                "Pocket Crafter",
                "Unlock /craft.",
                576L,
                Map.of("command", "craft")
            )),
            Material.CRAFTING_TABLE
        ));
    }

    private static void registerCosmetics(UnlockableRegistry registry, CosmeticRegistry cosmeticRegistry) {
        UnlockableDefinition sit = new UnlockableDefinition(
            SIT_ACTION,
            UnlockableType.COSMETIC,
            "Sit Action",
            "Right-click a block with headroom to plop down.",
            List.of(new UnlockableTier(
                1,
                "Sit",
                "Unlocks the sit emote.",
                500L,
                Map.of()
            )),
            Material.OAK_STAIRS,
            false
        );
        registry.register(sit);
        cosmeticRegistry.register(new ActionCosmetic(sit, "sit"));

        UnlockableDefinition crawl = new UnlockableDefinition(
            CRAWL_ACTION,
            UnlockableType.COSMETIC,
            "Crawl Action",
            "Double-tap sneak to drop into a crawl.",
            List.of(new UnlockableTier(
                1,
                "Crawl",
                "Unlocks the crawl emote.",
                750L,
                Map.of()
            )),
            Material.TURTLE_HELMET,
            false
        );
        registry.register(crawl);
        cosmeticRegistry.register(new ActionCosmetic(crawl, "crawl"));

        UnlockableDefinition ride = new UnlockableDefinition(
            RIDE_ACTION,
            UnlockableType.COSMETIC,
            "Ride Action",
            "Sneak + right-click a player to climb aboard.",
            List.of(new UnlockableTier(
                1,
                "Ride",
                "Unlocks stacking piggyback rides.",
                1350L,
                Map.of()
            )),
            Material.SADDLE,
            false
        );
        registry.register(ride);
        cosmeticRegistry.register(new ActionCosmetic(ride, "ride"));
    }
}
