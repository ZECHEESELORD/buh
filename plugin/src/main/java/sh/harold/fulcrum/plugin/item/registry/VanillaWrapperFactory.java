package sh.harold.fulcrum.plugin.item.registry;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;

import java.util.Map;

public final class VanillaWrapperFactory {

    public CustomItem wrap(Material material, String id) {
        VisualComponent visual = new VisualComponent(Component.text(material.translationKey()), null, null);
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
}
