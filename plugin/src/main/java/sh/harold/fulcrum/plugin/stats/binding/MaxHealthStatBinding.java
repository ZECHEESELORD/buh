package sh.harold.fulcrum.plugin.stats.binding;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import sh.harold.fulcrum.plugin.stats.StatEntityResolver;
import sh.harold.fulcrum.stats.binding.StatBinding;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.service.StatChange;

import java.util.Optional;

public final class MaxHealthStatBinding implements StatBinding {

    private final StatEntityResolver entityResolver;

    public MaxHealthStatBinding(StatEntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    @Override
    public StatId getStatId() {
        return StatIds.MAX_HEALTH;
    }

    @Override
    public void onStatChanged(StatChange change) {
        Optional<LivingEntity> entity = entityResolver.findLiving(change.entity());
        if (entity.isEmpty()) {
            return;
        }

        LivingEntity living = entity.get();
        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        double newMax = Math.max(0.0, change.newValue());
        maxHealth.setBaseValue(newMax);
        if (living.getHealth() > newMax) {
            living.setHealth(newMax);
        }
    }
}
