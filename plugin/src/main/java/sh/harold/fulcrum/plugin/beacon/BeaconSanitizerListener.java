package sh.harold.fulcrum.plugin.beacon;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class BeaconSanitizerListener implements Listener {

    private final BeaconSanitizerService sanitizerService;
    private final Set<UUID> playerLoadedChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    BeaconSanitizerListener(BeaconSanitizerService sanitizerService) {
        this.sanitizerService = Objects.requireNonNull(sanitizerService, "sanitizerService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        sanitizerService.handlePlayerLoad(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        sanitizerService.handlePlayerLoad(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        sanitizerService.markLegitimate(result);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack current = event.getCurrentItem();
        sanitizerService.markLegitimate(current);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != org.bukkit.Material.BEACON) {
            return;
        }
        ItemStack inHand = event.getItemInHand();
        if (inHand == null || inHand.getType() != org.bukkit.Material.BEACON) {
            return;
        }
        if (inHand.hasItemMeta() && inHand.getItemMeta().getPersistentDataContainer().has(sanitizerService.whitelistKey(), PersistentDataType.BYTE)) {
            var state = event.getBlockPlaced().getState();
            if (state instanceof org.bukkit.block.TileState tile) {
                tile.getPersistentDataContainer().set(sanitizerService.whitelistKey(), PersistentDataType.BYTE, (byte) 1);
                tile.update(true, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() == org.bukkit.entity.EntityType.WITHER) {
            event.getDrops().forEach(sanitizerService::markLegitimate);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        var inventory = event.getInventory();
        if (inventory.getHolder() instanceof org.bukkit.block.Container container) {
            int removed = sanitizerService.stripContainer(container);
            if (removed > 0) {
                event.getPlayer().sendMessage(sanitizerService.containerNotice());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            return;
        }
        if (isPlayerLoaded(event.getChunk())) {
            sanitizerService.enqueueChunk(event.getChunk());
        }
    }

    private boolean isPlayerLoaded(org.bukkit.Chunk chunk) {
        UUID key = chunk.getWorld().getUID();
        return chunk.getWorld().getPlayers().stream()
            .anyMatch(player -> player.getChunk().getX() == chunk.getX() && player.getChunk().getZ() == chunk.getZ());
    }
}
