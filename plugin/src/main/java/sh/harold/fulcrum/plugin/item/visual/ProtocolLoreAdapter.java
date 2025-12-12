package sh.harold.fulcrum.plugin.item.visual;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import org.bukkit.entity.Player;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.api.menu.impl.MenuInventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtocolLoreAdapter extends PacketListenerAbstract {

    private final ItemLoreRenderer renderer;
    private final Set<UUID> disabledViewers = ConcurrentHashMap.newKeySet();

    private ProtocolLoreAdapter(Plugin plugin, ItemLoreRenderer renderer) {
        this.renderer = renderer;
    }

    public static ProtocolLoreAdapter register(Plugin plugin, ItemLoreRenderer renderer) {
        ProtocolLoreAdapter adapter = new ProtocolLoreAdapter(plugin, renderer);
        PacketEvents.getAPI().getEventManager().registerListener(adapter);
        return adapter;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Object handle = event.getPlayer();
        if (!(handle instanceof Player viewer)) {
            return;
        }
        if (isDisabled(viewer.getUniqueId())) {
            return;
        }

        boolean isMenu = MenuInventoryHolder.isMenu(viewer.getOpenInventory().getTopInventory());
        int topSize = viewer.getOpenInventory().getTopInventory() == null ? 0 : viewer.getOpenInventory().getTopInventory().getSize();

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
            int windowId = packet.getWindowId();
            int slot = packet.getSlot();
            boolean isCursor = windowId == -1;
            boolean render = isCursor || windowId == 0 || !(isMenu && windowId != 0 && slot < topSize);
            if (!render) {
                return;
            }
            ItemStack item = SpigotConversionUtil.toBukkitItemStack(packet.getItem());
            ItemStack rendered = renderer.render(item, viewer);
            packet.setItem(SpigotConversionUtil.fromBukkitItemStack(rendered));
            return;
        }

        if (event.getPacketType() != PacketType.Play.Server.WINDOW_ITEMS) {
            if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
                List<Equipment> equipmentList = packet.getEquipment();
                if (equipmentList == null || equipmentList.isEmpty()) {
                    return;
                }
                List<Equipment> renderedList = new ArrayList<>(equipmentList.size());
                boolean mutated = false;
                for (Equipment equipment : equipmentList) {
                    if (equipment == null || equipment.getItem() == null) {
                        renderedList.add(equipment);
                        continue;
                    }
                    var rendered = render(equipment.getItem(), viewer);
                    if (!rendered.equals(equipment.getItem())) {
                        mutated = true;
                        renderedList.add(new Equipment(equipment.getSlot(), rendered));
                    } else {
                        renderedList.add(equipment);
                    }
                }
                if (mutated) {
                    packet.setEquipment(renderedList);
                }
            }
            return;
        }

        WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
        int windowId = packet.getWindowId();
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ArrayList<>(packet.getItems());
        boolean isCursor = windowId == -1;

        if (isCursor || windowId == 0 || !isMenu || items.size() < topSize) {
            for (int i = 0; i < items.size(); i++) {
                items.set(i, render(items.get(i), viewer));
            }
        } else {
            for (int i = topSize; i < items.size(); i++) {
                items.set(i, render(items.get(i), viewer));
            }
        }
        packet.setItems(items);
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    public void disable(UUID playerId) {
        if (playerId != null) {
            disabledViewers.add(playerId);
        }
    }

    public void enable(UUID playerId) {
        if (playerId != null) {
            disabledViewers.remove(playerId);
        }
    }

    public boolean toggle(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (disabledViewers.contains(playerId)) {
            disabledViewers.remove(playerId);
            return true;
        }
        disabledViewers.add(playerId);
        return false;
    }

    public boolean isDisabled(UUID playerId) {
        return playerId != null && disabledViewers.contains(playerId);
    }

    private com.github.retrooper.packetevents.protocol.item.ItemStack render(
        com.github.retrooper.packetevents.protocol.item.ItemStack stack,
        Player viewer
    ) {
        ItemStack bukkit = SpigotConversionUtil.toBukkitItemStack(stack);
        ItemStack rendered = renderer.render(bukkit, viewer);
        return SpigotConversionUtil.fromBukkitItemStack(rendered);
    }
}
