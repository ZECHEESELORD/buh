package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.Material;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.item.ItemKeys;
import sh.harold.fulcrum.plugin.item.registry.ItemRegistry;
import sh.harold.fulcrum.plugin.item.registry.VanillaWrapperFactory;

import java.util.Objects;
import java.util.Optional;

public final class ItemResolver {

    private final ItemRegistry registry;
    private final VanillaWrapperFactory wrapperFactory;
    private final NamespacedKey idKey;
    private final ItemPdc itemPdc;

    public ItemResolver(Plugin plugin, ItemRegistry registry, VanillaWrapperFactory wrapperFactory, ItemPdc itemPdc) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.wrapperFactory = Objects.requireNonNull(wrapperFactory, "wrapperFactory");
        this.itemPdc = Objects.requireNonNull(itemPdc, "itemPdc");
        this.idKey = new ItemKeys(plugin).idKey();
    }

    public Optional<ItemInstance> resolve(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return Optional.empty();
        }
        String id = readId(stack);
        if (id != null) {
            return registry.get(id)
                .map(definition -> new ItemInstance(definition, stack));
        }
        return Optional.of(new ItemInstance(registry.getOrCreateVanilla(stack.getType(), wrapperFactory), stack));
    }

    public ItemStack applyId(ItemStack stack, String id) {
        return itemPdc.setId(stack, id);
    }

    private String readId(ItemStack stack) {
        return itemPdc.readId(stack).orElse(null);
    }
}
