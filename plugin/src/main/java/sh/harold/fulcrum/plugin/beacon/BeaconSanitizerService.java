package sh.harold.fulcrum.plugin.beacon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.persistence.PersistentDataType;
import sh.harold.fulcrum.plugin.stash.StashService;

import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BeaconSanitizerService {

    private static final int CHUNKS_PER_TICK = 2;
    private static final int SCAN_RADIUS_CHUNKS = 1;

    private final JavaPlugin plugin;
    private final StashService stashService;
    private final Logger logger;
    private final NamespacedKey whitelistKey;
    private final Component containerNotice = Component.text(
        "A container in your area has been detected to contain illegal items! They have been removed!",
        NamedTextColor.RED
    );
    private final Component blockNotice = Component.text(
        "There was an illegal block placed in your vicinity! It has been removed!",
        NamedTextColor.RED
    );
    private final Component inventoryNotice = Component.text(
        "You had an illegal item in your inventory/enderchest! They have been removed",
        NamedTextColor.RED
    );
    private final Queue<ChunkCoordinate> chunkQueue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkCoordinate> queuedChunks = ConcurrentHashMap.newKeySet();
    private BukkitTask scanTask;

    BeaconSanitizerService(JavaPlugin plugin, StashService stashService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stashService = Objects.requireNonNull(stashService, "stashService");
        this.logger = plugin.getLogger();
        this.whitelistKey = new NamespacedKey(plugin, "beacon_whitelist");
    }

    void start() {
        if (scanTask != null) {
            return;
        }
        scanTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drainQueue, 20L, 20L);
    }

    void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        chunkQueue.clear();
        queuedChunks.clear();
    }

    void handlePlayerLoad(Player player) {
        Objects.requireNonNull(player, "player");
        stripPlayerInventories(player);
        cleanStash(player.getUniqueId());
        enqueueChunksAround(player);
    }

    private void stripPlayerInventories(Player player) {
        PlayerInventory inventory = player.getInventory();
        int removed = BeaconStripper.stripInventory(inventory, whitelistKey);
        removed += BeaconStripper.stripInventory(player.getEnderChest(), whitelistKey);
        if (removed > 0) {
            player.sendMessage(inventoryNotice);
        }
    }

    private void cleanStash(java.util.UUID playerId) {
        stashService.purgeBeacons(playerId)
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to purge beacons from stash for " + playerId, throwable);
                return null;
            });
    }

    private void enqueueChunksAround(Player player) {
        World world = player.getWorld();
        String worldKey = world.getKey().asString();
        int centerX = player.getChunk().getX();
        int centerZ = player.getChunk().getZ();

        for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                enqueueChunk(new ChunkCoordinate(worldKey, centerX + dx, centerZ + dz));
            }
        }
    }

    private void enqueueChunk(ChunkCoordinate coordinate) {
        if (queuedChunks.add(coordinate)) {
            chunkQueue.offer(coordinate);
        }
    }

    private void drainQueue() {
        int processed = 0;
        ChunkCoordinate coordinate;
        while (processed < CHUNKS_PER_TICK && (coordinate = chunkQueue.poll()) != null) {
            queuedChunks.remove(coordinate);
            if (!processChunk(coordinate)) {
                enqueueChunk(coordinate);
                processed++;
                continue;
            }
            processed++;
        }
    }

    private boolean processChunk(ChunkCoordinate coordinate) {
        World world = plugin.getServer().getWorld(coordinate.worldKey());
        if (world == null) {
            return true;
        }

        if (!world.isChunkLoaded(coordinate.x(), coordinate.z())) {
            return false;
        }
        Chunk chunk = world.getChunkAt(coordinate.x(), coordinate.z());

        int removedBlocks = stripBeacons(chunk);
        int removedItems = stripContainerItems(chunk);
        if (removedBlocks + removedItems > 0) {
            if (removedItems > 0) {
                notifyPlayersInChunk(chunk, containerNotice);
            }
            if (removedBlocks > 0) {
                notifyPlayersInChunk(chunk, blockNotice);
            }
            logger.log(
                Level.INFO,
                "Removed " + (removedBlocks + removedItems) + " beacon(s)/stacks in chunk (" + chunk.getX() + "," + chunk.getZ() + ") of world " + world.getName()
            );
        }
        return true;
    }

    private int stripBeacons(Chunk chunk) {
        BlockState[] states = chunk.getTileEntities();
        if (states.length == 0) {
            return 0;
        }
        int removed = 0;
        for (BlockState state : states) {
            if (state.getType() == Material.BEACON) {
                if (isWhitelisted(state)) {
                    continue;
                }
                state.getBlock().setType(Material.AIR, false);
                removed++;
            }
        }
        return removed;
    }

    private int stripContainerItems(Chunk chunk) {
        BlockState[] states = chunk.getTileEntities();
        if (states.length == 0) {
            return 0;
        }
        int removed = 0;
        for (BlockState state : states) {
            if (state instanceof org.bukkit.block.Container container) {
                removed += BeaconStripper.stripInventory(container.getInventory(), whitelistKey);
            }
        }
        return removed;
    }

    void markLegitimate(ItemStack item) {
        if (item == null || item.getType() != Material.BEACON) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(whitelistKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    NamespacedKey whitelistKey() {
        return whitelistKey;
    }

    private boolean isWhitelisted(BlockState state) {
        if (!(state instanceof TileState tileState)) {
            return false;
        }
        return tileState.getPersistentDataContainer().has(whitelistKey, PersistentDataType.BYTE);
    }

    private void notifyPlayersInChunk(Chunk chunk, Component message) {
        chunk.getWorld().getPlayers().stream()
            .filter(player -> player.getChunk().getX() == chunk.getX() && player.getChunk().getZ() == chunk.getZ())
            .forEach(player -> player.sendMessage(message));
    }

    private record ChunkCoordinate(String worldKey, int x, int z) {
    }
}
