package sh.harold.fulcrum.plugin.item.ability;

import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AbilityDefinition(
    String id,
    AbilityTrigger trigger,
    Component displayName,
    List<Component> description,
    Duration cooldown,
    String cooldownKey
) {

    public AbilityDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(displayName, "displayName");
        description = description == null ? List.of() : List.copyOf(description);
        cooldown = Optional.ofNullable(cooldown).orElse(Duration.ZERO);
        cooldownKey = cooldownKey == null || cooldownKey.isBlank() ? id : cooldownKey;
    }
}
