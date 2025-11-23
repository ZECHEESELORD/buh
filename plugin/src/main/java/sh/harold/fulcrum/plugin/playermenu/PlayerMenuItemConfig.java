package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.Material;

public record PlayerMenuItemConfig(Material material) {

    public static final String PATH = "inventory.menuItem.material";
    public static final PlayerMenuItemConfig DEFAULT = new PlayerMenuItemConfig(Material.NETHER_STAR);

    public PlayerMenuItemConfig {
        material = sanitize(material);
    }

    public static PlayerMenuItemConfig of(Material material) {
        return new PlayerMenuItemConfig(material);
    }

    public boolean isValid() {
        return material != null && !material.isAir();
    }

    private Material sanitize(Material value) {
        if (value == null || value.isAir()) {
            return Material.NETHER_STAR;
        }
        return value;
    }
}
