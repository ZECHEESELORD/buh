package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.ItemEngine;

import java.util.Objects;

public final class CreativeSanitizerListener implements Listener {

    private final ItemEngine itemEngine;

    public CreativeSanitizerListener(ItemEngine itemEngine) {
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE || event.getNewGameMode() == GameMode.CREATIVE) {
            return;
        }
        Player player = event.getPlayer();
        sanitizeInventory(player);
    }

    private void sanitizeInventory(Player player) {
        var resolver = itemEngine.resolver();
        var renderer = itemEngine.loreRenderer();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemStack cleaned = sanitize(stack, resolver, renderer, player);
            if (cleaned != stack) {
                inventory.setItem(slot, cleaned);
            }
        }
        ItemStack off = inventory.getItemInOffHand();
        ItemStack cleanedOff = sanitize(off, resolver, renderer, player);
        if (cleanedOff != off) {
            inventory.setItemInOffHand(cleanedOff);
        }
    }

    private ItemStack sanitize(ItemStack stack, sh.harold.fulcrum.plugin.item.runtime.ItemResolver resolver, sh.harold.fulcrum.plugin.item.visual.ItemLoreRenderer renderer, Player viewer) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        return renderer.render(stack, viewer);
    }
}
