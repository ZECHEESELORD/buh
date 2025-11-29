package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Consumer;

public record ParticleTrailCosmetic(UnlockableDefinition definition, Consumer<Player> executor) implements Cosmetic {

    public ParticleTrailCosmetic {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(executor, "executor");
        if (definition.type() != UnlockableType.COSMETIC) {
            throw new IllegalArgumentException("Particle trails must be cosmetics: " + definition.id());
        }
    }

    @Override
    public CosmeticSection section() {
        return CosmeticSection.PARTICLE_TRAIL;
    }

    public void execute(Player player) {
        Objects.requireNonNull(player, "player");
        executor.accept(player);
    }
}
