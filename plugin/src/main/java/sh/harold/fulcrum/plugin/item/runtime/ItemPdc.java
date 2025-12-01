package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public final class ItemPdc {

    private final ItemDataKeys keys;

    public ItemPdc(Plugin plugin) {
        this.keys = new ItemDataKeys(plugin);
    }

    public ItemStack setId(ItemStack stack, String id) {
        if (stack == null || id == null) {
            return stack;
        }
        ItemStack clone = stack.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(keys.version(), PersistentDataType.INTEGER, 1);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    public Optional<String> readId(ItemStack stack) {
        return read(stack, keys.itemId(), PersistentDataType.STRING);
    }

    public ItemStack setInt(ItemStack stack, NamespacedKey key, int value) {
        return write(stack, key, PersistentDataType.INTEGER, value);
    }

    public Optional<Integer> readInt(ItemStack stack, NamespacedKey key) {
        return read(stack, key, PersistentDataType.INTEGER);
    }

    public ItemStack setString(ItemStack stack, NamespacedKey key, String value) {
        return write(stack, key, PersistentDataType.STRING, value);
    }

    public Optional<String> readString(ItemStack stack, NamespacedKey key) {
        return read(stack, key, PersistentDataType.STRING);
    }

    public ItemStack clear(ItemStack stack, NamespacedKey key) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(key)) {
            container.remove(key);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private <T, Z> ItemStack write(ItemStack stack, NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.getPersistentDataContainer().set(key, type, value);
        stack.setItemMeta(meta);
        return stack;
    }

    private <T, Z> Optional<Z> read(ItemStack stack, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (stack == null) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Z value = container.get(key, type);
        return Optional.ofNullable(value);
    }
}
