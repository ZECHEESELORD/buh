package sh.harold.fulcrum.plugin.beacon;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BeaconStripper {

    private static final Set<Material> ILLEGAL_ITEMS = Set.of(Material.BEACON, Material.NETHER_STAR);

    private BeaconStripper() {
    }

    public static int stripInventory(Inventory inventory, NamespacedKey whitelistKey) {
        Objects.requireNonNull(inventory, "inventory");
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            StripResult result = stripItem(item, whitelistKey);
            removed += result.removed();
            contents[slot] = result.item();
        }
        inventory.setContents(contents);
        return removed;
    }

    public static StripResult stripItem(ItemStack item, NamespacedKey whitelistKey) {
        if (item == null || item.getType().isAir()) {
            return new StripResult(null, 0);
        }
        if (isIllegal(item) && !isWhitelisted(item, whitelistKey)) {
            return new StripResult(null, item.getAmount());
        }

        ItemStack working = item.clone();
        int removed = 0;

        ItemMeta meta = working.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            removed += stripBundle(bundleMeta, whitelistKey);
            working.setItemMeta(bundleMeta);
        } else if (meta instanceof BlockStateMeta blockStateMeta) {
            removed += stripBlockState(blockStateMeta, whitelistKey);
            working.setItemMeta(blockStateMeta);
        }

        return new StripResult(working, removed);
    }

    private static int stripBundle(BundleMeta bundleMeta, NamespacedKey whitelistKey) {
        List<ItemStack> sanitized = new ArrayList<>();
        int removed = 0;

        for (ItemStack entry : bundleMeta.getItems()) {
            StripResult result = stripItem(entry, whitelistKey);
            removed += result.removed();
            if (result.item() != null && !result.item().getType().isAir()) {
                sanitized.add(result.item());
            }
        }

        bundleMeta.setItems(sanitized);
        return removed;
    }

    private static int stripBlockState(BlockStateMeta blockStateMeta, NamespacedKey whitelistKey) {
        BlockState state = blockStateMeta.getBlockState();
        if (!(state instanceof Container container)) { // includes shulker boxes
            return 0;
        }

        int removed = stripInventory(container.getInventory(), whitelistKey);
        blockStateMeta.setBlockState(state);
        return removed;
    }

    private static boolean isWhitelisted(ItemStack item, NamespacedKey whitelistKey) {
        if (whitelistKey == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(whitelistKey, PersistentDataType.BYTE);
    }

    public static boolean isIllegal(ItemStack item) {
        return ILLEGAL_ITEMS.contains(item.getType());
    }

    public record StripResult(ItemStack item, int removed) {
    }
}
