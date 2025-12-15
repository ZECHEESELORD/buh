package sh.harold.fulcrum.plugin.mob;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.EntityType;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.Map;
import java.util.Objects;

public record MobDefinition(
    String id,
    EntityType baseType,
    MobTier tier,
    Component displayName,
    Map<StatId, Double> statBases,
    NameTagPolicy nameTagPolicy,
    MobControllerFactory controllerFactory
) {

    public MobDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseType, "baseType");
        Objects.requireNonNull(tier, "tier");
        displayName = displayName == null ? Component.empty() : displayName;
        statBases = statBases == null ? Map.of() : Map.copyOf(statBases);
        nameTagPolicy = nameTagPolicy == null ? NameTagPolicy.DENY : nameTagPolicy;
    }

    public static Builder builder(String id, EntityType baseType) {
        return new Builder(id, baseType);
    }

    public static final class Builder {
        private final String id;
        private final EntityType baseType;
        private MobTier tier = MobTier.CUSTOM;
        private Component displayName = Component.empty();
        private Map<StatId, Double> statBases = Map.of();
        private NameTagPolicy nameTagPolicy = NameTagPolicy.DENY;
        private MobControllerFactory controllerFactory;

        private Builder(String id, EntityType baseType) {
            this.id = Objects.requireNonNull(id, "id");
            this.baseType = Objects.requireNonNull(baseType, "baseType");
        }

        public Builder tier(MobTier tier) {
            this.tier = Objects.requireNonNull(tier, "tier");
            return this;
        }

        public Builder displayName(Component displayName) {
            this.displayName = displayName == null ? Component.empty() : displayName;
            return this;
        }

        public Builder statBases(Map<StatId, Double> statBases) {
            this.statBases = statBases == null ? Map.of() : Map.copyOf(statBases);
            return this;
        }

        public Builder nameTagPolicy(NameTagPolicy nameTagPolicy) {
            this.nameTagPolicy = nameTagPolicy == null ? NameTagPolicy.DENY : nameTagPolicy;
            return this;
        }

        public Builder controllerFactory(MobControllerFactory controllerFactory) {
            this.controllerFactory = controllerFactory;
            return this;
        }

        public MobDefinition build() {
            return new MobDefinition(id, baseType, tier, displayName, statBases, nameTagPolicy, controllerFactory);
        }
    }
}

