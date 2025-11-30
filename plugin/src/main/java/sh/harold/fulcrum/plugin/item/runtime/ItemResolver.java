package sh.harold.fulcrum.plugin.item.runtime;

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

    public ItemResolver(Plugin plugin, ItemRegistry registry, VanillaWrapperFactory wrapperFactory) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.wrapperFactory = Objects.requireNonNull(wrapperFactory, "wrapperFactory");
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
        if (stack == null || id == null) {
            return stack;
        }
        ItemStack clone = stack.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private String readId(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(idKey, PersistentDataType.STRING);
    }
}
