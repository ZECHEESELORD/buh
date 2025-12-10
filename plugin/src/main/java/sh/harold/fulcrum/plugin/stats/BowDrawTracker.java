package sh.harold.fulcrum.plugin.stats;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class BowDrawTracker {

    private final NamespacedKey forceKey;

    BowDrawTracker(Plugin plugin) {
        this.forceKey = new NamespacedKey(plugin, "bow_force");
    }

    void recordForce(AbstractArrow arrow, double force) {
        if (arrow == null) {
            return;
        }
        double clamped = clamp(force, 0.0, 1.0);
        arrow.getPersistentDataContainer().set(forceKey, PersistentDataType.DOUBLE, clamped);
    }

    double readForce(AbstractArrow arrow) {
        if (arrow == null) {
            return -1.0;
        }
        Double stored = arrow.getPersistentDataContainer().get(forceKey, PersistentDataType.DOUBLE);
        return stored == null ? -1.0 : clamp(stored, 0.0, 1.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
