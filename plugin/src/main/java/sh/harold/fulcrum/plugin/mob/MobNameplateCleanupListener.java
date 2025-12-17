package sh.harold.fulcrum.plugin.mob;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Objects;

final class MobNameplateCleanupListener implements Listener {

    private final MobEngine engine;

    MobNameplateCleanupListener(MobEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            engine.nameplateService().cleanupLabelEntity(entity);
        }
    }
}
