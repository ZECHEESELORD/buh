package sh.harold.fulcrum.plugin.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

public interface MobController {

    default void onSpawn(LivingEntity entity) {
    }

    default void onUnload(LivingEntity entity) {
    }

    default void tick(LivingEntity entity, long tick) {
    }

    default void onDamage(LivingEntity entity, EntityDamageEvent event) {
    }

    default void onDeath(LivingEntity entity) {
    }
}

