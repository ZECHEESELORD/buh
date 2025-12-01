package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

final class ItemDataKeys {

    private final NamespacedKey itemId;
    private final NamespacedKey version;
    private final NamespacedKey stats;
    private final NamespacedKey enchants;

    ItemDataKeys(Plugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item-id");
        this.version = new NamespacedKey(plugin, "item-version");
        this.stats = new NamespacedKey(plugin, "item-stats");
        this.enchants = new NamespacedKey(plugin, "item-enchants");
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
}
