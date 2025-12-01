package sh.harold.fulcrum.plugin.item.model;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

public enum ItemCategory {

    SWORD(SlotGroup.MAIN_HAND),
    AXE(SlotGroup.MAIN_HAND),
    BOW(SlotGroup.MAIN_HAND),
    WAND(SlotGroup.MAIN_HAND),
    PICKAXE(SlotGroup.MAIN_HAND),
    SHOVEL(SlotGroup.MAIN_HAND),
    HOE(SlotGroup.MAIN_HAND),
    FISHING_ROD(SlotGroup.MAIN_HAND),
    TRIDENT(SlotGroup.MAIN_HAND),
    HELMET(SlotGroup.HELMET, EquipmentSlot.HEAD),
    CHESTPLATE(SlotGroup.CHESTPLATE, EquipmentSlot.CHEST),
    LEGGINGS(SlotGroup.LEGGINGS, EquipmentSlot.LEGS),
    BOOTS(SlotGroup.BOOTS, EquipmentSlot.FEET),
    ACCESSORY(SlotGroup.ACCESSORY),
    CONSUMABLE(SlotGroup.NONE),
    MATERIAL(SlotGroup.NONE);

    private final SlotGroup defaultSlot;
    private final EquipmentSlot vanillaSlot;

    ItemCategory(SlotGroup defaultSlot) {
        this(defaultSlot, null);
    }

    ItemCategory(SlotGroup defaultSlot, EquipmentSlot vanillaSlot) {
        this.defaultSlot = defaultSlot;
        this.vanillaSlot = vanillaSlot;
    }

    public SlotGroup defaultSlot() {
        return defaultSlot;
    }

    public EquipmentSlot vanillaSlot() {
        return vanillaSlot;
    }

    public static ItemCategory fromMaterial(Material material) {
        return switch (material) {
            case NETHERITE_SWORD, DIAMOND_SWORD, GOLDEN_SWORD, IRON_SWORD, STONE_SWORD, WOODEN_SWORD -> SWORD;
            case NETHERITE_AXE, DIAMOND_AXE, GOLDEN_AXE, IRON_AXE, STONE_AXE, WOODEN_AXE -> AXE;
            case BOW, CROSSBOW -> BOW;
            case TRIDENT -> TRIDENT;
            case FISHING_ROD -> FISHING_ROD;
            case NETHERITE_PICKAXE, DIAMOND_PICKAXE, GOLDEN_PICKAXE, IRON_PICKAXE, STONE_PICKAXE, WOODEN_PICKAXE -> PICKAXE;
            case NETHERITE_SHOVEL, DIAMOND_SHOVEL, GOLDEN_SHOVEL, IRON_SHOVEL, STONE_SHOVEL, WOODEN_SHOVEL -> SHOVEL;
            case NETHERITE_HOE, DIAMOND_HOE, GOLDEN_HOE, IRON_HOE, STONE_HOE, WOODEN_HOE -> HOE;
            case LEATHER_HELMET, CHAINMAIL_HELMET, IRON_HELMET, GOLDEN_HELMET, DIAMOND_HELMET,
                 NETHERITE_HELMET, TURTLE_HELMET, CARVED_PUMPKIN, PLAYER_HEAD, SKELETON_SKULL,
                 WITHER_SKELETON_SKULL, ZOMBIE_HEAD, CREEPER_HEAD, PIGLIN_HEAD, DRAGON_HEAD -> HELMET;
            case LEATHER_CHESTPLATE, CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE, GOLDEN_CHESTPLATE,
                 DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE, ELYTRA -> CHESTPLATE;
            case LEATHER_LEGGINGS, CHAINMAIL_LEGGINGS, IRON_LEGGINGS, GOLDEN_LEGGINGS,
                 DIAMOND_LEGGINGS, NETHERITE_LEGGINGS -> LEGGINGS;
            case LEATHER_BOOTS, CHAINMAIL_BOOTS, IRON_BOOTS, GOLDEN_BOOTS,
                 DIAMOND_BOOTS, NETHERITE_BOOTS -> BOOTS;
            default -> MATERIAL;
        };
    }
}
