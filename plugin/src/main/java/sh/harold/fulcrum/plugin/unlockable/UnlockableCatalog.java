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
    public static final UnlockableId MENU_SKIN_BLAZE = UnlockableId.of("menu-skin-blaze");
    public static final UnlockableId MENU_SKIN_COMPASS = UnlockableId.of("menu-skin-compass");
    public static final UnlockableId MENU_SKIN_CLOCK = UnlockableId.of("menu-skin-clock");
    public static final UnlockableId MENU_SKIN_CHORUS = UnlockableId.of("menu-skin-chorus");
    public static final UnlockableId MENU_SKIN_FISH = UnlockableId.of("menu-skin-fish");
    public static final UnlockableId MENU_SKIN_PUFFER = UnlockableId.of("menu-skin-puffer");
    public static final UnlockableId MENU_SKIN_DANDELION = UnlockableId.of("menu-skin-dandelion");
    public static final UnlockableId MENU_SKIN_POPPY = UnlockableId.of("menu-skin-poppy");
    public static final UnlockableId MENU_SKIN_BLUE_ORCHID = UnlockableId.of("menu-skin-blue-orchid");
    public static final UnlockableId MENU_SKIN_ALLIUM = UnlockableId.of("menu-skin-allium");
    public static final UnlockableId MENU_SKIN_AZURE_BLUET = UnlockableId.of("menu-skin-azure-bluet");
    public static final UnlockableId MENU_SKIN_RED_TULIP = UnlockableId.of("menu-skin-red-tulip");
    public static final UnlockableId MENU_SKIN_ORANGE_TULIP = UnlockableId.of("menu-skin-orange-tulip");
    public static final UnlockableId MENU_SKIN_WHITE_TULIP = UnlockableId.of("menu-skin-white-tulip");
    public static final UnlockableId MENU_SKIN_PINK_TULIP = UnlockableId.of("menu-skin-pink-tulip");
    public static final UnlockableId MENU_SKIN_OXEYE_DAISY = UnlockableId.of("menu-skin-oxeye-daisy");
    public static final UnlockableId MENU_SKIN_CORNFLOWER = UnlockableId.of("menu-skin-cornflower");
    public static final UnlockableId MENU_SKIN_LILY_VALLEY = UnlockableId.of("menu-skin-lily-valley");
    public static final UnlockableId MENU_SKIN_WITHER_ROSE = UnlockableId.of("menu-skin-wither-rose");
    public static final UnlockableId MENU_SKIN_SUNFLOWER = UnlockableId.of("menu-skin-sunflower");
    public static final UnlockableId MENU_SKIN_LILAC = UnlockableId.of("menu-skin-lilac");
    public static final UnlockableId MENU_SKIN_ROSE_BUSH = UnlockableId.of("menu-skin-rose-bush");
    public static final UnlockableId MENU_SKIN_PEONY = UnlockableId.of("menu-skin-peony");
    public static final UnlockableId MENU_SKIN_TORCHFLOWER = UnlockableId.of("menu-skin-torchflower");
    public static final UnlockableId MENU_SKIN_PINK_PETALS = UnlockableId.of("menu-skin-pink-petals");
    public static final UnlockableId CHAT_PREFIX_OSU_RANK = UnlockableId.of("chat-prefix-osu-rank");

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
            "Summon a crafting grid anywhere with /craft; no table required.",
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
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_BLAZE, "Blaze Powder Menu Skin", "Dust your hotbar with ember sparks.", Material.BLAZE_POWDER);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_COMPASS, "Compass Menu Skin", "Pin your menu to true north.", Material.COMPASS);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_CLOCK, "Clock Menu Skin", "Wrap the menu in ticking gold.", Material.CLOCK);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_CHORUS, "Chorus Menu Skin", "Let your menu hum with void-touched vines.", Material.CHORUS_PLANT);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_FISH, "Fishy Menu Skin", "It smells like the docks and clicks like a catch.", Material.TROPICAL_FISH);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_PUFFER, "Pufferfish Menu Skin", "The client is lied to via packets; behold the true menu.", Material.PUFFERFISH);

        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_DANDELION, "Dandelion Menu Skin", "Sunny petals tucked into your menu slot.", Material.DANDELION);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_POPPY, "Poppy Menu Skin", "A bright red bloom on your hotbar anchor.", Material.POPPY);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_BLUE_ORCHID, "Blue Orchid Menu Skin", "Cool blue blossoms for chilled clicks.", Material.BLUE_ORCHID);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_ALLIUM, "Allium Menu Skin", "Purple pom-poms to crown your menu.", Material.ALLIUM);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_AZURE_BLUET, "Azure Bluet Menu Skin", "Gentle sky-tinged petals for the menu.", Material.AZURE_BLUET);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_RED_TULIP, "Red Tulip Menu Skin", "A bold tulip stem wrapped around the slot.", Material.RED_TULIP);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_ORANGE_TULIP, "Orange Tulip Menu Skin", "Warm orange petals guarding your menu item.", Material.ORANGE_TULIP);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_WHITE_TULIP, "White Tulip Menu Skin", "Clean white tulip trim for the menu icon.", Material.WHITE_TULIP);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_PINK_TULIP, "Pink Tulip Menu Skin", "Soft pink tulip accents for gentle clicks.", Material.PINK_TULIP);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_OXEYE_DAISY, "Oxeye Daisy Menu Skin", "Cheery daisies framing your hotbar entry.", Material.OXEYE_DAISY);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_CORNFLOWER, "Cornflower Menu Skin", "Blue cornflowers circling the menu item.", Material.CORNFLOWER);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_LILY_VALLEY, "Lily of the Valley Menu Skin", "Delicate bells for a hush over your menu.", Material.LILY_OF_THE_VALLEY);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_WITHER_ROSE, "Wither Rose Menu Skin", "A thorny warning on your menu anchor.", Material.WITHER_ROSE);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_SUNFLOWER, "Sunflower Menu Skin", "Tall sunbeams wrapped into your hotbar slot.", Material.SUNFLOWER);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_LILAC, "Lilac Menu Skin", "Soft lilac spires guarding the menu item.", Material.LILAC);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_ROSE_BUSH, "Rose Bush Menu Skin", "Layered roses around your menu button.", Material.ROSE_BUSH);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_PEONY, "Peony Menu Skin", "Fluffy peony blooms around the slot.", Material.PEONY);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_TORCHFLOWER, "Torchflower Menu Skin", "Warm torchflower glow on your menu.", Material.TORCHFLOWER);
        registerMenuSkin(registry, cosmeticRegistry, MENU_SKIN_PINK_PETALS, "Pink Petals Menu Skin", "Scatter petals over the true menu item.", Material.PINK_PETALS);

        UnlockableDefinition osuRankBadge = new UnlockableDefinition(
            CHAT_PREFIX_OSU_RANK,
            UnlockableType.COSMETIC,
            "osu! Rank Badge",
            "Show your osu! global rank in chat. A small bracketed flex; tidy, tasteful, and slightly smug.",
            List.of(new UnlockableTier(
                1,
                "osu! Rank Badge",
                "Unlock the rank badge for chat.",
                25L,
                Map.of()
            )),
            Material.NAME_TAG,
            false
        );
        registry.register(osuRankBadge);
        cosmeticRegistry.register(new OsuRankChatPrefixCosmetic(osuRankBadge));

        UnlockableDefinition sit = new UnlockableDefinition(
            SIT_ACTION,
            UnlockableType.COSMETIC,
            "Sit Action",
            "Right-click a block with headroom to plop down.",
            List.of(new UnlockableTier(
                1,
                "Sit",
                "Unlocks the sit emote.",
                81L,
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
                288L,
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
                900L,
                Map.of()
            )),
            Material.SADDLE,
            false
        );
        registry.register(ride);
        cosmeticRegistry.register(new ActionCosmetic(ride, "ride"));
    }

    private static void registerMenuSkin(
        UnlockableRegistry registry,
        CosmeticRegistry cosmeticRegistry,
        UnlockableId id,
        String name,
        String description,
        Material displayMaterial
    ) {
        UnlockableDefinition definition = new UnlockableDefinition(
            id,
            UnlockableType.COSMETIC,
            name,
            description,
            List.of(new UnlockableTier(1, name, description, 25L, Map.of())),
            displayMaterial,
            false
        );
        registry.register(definition);
        cosmeticRegistry.register(new MenuSkinCosmetic(definition, id.value()));
    }
}
