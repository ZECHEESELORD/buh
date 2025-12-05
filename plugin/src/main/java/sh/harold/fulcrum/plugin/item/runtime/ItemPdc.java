package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ItemPdc {

    private final ItemDataKeys keys;
    private final StatPdcCodec statCodec = new StatPdcCodec();
    private final EnchantPdcCodec enchantCodec = new EnchantPdcCodec();

    public ItemPdc(Plugin plugin) {
        this.keys = new ItemDataKeys(plugin);
    }

    public ItemDataKeys keys() {
        return keys;
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

    public ItemStack setIdInPlace(ItemStack stack, String id) {
        if (stack == null || id == null) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(keys.version(), PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public Optional<String> readId(ItemStack stack) {
        return read(stack, keys.itemId(), PersistentDataType.STRING);
    }

    public Optional<Integer> readVersion(ItemStack stack) {
        return readInt(stack, keys.version());
    }

    public ItemStack ensureInstanceId(ItemStack stack) {
        return ensureInstanceId(stack, UUID.randomUUID());
    }

    public ItemStack ensureInstanceId(ItemStack stack, UUID instanceId) {
        if (stack == null || instanceId == null) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(keys.instanceId(), PersistentDataType.STRING)) {
            return stack;
        }
        container.set(keys.instanceId(), PersistentDataType.STRING, instanceId.toString());
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<UUID> readInstanceId(ItemStack stack) {
        return read(stack, keys.instanceId(), PersistentDataType.STRING).map(UUID::fromString);
    }

    public ItemStack writeStats(ItemStack stack, Map<StatId, Double> stats) {
        if (stack == null || stats == null || stats.isEmpty()) {
            return stack;
        }
        String encoded = statCodec.encode(stats);
        return write(stack, keys.stats(), PersistentDataType.STRING, encoded);
    }

    public Optional<Map<StatId, Double>> readStats(ItemStack stack) {
        return read(stack, keys.stats(), PersistentDataType.STRING).map(statCodec::decode);
    }

    public ItemStack writeEnchants(ItemStack stack, Map<String, Integer> enchants) {
        if (stack == null || enchants == null) {
            return stack;
        }
        String encoded = enchantCodec.encode(enchants);
        return write(stack, keys.enchants(), PersistentDataType.STRING, encoded);
    }

    public Optional<Map<String, Integer>> readEnchants(ItemStack stack) {
        return read(stack, keys.enchants(), PersistentDataType.STRING).map(enchantCodec::decode);
    }

    public ItemStack clearEnchants(ItemStack stack) {
        return clear(stack, keys.enchants());
    }

    public ItemStack writeDurability(ItemStack stack, DurabilityData durability) {
        if (stack == null || durability == null) {
            return stack;
        }
        ItemStack withCurrent = write(stack, keys.durabilityCurrent(), PersistentDataType.INTEGER, durability.current());
        ItemStack withMax = write(withCurrent, keys.durabilityMax(), PersistentDataType.INTEGER, durability.max());
        mirrorVanillaDamage(withMax, durability);
        return withMax;
    }

    public Optional<DurabilityData> readDurability(ItemStack stack) {
        Optional<Integer> current = readInt(stack, keys.durabilityCurrent());
        Optional<Integer> max = readInt(stack, keys.durabilityMax());
        if (current.isEmpty() || max.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DurabilityData(current.get(), max.get()));
    }

    public ItemStack writeTrim(ItemStack stack, String patternKey, String materialKey) {
        if (stack == null) {
            return null;
        }
        ItemStack withPattern = write(stack, keys.trimPattern(), PersistentDataType.STRING, patternKey);
        return write(withPattern, keys.trimMaterial(), PersistentDataType.STRING, materialKey);
    }

    public Optional<TrimData> readTrim(ItemStack stack) {
        Optional<String> pattern = readString(stack, keys.trimPattern());
        Optional<String> material = readString(stack, keys.trimMaterial());
        if (pattern.isEmpty() || material.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TrimData(pattern.get(), material.get()));
    }

    public ItemStack setInt(ItemStack stack, NamespacedKey key, int value) {
        return write(stack, key, PersistentDataType.INTEGER, value);
    }

    private void mirrorVanillaDamage(ItemStack stack, DurabilityData durability) {
        if (!(stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            return;
        }
        int vanillaMax = stack.getType().getMaxDurability();
        if (vanillaMax <= 0) {
            return;
        }
        double fractionRemaining = durability.fraction();
        int visualDamage = (int) Math.round((1.0 - fractionRemaining) * vanillaMax);
        damageable.setDamage(Math.max(0, Math.min(visualDamage, vanillaMax)));
        stack.setItemMeta(damageable);
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
