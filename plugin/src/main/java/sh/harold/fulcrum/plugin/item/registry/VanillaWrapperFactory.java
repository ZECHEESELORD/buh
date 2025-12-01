package sh.harold.fulcrum.plugin.item.registry;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.model.ItemRarity;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;

import java.util.Map;

public final class VanillaWrapperFactory {

    public CustomItem wrap(Material material, String id) {
        VisualComponent visual = new VisualComponent(Component.translatable(material.translationKey()), null, rarityFor(material));
        return CustomItem.builder(id)
            .material(material)
            .category(ItemCategory.fromMaterial(material))
            .component(ComponentType.VISUAL, visual)
            .build();
    }

    public CustomItem wrap(ItemStack stack) {
        Material material = stack == null ? Material.AIR : stack.getType();
        String id = "vanilla:" + material.getKey().getKey();
        return wrap(material, id);
    }

    private ItemRarity rarityFor(Material material) {
        return switch (material) {
            case NETHERITE_SWORD, NETHERITE_AXE, NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS,
                 NETHERITE_BOOTS, NETHERITE_HOE, NETHERITE_PICKAXE, NETHERITE_SHOVEL -> ItemRarity.LEGENDARY;
            case DIAMOND_SWORD, DIAMOND_AXE, DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS,
                 DIAMOND_BOOTS, DIAMOND_HOE, DIAMOND_PICKAXE, DIAMOND_SHOVEL -> ItemRarity.EPIC;
            case GOLDEN_SWORD, GOLDEN_AXE, GOLDEN_HELMET, GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS,
                 GOLDEN_BOOTS, GOLDEN_HOE, GOLDEN_PICKAXE, GOLDEN_SHOVEL,
                 BOW, CROSSBOW, TRIDENT -> ItemRarity.RARE;
            case IRON_SWORD, IRON_AXE, IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS,
                 IRON_BOOTS, IRON_HOE, IRON_PICKAXE, IRON_SHOVEL,
                 CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS,
                 TURTLE_HELMET, ELYTRA, SHIELD -> ItemRarity.UNCOMMON;
            default -> ItemRarity.COMMON;
        };
    }
}
