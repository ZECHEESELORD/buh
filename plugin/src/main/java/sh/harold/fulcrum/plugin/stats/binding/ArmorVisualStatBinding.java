package sh.harold.fulcrum.plugin.stats.binding;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import sh.harold.fulcrum.plugin.stats.StatEntityResolver;
import sh.harold.fulcrum.plugin.stats.StatMappingConfig;
import sh.harold.fulcrum.stats.binding.StatBinding;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.service.StatChange;

import java.util.Optional;

public final class ArmorVisualStatBinding implements StatBinding {

    private final StatEntityResolver entityResolver;
    private final StatMappingConfig mappingConfig;

    public ArmorVisualStatBinding(StatEntityResolver entityResolver, StatMappingConfig mappingConfig) {
        this.entityResolver = entityResolver;
        this.mappingConfig = mappingConfig;
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
            double defense = Math.max(0.0, change.newValue());
            double reduction = mappingConfig == null
                ? 0.0
                : mappingConfig.maxReduction() * (1.0 - Math.exp(-defense / mappingConfig.defenseScale()));
            double barValue = Math.max(0.0, Math.min(20.0, reduction * 20.0)); // armor bar is 10 icons (20 points)
            double modifierSum = armorAttribute.getModifiers().stream()
                .mapToDouble(AttributeModifier::getAmount)
                .sum();
            double baseValue = Math.max(0.0, barValue - modifierSum);
            armorAttribute.setBaseValue(baseValue);
        }

        AttributeInstance toughnessAttribute = living.getAttribute(Attribute.ARMOR_TOUGHNESS);
        if (toughnessAttribute != null) {
            toughnessAttribute.setBaseValue(0.0);
        }
    }
}
