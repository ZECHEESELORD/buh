package sh.harold.fulcrum.plugin.beacon;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BeaconStripper {

    private BeaconStripper() {
    }

    static int stripInventory(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            StripResult result = stripItem(item);
            removed += result.removed();
            contents[slot] = result.item();
        }
        inventory.setContents(contents);
        return removed;
    }

    public static StripResult stripItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new StripResult(null, 0);
        }
        if (item.getType() == Material.BEACON) {
            return new StripResult(null, item.getAmount());
        }

        ItemStack working = item.clone();
        int removed = 0;

        ItemMeta meta = working.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            removed += stripBundle(bundleMeta);
            working.setItemMeta(bundleMeta);
        } else if (meta instanceof BlockStateMeta blockStateMeta) {
            removed += stripBlockState(blockStateMeta);
            working.setItemMeta(blockStateMeta);
        }

        return new StripResult(working, removed);
    }

    private static int stripBundle(BundleMeta bundleMeta) {
        List<ItemStack> sanitized = new ArrayList<>();
        int removed = 0;

        for (ItemStack entry : bundleMeta.getItems()) {
            StripResult result = stripItem(entry);
            removed += result.removed();
            if (result.item() != null && !result.item().getType().isAir()) {
                sanitized.add(result.item());
            }
        }

        bundleMeta.setItems(sanitized);
        return removed;
    }

    private static int stripBlockState(BlockStateMeta blockStateMeta) {
        BlockState state = blockStateMeta.getBlockState();
        if (!(state instanceof Container container)) { // includes shulker boxes
            return 0;
        }

        int removed = stripInventory(container.getInventory());
        blockStateMeta.setBlockState(state);
        return removed;
    }

    public record StripResult(ItemStack item, int removed) {
    }
}
