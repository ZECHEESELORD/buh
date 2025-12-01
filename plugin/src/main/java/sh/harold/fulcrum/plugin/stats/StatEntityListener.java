package sh.harold.fulcrum.plugin.stats;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import sh.harold.fulcrum.stats.core.StatContainer;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

public final class StatEntityListener implements Listener {

    private final StatService statService;

    public StatEntityListener(StatService statService) {
        this.statService = statService;
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        StatContainer container = statService.getContainer(EntityKey.fromUuid(living.getUniqueId()));
        bootstrapFromAttributes(living, container);
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            statService.removeContainer(EntityKey.fromUuid(living.getUniqueId()));
        }
    }

    private void bootstrapFromAttributes(LivingEntity living, StatContainer container) {
        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            container.setBase(StatIds.MAX_HEALTH, maxHealth.getValue());
        }

        AttributeInstance armor = living.getAttribute(Attribute.ARMOR);
        if (armor != null) {
            container.setBase(StatIds.ARMOR, armor.getValue());
        }

        AttributeInstance attackSpeed = living.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            container.setBase(StatIds.ATTACK_SPEED, attackSpeed.getValue());
        }

        AttributeInstance attackDamage = living.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            container.setBase(StatIds.ATTACK_DAMAGE, attackDamage.getValue());
        }
    }
}
