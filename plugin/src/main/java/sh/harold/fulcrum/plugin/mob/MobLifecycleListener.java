package sh.harold.fulcrum.plugin.mob;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Objects;

final class MobLifecycleListener implements Listener {

    private final MobEngine engine;

    MobLifecycleListener(MobEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        String mobId = engine.mobPdc().readId(living).orElse(null);
        MobDefinition definition = mobId == null ? null : engine.registry().get(mobId).orElse(null);
        if (definition != null) {
            engine.lifecycleService().ensureCustomMob(living, definition);
            engine.nameplateService().refresh(living, true, true);
            engine.controllerService().ensureSpawned(living);
            return;
        }

        if (engine.lifecycleService().isHostile(living)) {
            engine.lifecycleService().ensureVanillaHostile(living);
            engine.nameplateService().refresh(living, true, true);
            return;
        }

        if (living instanceof Mob mob && mob.getTarget() instanceof Player) {
            engine.provocationService().markProvoked(living);
            return;
        }

        if (engine.mobPdc().readNameMode(living).orElse(MobNameMode.BASE) == MobNameMode.ENGINE) {
            engine.nameplateService().restoreBaseName(living);
        }
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            engine.controllerService().handleUnload(living);
            engine.provocationService().forget(living);
            engine.lifecycleService().forgetEntity(living);
            engine.nameplateService().forget(living);
        }
    }
}
