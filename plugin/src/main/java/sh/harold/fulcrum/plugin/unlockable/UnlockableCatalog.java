package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class UnlockableCatalog {

    public static final UnlockableId POCKET_CRAFTER = UnlockableId.of("pocket-crafter");
    public static final UnlockableId MENU_SKIN_VERDANT = UnlockableId.of("menu-skin-verdant");
    public static final UnlockableId PARTICLE_TRAIL_AURORA = UnlockableId.of("particle-trail-aurora");
    public static final UnlockableId CHAT_PREFIX_SCOUT = UnlockableId.of("chat-prefix-scout");
    public static final UnlockableId STATUS_EXPLORER = UnlockableId.of("status-explorer");

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
        UnlockableDefinition verdantMenu = new UnlockableDefinition(
            MENU_SKIN_VERDANT,
            UnlockableType.COSMETIC,
            "Verdant Menu Skin",
            "Wrap the player menu in mossy greens and warm planks.",
            List.of(new UnlockableTier(
                1,
                "Verdant Menu Skin",
                "Unlocks a lush player menu frame.",
                150L,
                Map.of()
            )),
            Material.MOSS_BLOCK,
            false
        );
        registry.register(verdantMenu);
        cosmeticRegistry.register(new MenuSkinCosmetic(verdantMenu, "verdant-menu"));

        UnlockableDefinition auroraTrail = new UnlockableDefinition(
            PARTICLE_TRAIL_AURORA,
            UnlockableType.COSMETIC,
            "Aurora Trail",
            "Leave a pastel ribbon of particles while sprinting.",
            List.of(new UnlockableTier(
                1,
                "Aurora Trail",
                "Paint your path with shimmer.",
                400L,
                Map.of()
            )),
            Material.FIREWORK_ROCKET,
            false
        );
        registry.register(auroraTrail);
        cosmeticRegistry.register(new ParticleTrailCosmetic(auroraTrail, player -> player.getWorld().spawnParticle(
            Particle.END_ROD,
            player.getLocation().add(0, 0.15, 0),
            6,
            0.25,
            0.1,
            0.25,
            0.01
        )));

        UnlockableDefinition scoutPrefix = new UnlockableDefinition(
            CHAT_PREFIX_SCOUT,
            UnlockableType.COSMETIC,
            "Scout Prefix",
            "Add a crisp scout tag to your chat handle.",
            List.of(new UnlockableTier(
                1,
                "Scout Prefix",
                "Unlocks a simple [Scout] prefix.",
                120L,
                Map.of()
            )),
            Material.NAME_TAG,
            false
        );
        registry.register(scoutPrefix);
        cosmeticRegistry.register(new ChatPrefixCosmetic(scoutPrefix, "[Scout]"));

        UnlockableDefinition explorerStatus = new UnlockableDefinition(
            STATUS_EXPLORER,
            UnlockableType.COSMETIC,
            "Explorer Status",
            "Broadcast that you live for wandering.",
            List.of(new UnlockableTier(
                1,
                "Explorer Status",
                "Unlocks the Explorer status line.",
                100L,
                Map.of()
            )),
            Material.COMPASS,
            false
        );
        registry.register(explorerStatus);
        cosmeticRegistry.register(new StatusCosmetic(explorerStatus, "Explorer"));
    }
}
