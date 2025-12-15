package sh.harold.fulcrum.plugin.mob;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class VanillaMobStatSeeder {

    Map<StatId, Double> seedBases(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        Map<StatId, Double> bases = new HashMap<>();

        double maxHealth = attributeBase(entity, Attribute.MAX_HEALTH, entity.getMaxHealth());
        bases.put(StatIds.MAX_HEALTH, Math.max(0.0, maxHealth));

        double attackDamage = attributeBase(entity, Attribute.ATTACK_DAMAGE, 1.0);
        bases.put(StatIds.ATTACK_DAMAGE, Math.max(0.0, attackDamage));

        double armor = attributeBase(entity, Attribute.ARMOR, 0.0);
        bases.put(StatIds.ARMOR, Math.max(0.0, armor));

        double speed = attributeBase(entity, Attribute.MOVEMENT_SPEED, 0.1);
        bases.put(StatIds.MOVEMENT_SPEED, Math.max(0.0, speed));

        return Map.copyOf(bases);
    }

    private double attributeBase(LivingEntity entity, Attribute attribute, double fallback) {
        if (entity == null || attribute == null) {
            return fallback;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return fallback;
        }
        double base = instance.getBaseValue();
        if (!Double.isFinite(base)) {
            return fallback;
        }
        return base;
    }
}

