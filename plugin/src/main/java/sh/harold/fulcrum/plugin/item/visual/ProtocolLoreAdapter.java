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
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.api.menu.impl.MenuInventoryHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;

public final class ProtocolLoreAdapter extends PacketAdapter {

    private final ItemLoreRenderer renderer;
    private final Logger logger;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final Set<UUID> disabledViewers = ConcurrentHashMap.newKeySet();

    private ProtocolLoreAdapter(Plugin plugin, ItemLoreRenderer renderer) {
        // TODO: re-enable ENTITY_EQUIPMENT once ProtocolLib handles 1.21.11 equipment packets without client decode errors.
        super(plugin, PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS);
        this.renderer = renderer;
        this.logger = plugin.getLogger();
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
        if (viewer != null && isDisabled(viewer.getUniqueId())) {
            return;
        }
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
            Integer entityId = packet.getIntegers().readSafely(0);
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> list = packet.getSlotStackPairLists().read(0);
            for (int i = 0; i < list.size(); i++) {
                Pair<EnumWrappers.ItemSlot, ItemStack> pair = list.get(i);
                ItemStack rendered = renderer.render(pair.getSecond(), viewer);
                list.set(i, new Pair<>(pair.getFirst(), rendered));
            }
            logEquipment(viewer, entityId, list);
            packet.getSlotStackPairLists().write(0, list);
        }
    }

    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
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

    private void logEquipment(Player viewer, Integer entityId, List<Pair<EnumWrappers.ItemSlot, ItemStack>> entries) {
        if (!logger.isLoggable(Level.INFO)) {
            return;
        }
        String joined = entries.stream()
            .map(pair -> pair.getFirst() + " -> " + summarize(pair.getSecond()))
            .collect(Collectors.joining(", "));
        logger.log(
            Level.INFO,
            "Entity equipment packet viewer={0}({1}) entityId={2} slots={3}: {4}",
            new Object[]{
                viewer.getName(),
                viewer.getUniqueId(),
                entityId == null ? "unknown" : entityId,
                entries.size(),
                joined
            }
        );
    }

    private String summarize(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return "air";
        }
        var meta = stack.getItemMeta();
        String name = meta != null && meta.hasDisplayName() && meta.displayName() != null
            ? PLAIN.serialize(meta.displayName())
            : "";
        int loreLines = meta != null && meta.hasLore() && meta.lore() != null ? meta.lore().size() : 0;
        return stack.getType().name() + "x" + stack.getAmount()
            + (name.isBlank() ? "" : " name=\"" + name + "\"")
            + " lore=" + loreLines;
    }
}
