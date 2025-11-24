package sh.harold.fulcrum.plugin.beacon;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
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
    private final Queue<ChunkCoordinate> chunkQueue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkCoordinate> queuedChunks = ConcurrentHashMap.newKeySet();
    private BukkitTask scanTask;

    BeaconSanitizerService(JavaPlugin plugin, StashService stashService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stashService = Objects.requireNonNull(stashService, "stashService");
        this.logger = plugin.getLogger();
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
        BeaconStripper.stripInventory(inventory);
        BeaconStripper.stripInventory(player.getEnderChest());
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

        Chunk chunk = world.getChunkAtIfLoaded(coordinate.x(), coordinate.z());
        if (chunk == null) {
            return false;
        }

        int removedBlocks = stripBeacons(chunk);
        int removedItems = stripContainerItems(chunk);
        if (removedBlocks + removedItems > 0) {
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
                removed += BeaconStripper.stripInventory(container.getInventory());
            }
        }
        return removed;
    }

    private record ChunkCoordinate(String worldKey, int x, int z) {
    }
}
