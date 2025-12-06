package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public record ItemDataKeys(
    NamespacedKey itemId,
    NamespacedKey version,
    NamespacedKey stats,
    NamespacedKey enchants,
    NamespacedKey durabilityCurrent,
    NamespacedKey durabilityMax,
    NamespacedKey trimPattern,
    NamespacedKey trimMaterial,
    NamespacedKey instanceId,
    NamespacedKey createdAt,
    NamespacedKey source
) {
    public ItemDataKeys(Plugin plugin) {
        this(
            new NamespacedKey(plugin, "item-id"),
            new NamespacedKey(plugin, "item-version"),
            new NamespacedKey(plugin, "item-stats"),
            new NamespacedKey(plugin, "item-enchants"),
            new NamespacedKey(plugin, "item-durability-current"),
            new NamespacedKey(plugin, "item-durability-max"),
            new NamespacedKey(plugin, "item-trim-pattern"),
            new NamespacedKey(plugin, "item-trim-material"),
            new NamespacedKey(plugin, "item-instance-id"),
            new NamespacedKey(plugin, "item-created-at"),
            new NamespacedKey(plugin, "item-source")
        );
    }
}
