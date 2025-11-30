package sh.harold.fulcrum.plugin.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class ItemKeys {

    private final NamespacedKey idKey;

    public ItemKeys(Plugin plugin) {
        this.idKey = new NamespacedKey(plugin, "item-id");
    }

    public NamespacedKey idKey() {
        return idKey;
    }
}
