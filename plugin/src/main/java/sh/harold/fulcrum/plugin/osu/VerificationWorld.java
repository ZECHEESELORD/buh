package sh.harold.fulcrum.plugin.osu;

import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Random;

final class VerificationWorld {

    private static final String WORLD_NAME = "verification";
    private static final int PLATFORM_Y = 64;

    private final World world;
    private final Location spawn;

    VerificationWorld(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.world = ensureWorld(plugin);
        this.spawn = ensureSpawn(world);
    }

    World world() {
        return world;
    }

    Location spawn() {
        return spawn.clone();
    }

    private World ensureWorld(JavaPlugin plugin) {
        WorldCreator creator = new WorldCreator(WORLD_NAME)
            .environment(World.Environment.NORMAL)
            .generateStructures(false)
            .type(WorldType.FLAT)
            .generator(new VoidChunkGenerator());
        World created = creator.createWorld();
        if (created == null) {
            throw new IllegalStateException("Failed to create verification world");
        }
        created.setDifficulty(Difficulty.PEACEFUL);
        created.setPVP(false);
        created.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        created.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        created.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        created.setGameRule(GameRule.KEEP_INVENTORY, true);
        return created;
    }

    private Location ensureSpawn(World world) {
        Location spawn = new Location(world, 0.5, PLATFORM_Y + 1, 0.5);
        world.setSpawnLocation(spawn);
        world.getBlockAt(0, PLATFORM_Y, 0).setType(Material.BEDROCK);
        world.getBlockAt(1, PLATFORM_Y, 0).setType(Material.BARRIER);
        world.getBlockAt(-1, PLATFORM_Y, 0).setType(Material.BARRIER);
        world.getBlockAt(0, PLATFORM_Y, 1).setType(Material.BARRIER);
        world.getBlockAt(0, PLATFORM_Y, -1).setType(Material.BARRIER);
        return spawn;
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}
