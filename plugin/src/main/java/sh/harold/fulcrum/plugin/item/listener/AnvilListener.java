package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer;

import java.util.Objects;
import java.util.function.Consumer;

public final class AnvilListener implements Listener {

    private final Plugin plugin;
    private final ItemResolver resolver;

    public AnvilListener(Plugin plugin, ItemResolver resolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        applyResult(event.getInventory(), event.getResult(), event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        if (event.getRawSlot() != 2) { // result slot
            return;
        }
        applyResult(anvil, anvil.getResult(), updated -> anvil.setItem(2, updated));
    }

    private void applyResult(AnvilInventory inventory, ItemStack vanillaResult, Consumer<ItemStack> resultConsumer) {
        if (vanillaResult == null || vanillaResult.getType().isAir()) {
            resultConsumer.accept(null);
            return;
        }
        ItemStack working = vanillaResult.clone();
        ItemResolver.EnchantMerge merge = resolver.mergeEnchants(working);
        boolean hasRightItem = inventory.getSecondItem() != null && !inventory.getSecondItem().getType().isAir();
        if (merge.removedIncompatibles() && hasRightItem) {
            resultConsumer.accept(null);
            return;
        }
        ItemStack normalized = resolver.resolve(working).map(ItemInstance::stack).orElse(working.clone());
        normalized = ItemSanitizer.normalize(normalized);
        resultConsumer.accept(normalized);
        plugin.getServer().getScheduler().runTask(plugin, () ->
            inventory.getViewers().forEach(viewer -> {
                if (viewer instanceof org.bukkit.entity.Player player) {
                    player.updateInventory();
                }
            })
        );
    }
}
