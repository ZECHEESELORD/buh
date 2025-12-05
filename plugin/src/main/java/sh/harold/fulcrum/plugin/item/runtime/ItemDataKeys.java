package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

final class ItemDataKeys {

    private final NamespacedKey itemId;
    private final NamespacedKey version;
    private final NamespacedKey stats;
    private final NamespacedKey enchants;
    private final NamespacedKey durabilityCurrent;
    private final NamespacedKey durabilityMax;
    private final NamespacedKey trimPattern;
    private final NamespacedKey trimMaterial;
    private final NamespacedKey instanceId;

    ItemDataKeys(Plugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item-id");
        this.version = new NamespacedKey(plugin, "item-version");
        this.stats = new NamespacedKey(plugin, "item-stats");
        this.enchants = new NamespacedKey(plugin, "item-enchants");
        this.durabilityCurrent = new NamespacedKey(plugin, "item-durability-current");
        this.durabilityMax = new NamespacedKey(plugin, "item-durability-max");
        this.trimPattern = new NamespacedKey(plugin, "item-trim-pattern");
        this.trimMaterial = new NamespacedKey(plugin, "item-trim-material");
        this.instanceId = new NamespacedKey(plugin, "item-instance-id");
    }

    NamespacedKey itemId() {
        return itemId;
    }

    NamespacedKey version() {
        return version;
    }

    NamespacedKey stats() {
        return stats;
    }

    NamespacedKey enchants() {
        return enchants;
    }

    NamespacedKey durabilityCurrent() {
        return durabilityCurrent;
    }

    NamespacedKey durabilityMax() {
        return durabilityMax;
    }

    NamespacedKey trimPattern() {
        return trimPattern;
    }

    NamespacedKey trimMaterial() {
        return trimMaterial;
    }

    NamespacedKey instanceId() {
        return instanceId;
    }
}
