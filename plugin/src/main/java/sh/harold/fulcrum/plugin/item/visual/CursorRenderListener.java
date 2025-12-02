package sh.harold.fulcrum.plugin.item.visual;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.api.menu.impl.MenuInventoryHolder;

import java.util.Objects;

public final class CursorRenderListener implements Listener {

    private final Plugin plugin;
    private final ItemLoreRenderer renderer;

    public CursorRenderListener(Plugin plugin, ItemLoreRenderer renderer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (MenuInventoryHolder.isMenu(player.getOpenInventory().getTopInventory())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack cursor = player.getItemOnCursor();
            if (cursor == null || cursor.getType().isAir()) {
                return;
            }
            ItemStack rendered = renderer.render(cursor, player);
            if (!rendered.equals(cursor)) {
                player.setItemOnCursor(rendered);
            }
        });
    }
}
