package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.Material;

public record PlayerMenuItemConfig(Material material, int slot, boolean enabled) {

    public static final String MATERIAL_PATH = "inventory.menuItem.material";
    public static final String SLOT_PATH = "inventory.menuItem.slot";
    public static final String ENABLED_PATH = "inventory.menuItem.enabled";
    public static final int DEFAULT_SLOT = 8;
    public static final PlayerMenuItemConfig DEFAULT = new PlayerMenuItemConfig(Material.NETHER_STAR, DEFAULT_SLOT, true);

    public PlayerMenuItemConfig {
        material = sanitize(material);
        slot = sanitizeSlot(slot);
    }

    public static PlayerMenuItemConfig of(Material material) {
        return new PlayerMenuItemConfig(material, DEFAULT_SLOT, true);
    }

    public PlayerMenuItemConfig withSlot(int newSlot) {
        return new PlayerMenuItemConfig(material, newSlot, enabled);
    }

    public PlayerMenuItemConfig withEnabled(boolean newEnabled) {
        return new PlayerMenuItemConfig(material, slot, newEnabled);
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
