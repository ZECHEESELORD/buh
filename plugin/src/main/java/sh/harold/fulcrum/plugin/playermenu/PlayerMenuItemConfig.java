package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.Material;

public record PlayerMenuItemConfig(Material material, int slot) {

    public static final String MATERIAL_PATH = "inventory.menuItem.material";
    public static final String SLOT_PATH = "inventory.menuItem.slot";
    public static final int DEFAULT_SLOT = 8;
    public static final PlayerMenuItemConfig DEFAULT = new PlayerMenuItemConfig(Material.NETHER_STAR, DEFAULT_SLOT);

    public PlayerMenuItemConfig {
        material = sanitize(material);
        slot = sanitizeSlot(slot);
    }

    public static PlayerMenuItemConfig of(Material material) {
        return new PlayerMenuItemConfig(material, DEFAULT_SLOT);
    }

    public PlayerMenuItemConfig withSlot(int newSlot) {
        return new PlayerMenuItemConfig(material, newSlot);
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

    private int sanitizeSlot(int value) {
        if (value < 0 || value > 35) {
            return DEFAULT_SLOT;
        }
        return value;
    }
}
