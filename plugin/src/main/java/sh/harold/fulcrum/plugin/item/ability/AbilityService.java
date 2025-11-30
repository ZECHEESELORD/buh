package sh.harold.fulcrum.plugin.item.ability;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class AbilityService {

    private final Map<String, AbilityExecutor> executors = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Instant>> cooldownsByPlayer = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public AbilityService() {
        this(Instant::now);
    }

    AbilityService(Supplier<Instant> clock) {
        this.clock = clock;
    }

    public void registerExecutor(String abilityId, AbilityExecutor executor) {
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(executor, "executor");
        executors.put(abilityId, executor);
    }

    public boolean trigger(Player player, AbilityDefinition definition, AbilityContext context) {
        if (player == null || definition == null) {
            return false;
        }
        if (onCooldown(player.getUniqueId(), definition)) {
            return false;
        }
        AbilityExecutor executor = executors.get(definition.id());
        if (executor == null) {
            return false;
        }
        markCooldown(player.getUniqueId(), definition);
        executor.execute(definition, context);
        return true;
    }

    private boolean onCooldown(UUID playerId, AbilityDefinition definition) {
        Map<String, Instant> playerCooldowns = cooldownsByPlayer.get(playerId);
        if (playerCooldowns == null) {
            return false;
        }
        Instant expiresAt = playerCooldowns.get(definition.cooldownKey());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isAfter(clock.get())) {
            return true;
        }
        playerCooldowns.remove(definition.cooldownKey());
        return false;
    }

    private void markCooldown(UUID playerId, AbilityDefinition definition) {
        Duration cooldown = Optional.ofNullable(definition.cooldown()).orElse(Duration.ZERO);
        if (cooldown.isZero() || cooldown.isNegative()) {
            return;
        }
        cooldownsByPlayer
            .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
            .put(definition.cooldownKey(), clock.get().plus(cooldown));
    }
}
