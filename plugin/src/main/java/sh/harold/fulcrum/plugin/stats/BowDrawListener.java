package sh.harold.fulcrum.plugin.stats;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;

import java.util.Objects;

public final class BowDrawListener implements Listener {

    private final BowDrawTracker tracker;

    public BowDrawListener(BowDrawTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        tracker.recordForce(arrow, event.getForce());
    }
}
