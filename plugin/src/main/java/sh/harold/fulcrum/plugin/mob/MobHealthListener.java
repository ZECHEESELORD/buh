package sh.harold.fulcrum.plugin.mob;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

final class MobHealthListener implements Listener {

    private final Plugin plugin;
    private final MobEngine engine;

    MobHealthListener(Plugin plugin, MobEngine engine) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (!engine.shouldShowNameplate(living)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> engine.nameplateService().refresh(living, true, true));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHeal(EntityRegainHealthEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (!engine.shouldShowNameplate(living)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> engine.nameplateService().refresh(living, true, true));
    }
}

