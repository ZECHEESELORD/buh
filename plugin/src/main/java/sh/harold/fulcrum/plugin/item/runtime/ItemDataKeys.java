package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

final class ItemDataKeys {

    private final NamespacedKey itemId;
    private final NamespacedKey version;

    ItemDataKeys(Plugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item-id");
        this.version = new NamespacedKey(plugin, "item-version");
    }

    NamespacedKey itemId() {
        return itemId;
    }

    NamespacedKey version() {
        return version;
    }
}
