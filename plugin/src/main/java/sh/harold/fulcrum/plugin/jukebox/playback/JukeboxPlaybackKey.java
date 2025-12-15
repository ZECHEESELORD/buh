package sh.harold.fulcrum.plugin.jukebox.playback;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public record JukeboxPlaybackKey(UUID worldId, int x, int y, int z) {

    public JukeboxPlaybackKey {
        Objects.requireNonNull(worldId, "worldId");
    }

    public static JukeboxPlaybackKey fromLocation(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "world");
        return new JukeboxPlaybackKey(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}

