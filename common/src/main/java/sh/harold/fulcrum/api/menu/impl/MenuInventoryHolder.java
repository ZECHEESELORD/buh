package sh.harold.fulcrum.api.menu.impl;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import sh.harold.fulcrum.api.menu.Menu;

import java.util.Objects;

/**
 * Custom inventory holder for menus.
 * Implements Paper's InventoryHolder for exploit protection and menu identification.
 */
public class MenuInventoryHolder implements InventoryHolder {

    private final Menu menu;

    /**
     * Creates a new menu inventory holder.
     *
     * @param menu the menu this holder represents
     */
    public MenuInventoryHolder(Menu menu) {
        this.menu = Objects.requireNonNull(menu, "Menu cannot be null");
    }

    /**
     * Checks if an inventory belongs to a menu.
     *
     * @param inventory the inventory to check
     * @return true if the inventory is a menu, false otherwise
     */
    public static boolean isMenu(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        return getHolderSafely(inventory) instanceof MenuInventoryHolder;
    }

    /**
     * Gets the menu from an inventory if it is a menu inventory.
     *
     * @param inventory the inventory
     * @return the menu if this is a menu inventory, null otherwise
     */
    public static Menu getMenu(Inventory inventory) {
        InventoryHolder holder = getHolderSafely(inventory);
        if (holder instanceof MenuInventoryHolder menuHolder) {
            return menuHolder.getMenu();
        }
        return null;
    }

    @Override
    public Inventory getInventory() {
        return menu.getInventory();
    }

    /**
     * Gets the menu associated with this holder.
     *
     * @return the menu
     */
    public Menu getMenu() {
        return menu;
    }

    private static InventoryHolder getHolderSafely(Inventory inventory) {
        try {
            return inventory.getHolder(false);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }
}
