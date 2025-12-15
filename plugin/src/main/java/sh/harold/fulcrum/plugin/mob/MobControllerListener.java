package sh.harold.fulcrum.plugin.mob;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Objects;

final class MobControllerListener implements Listener {

    private final MobEngine engine;

    MobControllerListener(MobEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            engine.controllerService().handleDamage(living, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        engine.controllerService().handleDeath(entity);
    }
}

