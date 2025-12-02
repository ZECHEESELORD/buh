package sh.harold.fulcrum.plugin.item.visual;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.api.menu.impl.MenuInventoryHolder;

import java.util.List;

public final class ProtocolLoreAdapter extends PacketAdapter {

    private final ItemLoreRenderer renderer;

    private ProtocolLoreAdapter(Plugin plugin, ItemLoreRenderer renderer) {
        super(plugin, PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.ENTITY_EQUIPMENT);
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
        boolean isMenu = MenuInventoryHolder.isMenu(viewer.getOpenInventory().getTopInventory());
        int topSize = viewer.getOpenInventory().getTopInventory() == null ? 0 : viewer.getOpenInventory().getTopInventory().getSize();
        PacketContainer packet = event.getPacket();
        if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
            int windowId = packet.getIntegers().readSafely(0);
            int slot = packet.getIntegers().readSafely(2);
            boolean isCursor = windowId == -1;
            boolean render = isCursor || windowId == 0 || !(isMenu && windowId != 0 && slot < topSize);
            if (render) {
                ItemStack item = packet.getItemModifier().read(0);
                ItemStack rendered = renderer.render(item, viewer);
                packet.getItemModifier().write(0, rendered);
            }
        } else if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {
            int windowId = packet.getIntegers().readSafely(0);
            List<ItemStack> items = packet.getItemListModifier().read(0);
            boolean isCursor = windowId == -1;
            if (isCursor || windowId == 0 || !isMenu || items.size() < topSize) {
                for (int i = 0; i < items.size(); i++) {
                    items.set(i, renderer.render(items.get(i), viewer));
                }
            } else {
                for (int i = topSize; i < items.size(); i++) {
                    items.set(i, renderer.render(items.get(i), viewer));
                }
            }
            packet.getItemListModifier().write(0, items);
        } else if (packet.getType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> list = packet.getSlotStackPairLists().read(0);
            for (int i = 0; i < list.size(); i++) {
                Pair<EnumWrappers.ItemSlot, ItemStack> pair = list.get(i);
                ItemStack rendered = renderer.render(pair.getSecond(), viewer);
                list.set(i, new Pair<>(pair.getFirst(), rendered));
            }
            packet.getSlotStackPairLists().write(0, list);
        }
    }

    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }
}
