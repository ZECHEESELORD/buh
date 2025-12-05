package sh.harold.fulcrum.plugin.item.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;

import java.util.Objects;

public final class AnvilListener implements Listener {

    private final Plugin plugin;
    private final ItemResolver resolver;

    public AnvilListener(Plugin plugin, ItemResolver resolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        applyResult(event.getInventory(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        if (event.getRawSlot() != 2) { // result slot
            return;
        }
        applyResult(anvil, null);
    }

    private void applyResult(AnvilInventory inventory, PrepareAnvilEvent event) {
        ItemStack base = inventory.getFirstItem();
        if (base == null || base.getType().isAir()) {
            if (event != null) {
                event.setResult(null);
            }
            return;
        }
        ItemStack template = resolver.resolve(base).map(ItemInstance::stack).orElse(base.clone());
        ItemStack result = template.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            String rename = inventory.getRenameText();
            if (rename != null && !rename.isBlank()) {
                meta.displayName(Component.text(rename));
            }
            result.setItemMeta(meta);
        }
        result = sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(result);
        if (event != null) {
            event.setResult(result);
        } else {
            inventory.setItem(2, result);
        }
        plugin.getServer().getScheduler().runTask(plugin, () ->
            inventory.getViewers().forEach(viewer -> {
                if (viewer instanceof org.bukkit.entity.Player player) {
                    player.updateInventory();
                }
            })
        );
    }
}
