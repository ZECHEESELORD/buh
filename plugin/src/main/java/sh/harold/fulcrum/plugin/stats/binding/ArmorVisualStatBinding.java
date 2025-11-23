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

public final class ArmorVisualStatBinding implements StatBinding {

    private final StatEntityResolver entityResolver;

    public ArmorVisualStatBinding(StatEntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    @Override
    public StatId getStatId() {
        return StatIds.ARMOR;
    }

    @Override
    public void onStatChanged(StatChange change) {
        Optional<LivingEntity> entity = entityResolver.findLiving(change.entity());
        if (entity.isEmpty()) {
            return;
        }

        LivingEntity living = entity.get();
        AttributeInstance armorAttribute = living.getAttribute(Attribute.ARMOR);
        if (armorAttribute != null) {
            armorAttribute.setBaseValue(Math.max(0.0, change.newValue()));
        }

        AttributeInstance toughnessAttribute = living.getAttribute(Attribute.ARMOR_TOUGHNESS);
        if (toughnessAttribute != null) {
            toughnessAttribute.setBaseValue(0.0);
        }
    }
}
