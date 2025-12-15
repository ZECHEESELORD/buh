package sh.harold.fulcrum.plugin.mob.pdc;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public record MobDataKeys(
    NamespacedKey id,
    NamespacedKey version,
    NamespacedKey tier,
    NamespacedKey statBases,
    NamespacedKey nameBase,
    NamespacedKey seed,
    NamespacedKey nameMode
) {

    public MobDataKeys(Plugin plugin) {
        this(
            new NamespacedKey(plugin, "mob-id"),
            new NamespacedKey(plugin, "mob-version"),
            new NamespacedKey(plugin, "mob-tier"),
            new NamespacedKey(plugin, "mob-stat-bases"),
            new NamespacedKey(plugin, "mob-name-base"),
            new NamespacedKey(plugin, "mob-seed"),
            new NamespacedKey(plugin, "mob-name-mode")
        );
    }
}
