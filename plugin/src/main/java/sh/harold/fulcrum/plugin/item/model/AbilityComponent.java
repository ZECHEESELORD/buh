package sh.harold.fulcrum.plugin.item.model;

import sh.harold.fulcrum.plugin.item.ability.AbilityDefinition;
import sh.harold.fulcrum.plugin.item.ability.AbilityTrigger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AbilityComponent implements ItemComponent {

    private final Map<AbilityTrigger, AbilityDefinition> abilitiesByTrigger;

    public AbilityComponent(Map<AbilityTrigger, AbilityDefinition> abilitiesByTrigger) {
        Objects.requireNonNull(abilitiesByTrigger, "abilitiesByTrigger");
        this.abilitiesByTrigger = Collections.unmodifiableMap(new EnumMap<>(abilitiesByTrigger));
    }

    public Map<AbilityTrigger, AbilityDefinition> abilities() {
        return abilitiesByTrigger;
    }

    public Optional<AbilityDefinition> abilityFor(AbilityTrigger trigger) {
        return Optional.ofNullable(abilitiesByTrigger.get(trigger));
    }
}
