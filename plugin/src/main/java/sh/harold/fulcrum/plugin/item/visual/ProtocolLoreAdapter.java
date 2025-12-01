package sh.harold.fulcrum.plugin.item.visual;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.api.menu.impl.MenuInventoryHolder;

import java.util.List;

public final class ProtocolLoreAdapter extends PacketAdapter {

    private final ItemLoreRenderer renderer;

    private ProtocolLoreAdapter(Plugin plugin, ItemLoreRenderer renderer) {
        super(plugin, PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS);
        this.renderer = renderer;
    }

    public static ProtocolLoreAdapter register(Plugin plugin, ItemLoreRenderer renderer) {
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().warning("ProtocolLib not found; item lore rendering is disabled.");
            return null;
        }
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        ProtocolLoreAdapter adapter = new ProtocolLoreAdapter(plugin, renderer);
        manager.addPacketListener(adapter);
        return adapter;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        Player viewer = event.getPlayer();
        if (MenuInventoryHolder.isMenu(viewer.getOpenInventory().getTopInventory())) {
            return;
        }
        PacketContainer packet = event.getPacket();
        if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
            ItemStack item = packet.getItemModifier().read(0);
            ItemStack rendered = renderer.render(item, viewer);
            packet.getItemModifier().write(0, rendered);
        } else if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {
            List<ItemStack> items = packet.getItemListModifier().read(0);
            for (int i = 0; i < items.size(); i++) {
                ItemStack rendered = renderer.render(items.get(i), viewer);
                items.set(i, rendered);
            }
            packet.getItemListModifier().write(0, items);
        }
    }

    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }
}
